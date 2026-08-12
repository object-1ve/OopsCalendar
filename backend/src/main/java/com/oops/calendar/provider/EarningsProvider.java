package com.oops.calendar.provider;

import com.oops.calendar.dto.EarningsEvent;

import java.time.LocalDate;
import java.util.List;

/**
 * 财报数据源抽象。实现:FmpEarningsProvider(真实数据)、MockEarningsProvider(演示数据)。
 */
public interface EarningsProvider {

    /** 数据源标识,"fmp" 或 "mock"。 */
    String source();

    /** 拉取 [from, to] 闭区间内的财报日历事件。 */
    List<EarningsEvent> fetch(LocalDate from, LocalDate to);
}
