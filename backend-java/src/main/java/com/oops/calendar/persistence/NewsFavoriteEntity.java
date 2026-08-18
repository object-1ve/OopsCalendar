package com.oops.calendar.persistence;

import com.oops.calendar.dto.NewsItem;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * 快讯收藏持久化实体(H2 文件库)。
 * 收藏时保存整条快讯快照(title/url/摘要等),即便该快讯已从实时流中滚动淘汰,
 * 收藏列表仍能完整展示。同一客户端对同一快讯(id)只能收藏一次,重复收藏保持原时间。
 */
@Entity
@Table(
        name = "news_favorite",
        uniqueConstraints = @UniqueConstraint(name = "uk_news_fav_client_item", columnNames = {"client_id", "item_id"})
)
public class NewsFavoriteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 浏览器客户端标识(前端生成,localStorage + cookie 持久化)。 */
    @Column(name = "client_id", nullable = false)
    private String clientId;

    /** 快讯 id(跨数据源唯一,来自上游解析)。 */
    @Column(name = "item_id", nullable = false)
    private String itemId;

    @Column(name = "title", nullable = false)
    private String title;

    /** 原文链接(部分快讯无外链,可为空)。 */
    @Column(name = "url")
    private String url;

    /** 发布时间(epoch 毫秒),未知为 null。 */
    @Column(name = "pub_date")
    private Long pubDate;

    /** 数据源 key,如 jin10 / cls。 */
    @Column(name = "source", nullable = false)
    private String source;

    /** 数据源展示名,如 金十数据 / 财联社。 */
    @Column(name = "source_name")
    private String sourceName;

    /** 摘要/正文快照(可能较长,用 CLOB 存储)。 */
    @Lob
    @Column(name = "summary")
    private String summary;

    @Column(name = "important")
    private boolean important;

    /** 首次收藏时间,收藏列表按此倒序展示(最近收藏的在前)。 */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected NewsFavoriteEntity() {
    }

    public NewsFavoriteEntity(String clientId, String itemId, String title, String url, Long pubDate,
                              String source, String sourceName, String summary, boolean important,
                              Instant createdAt) {
        this.clientId = clientId;
        this.itemId = itemId;
        this.title = title;
        this.url = url;
        this.pubDate = pubDate;
        this.source = source;
        this.sourceName = sourceName;
        this.summary = summary;
        this.important = important;
        this.createdAt = createdAt;
    }

    /** 由快讯 DTO 构造新收藏(首次收藏时间 = 现在)。 */
    public static NewsFavoriteEntity from(NewsItem it, String clientId, Instant createdAt) {
        return new NewsFavoriteEntity(clientId, it.getId(), it.getTitle(), it.getUrl(), it.getPubDate(),
                it.getSource(), it.getSourceName(), it.getSummary(), it.isImportant(), createdAt);
    }

    /** 已收藏条目再次保存时,刷新快照字段,保留首次收藏时间。 */
    public void updateSnapshot(NewsItem it) {
        this.title = it.getTitle();
        this.url = it.getUrl();
        this.pubDate = it.getPubDate();
        this.source = it.getSource();
        this.sourceName = it.getSourceName();
        this.summary = it.getSummary();
        this.important = it.isImportant();
    }

    /** 还原为快讯 DTO。 */
    public NewsItem toDto() {
        NewsItem it = new NewsItem();
        it.setId(itemId);
        it.setTitle(title);
        it.setUrl(url);
        it.setPubDate(pubDate);
        it.setSource(source);
        it.setSourceName(sourceName);
        it.setSummary(summary);
        it.setImportant(important);
        return it;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public Long getPubDate() { return pubDate; }
    public void setPubDate(Long pubDate) { this.pubDate = pubDate; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public boolean isImportant() { return important; }
    public void setImportant(boolean important) { this.important = important; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
