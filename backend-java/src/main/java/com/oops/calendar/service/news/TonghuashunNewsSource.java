package com.oops.calendar.service.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.oops.calendar.dto.NewsItem;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 同花顺 7x24 快讯。
 * GET http://news.10jqka.com.cn/tapp/news/push/stock/?page=1&tag=&track=website&pagesize=50
 * 返回 {"code":"200","data":{"list":[{id,title,digest,url,ctime,...}]}}
 * ctime 为 epoch 秒;无鉴权、无 cookie。
 */
@Component
public class TonghuashunNewsSource implements NewsSource {

    private final NewsHttpClient http;

    public TonghuashunNewsSource(NewsHttpClient http) {
        this.http = http;
    }

    @Override
    public String key() {
        return "tonghuashun";
    }

    @Override
    public String name() {
        return "同花顺";
    }

    @Override
    public String icon() {
        return "tonghuashun.png";
    }

    @Override
    public List<NewsItem> fetch() {
        String url = "http://news.10jqka.com.cn/tapp/news/push/stock/?page=1&tag=&track=website&pagesize=50";
        JsonNode root = http.getJson(url);
        List<NewsItem> items = new ArrayList<>();
        if (root == null || !"200".equals(root.path("code").asText())) {
            return items;
        }
        JsonNode list = root.path("data").path("list");
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
            item.setId("tonghuashun:" + id);
            item.setTitle(title);
            item.setSummary(trimToNull(n.path("digest").asText(null)));
            String link = trimToNull(n.path("url").asText(null));
            item.setUrl(link != null ? link : ("https://news.10jqka.com.cn/" + id + ".shtml"));
            item.setPubDate(parseSeconds(n.path("ctime").asText(null)));
            item.setSource(key());
            item.setSourceName(name());
            items.add(item);
        }
        return items;
    }

    private static Long parseSeconds(String s) {
        if (s == null) {
            return null;
        }
        try {
            return Long.parseLong(s.trim()) * 1000L;
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
