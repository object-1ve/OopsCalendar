package com.oops.calendar.service;

import com.oops.calendar.config.FinnhubProperties;
import com.oops.calendar.config.FmpProperties;
import com.oops.calendar.dto.EarningsEvent;
import com.oops.calendar.dto.EarningsResponse;
import com.oops.calendar.dto.Session;
import com.oops.calendar.provider.FinnhubEarningsProvider;
import com.oops.calendar.provider.FmpEarningsProvider;
import com.oops.calendar.provider.MockEarningsProvider;
import com.oops.calendar.provider.UpstreamUnavailableException;
import com.oops.calendar.web.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarningsServiceTest {

    private FmpProperties props;
    private EarningsService service;

    @BeforeEach
    void setUp() {
        props = new FmpProperties();
        props.setApiKey(""); // mock 模式
        service = new EarningsService(props, new FinnhubProperties(),
                new FmpEarningsProvider(props), new FinnhubEarningsProvider(new FinnhubProperties()),
                new MockEarningsProvider());
    }

    @Test
    void mockProviderActiveWithoutKey() {
        assertEquals("mock", service.activeProvider().source());
    }

    @Test
    void queryReturnsSortedDates() {
        EarningsResponse resp = service.query(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        assertTrue(resp.count > 0);
        assertEquals("mock", resp.source);
        String prev = "";
        for (EarningsEvent e : resp.events) {
            assertTrue(e.date.compareTo(prev) >= 0, "日期应升序: " + prev + " -> " + e.date);
            prev = e.date;
        }
    }

    @Test
    void queryCachesSameListInstance() {
        EarningsResponse a = service.query(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        EarningsResponse b = service.query(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        assertSame(a.events, b.events, "缓存命中应返回同一列表实例");
    }

    @Test
    void invalidRangeRejected() {
        assertThrows(ApiException.class, () ->
                service.query(LocalDate.of(2026, 8, 31), LocalDate.of(2026, 8, 1)));
        assertThrows(ApiException.class, () ->
                service.query(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));
    }

    @Test
    void querySymbolFiltersCaseInsensitive() {
        EarningsResponse all = service.query(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        if (all.count == 0) {
            return;
        }
        String some = all.events.get(0).symbol;
        EarningsResponse one = service.querySymbol(some, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        assertTrue(one.count >= 1);
        for (EarningsEvent e : one.events) {
            assertEquals(some.toUpperCase(), e.symbol.toUpperCase());
        }
    }

    @Test
    void parseDateRejectsBadFormat() {
        assertThrows(ApiException.class, () -> EarningsService.parseDate("2026/08/01", "from"));
        assertThrows(ApiException.class, () -> EarningsService.parseDate("", "from"));
        assertEquals(LocalDate.of(2026, 8, 1), EarningsService.parseDate("2026-08-01", "from"));
    }

    // ---------- 降级回退 ----------

    /** FMP 前 N 次 fetch 抛 UpstreamUnavailableException,之后返回一条 fmp 数据。 */
    private static class FlakyFmpProvider extends FmpEarningsProvider {
        final String reason;
        final int failForCalls;
        int calls = 0;

        FlakyFmpProvider(FmpProperties props, String reason, int failForCalls) {
            super(props);
            this.reason = reason;
            this.failForCalls = failForCalls;
        }

        @Override
        public List<EarningsEvent> fetch(LocalDate from, LocalDate to) {
            calls++;
            if (calls <= failForCalls) {
                throw new UpstreamUnavailableException(reason);
            }
            return Collections.singletonList(new EarningsEvent(from.toString(), "TEST", "Test Corp",
                    Session.BMO, true, new BigDecimal("1.23"), new BigDecimal("1.20"),
                    new BigDecimal("1000"), new BigDecimal("950"), "fmp"));
        }
    }

    private EarningsService failingService(int failForCalls) {
        props.setApiKey("dummy-key");
        props.setDegradedRetryMs(0);
        props.setMinRequestIntervalMs(0); // 测试中禁用上游节流等待
        FlakyFmpProvider fmp = new FlakyFmpProvider(props, "FMP API Key 无效(请检查 FMP_API_KEY 配置)", failForCalls);
        return new EarningsService(props, new FinnhubProperties(), fmp,
                new FinnhubEarningsProvider(new FinnhubProperties()), new MockEarningsProvider());
    }

    @Test
    void fmpFailureFallsBackToMockAndRecordsDegraded() {
        EarningsService svc = failingService(Integer.MAX_VALUE);
        LocalDate from = LocalDate.of(2026, 8, 3);
        LocalDate to = LocalDate.of(2026, 8, 7);

        EarningsResponse resp = svc.query(from, to);

        assertEquals("mock", resp.source, "FMP 失败时应对该请求回退 mock");
        assertTrue(resp.count > 0, "mock 数据应完整");
        for (EarningsEvent e : resp.events) {
            assertEquals("mock", e.source, "事件应标记为 mock 来源");
        }
        assertTrue(svc.isDegraded(), "应记录降级状态");
        assertTrue(svc.degradationReason().contains("FMP API Key 无效"),
                "降级原因应为安全摘要: " + svc.degradationReason());
    }

    @Test
    void degradedCooldownSkipsUpstreamThenRecovers() {
        // 前 1 次失败,之后成功;冷却期为 0,下次查询即重试上游
        EarningsService svc = failingService(1);
        LocalDate aFrom = LocalDate.of(2026, 8, 3), aTo = LocalDate.of(2026, 8, 3);
        LocalDate bFrom = LocalDate.of(2026, 8, 4), bTo = LocalDate.of(2026, 8, 4);

        EarningsResponse first = svc.query(aFrom, aTo);
        assertEquals("mock", first.source);
        assertTrue(svc.isDegraded());

        // 冷却期(0ms)已过,新区间应重试上游并恢复
        EarningsResponse second = svc.query(bFrom, bTo);
        assertEquals("fmp", second.source, "上游恢复后应切回真实数据");
        assertFalse(svc.isDegraded(), "恢复后应退出降级态");
    }

    @Test
    void querySymbolFallsBackToMockLikeInterval() {
        EarningsService svc = failingService(Integer.MAX_VALUE);
        EarningsResponse resp = svc.querySymbol("AAPL",
                LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 7));
        assertEquals("mock", resp.source, "单股接口与区间接口降级行为应一致");
        assertTrue(svc.isDegraded());
        for (EarningsEvent e : resp.events) {
            assertEquals("AAPL", e.symbol.toUpperCase());
        }
    }

    // ---------- 上游节流(CAS 串行化) ----------

    /** 计数 + 记录调用时间戳的 FMP provider;fetch 内模拟上游延迟,保证缓存写入晚于并发读。 */
    private static class CountingFmpProvider extends FmpEarningsProvider {
        static final long SIMULATED_UPSTREAM_MS = 150L;
        final AtomicInteger calls = new AtomicInteger();
        final AtomicLong firstCallNanos = new AtomicLong();
        final AtomicLong lastCallNanos = new AtomicLong();

        CountingFmpProvider(FmpProperties props) {
            super(props);
        }

        @Override
        public List<EarningsEvent> fetch(LocalDate from, LocalDate to) {
            int n = calls.incrementAndGet();
            long now = System.nanoTime();
            if (n == 1) {
                firstCallNanos.set(now);
            }
            lastCallNanos.set(now);
            try {
                Thread.sleep(SIMULATED_UPSTREAM_MS); // 拉长首个请求,保证第二个线程的 cache.get 发生在其 cache.put 之前
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return Collections.singletonList(new EarningsEvent(from.toString(), "TEST", "Test Corp",
                    Session.BMO, true, new BigDecimal("1.23"), new BigDecimal("1.20"),
                    new BigDecimal("1000"), new BigDecimal("950"), "fmp"));
        }
    }

    @Test
    void concurrentQueriesThrottleSerializedUpstreamCalls() throws Exception {
        FmpProperties p = new FmpProperties();
        p.setApiKey("dummy-key");
        p.setMinRequestIntervalMs(50); // 较小的最小间隔,便于测试
        p.setCacheTtlSeconds(0);
        p.setDegradedRetryMs(0);
        CountingFmpProvider fmp = new CountingFmpProvider(p);
        EarningsService svc = new EarningsService(p, new FinnhubProperties(), fmp,
                new FinnhubEarningsProvider(new FinnhubProperties()), new MockEarningsProvider());

        LocalDate from = LocalDate.of(2026, 8, 3);
        LocalDate to = LocalDate.of(2026, 8, 5);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<EarningsResponse> f1 = pool.submit(() -> {
                start.await();
                return svc.query(from, to);
            });
            Future<EarningsResponse> f2 = pool.submit(() -> {
                start.await();
                return svc.query(from, to);
            });
            start.countDown();
            assertEquals("fmp", f1.get(10, TimeUnit.SECONDS).source);
            assertEquals("fmp", f2.get(10, TimeUnit.SECONDS).source);
        } finally {
            pool.shutdownNow();
        }

        assertEquals(2, fmp.calls.get(), "两个并发线程查相同区间都应打到上游(CAS 节流串行化)");
        long intervalNanos = fmp.lastCallNanos.get() - fmp.firstCallNanos.get();
        assertTrue(intervalNanos >= 45_000_000L,
                "上游调用间隔应≥最小请求间隔(50ms),实际 " + intervalNanos + "ns");
    }
}
