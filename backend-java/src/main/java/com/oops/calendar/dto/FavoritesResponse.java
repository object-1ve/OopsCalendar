package com.oops.calendar.dto;

import java.util.List;

/**
 * 收藏响应:configured=false 表示该客户端从未保存过收藏(前端用本地缓存/空列表)。
 */
public class FavoritesResponse {

    private boolean configured;
    private List<String> symbols;

    public FavoritesResponse() {
    }

    public FavoritesResponse(boolean configured, List<String> symbols) {
        this.configured = configured;
        this.symbols = symbols;
    }

    public boolean isConfigured() { return configured; }
    public void setConfigured(boolean configured) { this.configured = configured; }

    public List<String> getSymbols() { return symbols; }
    public void setSymbols(List<String> symbols) { this.symbols = symbols; }
}
