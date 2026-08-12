package com.oops.calendar.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oops.calendar.config.FmpProperties;
import com.oops.calendar.dto.EarningsEvent;
import com.oops.calendar.dto.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Financial Modeling Prep 真实数据源。
 * GET {base}/earnings-calendar?from=YYYY-MM-DD&to=YYYY-MM-DD&apikey=KEY
 * time 字段:bmo=盘前, amc=盘后, dnh=盘中。
 * 已公布判定:eps 或 revenue 任一非空(实际值已发布)。
 * <p>
 * 任何上游失败(连接/超时/4xx/5xx/解析失败/错误响应体)都会抛出
 * {@link UpstreamUnavailableException},消息为安全简短摘要,不包含原始响应体,
 * 由 EarningsService 捕获后回退 MockEarningsProvider。
 */
@Component
public class FmpEarningsProvider implements EarningsProvider {

    private static final Logger log = LoggerFactory.getLogger(FmpEarningsProvider.class);

    private final FmpProperties props;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /** Spring 装配入口:保留原有行为,委托到包内可见构造器。 */
    @Autowired
    public FmpEarningsProvider(FmpProperties props) {
        this(props, null);
    }

    /** 测试用:允许注入外部 RestTemplate(如 MockRestServiceServer 绑定)。不参与 Spring 装配。 */
    FmpEarningsProvider(FmpProperties props, RestTemplate restTemplate) {
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
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String source() {
        return "fmp";
    }

    @Override
    public List<EarningsEvent> fetch(LocalDate from, LocalDate to) {
        String url = UriComponentsBuilder.fromHttpUrl(props.getBaseUrl())
                .path("/earnings-calendar")
                .queryParam("from", from)
                .queryParam("to", to)
                .queryParam("apikey", props.getApiKey())
                .build()
                .toUriString();

        ResponseEntity<String> response;
        try {
            response = restTemplate.getForEntity(url, String.class);
        } catch (HttpClientErrorException e) {
            // 4xx:含 401/403(Key 无效)。e.getMessage() 可能含响应体,只记录状态码。
            String reason = e.getStatusCode() == HttpStatus.UNAUTHORIZED || e.getStatusCode() == HttpStatus.FORBIDDEN
                    ? "FMP API Key 无效(HTTP " + e.getStatusCode().value() + ")"
                    : "FMP 上游拒绝请求(HTTP " + e.getStatusCode().value() + ")";
            log.warn("FMP upstream request failed: {}", reason);
            throw new UpstreamUnavailableException(reason, e);
        } catch (HttpServerErrorException e) {
            // 5xx
            String reason = "FMP 上游服务异常(HTTP " + e.getStatusCode().value() + ")";
            log.warn("FMP upstream request failed: {}", reason);
            throw new UpstreamUnavailableException(reason, e);
        } catch (ResourceAccessException e) {
            // 连接/超时等 IO 类错误
            String reason = ioFailureReason(e);
            log.warn("FMP upstream request failed: {}", reason);
            throw new UpstreamUnavailableException(reason, e);
        } catch (RestClientException e) {
            String reason = "FMP 上游请求失败";
            log.warn("FMP upstream request failed: {}", reason);
            throw new UpstreamUnavailableException(reason, e);
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            log.warn("FMP upstream response parse failed");
            throw new UpstreamUnavailableException("FMP 上游响应解析失败", e);
        }

        if (!root.isArray()) {
            // FMP 错误响应形如 {"Error Message": "..."} 或 {"error": ...}。
            // 严禁把原始错误体(含 FAQ URL 等)透出或写入日志,只保留安全分类。
            String raw = root.has("Error Message") ? root.get("Error Message").asText()
                    : root.has("error") ? root.get("error").asText() : null;
            String reason = classifyErrorBody(raw);
            log.warn("FMP upstream returned error response: {}", reason);
            throw new UpstreamUnavailableException(reason);
        }

        List<EarningsEvent> events = new ArrayList<>();
        for (JsonNode node : root) {
            String date = textOrNull(node, "date");
            String symbol = textOrNull(node, "symbol");
            if (date == null || symbol == null) {
                continue; // 防御:跳过缺关键字段的记录
            }
            BigDecimal eps = decimalOrNull(node, "eps");
            BigDecimal epsEst = decimalOrNull(node, "epsEstimated");
            BigDecimal revenue = decimalOrNull(node, "revenue");
            BigDecimal revenueEst = decimalOrNull(node, "revenueEstimated");
            boolean confirmed = eps != null || revenue != null;
            Session session = Session.fromFmpTime(textOrNull(node, "time"));
            events.add(new EarningsEvent(date, symbol, null, session, confirmed,
                    eps, epsEst, revenue, revenueEst, source()));
        }
        log.info("FMP earnings-calendar: {} events in [{}, {}]", events.size(), from, to);
        return events;
    }

    /** 从 ResourceAccessException 的根因提炼安全的中文摘要。 */
    private String ioFailureReason(ResourceAccessException e) {
        Throwable cause = e.getCause();
        while (cause != null) {
            if (cause instanceof SocketTimeoutException) {
                return "FMP 请求超时";
            }
            if (cause instanceof ConnectException) {
                return "无法连接 FMP 服务";
            }
            cause = cause.getCause();
        }
        return "FMP 网络请求失败";
    }

    /** 从 FMP 错误响应体提炼安全分类,绝不回显原始文本。 */
    private String classifyErrorBody(String raw) {
        if (raw == null) {
            return "FMP 上游返回错误响应";
        }
        String lower = raw.toLowerCase();
        if (lower.contains("invalid api key") || lower.contains("invalid key")) {
            return "FMP API Key 无效(请检查 FMP_API_KEY 配置)";
        }
        if (lower.contains("limit") || lower.contains("quota") || lower.contains("rate") || lower.contains("max")) {
            return "FMP 免费档请求次数已用尽";
        }
        return "FMP 上游返回错误响应";
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private BigDecimal decimalOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || !v.isNumber()) {
            return null;
        }
        try {
            return v.decimalValue();
        } catch (NumberFormatException e) {
            // 防御:极端数值(如 1e999 解析为 Infinity)不应导致解析崩溃
            return null;
        }
    }
}
