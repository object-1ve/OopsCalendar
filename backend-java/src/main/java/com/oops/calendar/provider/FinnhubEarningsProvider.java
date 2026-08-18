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
import java.time.temporal.ChronoUnit;
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

    /** Finnhub 单次最多返回 1500 条,超过会被截断(丢最旧)。 */
    private static final int MAX_PER_REQUEST = 1500;
    /** 分段拉取窗口:3 天一段并行请求,避免单次超限截断。 */
    private static final int WINDOW_DAYS = 3;
    private final java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(8);

    @javax.annotation.PreDestroy
    void shutdown() {
        pool.shutdownNow();
    }

    @Override
    public List<EarningsEvent> fetch(LocalDate from, LocalDate to) {
        // 按 WINDOW_DAYS 切窗,并行拉取再合并,保证整段完整(如整月拉取时早期日期不会被 1500 上限截掉)
        List<java.util.concurrent.Future<List<EarningsEvent>>> futures = new ArrayList<>();
        LocalDate start = from;
        while (!start.isAfter(to)) {
            LocalDate end = start.plusDays(WINDOW_DAYS - 1);
            if (end.isAfter(to)) {
                end = to;
            }
            final LocalDate wStart = start;
            final LocalDate wEnd = end;
            futures.add(pool.submit(() -> fetchWindow(wStart, wEnd)));
            start = end.plusDays(1);
        }
        List<EarningsEvent> events = new ArrayList<>();
        for (java.util.concurrent.Future<List<EarningsEvent>> f : futures) {
            try {
                events.addAll(f.get());
            } catch (java.util.concurrent.ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException) {
                    throw (RuntimeException) cause;
                }
                throw new UpstreamUnavailableException("Finnhub 分段拉取失败", cause);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new UpstreamUnavailableException("Finnhub 分段拉取被中断", e);
            }
        }
        log.info("Finnhub earnings calendar: {} events in [{}, {}]", events.size(), from, to);
        return events;
    }

    /** 拉取一个窗口;若仍达到 1500 上限(极端峰值),按天补查保证完整。 */
    private List<EarningsEvent> fetchWindow(LocalDate from, LocalDate to) {
        List<EarningsEvent> events = fetchOnce(from, to);
        if (events.size() >= MAX_PER_REQUEST && from.isBefore(to)) {
            List<EarningsEvent> merged = new ArrayList<>();
            LocalDate d = from;
            while (!d.isAfter(to)) {
                merged.addAll(fetchOnce(d, d));
                d = d.plusDays(1);
            }
            return merged;
        }
        return events;
    }

    private List<EarningsEvent> fetchOnce(LocalDate from, LocalDate to) {
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
            // Finnhub 营收为美元,FMP/mock 为百万美元;统一为百万美元口径
            BigDecimal revenueActual = toMillions(decimalOrNull(node, "revenueActual"));
            BigDecimal revenueEstimate = toMillions(decimalOrNull(node, "revenueEstimate"));
            boolean confirmed = epsActual != null || revenueActual != null;
            Session session = Session.fromFmpTime(textOrNull(node, "hour"));
            events.add(new EarningsEvent(date, symbol, null, session, confirmed,
                    epsActual, epsEstimate, revenueActual, revenueEstimate, source()));
        }
        return events;
    }

    /** 美元 -> 百万美元(2 位小数),保持与 FMP/mock 口径一致。 */
    private BigDecimal toMillions(BigDecimal dollars) {
        if (dollars == null) {
            return null;
        }
        return dollars.divide(BigDecimal.valueOf(1_000_000), 2, java.math.RoundingMode.HALF_UP);
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
