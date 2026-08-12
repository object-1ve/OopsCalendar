package com.oops.calendar.service;

import com.oops.calendar.dto.EarningsEvent;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 财报事件富化:补充公司全称与行业分类。
 * 优先内置知名公司表(名称+行业);其余公司全称取 Finnhub 全量列表;两者都没有则保持 null。
 */
@Service
public class EnrichmentService {

    private final FinnhubSymbolService symbolService;

    public EnrichmentService(FinnhubSymbolService symbolService) {
        this.symbolService = symbolService;
    }

    public void enrich(List<EarningsEvent> events) {
        for (EarningsEvent e : events) {
            KnownCompanies.CompanyInfo known = KnownCompanies.get(e.symbol);
            if (known != null) {
                if (e.name == null) {
                    e.name = known.name;
                }
                e.nameZh = known.nameZh;
                e.industry = known.industry;
            } else if (e.name == null) {
                e.name = symbolService.nameOf(e.symbol);
            }
        }
    }
}
