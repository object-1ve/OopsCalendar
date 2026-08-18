package com.oops.calendar.persistence;

import com.oops.calendar.dto.EarningsEvent;
import com.oops.calendar.dto.Session;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 财报日历事件持久化实体(H2 文件库)。
 * 与上游返回一一对应,不设唯一约束:上游(Finnhub)同一日期同一代码可能出现
 * 多条记录,必须原样保存,否则整段 saveAll 会因约束冲突回滚。
 * 富化后的公司名/行业一并落库,读回时无需再次调用上游补全。
 */
@Entity
@Table(name = "earnings_event")
public class EarningsEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(nullable = false)
    private String symbol;

    private String name;

    @Column(name = "name_zh")
    private String nameZh;

    private String industry;

    @Column(nullable = false)
    private String session;

    @Column(nullable = false)
    private boolean confirmed;

    @Column(precision = 30, scale = 8)
    private BigDecimal eps;

    @Column(name = "eps_estimated", precision = 30, scale = 8)
    private BigDecimal epsEstimated;

    @Column(precision = 30, scale = 8)
    private BigDecimal revenue;

    @Column(name = "revenue_estimated", precision = 30, scale = 8)
    private BigDecimal revenueEstimated;

    @Column(nullable = false)
    private String source;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EarningsEventEntity() {
    }

    public EarningsEventEntity(LocalDate eventDate, String symbol, String name, String nameZh, String industry,
                               String session, boolean confirmed, BigDecimal eps, BigDecimal epsEstimated,
                               BigDecimal revenue, BigDecimal revenueEstimated, String source, Instant updatedAt) {
        this.eventDate = eventDate;
        this.symbol = symbol;
        this.name = name;
        this.nameZh = nameZh;
        this.industry = industry;
        this.session = session;
        this.confirmed = confirmed;
        this.eps = eps;
        this.epsEstimated = epsEstimated;
        this.revenue = revenue;
        this.revenueEstimated = revenueEstimated;
        this.source = source;
        this.updatedAt = updatedAt;
    }

    /** 由 DTO 构造实体;date 为 YYYY-MM-DD。 */
    public static EarningsEventEntity from(EarningsEvent e) {
        return new EarningsEventEntity(
                LocalDate.parse(e.date),
                e.symbol,
                e.name,
                e.nameZh,
                e.industry,
                e.session == null ? Session.UNKNOWN.name() : e.session.name(),
                e.confirmed,
                e.eps,
                e.epsEstimated,
                e.revenue,
                e.revenueEstimated,
                e.source,
                Instant.now());
    }

    /** 还原为 DTO。 */
    public EarningsEvent toDto() {
        EarningsEvent e = new EarningsEvent();
        e.date = eventDate.toString();
        e.symbol = symbol;
        e.name = name;
        e.nameZh = nameZh;
        e.industry = industry;
        e.session = session == null ? Session.UNKNOWN : Session.valueOf(session);
        e.confirmed = confirmed;
        e.eps = eps;
        e.epsEstimated = epsEstimated;
        e.revenue = revenue;
        e.revenueEstimated = revenueEstimated;
        e.source = source;
        return e;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDate getEventDate() { return eventDate; }
    public void setEventDate(LocalDate eventDate) { this.eventDate = eventDate; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNameZh() { return nameZh; }
    public void setNameZh(String nameZh) { this.nameZh = nameZh; }
    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }
    public String getSession() { return session; }
    public void setSession(String session) { this.session = session; }
    public boolean isConfirmed() { return confirmed; }
    public void setConfirmed(boolean confirmed) { this.confirmed = confirmed; }
    public BigDecimal getEps() { return eps; }
    public void setEps(BigDecimal eps) { this.eps = eps; }
    public BigDecimal getEpsEstimated() { return epsEstimated; }
    public void setEpsEstimated(BigDecimal epsEstimated) { this.epsEstimated = epsEstimated; }
    public BigDecimal getRevenue() { return revenue; }
    public void setRevenue(BigDecimal revenue) { this.revenue = revenue; }
    public BigDecimal getRevenueEstimated() { return revenueEstimated; }
    public void setRevenueEstimated(BigDecimal revenueEstimated) { this.revenueEstimated = revenueEstimated; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
