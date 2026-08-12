package com.oops.calendar.service;

import com.oops.calendar.config.FmpProperties;
import com.oops.calendar.dto.EarningsEvent;
import com.oops.calendar.dto.EarningsResponse;
import com.oops.calendar.dto.Session;
import com.oops.calendar.provider.EarningsProvider;
import com.oops.calendar.provider.FmpEarningsProvider;
import com.oops.calendar.provider.MockEarningsProvider;
import com.oops.calendar.provider.UpstreamUnavailableException;
import com.oops.calendar.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 财报服务:参数校验、数据源选择、FMP 失败自动降级(mock 保底)、内存缓存、上游节流。
 * <p>
 * 降级语义:FMP 上游任何失败(连接/超时/4xx/5xx/解析失败)对该请求回退
 * MockEarningsProvider,并记录降级状态;之后在降级冷却期内直接走 mock,
 * 冷却期结束后自动重试一次 FMP,成功则恢复真实数据。
 */
@Service
public class EarningsService {

    private static final Logger log = LoggerFactory.getLogger(EarningsService.class);

    private final FmpProperties props;
    private final EarningsProvider provider;
    private final MockEarningsProvider mockProvider;
    private final Map<String, CachedRange> cache = new ConcurrentHashMap<>();
    private final AtomicLong lastUpstreamCallNanos = new AtomicLong(0);

    private volatile boolean degraded = false;
    private volatile String degradationReason = null;
    private volatile Instant degradedAt = null;

    public EarningsService(FmpProperties props,
                           FmpEarningsProvider fmpProvider,
                           MockEarningsProvider mockProvider) {
        this.props = props;
        this.provider = props.hasApiKey() ? fmpProvider : mockProvider;
        this.mockProvider = mockProvider;
        log.info("Earnings provider active: {}", this.provider.source());
    }

    /** 启动时轻量探测 FMP(仅真实模式):失败即进入降级态,保证 /api/health 第一时间如实反映。 */
    @PostConstruct
    public void probeUpstream() {
        if (!"fmp".equals(provider.source())) {
            return;
        }
        try {
            LocalDate today = LocalDate.now();
            provider.fetch(today, today);
            log.info("FMP upstream probe ok, provider={}", provider.source());
        } catch (UpstreamUnavailableException e) {
            markDegraded(e);
        } catch (Exception e) {
            // 防御:任何意外异常都不应阻断启动
            log.warn("FMP upstream probe unexpected failure, degraded to mock");
            degraded = true;
            degradationReason = "FMP 上游探测失败";
            degradedAt = Instant.now();
        }
    }

    public EarningsProvider activeProvider() {
        return provider;
    }

    /** 是否处于降级态(健康检查用)。 */
    public boolean isDegraded() {
        return degraded;
    }

    /** 降级原因摘要(健康检查用),未降级时为 null。 */
    public String degradationReason() {
        return degradationReason;
    }

    /**
     * 查询 [from, to] 区间财报,按日期+代码升序。
     * FMP 失败时对该请求回退 mock,并记录降级状态。
     */
    public EarningsResponse query(LocalDate from, LocalDate to) {
        validateRange(from, to);
        String cacheKey = from + "|" + to;
        long ttlMs = props.getCacheTtlSeconds() * 1000;

        CachedRange cached = cache.get(cacheKey);
        if (cached != null && !cached.expired(ttlMs)) {
            return new EarningsResponse(from.toString(), to.toString(), cached.events.size(),
                    cached.source, cached.events);
        }

        String source;
        List<EarningsEvent> events;
        if (degraded && !retryCooldownElapsed()) {
            // 降级冷却期内:直接走 mock,不再打上游
            source = "mock";
            events = mockProvider.fetch(from, to);
        } else {
            try {
                throttleIfNeeded();
                events = new ArrayList<>(provider.fetch(from, to));
                source = provider.source();
                if (degraded) {
                    // 上游恢复,退出降级态
                    degraded = false;
                    degradationReason = null;
                    degradedAt = null;
                    log.info("FMP upstream recovered, back to provider={}", source);
                }
            } catch (UpstreamUnavailableException e) {
                markDegraded(e);
                source = "mock";
                events = mockProvider.fetch(from, to);
            }
        }

        events.sort(Comparator.comparing((EarningsEvent e) -> e.date)
                .thenComparing(e -> e.symbol, String.CASE_INSENSITIVE_ORDER));
        cache.put(cacheKey, new CachedRange(events, Instant.now(), source));
        return new EarningsResponse(from.toString(), to.toString(), events.size(), source, events);
    }

    /**
     * 单股查询:默认查今天前后各 30 天。与区间接口共享同一降级逻辑。
     */
    public EarningsResponse querySymbol(String symbol, LocalDate from, LocalDate to) {
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new ApiException(400, "股票代码不能为空");
        }
        EarningsResponse all = query(from, to);
        List<EarningsEvent> filtered = all.events.stream()
                .filter(e -> e.symbol != null && e.symbol.equalsIgnoreCase(symbol.trim()))
                .collect(Collectors.toList());
        return new EarningsResponse(all.from, all.to, filtered.size(), all.source, filtered);
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new ApiException(400, "参数 from 与 to 必填,格式 YYYY-MM-DD");
        }
        if (from.isAfter(to)) {
            throw new ApiException(400, "from 不能晚于 to");
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days > props.getMaxRangeDays()) {
            throw new ApiException(400, "查询区间不能超过 " + props.getMaxRangeDays() + " 天");
        }
    }

    public static LocalDate parseDate(String value, String paramName) {
        if (value == null || value.trim().isEmpty()) {
            throw new ApiException(400, "参数 " + paramName + " 必填,格式 YYYY-MM-DD");
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new ApiException(400, "参数 " + paramName + " 格式非法:" + value + " (应为 YYYY-MM-DD)");
        }
    }

    private boolean retryCooldownElapsed() {
        // 冷却已过:当前时刻 >= degradedAt + retryMs(用 !isAfter 保证 0ms 冷却时立即重试)
        return degradedAt == null
                || !degradedAt.plusMillis(props.getDegradedRetryMs()).isAfter(Instant.now());
    }

    /** 记录降级状态,日志只记录简短摘要,严禁倾倒原始响应体。 */
    private void markDegraded(UpstreamUnavailableException e) {
        degraded = true;
        degradationReason = e.getMessage();
        degradedAt = Instant.now();
        log.warn("FMP upstream unavailable ({}), fallback to mock provider", e.getMessage());
    }

    /** FMP 免费档限流保护:距上次上游调用不足最小间隔时等待。 */
    private void throttleIfNeeded() {
        if (!"fmp".equals(provider.source())) {
            return;
        }
        long minIntervalNanos = props.getMinRequestIntervalMs() * 1_000_000L;
        while (true) {
            long last = lastUpstreamCallNanos.get();
            long now = System.nanoTime();
            long waitNanos = last + minIntervalNanos - now;
            if (waitNanos <= 0) {
                if (lastUpstreamCallNanos.compareAndSet(last, now)) {
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

    private static class CachedRange {
        final List<EarningsEvent> events;
        final Instant createdAt;
        final String source;

        CachedRange(List<EarningsEvent> events, Instant createdAt, String source) {
            this.events = events;
            this.createdAt = createdAt;
            this.source = source;
        }

        boolean expired(long ttlMs) {
            return createdAt.plusMillis(ttlMs).isBefore(Instant.now());
        }
    }
}
