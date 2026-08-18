package com.oops.calendar.dto;

import java.util.List;

/**
 * 快讯收藏响应:configured=false 表示该客户端从未收藏过快讯(前端用本地缓存/空列表);
 * items 按收藏时间倒序(最近收藏的在前),每项为收藏时的完整快讯快照。
 */
public class NewsFavoritesResponse {

    private boolean configured;
    private List<NewsItem> items;

    public NewsFavoritesResponse() {
    }

    public NewsFavoritesResponse(boolean configured, List<NewsItem> items) {
        this.configured = configured;
        this.items = items;
    }

    public boolean isConfigured() { return configured; }
    public void setConfigured(boolean configured) { this.configured = configured; }

    public List<NewsItem> getItems() { return items; }
    public void setItems(List<NewsItem> items) { this.items = items; }
}
