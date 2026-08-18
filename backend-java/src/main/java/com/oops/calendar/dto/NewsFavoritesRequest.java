package com.oops.calendar.dto;

import java.util.List;

/**
 * 保存快讯收藏请求体:clientId 由前端生成并本地持久化(cookie + localStorage);
 * items 为该客户端的完整收藏列表(整表替换语义,每项保存快照,未在列表中的收藏会被移除)。
 */
public class NewsFavoritesRequest {

    private String clientId;
    private List<NewsItem> items;

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public List<NewsItem> getItems() { return items; }
    public void setItems(List<NewsItem> items) { this.items = items; }
}
