package com.oops.calendar.persistence;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.Instant;

/**
 * 财报覆盖记录:记录某段 [from, to] 已完整拉取过(即使结果为空),
 * 据此在内存缓存失效后仍可从数据库读回,而无需整段回源。
 */
@Entity
@Table(name = "earnings_coverage")
public class EarningsCoverageEntity {

    /** 区间键,如 "2026-08-01|2026-08-31"。 */
    @Id
    @Column(name = "range_key", nullable = false)
    private String rangeKey;

    @Column(nullable = false)
    private String source;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    public String getRangeKey() { return rangeKey; }
    public void setRangeKey(String rangeKey) { this.rangeKey = rangeKey; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Instant getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(Instant fetchedAt) { this.fetchedAt = fetchedAt; }
}
