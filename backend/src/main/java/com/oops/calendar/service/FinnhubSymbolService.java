package com.oops.calendar.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oops.calendar.config.FinnhubProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Finnhub 全量美股上市公司列表(symbol -> 公司全称)。
 * GET {base}/stock/symbol?exchange=US&token=KEY,一次返回约 1 万家公司,内存缓存 24 小时。
 * 加载失败静默降级(仅知名公司表兜底),不阻塞主流程。
 */
@Service
public class FinnhubSymbolService {

    private static final Logger log = LoggerFactory.getLogger(FinnhubSymbolService.class);
    private static final long CACHE_TTL_SECONDS = 24 * 3600;

    private final FinnhubProperties props;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile Map<String, String> symbolNames;
    private volatile Instant loadedAt;
    private volatile boolean loading = false;

    @Autowired
    public FinnhubSymbolService(FinnhubProperties props) {
        this(props, null);
    }

    /** 测试用:允许注入外部 RestTemplate(如 MockRestServiceServer 绑定)。 */
    FinnhubSymbolService(FinnhubProperties props, RestTemplate restTemplate) {
        this.props = props;
        if (restTemplate != null) {
            this.restTemplate = restTemplate;
        } else {
            org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                    new org.springframework.http.client.SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(props.getConnectTimeoutMs());
            factory.setReadTimeout(props.getReadTimeoutMs());
            this.restTemplate = new RestTemplate(factory);
        }
    }

    /** 查询公司全称;未加载或失败返回 null。 */
    public String nameOf(String symbol) {
        if (symbol == null || !props.hasApiKey()) {
            return null;
        }
        Map<String, String> map = getOrLoad();
        return map == null ? null : map.get(symbol.toUpperCase());
    }

    private Map<String, String> getOrLoad() {
        Map<String, String> map = symbolNames;
        if (map != null && loadedAt != null
                && !loadedAt.plusSeconds(CACHE_TTL_SECONDS).isBefore(Instant.now())) {
            return map;
        }
        synchronized (this) {
            map = symbolNames;
            if (map != null && loadedAt != null
                    && !loadedAt.plusSeconds(CACHE_TTL_SECONDS).isBefore(Instant.now())) {
                return map;
            }
            if (loading) {
                return map; // 已有线程在加载,直接用旧数据(可能为 null)
            }
            loading = true;
            try {
                symbolNames = fetchAll();
                loadedAt = Instant.now();
                log.info("Finnhub symbol list loaded: {} companies", symbolNames.size());
                return symbolNames;
            } catch (Exception e) {
                log.warn("Finnhub symbol list load failed, known-companies table only: {}", e.getMessage());
                return null;
            } finally {
                loading = false;
            }
        }
    }

    private Map<String, String> fetchAll() {
        String url = UriComponentsBuilder.fromHttpUrl(props.getBaseUrl())
                .path("/stock/symbol")
                .queryParam("exchange", "US")
                .queryParam("token", props.getApiKey())
                .build()
                .toUriString();

        ResponseEntity<String> response;
        try {
            response = restTemplate.getForEntity(url, String.class);
        } catch (RestClientException e) {
            throw new RuntimeException("Finnhub symbol list request failed", e);
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new RuntimeException("Finnhub symbol list parse failed", e);
        }
        if (root == null || !root.isArray()) {
            throw new RuntimeException("Finnhub symbol list: unexpected response shape");
        }

        Map<String, String> names = new HashMap<>(root.size() * 2);
        for (JsonNode node : root) {
            String symbol = node.has("symbol") ? node.get("symbol").asText() : null;
            String desc = node.has("description") ? node.get("description").asText() : null;
            if (symbol != null && !symbol.isEmpty() && desc != null && !desc.isEmpty()) {
                names.put(symbol.toUpperCase(), desc);
            }
        }
        return names;
    }
}
