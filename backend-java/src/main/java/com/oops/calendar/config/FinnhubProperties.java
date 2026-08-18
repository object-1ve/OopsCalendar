package com.oops.calendar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Finnhub upstream configuration. apiKey empty => 不启用 Finnhub 数据源。
 * 免费档 60 次/分钟,财报日历覆盖完整(优于 FMP 免费档的稀疏数据)。
 */
@ConfigurationProperties(prefix = "finnhub")
public class FinnhubProperties {

    private String apiKey = "";
    private String baseUrl = "https://finnhub.io/api/v1";
    private int connectTimeoutMs = 8000;
    private int readTimeoutMs = 15000;

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }

    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.trim().isEmpty();
    }
}
