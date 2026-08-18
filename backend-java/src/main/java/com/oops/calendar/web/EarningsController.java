package com.oops.calendar.web;

import com.oops.calendar.dto.EarningsResponse;
import com.oops.calendar.service.EarningsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api")
public class EarningsController {

    private final EarningsService service;

    public EarningsController(EarningsService service) {
        this.service = service;
    }

    /**
     * GET /api/earnings?from=2026-08-01&to=2026-08-31[&refresh=true]
     * refresh=true 绕过缓存强制拉取上游(用于"单独刷新某一天")。
     */
    @GetMapping("/earnings")
    public EarningsResponse earnings(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "refresh", defaultValue = "false") boolean refresh) {
        return service.query(from, to, refresh);
    }

    /**
     * GET /api/earnings/{symbol}?from=...&to=... (默认今天前后各 30 天)
     */
    @GetMapping("/earnings/{symbol}")
    public EarningsResponse earningsBySymbol(
            @PathVariable String symbol,
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate base = LocalDate.now();
        LocalDate f = from != null ? from : base.minusDays(30);
        LocalDate t = to != null ? to : base.plusDays(30);
        return service.querySymbol(symbol, f, t);
    }
}
