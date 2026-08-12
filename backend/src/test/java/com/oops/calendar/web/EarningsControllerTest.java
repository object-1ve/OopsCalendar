package com.oops.calendar.web;

import com.oops.calendar.config.FmpProperties;
import com.oops.calendar.dto.EarningsEvent;
import com.oops.calendar.dto.Session;
import com.oops.calendar.provider.FmpEarningsProvider;
import com.oops.calendar.provider.MockEarningsProvider;
import com.oops.calendar.provider.UpstreamUnavailableException;
import com.oops.calendar.service.EarningsService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EarningsControllerTest {

    private MockMvc mockMvc() {
        FmpProperties props = new FmpProperties();
        props.setApiKey("");
        EarningsService service = new EarningsService(props, new FmpEarningsProvider(props), new MockEarningsProvider());
        return MockMvcBuilders.standaloneSetup(
                new EarningsController(service), new HealthController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void healthShowsMockSource() throws Exception {
        mockMvc().perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.provider").value("mock"));
    }

    @Test
    void earningsReturnsCalendarJson() throws Exception {
        mockMvc().perform(get("/api/earnings").param("from", "2026-08-01").param("to", "2026-08-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-08-01"))
                .andExpect(jsonPath("$.to").value("2026-08-31"))
                .andExpect(jsonPath("$.source").value("mock"))
                .andExpect(jsonPath("$.count").isNumber())
                .andExpect(jsonPath("$.events[0].date").isString())
                .andExpect(jsonPath("$.events[0].symbol").isString())
                .andExpect(jsonPath("$.events[0].session").exists())
                .andExpect(jsonPath("$.events[0].confirmed").isBoolean())
                .andExpect(jsonPath("$.events[0].source").value("mock"));
    }

    @Test
    void earningsRejectsMissingParams() throws Exception {
        mockMvc().perform(get("/api/earnings"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void earningsRejectsBadDate() throws Exception {
        mockMvc().perform(get("/api/earnings").param("from", "2026-13-99").param("to", "2026-08-31"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void earningsRejectsReversedRange() throws Exception {
        mockMvc().perform(get("/api/earnings").param("from", "2026-08-31").param("to", "2026-08-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("from 不能晚于 to"));
    }

    @Test
    void earningsRejectsHugeRange() throws Exception {
        mockMvc().perform(get("/api/earnings").param("from", "2026-01-01").param("to", "2026-12-31"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void earningsBySymbolWorks() throws Exception {
        mockMvc().perform(get("/api/earnings/AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").isNumber());
    }

    @Test
    void healthReportsDegradedFallback() throws Exception {
        // FMP 恒失败 -> 查询触发降级 -> /api/health 应如实说明已回退演示数据
        FmpProperties props = new FmpProperties();
        props.setApiKey("dummy-key");
        props.setDegradedRetryMs(60000);
        FmpEarningsProvider failing = new FmpEarningsProvider(props) {
            @Override
            public List<EarningsEvent> fetch(LocalDate from, LocalDate to) {
                throw new UpstreamUnavailableException("FMP API Key 无效(请检查 FMP_API_KEY 配置)");
            }
        };
        EarningsService service = new EarningsService(props, failing, new MockEarningsProvider());
        service.query(LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 7)); // 触发降级

        MockMvc mvc = MockMvcBuilders.standaloneSetup(new HealthController(service))
                .build();
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.provider").value("mock"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("已回退到内置演示数据")));
    }
}
