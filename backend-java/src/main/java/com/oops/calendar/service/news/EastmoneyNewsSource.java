package com.oops.calendar.service.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.oops.calendar.dto.NewsItem;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 东方财富 7x24 快讯。
 * GET https://newsapi.eastmoney.com/kuaixun/v1/getlist_102_ajaxResult_50_1_.html
 * 返回 JSONP: var ajaxResult = {"LivesList":[...]}
 */
@Component
public class EastmoneyNewsSource implements NewsSource {

    private final NewsHttpClient http;

    public EastmoneyNewsSource(NewsHttpClient http) {
        this.http = http;
    }

    @Override
    public String key() {
        return "eastmoney";
    }

    @Override
    public String name() {
        return "东方财富";
    }

    @Override
    public String icon() {
        return "eastmoney.png";
    }

    @Override
    public List<NewsItem> fetch() {
        String url = "https://newsapi.eastmoney.com/kuaixun/v1/getlist_102_ajaxResult_50_1_.html";
        String text = http.getText(url);
        int eq = text.indexOf('=');
        String json = (eq >= 0 ? text.substring(eq + 1) : text).replaceAll(";+\\s*$", "").trim();
        JsonNode root = http.parse(json);
        JsonNode list = root != null ? root.get("LivesList") : null;
        List<NewsItem> items = new ArrayList<>();
        if (list == null || !list.isArray()) {
            return items;
        }
        for (JsonNode n : list) {
            String id = trimToNull(n.path("id").asText(null));
            String title = trimToNull(n.path("title").asText(null));
            if (id == null || title == null) {
                continue;
            }
            NewsItem item = new NewsItem();
            item.setId("eastmoney:" + id);
            item.setTitle(title);
            String urlW = trimToNull(n.path("url_w").asText(null));
            item.setUrl(urlW != null ? urlW : "https://finance.eastmoney.com/a/" + id + ".html");
            item.setPubDate(parseMillis(n.path("sort").asText(null)));
            item.setSource(key());
            item.setSourceName(name());
            item.setSummary(trimToNull(n.path("digest").asText(null)));
            items.add(item);
        }
        return items;
    }

    private static Long parseMillis(String sort) {
        if (sort == null) {
            return null;
        }
        try {
            long v = Long.parseLong(sort);
            // sort 为 epoch 微秒(16 位),统一为毫秒
            return v > 0 ? v / 1000 : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
