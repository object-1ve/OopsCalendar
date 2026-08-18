package com.oops.calendar.dto;

/**
 * 一条财经快讯。id 为跨数据源唯一的字符串(前端用作 React key)。
 */
public class NewsItem {

    private String id;
    private String title;
    private String url;
    /** 发布时间(epoch 毫秒);未知为 null。 */
    private Long pubDate;
    /** 数据源 key,如 jin10 / cls。 */
    private String source;
    /** 数据源展示名,如 金十数据 / 财联社。 */
    private String sourceName;
    /** 摘要/正文(可空)。 */
    private String summary;
    /** 是否重要(金十重要快讯等)。 */
    private boolean important;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

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
}
