package com.oops.calendar.dto;

import java.util.List;

/**
 * /api/earnings 响应:区间内全部财报事件,按日期升序。
 */
public class EarningsResponse {

    public String from;
    public String to;
    public int count;
    public String source;           // fmp | mock
    public List<EarningsEvent> events;

    public EarningsResponse() {
    }

    public EarningsResponse(String from, String to, int count, String source, List<EarningsEvent> events) {
        this.from = from;
        this.to = to;
        this.count = count;
        this.source = source;
        this.events = events;
    }
}
