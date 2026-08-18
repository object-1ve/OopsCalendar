package com.oops.calendar.dto;

import java.util.List;

/**
 * 保存收藏请求体:clientId 由前端生成并本地持久化(cookie + localStorage),用于跨会话恢复。
 */
public class FavoritesRequest {

    private String clientId;
    private List<String> symbols;

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public List<String> getSymbols() { return symbols; }
    public void setSymbols(List<String> symbols) { this.symbols = symbols; }
}
