package com.oops.calendar.dto;

import java.math.BigDecimal;

/**
 * 单条财报日历事件。
 * confirmed=true 表示该公司已公布财报(有实际 EPS/营收),否则为待公布(预估)。
 */
public class EarningsEvent {

    public String date;              // YYYY-MM-DD
    public String symbol;            // 股票代码,如 AAPL
    public String name;              // 公司全称,FMP 日历接口无此字段时为 null
    public String industry;          // 行业分类(中文),知名公司内置表提供
    public Session session;          // 盘前/盘后/盘中/待定
    public boolean confirmed;        // 是否已公布
    public BigDecimal eps;           // 实际 EPS(未公布为 null)
    public BigDecimal epsEstimated;  // 预估 EPS
    public BigDecimal revenue;       // 实际营收(万美元口径按源)
    public BigDecimal revenueEstimated; // 预估营收
    public String source;            // "fmp" | "mock"

    public EarningsEvent() {
    }

    public EarningsEvent(String date, String symbol, String name, Session session, boolean confirmed,
                         BigDecimal eps, BigDecimal epsEstimated,
                         BigDecimal revenue, BigDecimal revenueEstimated, String source) {
        this.date = date;
        this.symbol = symbol;
        this.name = name;
        this.session = session;
        this.confirmed = confirmed;
        this.eps = eps;
        this.epsEstimated = epsEstimated;
        this.revenue = revenue;
        this.revenueEstimated = revenueEstimated;
        this.source = source;
    }
}
