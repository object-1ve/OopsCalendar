package com.oops.calendar;

import com.oops.calendar.provider.FmpEarningsProvider;
import com.oops.calendar.provider.MockEarningsProvider;
import com.oops.calendar.service.EarningsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 验证 Spring 装配未被 FmpEarningsProvider 构造器重构破坏:
 * FmpEarningsProvider 新增包内可见(RestTemplate)构造器后,@Autowired 仍应注入
 * 主构造器,应用上下文可正常加载(无 key 时为 mock 模式,不发起网络请求)。
 */
@SpringBootTest
class EarningsCalendarApplicationTest {

    @Autowired
    private FmpEarningsProvider fmpProvider;

    @Autowired
    private MockEarningsProvider mockProvider;

    @Autowired
    private EarningsService service;

    @Test
    void contextLoadsAndWiresAllProviders() {
        assertNotNull(fmpProvider, "FmpEarningsProvider 应被 Spring 注入");
        assertNotNull(mockProvider, "MockEarningsProvider 应被 Spring 注入");
        assertNotNull(service, "EarningsService 应被 Spring 注入");
    }

    @Test
    void noApiKeyMeansMockMode() {
        assertEquals("mock", service.activeProvider().source(), "未配置 FMP_API_KEY 时应为 mock 模式");
    }
}
