package com.oops.calendar.service;

import com.oops.calendar.dto.EarningsEvent;
import com.oops.calendar.dto.Session;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * EarningsService.dedupeExact:只去掉完全相同的副本,保留 Finnhub 原生"占位 + 真实"成对数据。
 */
class EarningsServiceDedupeTest {

    private static EarningsEvent ev(String date, String symbol, String eps, String epsEst, String rev, String revEst) {
        return new EarningsEvent(date, symbol, null, Session.BMO, true,
                eps == null ? null : new BigDecimal(eps),
                epsEst == null ? null : new BigDecimal(epsEst),
                rev == null ? null : new BigDecimal(rev),
                revEst == null ? null : new BigDecimal(revEst),
                "finnhub");
    }

    @Test
    void removesExactDuplicates() {
        List<EarningsEvent> in = Arrays.asList(
                ev("2026-08-06", "SHAZ", "-1.47", "-0.4157", "1931380", "6073335"),
                ev("2026-08-06", "SHAZ", "-1.47", "-0.4157", "1931380", "6073335"),
                ev("2026-08-06", "OTHER", "1", "2", "3", "4"));
        List<EarningsEvent> out = EarningsService.dedupeExact(in);
        assertEquals(2, out.size(), "完全相同的副本应去重,其余保留");
    }

    @Test
    void keepsStubAndRealPair() {
        // Finnhub 同一日期代码可能返回一条空占位 + 一条含数据,内容不同必须保留
        List<EarningsEvent> in = Arrays.asList(
                ev("2026-08-19", "TGT", null, null, null, null),
                ev("2026-08-19", "TGT", null, "2.3095", null, "26324568724"));
        List<EarningsEvent> out = EarningsService.dedupeExact(in);
        assertEquals(2, out.size(), "占位与真实记录内容不同,不应合并");
    }

    @Test
    void nullOrSingleListUnchanged() {
        assertEquals(null, EarningsService.dedupeExact(null));
        List<EarningsEvent> single = Arrays.asList(ev("2026-08-06", "SHAZ", "1", "2", "3", "4"));
        assertEquals(single, EarningsService.dedupeExact(single));
    }
}
