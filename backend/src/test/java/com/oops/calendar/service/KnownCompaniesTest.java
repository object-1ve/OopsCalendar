package com.oops.calendar.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class KnownCompaniesTest {

    @Test
    void coversUserMentionedCompanies() {
        KnownCompanies.CompanyInfo nbis = KnownCompanies.get("NBIS");
        assertNotNull(nbis, "NBIS 应在内置表中");
        assertEquals("Nebius Group N.V.", nbis.name);
        assertEquals("AI 基础设施", nbis.industry);

        KnownCompanies.CompanyInfo cbrs = KnownCompanies.get("CBRS");
        assertNotNull(cbrs, "CBRS 应在内置表中");
        assertEquals("Cerebras Systems Inc.", cbrs.name);
        assertEquals("半导体 / AI 芯片", cbrs.industry);
    }

    @Test
    void lookupIsCaseInsensitive() {
        assertEquals("Apple Inc.", KnownCompanies.get("aapl").name);
        assertEquals("NVIDIA Corp.", KnownCompanies.get("Nvda").name);
    }

    @Test
    void unknownSymbolReturnsNull() {
        assertNull(KnownCompanies.get("ZZZZ_FAKE"));
        assertNull(KnownCompanies.get(null));
    }

    @Test
    void tableHasReasonableSize() {
        assertTrue(KnownCompanies.all().size() >= 100, "内置表应覆盖 100+ 知名公司");
    }

    private static void assertTrue(boolean condition, String msg) {
        if (!condition) {
            throw new AssertionError(msg);
        }
    }
}
