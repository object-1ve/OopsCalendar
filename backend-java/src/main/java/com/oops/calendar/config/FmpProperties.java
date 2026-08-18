package com.oops.calendar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * FMP upstream configuration. apiKey empty => MOCK provider is active.
 */
@ConfigurationProperties(prefix = "fmp")
public class FmpProperties {

    private String apiKey = "";
    private String baseUrl = "https://financialmodelingprep.com/stable";
    private int connectTimeoutMs = 8000;
    private int readTimeoutMs = 15000;
    private long minRequestIntervalMs = 1500;
    private long cacheTtlSeconds = 3600;
    private int maxRangeDays = 120;
    /** FMP 降级后自动重试上游的冷却时长(毫秒)。 */
    private long degradedRetryMs = 60000;

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }

    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }

    public long getMinRequestIntervalMs() { return minRequestIntervalMs; }
    public void setMinRequestIntervalMs(long minRequestIntervalMs) { this.minRequestIntervalMs = minRequestIntervalMs; }

    public long getCacheTtlSeconds() { return cacheTtlSeconds; }
    public void setCacheTtlSeconds(long cacheTtlSeconds) { this.cacheTtlSeconds = cacheTtlSeconds; }

    public int getMaxRangeDays() { return maxRangeDays; }
    public void setMaxRangeDays(int maxRangeDays) { this.maxRangeDays = maxRangeDays; }

    public long getDegradedRetryMs() { return degradedRetryMs; }
    public void setDegradedRetryMs(long degradedRetryMs) { this.degradedRetryMs = degradedRetryMs; }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.trim().isEmpty();
    }
}
