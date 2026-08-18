package com.oops.calendar.dto;

/**
 * 新闻数据源元信息。icon 为前端图标文件名(public/icons 下),无图标为 null。
 */
public class NewsSourceMeta {

    private String key;
    private String name;
    private String icon;

    public NewsSourceMeta() {
    }

    public NewsSourceMeta(String key, String name, String icon) {
        this.key = key;
        this.name = name;
        this.icon = icon;
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
}
