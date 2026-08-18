package com.oops.calendar.dto;

import java.util.List;

/**
 * 保存数据源偏好请求体:clientId 由前端生成本地持久化,用于跨会话恢复。
 */
public class NewsPreferencesRequest {

    private String clientId;
    private List<String> sources;

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public List<String> getSources() { return sources; }
    public void setSources(List<String> sources) { this.sources = sources; }
}
