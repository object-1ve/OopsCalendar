package com.oops.calendar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 财经快讯(新闻)配置。所有数据源均为公开接口,无需 API Key。
 */
@ConfigurationProperties(prefix = "news")
public class NewsProperties {

    /** 每个数据源结果的缓存时长(秒),避免频繁请求上游。 */
    private int cacheTtlSeconds = 60;
    /** 单数据源最多保留条数。 */
    private int maxItemsPerSource = 50;
    /** 合并后最多返回条数。 */
    private int maxItems = 200;
    /** 实时推送轮询周期(毫秒)。 */
    private long pollMs = 15000;
    /** 是否启用后台轮询 + SSE 推送(测试可关)。 */
    private boolean enabled = true;
    /** 用户数据源偏好持久化目录(JSON 文件)。 */
    private String dataDir = "./data";
    private int connectTimeoutMs = 8000;
    private int readTimeoutMs = 15000;

    public int getCacheTtlSeconds() { return cacheTtlSeconds; }
    public void setCacheTtlSeconds(int cacheTtlSeconds) { this.cacheTtlSeconds = cacheTtlSeconds; }

    public int getMaxItemsPerSource() { return maxItemsPerSource; }
    public void setMaxItemsPerSource(int maxItemsPerSource) { this.maxItemsPerSource = maxItemsPerSource; }

    public int getMaxItems() { return maxItems; }
    public void setMaxItems(int maxItems) { this.maxItems = maxItems; }

    public long getPollMs() { return pollMs; }
    public void setPollMs(long pollMs) { this.pollMs = pollMs; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getDataDir() { return dataDir; }
    public void setDataDir(String dataDir) { this.dataDir = dataDir; }

    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }

    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
}
