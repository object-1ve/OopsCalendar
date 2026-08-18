package com.oops.calendar.service.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.oops.calendar.dto.NewsItem;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 华尔街见闻 7x24 快讯。
 * GET https://api-one.wallstcn.com/apiv1/content/lives?channel=global-channel&limit=30
 */
@Component
public class WallstreetcnNewsSource implements NewsSource {

    private final NewsHttpClient http;

    public WallstreetcnNewsSource(NewsHttpClient http) {
        this.http = http;
    }

    @Override
    public String key() {
        return "wallstreetcn";
    }

    @Override
    public String name() {
        return "华尔街见闻";
    }

    @Override
    public String icon() {
        return "wallstreetcn.png";
    }

    @Override
    public List<NewsItem> fetch() {
        String url = "https://api-one.wallstcn.com/apiv1/content/lives?channel=global-channel&limit=30";
        JsonNode root = http.getJson(url);
        JsonNode list = root.path("data").path("items");
        List<NewsItem> items = new ArrayList<>();
        if (list == null || !list.isArray()) {
            return items;
        }
        for (JsonNode n : list) {
            String id = n.path("id").asText();
            if (id.isEmpty()) {
                continue;
            }
            String title = trimToNull(n.path("title").asText(null));
            String content = trimToNull(n.path("content_text").asText(null));
            if (title == null) {
                title = content;
            }
            if (title == null) {
                continue;
            }
            String uri = trimToNull(n.path("uri").asText(null));
            if (uri == null) {
                continue;
            }
            NewsItem item = new NewsItem();
            item.setId("wallstreetcn:" + id);
            item.setTitle(title);
            item.setUrl(uri.startsWith("http") ? uri : "https://wallstreetcn.com" + uri);
            long displayTime = n.path("display_time").asLong(0);
            item.setPubDate(displayTime > 0 ? displayTime * 1000 : null);
            item.setSource(key());
            item.setSourceName(name());
            item.setSummary(content != null && !content.equals(title) ? content : null);
            items.add(item);
        }
        return items;
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
