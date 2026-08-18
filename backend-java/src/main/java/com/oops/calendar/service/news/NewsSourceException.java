package com.oops.calendar.service.news;

/**
 * 数据源抓取失败。由 NewsService 捕获并降级(单个源失败不影响其他源)。
 */
public class NewsSourceException extends RuntimeException {

    public NewsSourceException(String message) {
        super(message);
    }

    public NewsSourceException(String message, Throwable cause) {
        super(message, cause);
    }
}
