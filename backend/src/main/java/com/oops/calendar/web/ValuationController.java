package com.oops.calendar.web;

import com.oops.calendar.service.ValuationService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ValuationController {

    private final ValuationService service;

    public ValuationController(ValuationService service) {
        this.service = service;
    }

    /**
     * GET /api/valuation?date=2026-08-12
     * 当日财报公司的市盈率(PE TTM),仅覆盖内置知名公司。
     */
    @GetMapping("/valuation")
    public Map<String, Object> valuation(
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Map<String, BigDecimal> values = service.valuationsForDate(date);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("date", date.toString());
        body.put("count", values.size());
        body.put("values", values);
        return body;
    }
}
