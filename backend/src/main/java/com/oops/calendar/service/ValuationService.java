package com.oops.calendar.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oops.calendar.config.FinnhubProperties;
import com.oops.calendar.dto.EarningsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 市盈率(PE)服务:按日期对当日财报公司拉取 Finnhub /stock/metric 的 peTTM。
 * 免费档 60 次/分钟,逐家调用带 1s 节流;结果内存缓存 1 小时。
 * 为避免一次拉几百家(需数分钟),默认只对内置知名公司表内的公司拉取。
 */
@Service
public class ValuationService {

    private static final Logger log = LoggerFactory.getLogger(ValuationService.class);
    private static final long CACHE_TTL_SECONDS = 3600;

    private final FinnhubProperties props;
    private final EarningsService earningsService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final AtomicLong lastCallNanos = new AtomicLong(0);

    @Autowired
    public ValuationService(FinnhubProperties props, EarningsService earningsService) {
        this(props, earningsService, null);
    }

    /** 测试用:允许注入外部 RestTemplate。 */
    ValuationService(FinnhubProperties props, EarningsService earningsService, RestTemplate restTemplate) {
        this.props = props;
        this.earningsService = earningsService;
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

    /** 某日财报公司的 PE(仅内置知名公司),{symbol -> peTTM}。 */
    public Map<String, BigDecimal> valuationsForDate(LocalDate date) {
        String key = date.toString();
        CacheEntry cached = cache.get(key);
        if (cached != null && !cached.expired()) {
            return cached.values;
        }
        if (!props.hasApiKey()) {
            return java.util.Collections.emptyMap();
        }

        List<EarningsEvent> events = earningsService.query(date, date).events;
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (EarningsEvent e : events) {
            KnownCompanies.CompanyInfo known = KnownCompanies.get(e.symbol);
            if (known == null) {
                continue; // 免费档限流:只对知名公司拉取
            }
            BigDecimal pe = fetchPe(e.symbol);
            if (pe != null) {
                result.put(e.symbol, pe);
            }
        }
        cache.put(key, new CacheEntry(result, Instant.now()));
        log.info("Valuation for {}: {} companies with PE", date, result.size());
        return result;
    }

    /** 拉取单家公司 peTTM;失败返回 null(不阻塞)。 */
    private BigDecimal fetchPe(String symbol) {
        throttle();
        String url = UriComponentsBuilder.fromHttpUrl(props.getBaseUrl())
                .path("/stock/metric")
                .queryParam("symbol", symbol)
                .queryParam("metric", "all")
                .queryParam("token", props.getApiKey())
                .build()
                .toUriString();
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode metric = root != null ? root.get("metric") : null;
            JsonNode pe = metric != null ? metric.get("peTTM") : null;
            if (pe == null || pe.isNull() || !pe.isNumber()) {
                return null;
            }
            return pe.decimalValue();
        } catch (RestClientException | java.io.IOException e) {
            log.warn("PE fetch failed for {}: {}", symbol, e.getMessage());
            return null;
        }
    }

    private void throttle() {
        long minIntervalNanos = 1_000_000_000L; // 1s,免费档 60/min
        while (true) {
            long last = lastCallNanos.get();
            long now = System.nanoTime();
            long waitNanos = last + minIntervalNanos - now;
            if (waitNanos <= 0) {
                if (lastCallNanos.compareAndSet(last, now)) {
                    return;
                }
            } else {
                try {
                    Thread.sleep(waitNanos / 1_000_000L + 1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private static class CacheEntry {
        final Map<String, BigDecimal> values;
        final Instant createdAt;

        CacheEntry(Map<String, BigDecimal> values, Instant createdAt) {
            this.values = values;
            this.createdAt = createdAt;
        }

        boolean expired() {
            return createdAt.plusSeconds(CACHE_TTL_SECONDS).isBefore(Instant.now());
        }
    }
}
