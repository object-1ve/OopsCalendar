package com.oops.calendar.service.news;

import com.oops.calendar.dto.NewsItem;

import java.util.List;

/**
 * 财经快讯数据源。key 全局唯一,用于前端筛选与缓存;icon 为 public/icons 下文件名,无则 null。
 */
public interface NewsSource {

    String key();

    String name();

    /** 图标文件名(如 jin10.png),无图标返回 null。 */
    default String icon() {
        return null;
    }

    /** 抓取最新快讯;失败抛 {@link NewsSourceException}。 */
    List<NewsItem> fetch();
}
