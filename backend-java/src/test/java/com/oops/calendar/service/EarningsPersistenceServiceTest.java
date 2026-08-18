package com.oops.calendar.service;

import com.oops.calendar.dto.EarningsEvent;
import com.oops.calendar.dto.Session;
import com.oops.calendar.persistence.EarningsCoverageEntity;
import com.oops.calendar.persistence.EarningsCoverageRepository;
import com.oops.calendar.persistence.EarningsEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 财报持久化层测试(内存 H2):
 * replaceRange 整段替换 + 覆盖记录;loadRange 在覆盖且未过期时读回,否则返回 null。
 */
@DataJpaTest
class EarningsPersistenceServiceTest {

    @Autowired
    private EarningsEventRepository eventRepository;

    @Autowired
    private EarningsCoverageRepository coverageRepository;

    private EarningsPersistenceService svc;

    @BeforeEach
    void setUp() {
        svc = new EarningsPersistenceService(eventRepository, coverageRepository);
    }

    private static EarningsEvent event(String date, String symbol, String name) {
        return new EarningsEvent(date, symbol, name, Session.BMO, true,
                new BigDecimal("1.23"), new BigDecimal("1.20"),
                new BigDecimal("1000"), new BigDecimal("950"), "finnhub");
    }

    @Test
    void replaceThenLoadRoundTrips() {
        LocalDate from = LocalDate.of(2026, 8, 3);
        LocalDate to = LocalDate.of(2026, 8, 5);
        List<EarningsEvent> events = Arrays.asList(
                event("2026-08-03", "AAPL", "Apple Inc."),
                event("2026-08-05", "MSFT", "Microsoft Corp."));

        svc.replaceRange(from, to, "finnhub", events);

        EarningsPersistenceService.LoadedRange loaded = svc.loadRange(from, to, 3600_000L);
        assertEquals("finnhub", loaded.source);
        assertEquals(2, loaded.events.size());
        assertEquals("2026-08-03", loaded.events.get(0).date);
        assertEquals("Apple Inc.", loaded.events.get(0).name);
        assertEquals(Session.BMO, loaded.events.get(0).session);
    }

    @Test
    void replaceRangeIsIdempotentPerRange() {
        LocalDate from = LocalDate.of(2026, 8, 3);
        LocalDate to = LocalDate.of(2026, 8, 3);
        svc.replaceRange(from, to, "finnhub", Arrays.asList(event("2026-08-03", "AAPL", "Apple Inc.")));
        svc.replaceRange(from, to, "finnhub", Arrays.asList(event("2026-08-03", "MSFT", "Microsoft Corp.")));

        EarningsPersistenceService.LoadedRange loaded = svc.loadRange(from, to, 3600_000L);
        assertEquals(1, loaded.events.size(), "同区间再次替换不应留下旧记录");
        assertEquals("MSFT", loaded.events.get(0).symbol);
    }

    @Test
    void replaceRangeAllowsDuplicateDateSymbol() {
        // 真实上游(Finnhub)同一日期同一代码可能返回多条记录,必须原样保存,不因唯一约束回滚
        LocalDate from = LocalDate.of(2026, 8, 3);
        LocalDate to = LocalDate.of(2026, 8, 3);
        svc.replaceRange(from, to, "finnhub", Arrays.asList(
                event("2026-08-03", "CTHR", "Cintas Corp"),
                event("2026-08-03", "CTHR", "Cintas Corp")));

        EarningsPersistenceService.LoadedRange loaded = svc.loadRange(from, to, 3600_000L);
        assertEquals(2, loaded.events.size(), "重复的日期+代码条目应原样保存");
    }

    @Test
    void overlappingReplaceRangesDoNotDuplicate() {
        // 复现曾导致数据翻倍的场景:整月写入后再覆盖其中某一天,最终数据不应出现重复行
        LocalDate monthFrom = LocalDate.of(2026, 8, 1);
        LocalDate monthTo = LocalDate.of(2026, 8, 31);
        svc.replaceRange(monthFrom, monthTo, "finnhub", Arrays.asList(
                event("2026-08-03", "AAPL", "Apple Inc."),
                event("2026-08-05", "MSFT", "Microsoft Corp.")));

        svc.replaceRange(LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 3), "finnhub",
                Arrays.asList(event("2026-08-03", "NVDA", "NVIDIA Corp.")));

        EarningsPersistenceService.LoadedRange loaded = svc.loadRange(monthFrom, monthTo, 3600_000L);
        assertEquals(2, loaded.events.size(), "重叠区间替换后总行数不应翻倍");
        List<String> symbols = loaded.events.stream().map(e -> e.symbol).collect(java.util.stream.Collectors.toList());
        assertTrue(symbols.contains("NVDA"), "小范围覆盖应生效");
        assertTrue(symbols.contains("MSFT"), "未被覆盖的记录应保留");
        assertTrue(!symbols.contains("AAPL"), "被覆盖的旧记录应被删除");
    }

    @Test
    void loadReturnsNullWhenNotCovered() {
        assertNull(svc.loadRange(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), 3600_000L),
                "未拉取过的区间不应命中数据库");
    }

    @Test
    void loadReturnsNullWhenExpired() {
        LocalDate from = LocalDate.of(2026, 8, 3);
        LocalDate to = LocalDate.of(2026, 8, 3);
        svc.replaceRange(from, to, "finnhub", Arrays.asList(event("2026-08-03", "AAPL", "Apple Inc.")));

        // 手动把覆盖时间拨到 2 小时前,模拟 TTL(1 小时)已过期
        EarningsCoverageEntity cov = coverageRepository.findById(from + "|" + to).orElseThrow(AssertionError::new);
        cov.setFetchedAt(Instant.now().minusSeconds(7200));
        coverageRepository.saveAndFlush(cov);

        assertNull(svc.loadRange(from, to, 3600_000L), "覆盖记录过期后应回源而非读库");
    }

    @Test
    void emptyRangeStillRecordsCoverage() {
        LocalDate from = LocalDate.of(2026, 8, 15); // 周六,无事件
        LocalDate to = LocalDate.of(2026, 8, 15);
        svc.replaceRange(from, to, "finnhub", java.util.Collections.emptyList());

        EarningsPersistenceService.LoadedRange loaded = svc.loadRange(from, to, 3600_000L);
        assertTrue(loaded.events.isEmpty(), "空区间也应可读回(覆盖记录存在)");
    }
}
