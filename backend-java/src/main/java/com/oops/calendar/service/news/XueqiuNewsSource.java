package com.oops.calendar.service.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.oops.calendar.dto.NewsItem;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 雪球 热门股票(炒股相关:实时热股榜)。
 * 先访问 https://xueqiu.com/hq 取 cookie,再请求热股列表,否则会被风控拒绝。
 */
@Component
public class XueqiuNewsSource implements NewsSource {

    private final NewsHttpClient http;

    public XueqiuNewsSource(NewsHttpClient http) {
        this.http = http;
    }

    @Override
    public String key() {
        return "xueqiu";
    }

    @Override
    public String name() {
        return "雪球";
    }

    @Override
    public String icon() {
        return "xueqiu.png";
    }

    @Override
    public List<NewsItem> fetch() {
        List<String> cookies = http.getSetCookies("https://xueqiu.com/hq");
        Map<String, String> headers = new HashMap<>();
        headers.put(HttpHeaders.COOKIE, String.join("; ", cookies));

        String url = "https://stock.xueqiu.com/v5/stock/hot_stock/list.json?size=30&_type=10&type=10";
        JsonNode root = http.getJson(url, headers);
        JsonNode list = root.path("data").path("items");
        List<NewsItem> items = new ArrayList<>();
        if (list == null || !list.isArray()) {
            return items;
        }
        long now = System.currentTimeMillis();
        for (JsonNode n : list) {
            if (n.path("ad").asInt(0) == 1) {
                continue;
            }
            String code = trimToNull(n.path("code").asText(null));
            String name = trimToNull(n.path("name").asText(null));
            if (code == null || name == null) {
                continue;
            }
            NewsItem item = new NewsItem();
            item.setId("xueqiu:" + code);
            item.setTitle(name);
            item.setUrl("https://xueqiu.com/s/" + code);
            item.setPubDate(now);
            item.setSource(key());
            item.setSourceName(name());
            double percent = n.path("percent").asDouble(0);
            double current = n.path("current").asDouble(0);
            item.setSummary(String.format("现价 %.2f · %+.2f%%", current, percent));
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
