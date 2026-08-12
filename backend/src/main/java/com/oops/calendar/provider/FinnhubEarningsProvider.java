package com.oops.calendar.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oops.calendar.config.FinnhubProperties;
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
 * Finnhub 真实数据源(免费 key,覆盖完整)。
 * GET {base}/calendar/earnings?from=YYYY-MM-DD&to=YYYY-MM-DD&token=KEY
 * 响应 {"earningsCalendar":[{date, symbol, hour(bmo/amc/dmh), epsActual, epsEstimate, revenueActual, revenueEstimate}]}
 * 已公布判定:epsActual 或 revenueActual 任一非空。
 * 任何上游失败抛 {@link UpstreamUnavailableException},由 EarningsService 降级。
 */
@Component
public class FinnhubEarningsProvider implements EarningsProvider {

    private static final Logger log = LoggerFactory.getLogger(FinnhubEarningsProvider.class);

    private final FinnhubProperties props;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public FinnhubEarningsProvider(FinnhubProperties props) {
        this(props, null);
    }

    /** 测试用:允许注入外部 RestTemplate(如 MockRestServiceServer 绑定)。 */
    FinnhubEarningsProvider(FinnhubProperties props, RestTemplate restTemplate) {
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
        return "finnhub";
    }

    @Override
    public List<EarningsEvent> fetch(LocalDate from, LocalDate to) {
        String url = UriComponentsBuilder.fromHttpUrl(props.getBaseUrl())
                .path("/calendar/earnings")
                .queryParam("from", from)
                .queryParam("to", to)
                .queryParam("token", props.getApiKey())
                .build()
                .toUriString();

        ResponseEntity<String> response;
        try {
            response = restTemplate.getForEntity(url, String.class);
        } catch (HttpClientErrorException e) {
            String reason = e.getStatusCode() == HttpStatus.UNAUTHORIZED || e.getStatusCode() == HttpStatus.FORBIDDEN
                    ? "Finnhub API Key 无效(HTTP " + e.getStatusCode().value() + ")"
                    : "Finnhub 上游拒绝请求(HTTP " + e.getStatusCode().value() + ")";
            log.warn("Finnhub upstream request failed: {}", reason);
            throw new UpstreamUnavailableException(reason, e);
        } catch (HttpServerErrorException e) {
            String reason = "Finnhub 上游服务异常(HTTP " + e.getStatusCode().value() + ")";
            log.warn("Finnhub upstream request failed: {}", reason);
            throw new UpstreamUnavailableException(reason, e);
        } catch (ResourceAccessException e) {
            String reason = ioFailureReason(e);
            log.warn("Finnhub upstream request failed: {}", reason);
            throw new UpstreamUnavailableException(reason, e);
        } catch (RestClientException e) {
            log.warn("Finnhub upstream request failed");
            throw new UpstreamUnavailableException("Finnhub 上游请求失败", e);
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            log.warn("Finnhub upstream response parse failed");
            throw new UpstreamUnavailableException("Finnhub 上游响应解析失败", e);
        }

        JsonNode calendar = root != null ? root.get("earningsCalendar") : null;
        if (calendar == null || !calendar.isArray()) {
            // 错误形如 {"error": "..."}
            String err = root != null && root.has("error") ? root.get("error").asText() : null;
            throw new UpstreamUnavailableException(err != null ? "Finnhub 返回错误:" + err : "Finnhub 上游返回错误响应");
        }

        List<EarningsEvent> events = new ArrayList<>();
        for (JsonNode node : calendar) {
            String date = textOrNull(node, "date");
            String symbol = textOrNull(node, "symbol");
            if (date == null || symbol == null) {
                continue; // 防御:跳过缺关键字段的记录
            }
            BigDecimal epsActual = decimalOrNull(node, "epsActual");
            BigDecimal epsEstimate = decimalOrNull(node, "epsEstimate");
            BigDecimal revenueActual = decimalOrNull(node, "revenueActual");
            BigDecimal revenueEstimate = decimalOrNull(node, "revenueEstimate");
            boolean confirmed = epsActual != null || revenueActual != null;
            Session session = Session.fromFmpTime(textOrNull(node, "hour"));
            events.add(new EarningsEvent(date, symbol, null, session, confirmed,
                    epsActual, epsEstimate, revenueActual, revenueEstimate, source()));
        }
        log.info("Finnhub earnings calendar: {} events in [{}, {}]", events.size(), from, to);
        return events;
    }

    private String ioFailureReason(ResourceAccessException e) {
        Throwable cause = e.getCause();
        while (cause != null) {
            if (cause instanceof SocketTimeoutException) {
                return "Finnhub 请求超时";
            }
            if (cause instanceof ConnectException) {
                return "无法连接 Finnhub 服务";
            }
            cause = cause.getCause();
        }
        return "Finnhub 网络请求失败";
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
            return null; // 防御:极端数值不应导致解析崩溃
        }
    }
}
