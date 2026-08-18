package com.oops.calendar.provider;

import com.oops.calendar.dto.EarningsEvent;
import com.oops.calendar.dto.Session;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockEarningsProviderTest {

    private final MockEarningsProvider provider = new MockEarningsProvider();

    @Test
    void sourceIsMock() {
        assertEquals("mock", provider.source());
    }

    @Test
    void deterministicAcrossCalls() {
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 31);
        List<EarningsEvent> a = provider.fetch(from, to);
        List<EarningsEvent> b = provider.fetch(from, to);
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            EarningsEvent ea = a.get(i);
            EarningsEvent eb = b.get(i);
            assertEquals(ea.date, eb.date);
            assertEquals(ea.symbol, eb.symbol);
            assertEquals(ea.session, eb.session);
            assertEquals(ea.confirmed, eb.confirmed);
        }
    }

    @Test
    void weekendHasNoEvents() {
        LocalDate sat = LocalDate.of(2026, 8, 1); // 2026-08-01 is a Saturday
        assertEquals(DayOfWeek.SATURDAY, sat.getDayOfWeek());
        assertTrue(provider.fetch(sat, sat).isEmpty());
    }

    @Test
    void weekdaysHaveEventsInRange() {
        LocalDate mon = LocalDate.of(2026, 8, 3); // Monday
        List<EarningsEvent> events = provider.fetch(mon, mon);
        assertFalse(events.isEmpty());
        assertTrue(events.size() >= 2 && events.size() <= 5);
        for (EarningsEvent e : events) {
            assertEquals("2026-08-03", e.date);
            assertNotNull(e.symbol);
            assertNotNull(e.name);
            assertTrue(e.session == Session.BMO || e.session == Session.AMC || e.session == Session.DNH);
            assertNotNull(e.epsEstimated);
        }
    }

    @Test
    void confirmedEventsHaveActualsUnconfirmedDoNot() {
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 31);
        boolean sawConfirmed = false;
        boolean sawUnconfirmed = false;
        for (EarningsEvent e : provider.fetch(from, to)) {
            if (e.confirmed) {
                sawConfirmed = true;
                assertNotNull(e.eps);
                assertNotNull(e.revenue);
            } else {
                sawUnconfirmed = true;
                assertEquals(null, e.eps);
                assertEquals(null, e.revenue);
            }
        }
        assertTrue(sawConfirmed, "mock 数据应包含已公布事件");
        assertTrue(sawUnconfirmed, "mock 数据应包含未公布事件");
    }

    @Test
    void noDuplicateSymbolOnSameDay() {
        LocalDate from = LocalDate.of(2026, 8, 3);
        LocalDate to = LocalDate.of(2026, 8, 7);
        for (EarningsEvent e : provider.fetch(from, to)) {
            List<EarningsEvent> sameDaySameSymbol = provider.fetch(from, to).stream()
                    .filter(x -> x.date.equals(e.date) && x.symbol.equals(e.symbol))
                    .collect(Collectors.toList());
            assertEquals(1, sameDaySameSymbol.size(), "同一天同一代码不应重复出现: " + e.date + " " + e.symbol);
        }
    }

    @Test
    void sessionsOnlyBmoAmcDnh() {
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 31);
        Set<Session> sessions = provider.fetch(from, to).stream()
                .map(e -> e.session)
                .collect(Collectors.toSet());
        assertFalse(sessions.contains(Session.UNKNOWN));
    }
}
