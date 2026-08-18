package com.oops.calendar.service;

import com.oops.calendar.config.FmpProperties;
import com.oops.calendar.config.FinnhubProperties;
import com.oops.calendar.dto.EarningsEvent;
import com.oops.calendar.dto.EarningsResponse;
import com.oops.calendar.dto.Session;
import com.oops.calendar.provider.EarningsProvider;
import com.oops.calendar.provider.FinnhubEarningsProvider;
import com.oops.calendar.provider.FmpEarningsProvider;
import com.oops.calendar.provider.MockEarningsProvider;
import com.oops.calendar.provider.UpstreamUnavailableException;
import com.oops.calendar.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final EnrichmentService enrichmentService;
    private final EarningsPersistenceService persistence;
    private final Map<String, CachedRange> cache = new ConcurrentHashMap<>();
    private final AtomicLong lastUpstreamCallNanos = new AtomicLong(0);

    private volatile boolean degraded = false;
    private volatile String degradationReason = null;
    private volatile Instant degradedAt = null;

    /** 测试用构造器:不启用数据库持久化(persistence=null)。 */
    public EarningsService(FmpProperties props,
                           FinnhubProperties finnhubProps,
                           EnrichmentService enrichmentService,
                           FmpEarningsProvider fmpProvider,
                           FinnhubEarningsProvider finnhubProvider,
                           MockEarningsProvider mockProvider) {
        this(props, finnhubProps, enrichmentService, fmpProvider, finnhubProvider, mockProvider, null);
    }

    @Autowired
    public EarningsService(FmpProperties props,
                           FinnhubProperties finnhubProps,
                           EnrichmentService enrichmentService,
                           FmpEarningsProvider fmpProvider,
                           FinnhubEarningsProvider finnhubProvider,
                           MockEarningsProvider mockProvider,
                           EarningsPersistenceService persistence) {
        this.props = props;
        // 数据源优先级:FINNHUB_API_KEY(免费档数据完整)> FMP_API_KEY > mock(演示)
        // 用户同时配置两个 key 时自动使用 Finnhub,无需手动删除 FMP key。
        this.provider = finnhubProps.hasApiKey() ? finnhubProvider
                : props.hasApiKey() ? fmpProvider
                : mockProvider;
        this.mockProvider = mockProvider;
        this.enrichmentService = enrichmentService;
        this.persistence = persistence;
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
        return query(from, to, false);
    }

    /**
     * 查询 [from, to] 区间财报。refresh=true 时绕过缓存强制拉取上游,
     * 用于"单独刷新某一天"等需要最新数据的场景。
     * <p>
     * 全局刷新语义:过去日期(今天之前)的财报已公布、内容不会再变,
     * 不回源强制刷新;只对"今天及之后"强制回源,过去部分复用已有缓存。
     */
    public EarningsResponse query(LocalDate from, LocalDate to, boolean refresh) {
        validateRange(from, to);
        String cacheKey = from + "|" + to;
        long ttlMs = props.getCacheTtlSeconds() * 1000;

        if (!refresh) {
            CachedRange cached = cache.get(cacheKey);
            if (cached != null && !cached.expired(ttlMs)) {
                return new EarningsResponse(from.toString(), to.toString(), cached.events.size(),
                        cached.source, cached.events);
            }
            // 内存缓存未命中:尝试从数据库读回(持久化二级缓存),避免后端重启后整月回源
            if (persistence != null) {
                EarningsPersistenceService.LoadedRange fromDb = persistence.loadRange(from, to, ttlMs);
                if (fromDb != null) {
                    // 历史库可能残留并发重叠写入产生的完全重复行,读回时兜底去重
                    List<EarningsEvent> deduped = dedupeExact(fromDb.events);
                    deduped.sort(Comparator.comparing((EarningsEvent e) -> e.date)
                            .thenComparing(e -> e.symbol, String.CASE_INSENSITIVE_ORDER));
                    cache.put(cacheKey, new CachedRange(deduped, Instant.now(), fromDb.source));
                    return new EarningsResponse(from.toString(), to.toString(), deduped.size(),
                            fromDb.source, deduped);
                }
            }
        }

        // 区间跨越今天之前且是强制刷新:拆分为"过去段(复用缓存) + 今天及之后(强制回源)"
        if (refresh && from.isBefore(LocalDate.now())) {
            return queryRefreshSplittingPast(from, to);
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

        return storeAndRespond(from, to, source, events);
    }

    /**
     * 全局刷新(区间跨越今天之前):拆分为两段合并返回。
     * <ul>
     *   <li>过去段 [from, today-1]:优先复用整段缓存中的历史事件(财报已公布不再变化,
     *       即使缓存过期也无需重拉);整段缓存不存在时退化为普通查询拉取一次补齐。</li>
     *   <li>今天及之后 [today, to]:refresh=true 强制回源,拿到最新数据。</li>
     * </ul>
     * 过去段与未来段来源可能不同(缓存来源 vs 本次来源),以未来段来源为准。
     */
    private EarningsResponse queryRefreshSplittingPast(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now();
        List<EarningsEvent> merged = new ArrayList<>();
        String source = null;

        if (from.isBefore(today)) {
            LocalDate pastEnd = to.isBefore(today) ? to : today.minusDays(1);
            CachedRange cached = cache.get(from + "|" + to);
            if (cached != null) {
                // 复用整段缓存:只取今天之前的条目,内容不变无需回源
                for (EarningsEvent e : cached.events) {
                    if (e.date.compareTo(pastEnd.toString()) <= 0) {
                        merged.add(e);
                    }
                }
                source = cached.source;
            } else {
                // 无整段缓存(异常场景,如首次即强制刷新):普通查询拉取一次补齐
                EarningsResponse past = query(from, pastEnd);
                merged.addAll(past.events);
                source = past.source;
            }
        }
        if (!to.isBefore(today)) {
            EarningsResponse future = query(today, to, true);
            merged.addAll(future.events);
            source = future.source;
        }

        return storeAndRespond(from, to, source, merged);
    }

    /** 排序、富化、写入缓存并返回响应(查询与全局刷新的公共收尾)。 */
    private EarningsResponse storeAndRespond(LocalDate from, LocalDate to, String source, List<EarningsEvent> events) {
        // 兜底去重:并发/重叠写入可能让同一份数据重复入库,响应前去除完全相同的副本
        events = dedupeExact(events);
        events.sort(Comparator.comparing((EarningsEvent e) -> e.date)
                .thenComparing(e -> e.symbol, String.CASE_INSENSITIVE_ORDER));
        // 富化:补充公司全称与行业分类(富化结果随缓存保存,不重复拉取;重复调用幂等)
        enrichmentService.enrich(events);
        cache.put(from + "|" + to, new CachedRange(events, Instant.now(), source));
        // 落库(仅真实数据源;mock 演示数据不写库,避免污染持久化数据)
        if (persistence != null && !"mock".equals(source)) {
            try {
                persistence.replaceRange(from, to, source, events);
            } catch (Exception e) {
                log.warn("财报数据持久化失败: {}", e.getMessage());
            }
        }
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

    /** 限流保护(免费档):距上次上游调用不足最小间隔时等待。FMP 与 Finnhub 均适用。 */
    private void throttleIfNeeded() {
        if ("mock".equals(provider.source())) {
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

    /**
     * 去除完全重复的事件(同一日期/代码/时段/已公布/实际值与预估全同),
     * 仅去掉完全相同副本;保留 Finnhub 原生"占位 + 真实"成对数据
     * (如 TGT 同日一条空占位 + 一条含数据,内容不同不应合并)。
     */
    static List<EarningsEvent> dedupeExact(List<EarningsEvent> events) {
        if (events == null || events.size() < 2) {
            return events;
        }
        Set<String> seen = new HashSet<>();
        List<EarningsEvent> out = new ArrayList<>(events.size());
        for (EarningsEvent e : events) {
            String key = e.date + '\u0000' + e.symbol + '\u0000' + (e.session == null ? "" : e.session.name())
                    + '\u0000' + e.confirmed + '\u0000' + e.eps + '\u0000' + e.epsEstimated
                    + '\u0000' + e.revenue + '\u0000' + e.revenueEstimated + '\u0000' + e.source;
            if (seen.add(key)) {
                out.add(e);
            }
        }
        return out;
    }
}
