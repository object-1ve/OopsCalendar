package com.oops.calendar.service;

import com.oops.calendar.config.FinnhubProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * FinnhubSymbolService:全量公司名列表解析、缓存与失败降级。
 */
class FinnhubSymbolServiceTest {

    private FinnhubProperties props;
    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private FinnhubSymbolService service;

    @BeforeEach
    void setUp() {
        props = new FinnhubProperties();
        props.setApiKey("test-token");
        props.setBaseUrl("https://finnhub.test/api/v1");
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        service = new FinnhubSymbolService(props, restTemplate);
    }

    private void stubSymbolList() {
        String body = "["
                + "{\"symbol\":\"AAPL\",\"description\":\"Apple Inc.\"},"
                + "{\"symbol\":\"MSFT\",\"description\":\"Microsoft Corporation\"},"
                + "{\"symbol\":\"ZZZZ\",\"description\":\"\"},"
                + "{\"symbol\":\"\",\"description\":\"NoSymbol\"}"
                + "]";
        server.expect(requestTo("https://finnhub.test/api/v1/stock/symbol?exchange=US&token=test-token"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    @Test
    void resolvesNameFromSymbolList() {
        stubSymbolList();
        assertEquals("Apple Inc.", service.nameOf("AAPL"));
        assertEquals("Apple Inc.", service.nameOf("aapl"), "应大小写不敏感");
        assertEquals("Microsoft Corporation", service.nameOf("MSFT"));
        assertNull(service.nameOf("ZZZZ"), "空描述应被跳过");
        assertNull(service.nameOf("UNKNOWN"));
    }

    @Test
    void cachesAfterFirstLoad() {
        stubSymbolList();
        assertEquals("Apple Inc.", service.nameOf("AAPL"));
        assertEquals("Apple Inc.", service.nameOf("AAPL"));
        server.verify(); // 只发了一次请求
    }

    @Test
    void returnsNullOnFailureWithoutThrowing() {
        server.expect(requestTo("https://finnhub.test/api/v1/stock/symbol?exchange=US&token=test-token"))
                .andRespond(withServerError());
        assertNull(service.nameOf("AAPL"), "加载失败应静默返回 null,不抛异常");
    }

    @Test
    void returnsNullWithoutApiKey() {
        FinnhubProperties noKey = new FinnhubProperties();
        noKey.setApiKey("");
        FinnhubSymbolService svc = new FinnhubSymbolService(noKey);
        assertNull(svc.nameOf("AAPL"));
    }
}
