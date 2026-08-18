package com.oops.calendar.dto;

import java.util.List;

/**
 * 数据源偏好响应:configured=false 表示该客户端从未保存过配置(前端默认全开)。
 */
public class NewsPreferencesResponse {

    private boolean configured;
    private List<String> sources;

    public NewsPreferencesResponse() {
    }

    public NewsPreferencesResponse(boolean configured, List<String> sources) {
        this.configured = configured;
        this.sources = sources;
    }

    public boolean isConfigured() { return configured; }
    public void setConfigured(boolean configured) { this.configured = configured; }

    public List<String> getSources() { return sources; }
    public void setSources(List<String> sources) { this.sources = sources; }
}
