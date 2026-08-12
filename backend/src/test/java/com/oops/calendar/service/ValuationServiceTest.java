package com.oops.calendar.service;

import com.oops.calendar.config.FinnhubProperties;
import com.oops.calendar.config.FmpProperties;
import com.oops.calendar.dto.EarningsEvent;
import com.oops.calendar.dto.EarningsResponse;
import com.oops.calendar.provider.FinnhubEarningsProvider;
import com.oops.calendar.provider.FmpEarningsProvider;
import com.oops.calendar.provider.MockEarningsProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * ValuationService:PE 拉取、知名公司过滤、缓存、失败降级。
 */
class ValuationServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 3); // mock 数据含 NBIS 的日期

    private FinnhubProperties finnhubProps;
    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private ValuationService valuationService;
    private EarningsService earningsService;

    @BeforeEach
    void setUp() {
        FmpProperties fmpProps = new FmpProperties();
        fmpProps.setApiKey("");
        finnhubProps = new FinnhubProperties();
        finnhubProps.setApiKey("test-token");
        finnhubProps.setBaseUrl("https://finnhub.test/api/v1");
        earningsService = new EarningsService(fmpProps, finnhubProps,
                new EnrichmentService(new FinnhubSymbolService(new FinnhubProperties())),
                new FmpEarningsProvider(fmpProps),
                new FinnhubEarningsProvider(new FinnhubProperties()),
                new MockEarningsProvider());
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        valuationService = new ValuationService(finnhubProps, earningsService, restTemplate);
    }

    private List<String> knownSymbolsOn(LocalDate date) {
        EarningsResponse resp = earningsService.query(date, date);
        return resp.events.stream()
                .map(e -> e.symbol)
                .filter(s -> KnownCompanies.get(s) != null)
                .distinct()
                .collect(Collectors.toList());
    }

    private String metricsUrl(String symbol) {
        return "https://finnhub.test/api/v1/stock/metric?symbol=" + symbol + "&metric=all&token=test-token";
    }

    @Test
    void returnsPeForKnownCompaniesOnly() {
        List<String> known = knownSymbolsOn(DATE);
        assertTrue(known.size() >= 1, "mock 数据当天应至少有一家知名公司");
        for (String s : known) {
            server.expect(requestTo(metricsUrl(s)))
                    .andRespond(withSuccess("{\"metric\":{\"peTTM\":42.5}}", MediaType.APPLICATION_JSON));
        }

        Map<String, BigDecimal> vals = valuationService.valuationsForDate(DATE);

        assertEquals(known.size(), vals.size());
        for (String s : known) {
            assertEquals(0, vals.get(s).compareTo(new BigDecimal("42.5")), s);
        }
    }

    @Test
    void cachesResultAfterFirstCall() {
        List<String> known = knownSymbolsOn(DATE);
        for (String s : known) {
            server.expect(requestTo(metricsUrl(s)))
                    .andRespond(withSuccess("{\"metric\":{\"peTTM\":42.5}}", MediaType.APPLICATION_JSON));
        }
        valuationService.valuationsForDate(DATE);
        valuationService.valuationsForDate(DATE); // 命中缓存,不再请求
        server.verify();
    }

    @Test
    void skipsSymbolsWithoutPe() {
        List<String> known = knownSymbolsOn(DATE);
        for (String s : known) {
            server.expect(requestTo(metricsUrl(s)))
                    .andRespond(withSuccess("{\"metric\":{}}", MediaType.APPLICATION_JSON));
        }
        Map<String, BigDecimal> vals = valuationService.valuationsForDate(DATE);
        assertTrue(vals.isEmpty(), "无 PE 字段应跳过");
    }

    @Test
    void returnsEmptyWithoutApiKey() {
        FinnhubProperties noKey = new FinnhubProperties();
        noKey.setApiKey("");
        ValuationService svc = new ValuationService(noKey, earningsService, restTemplate);
        assertTrue(svc.valuationsForDate(DATE).isEmpty());
    }

    @Test
    void toleratesUpstreamFailure() {
        List<String> known = knownSymbolsOn(DATE);
        for (String s : known) {
            server.expect(requestTo(metricsUrl(s)))
                    .andRespond(withServerError());
        }
        Map<String, BigDecimal> vals = valuationService.valuationsForDate(DATE);
        assertTrue(vals.isEmpty(), "上游失败应静默跳过");
    }
}
