package com.oops.calendar.dto;

import java.util.List;

/**
 * GET /api/news 响应:合并排序后的快讯 + 可用数据源列表。
 */
public class NewsResponse {

    private List<NewsItem> items;
    private List<NewsSourceMeta> sources;
    private long fetchedAt;

    public NewsResponse() {
    }

    public NewsResponse(List<NewsItem> items, List<NewsSourceMeta> sources, long fetchedAt) {
        this.items = items;
        this.sources = sources;
        this.fetchedAt = fetchedAt;
    }

    public List<NewsItem> getItems() { return items; }
    public void setItems(List<NewsItem> items) { this.items = items; }

    public List<NewsSourceMeta> getSources() { return sources; }
    public void setSources(List<NewsSourceMeta> sources) { this.sources = sources; }

    public long getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(long fetchedAt) { this.fetchedAt = fetchedAt; }
}
