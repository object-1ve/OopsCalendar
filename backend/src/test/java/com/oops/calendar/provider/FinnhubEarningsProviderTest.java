package com.oops.calendar.provider;

import com.oops.calendar.config.FinnhubProperties;
import com.oops.calendar.dto.EarningsEvent;
import com.oops.calendar.dto.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * FinnhubEarningsProvider 解析与错误处理单元测试。
 * 契约:任何上游失败抛 UpstreamUnavailableException;hour 映射 bmo/amc/dmh。
 */
class FinnhubEarningsProviderTest {

    private FinnhubProperties props;
    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private FinnhubEarningsProvider provider;

    @BeforeEach
    void setUp() {
        props = new FinnhubProperties();
        props.setApiKey("test-token");
        props.setBaseUrl("https://finnhub.test/api/v1");
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        provider = new FinnhubEarningsProvider(props, restTemplate);
    }

    private static final String URL =
            "https://finnhub.test/api/v1/calendar/earnings?from=2026-08-21&to=2026-08-21&token=test-token";

    @Test
    void sourceIsFinnhub() {
        assertEquals("finnhub", provider.source());
    }

    @Test
    void parsesSessionsAndConfirmed() {
        String body = "{\"earningsCalendar\":["
                + "{\"date\":\"2026-08-21\",\"symbol\":\"NBIS\",\"hour\":\"bmo\",\"epsActual\":null,\"epsEstimate\":0.87,\"revenueActual\":null,\"revenueEstimate\":220},"
                + "{\"date\":\"2026-08-21\",\"symbol\":\"CBRS\",\"hour\":\"amc\",\"epsActual\":-0.12,\"epsEstimate\":-0.15,\"revenueActual\":185,\"revenueEstimate\":170},"
                + "{\"date\":\"2026-08-21\",\"symbol\":\"AAPL\",\"hour\":\"dmh\",\"epsActual\":null,\"epsEstimate\":1.2,\"revenueActual\":null,\"revenueEstimate\":90000}"
                + "]}";
        server.expect(requestTo(URL))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        List<EarningsEvent> events = provider.fetch(LocalDate.of(2026, 8, 21), LocalDate.of(2026, 8, 21));

        assertEquals(3, events.size());

        EarningsEvent nbis = events.get(0);
        assertEquals("NBIS", nbis.symbol);
        assertEquals(Session.BMO, nbis.session);
        assertFalse(nbis.confirmed);
        assertNull(nbis.eps);
        assertEquals(0, nbis.epsEstimated.compareTo(new BigDecimal("0.87")));

        EarningsEvent cbrs = events.get(1);
        assertEquals("CBRS", cbrs.symbol);
        assertEquals(Session.AMC, cbrs.session);
        assertTrue(cbrs.confirmed);
        assertEquals(0, cbrs.eps.compareTo(new BigDecimal("-0.12")));
        assertEquals("finnhub", cbrs.source);

        EarningsEvent aapl = events.get(2);
        assertEquals(Session.DNH, aapl.session);
    }

    @Test
    void skipsRecordsWithoutDateOrSymbol() {
        String body = "{\"earningsCalendar\":["
                + "{\"symbol\":\"AAPL\",\"hour\":\"bmo\"},"
                + "{\"date\":\"2026-08-21\",\"hour\":\"amc\"},"
                + "{\"date\":\"2026-08-21\",\"symbol\":\"MSFT\",\"hour\":\"bmo\",\"epsActual\":1.0}"
                + "]}";
        server.expect(requestTo(URL))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        List<EarningsEvent> events = provider.fetch(LocalDate.of(2026, 8, 21), LocalDate.of(2026, 8, 21));
        assertEquals(1, events.size());
        assertEquals("MSFT", events.get(0).symbol);
    }

    @Test
    void throwsOnErrorBody() {
        String body = "{\"error\":\"Invalid token\"}";
        server.expect(requestTo(URL))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        UpstreamUnavailableException ex = assertThrows(UpstreamUnavailableException.class,
                () -> provider.fetch(LocalDate.of(2026, 8, 21), LocalDate.of(2026, 8, 21)));
        assertTrue(ex.getMessage().contains("Invalid token"), ex.getMessage());
    }

    @Test
    void throwsOnTransportError() {
        server.expect(requestTo(URL))
                .andRespond(withServerError());

        UpstreamUnavailableException ex = assertThrows(UpstreamUnavailableException.class,
                () -> provider.fetch(LocalDate.of(2026, 8, 21), LocalDate.of(2026, 8, 21)));
        assertTrue(ex.getMessage().contains("HTTP 500"), ex.getMessage());
    }

    @Test
    void throwsOnMissingCalendarArray() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThrows(UpstreamUnavailableException.class,
                () -> provider.fetch(LocalDate.of(2026, 8, 21), LocalDate.of(2026, 8, 21)));
    }
}
