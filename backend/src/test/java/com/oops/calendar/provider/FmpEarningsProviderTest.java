package com.oops.calendar.provider;

import com.oops.calendar.config.FmpProperties;
import com.oops.calendar.dto.EarningsEvent;
import com.oops.calendar.dto.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * FmpEarningsProvider 真实解析路径单测(MockRestServiceServer 桩上游)。
 * 覆盖:全字段映射、time->Session 映射、confirmed 判定、防御解析、错误响应体分类、
 * HTTP 状态码映射、超时映射,以及"异常消息不回显原始响应体"。
 */
class FmpEarningsProviderTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 21);
    private static final String URL =
            "https://fmp.test/stable/earnings-calendar?from=2026-08-21&to=2026-08-21&apikey=test-key";

    private FmpProperties props;
    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private FmpEarningsProvider provider;

    @BeforeEach
    void setUp() {
        props = new FmpProperties();
        props.setApiKey("test-key");
        props.setBaseUrl("https://fmp.test/stable");
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        provider = new FmpEarningsProvider(props, restTemplate);
    }

    @Test
    void sourceIsFmp() {
        assertEquals("fmp", provider.source());
    }

    @Test
    void successResponseMapsAllFieldsAndSourceFmp() {
        String body = "[{\"date\":\"2026-08-21\",\"symbol\":\"AAPL\",\"name\":\"Apple Inc.\","
                + "\"time\":\"bmo\",\"eps\":3.53,\"epsEstimated\":3.81,"
                + "\"revenue\":6807,\"revenueEstimated\":6364}]";
        server.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        List<EarningsEvent> events = provider.fetch(DATE, DATE);

        assertEquals(1, events.size());
        EarningsEvent e = events.get(0);
        assertEquals("2026-08-21", e.date);
        assertEquals("AAPL", e.symbol);
        assertNull(e.name, "FMP 日历接口无公司名字段,应为 null");
        assertEquals(Session.BMO, e.session);
        assertTrue(e.confirmed);
        assertEquals(new BigDecimal("3.53"), e.eps);
        assertEquals(new BigDecimal("3.81"), e.epsEstimated);
        assertEquals(new BigDecimal("6807"), e.revenue);
        assertEquals(new BigDecimal("6364"), e.revenueEstimated);
        assertEquals("fmp", e.source);
        server.verify();
    }

    @Test
    void sessionMappingCoversAllVariants() {
        String body = "["
                + "{\"date\":\"2026-08-21\",\"symbol\":\"A\",\"time\":\"bmo\",\"eps\":1},"
                + "{\"date\":\"2026-08-21\",\"symbol\":\"B\",\"time\":\"amc\",\"eps\":1},"
                + "{\"date\":\"2026-08-21\",\"symbol\":\"C\",\"time\":\"dnh\",\"eps\":1},"
                + "{\"date\":\"2026-08-21\",\"symbol\":\"D\",\"time\":\"before-market\",\"eps\":1},"
                + "{\"date\":\"2026-08-21\",\"symbol\":\"E\",\"time\":\"after-market\",\"eps\":1},"
                + "{\"date\":\"2026-08-21\",\"symbol\":\"F\",\"time\":\"during\",\"eps\":1},"
                + "{\"date\":\"2026-08-21\",\"symbol\":\"G\",\"time\":null,\"eps\":1},"
                + "{\"date\":\"2026-08-21\",\"symbol\":\"H\",\"time\":\"\",\"eps\":1},"
                + "{\"date\":\"2026-08-21\",\"symbol\":\"I\",\"time\":\"afterhours\",\"eps\":1},"
                + "{\"date\":\"2026-08-21\",\"symbol\":\"J\",\"time\":\" BMO \",\"eps\":1}"
                + "]";
        server.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        List<EarningsEvent> events = provider.fetch(DATE, DATE);

        assertEquals(10, events.size());
        assertEquals(Session.BMO, bySymbol(events, "A").session);
        assertEquals(Session.AMC, bySymbol(events, "B").session);
        assertEquals(Session.DNH, bySymbol(events, "C").session);
        assertEquals(Session.BMO, bySymbol(events, "D").session);
        assertEquals(Session.AMC, bySymbol(events, "E").session);
        assertEquals(Session.DNH, bySymbol(events, "F").session);
        assertEquals(Session.UNKNOWN, bySymbol(events, "G").session);
        assertEquals(Session.UNKNOWN, bySymbol(events, "H").session);
        assertEquals(Session.UNKNOWN, bySymbol(events, "I").session);
        assertEquals(Session.BMO, bySymbol(events, "J").session);
        server.verify();
    }

    @Test
    void confirmedOnlyWhenEpsOrRevenuePresent() {
        String body = "["
                + "{\"date\":\"2026-08-21\",\"symbol\":\"EPS_ONLY\",\"eps\":1.1},"
                + "{\"date\":\"2026-08-21\",\"symbol\":\"REV_ONLY\",\"revenue\":100},"
                + "{\"date\":\"2026-08-21\",\"symbol\":\"NEITHER\",\"epsEstimated\":1.1,\"revenueEstimated\":100},"
                + "{\"date\":\"2026-08-21\",\"symbol\":\"NULLS\",\"eps\":null,\"revenue\":null}"
                + "]";
        server.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        List<EarningsEvent> events = provider.fetch(DATE, DATE);

        assertEquals(4, events.size());
        assertTrue(bySymbol(events, "EPS_ONLY").confirmed, "仅 eps 非空应判定为已公布");
        assertTrue(bySymbol(events, "REV_ONLY").confirmed, "仅 revenue 非空应判定为已公布");
        assertFalse(bySymbol(events, "NEITHER").confirmed, "eps/revenue 皆空应判定为未公布");
        assertFalse(bySymbol(events, "NULLS").confirmed, "eps/revenue 显式 null 应判定为未公布");
        server.verify();
    }

    @Test
    void defensiveParsingHandlesBadValuesWithoutCrash() {
        String body = "["
                + "{\"date\":\"2026-08-21\",\"symbol\":\"OK\",\"eps\":1.23,\"revenue\":456},"
                + "{\"date\":\"2026-08-21\",\"symbol\":\"STR\",\"eps\":\"1.23\",\"revenue\":\"ops\"},"
                + "{\"date\":\"2026-08-21\",\"symbol\":\"BOOL\",\"eps\":true,\"revenue\":{\"x\":1}},"
                + "{\"date\":\"2026-08-21\",\"symbol\":\"BAD\",\"eps\":\"abc\",\"revenue\":123},"
                + "{\"date\":\"2026-08-21\",\"symbol\":\"HUGE\",\"eps\":1e999,\"revenue\":null},"
                + "{\"symbol\":\"NO_DATE\",\"eps\":1},"
                + "{\"date\":\"2026-08-21\",\"eps\":1},"
                + "{\"foo\":\"bar\"},"
                + "42, \"text\", null, [], {}"
                + "]";
        server.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        List<EarningsEvent> events = provider.fetch(DATE, DATE);

        assertEquals(5, events.size(), "仅合法记录应保留,垃圾元素/缺关键字段记录应被跳过");
        EarningsEvent ok = bySymbol(events, "OK");
        assertEquals(new BigDecimal("1.23"), ok.eps);
        assertEquals(new BigDecimal("456"), ok.revenue);
        assertNull(bySymbol(events, "STR").eps, "字符串数字应防御解析为 null");
        assertNull(bySymbol(events, "STR").revenue, "非法字符串应防御解析为 null");
        assertNull(bySymbol(events, "BOOL").eps, "布尔值应防御解析为 null");
        assertNull(bySymbol(events, "BOOL").revenue, "对象值应防御解析为 null");
        assertNull(bySymbol(events, "BAD").eps, "非法字符串应防御解析为 null");
        assertEquals(new BigDecimal("123"), bySymbol(events, "BAD").revenue);
        assertNull(bySymbol(events, "HUGE").eps, "溢出 double 的数字应防御解析为 null 而非崩溃");
        server.verify();
    }

    @Test
    void emptyArrayReturnsEmptyList() {
        server.expect(requestTo(URL)).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        List<EarningsEvent> events = provider.fetch(DATE, DATE);

        assertTrue(events.isEmpty());
        server.verify();
    }

    @Test
    void errorBodyInvalidApiKeyClassifiedAndNotEchoed() {
        String secret = "https://site.financialmodelingprep.com/developer/docs?token=SECRET-TOKEN-42";
        String body = "{\"Error Message\":\"Invalid API KEY. Please retry or visit " + secret + " for more info.\"}";
        server.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        UpstreamUnavailableException ex = assertThrows(UpstreamUnavailableException.class,
                () -> provider.fetch(DATE, DATE));

        assertTrue(ex.getMessage().contains("FMP API Key 无效"), "实际消息: " + ex.getMessage());
        assertFalse(ex.getMessage().contains("SECRET-TOKEN-42"), "异常消息不得回显原始响应体");
        server.verify();
    }

    @Test
    void errorBodyRateLimitClassified() {
        String body = "{\"Error Message\":\"You have reached your API request limit. Please upgrade your plan.\"}";
        server.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        UpstreamUnavailableException ex = assertThrows(UpstreamUnavailableException.class,
                () -> provider.fetch(DATE, DATE));

        assertTrue(ex.getMessage().contains("免费档请求次数已用尽"), "实际消息: " + ex.getMessage());
        server.verify();
    }

    @Test
    void errorBodyUnknownDoesNotEchoRawBody() {
        String body = "{\"Error Message\":\"Unknown endpoint. Contact support with token SECRET-TOKEN-42.\"}";
        server.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        UpstreamUnavailableException ex = assertThrows(UpstreamUnavailableException.class,
                () -> provider.fetch(DATE, DATE));

        assertEquals("FMP 上游返回错误响应", ex.getMessage());
        assertFalse(ex.getMessage().contains("SECRET-TOKEN-42"));
        server.verify();
    }

    @Test
    void errorFieldVariantClassified() {
        String body = "{\"error\":\"Invalid API KEY\"}";
        server.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        UpstreamUnavailableException ex = assertThrows(UpstreamUnavailableException.class,
                () -> provider.fetch(DATE, DATE));

        assertTrue(ex.getMessage().contains("FMP API Key 无效"), "实际消息: " + ex.getMessage());
        server.verify();
    }

    @Test
    void nullOrEmptyBodyFailsWithParseMessage() {
        server.expect(requestTo(URL)).andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        UpstreamUnavailableException ex = assertThrows(UpstreamUnavailableException.class,
                () -> provider.fetch(DATE, DATE));

        assertTrue(ex.getMessage().contains("解析失败"), "实际消息: " + ex.getMessage());
        server.verify();
    }

    @Test
    void http401MapsToInvalidKeyWithoutEcho() {
        server.expect(requestTo(URL))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{\"detail\":\"TOP-SECRET-401-BODY\"}"));

        UpstreamUnavailableException ex = assertThrows(UpstreamUnavailableException.class,
                () -> provider.fetch(DATE, DATE));

        assertTrue(ex.getMessage().contains("FMP API Key 无效"), "实际消息: " + ex.getMessage());
        assertFalse(ex.getMessage().contains("TOP-SECRET-401-BODY"), "异常消息不得回显响应体");
        server.verify();
    }

    @Test
    void http403MapsToInvalidKeyWithoutEcho() {
        server.expect(requestTo(URL))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).body("{\"detail\":\"TOP-SECRET-403-BODY\"}"));

        UpstreamUnavailableException ex = assertThrows(UpstreamUnavailableException.class,
                () -> provider.fetch(DATE, DATE));

        assertTrue(ex.getMessage().contains("FMP API Key 无效"), "实际消息: " + ex.getMessage());
        assertFalse(ex.getMessage().contains("TOP-SECRET-403-BODY"), "异常消息不得回显响应体");
        server.verify();
    }

    @Test
    void http429MapsToUpstreamRejection() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        UpstreamUnavailableException ex = assertThrows(UpstreamUnavailableException.class,
                () -> provider.fetch(DATE, DATE));

        assertTrue(ex.getMessage().contains("FMP 上游拒绝请求"), "实际消息: " + ex.getMessage());
        server.verify();
    }

    @Test
    void http500MapsToUpstreamServiceError() {
        server.expect(requestTo(URL)).andRespond(withServerError());

        UpstreamUnavailableException ex = assertThrows(UpstreamUnavailableException.class,
                () -> provider.fetch(DATE, DATE));

        assertTrue(ex.getMessage().contains("上游服务异常"), "实际消息: " + ex.getMessage());
        server.verify();
    }

    @Test
    void socketTimeoutMapsToRequestTimeout() {
        server.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("Read timed out")));

        UpstreamUnavailableException ex = assertThrows(UpstreamUnavailableException.class,
                () -> provider.fetch(DATE, DATE));

        assertTrue(ex.getMessage().contains("请求超时"), "实际消息: " + ex.getMessage());
        server.verify();
    }

    private EarningsEvent bySymbol(List<EarningsEvent> events, String symbol) {
        for (EarningsEvent e : events) {
            if (symbol.equals(e.symbol)) {
                return e;
            }
        }
        throw new AssertionError("缺少 symbol=" + symbol + " 的记录");
    }
}
