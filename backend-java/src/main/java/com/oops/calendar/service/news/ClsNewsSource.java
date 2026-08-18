package com.oops.calendar.service.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.oops.calendar.dto.NewsItem;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 财联社 电报(telegraph)。
 * GET https://www.cls.cn/v1/roll/get_roll_list?appName=...&sign=...
 * 签名:sign = MD5(SHA1(按字典序排序后的查询串)),参考 newsnow/RSSHub 实现。
 */
@Component
public class ClsNewsSource implements NewsSource {

    private final NewsHttpClient http;

    public ClsNewsSource(NewsHttpClient http) {
        this.http = http;
    }

    @Override
    public String key() {
        return "cls";
    }

    @Override
    public String name() {
        return "财联社";
    }

    @Override
    public String icon() {
        return "cls.png";
    }

    @Override
    public List<NewsItem> fetch() {
        long nowSec = System.currentTimeMillis() / 1000;
        TreeMap<String, String> params = new TreeMap<>();
        params.put("appName", "CailianpressWeb");
        params.put("os", "web");
        params.put("sv", "7.7.5");
        params.put("last_time", String.valueOf(nowSec));
        params.put("refresh_type", "1");
        params.put("rn", "30");

        StringBuilder qs = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (qs.length() > 0) {
                qs.append('&');
            }
            qs.append(e.getKey()).append('=').append(e.getValue());
        }
        String sign = DigestUtil.md5Hex(DigestUtil.sha1Hex(qs.toString()));
        String url = "https://www.cls.cn/v1/roll/get_roll_list?" + qs + "&sign=" + sign;

        Map<String, String> headers = new HashMap<>();
        headers.put(HttpHeaders.REFERER, "https://www.cls.cn/telegraph");
        JsonNode root = http.getJson(url, headers);
        JsonNode list = root.path("data").path("roll_data");

        List<NewsItem> items = new ArrayList<>();
        if (list == null || !list.isArray()) {
            return items;
        }
        for (JsonNode n : list) {
            if (n.path("is_ad").asInt(0) == 1) {
                continue;
            }
            String id = n.path("id").asText();
            if (id.isEmpty()) {
                continue;
            }
            String title = trimToNull(n.path("title").asText(null));
            String brief = trimToNull(n.path("brief").asText(null));
            if (title == null) {
                title = brief;
            }
            if (title == null) {
                continue;
            }
            NewsItem item = new NewsItem();
            item.setId("cls:" + id);
            item.setTitle(title);
            item.setUrl("https://www.cls.cn/detail/" + id);
            long ctime = n.path("ctime").asLong(0);
            item.setPubDate(ctime > 0 ? ctime * 1000 : null);
            item.setSource(key());
            item.setSourceName(name());
            item.setSummary(brief != null && !brief.equals(title) ? brief : null);
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
