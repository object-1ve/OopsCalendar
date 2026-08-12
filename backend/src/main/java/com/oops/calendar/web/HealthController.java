package com.oops.calendar.web;

import com.oops.calendar.dto.HealthResponse;
import com.oops.calendar.service.EarningsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
public class HealthController {

    private final EarningsService service;

    public HealthController(EarningsService service) {
        this.service = service;
    }

    /**
     * GET /api/health -> 当前数据源(fmp 真实 / mock 演示),降级时如实说明。
     */
    @GetMapping("/api/health")
    public HealthResponse health() {
        if (service.isDegraded()) {
            String message = "财报数据源请求失败(" + service.degradationReason() + "),已回退到内置演示数据,"
                    + "冷却期后将自动重试恢复。请检查 API Key 与网络。";
            return new HealthResponse("UP", "mock", message, Instant.now().toString());
        }
        String source = service.activeProvider().source();
        boolean mock = "mock".equals(source);
        String message = mock
                ? "未配置数据源 API Key,当前使用内置演示数据(确定性生成)。设置 FINNHUB_API_KEY 或 FMP_API_KEY 环境变量后重启即可切换为真实美股财报数据。"
                : "已连接 " + ("fmp".equals(source) ? "Financial Modeling Prep" : "Finnhub") + " 真实财报数据。";
        return new HealthResponse("UP", source, message, Instant.now().toString());
    }
}
