package com.oops.calendar.service.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.oops.calendar.dto.NewsItem;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 金十数据 快讯。
 * GET https://www.jin10.com/flash_newest.js?t={ts} 返回 "var newest = [...]"。
 */
@Component
public class Jin10NewsSource implements NewsSource {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId SH = ZoneId.of("Asia/Shanghai");
    private static final Pattern BRACKET = Pattern.compile("^【([^】]*)】(.*)$");

    private final NewsHttpClient http;

    public Jin10NewsSource(NewsHttpClient http) {
        this.http = http;
    }

    @Override
    public String key() {
        return "jin10";
    }

    @Override
    public String name() {
        return "金十数据";
    }

    @Override
    public String icon() {
        return "jin10.png";
    }

    @Override
    public List<NewsItem> fetch() {
        String url = "https://www.jin10.com/flash_newest.js?t=" + System.currentTimeMillis();
        String text = http.getText(url);
        int eq = text.indexOf('=');
        String json = (eq >= 0 ? text.substring(eq + 1) : text).replaceAll(";+\\s*$", "").trim();
        JsonNode arr = http.parse(json);
        List<NewsItem> items = new ArrayList<>();
        if (arr == null || !arr.isArray()) {
            return items;
        }
        for (JsonNode n : arr) {
            if (containsChannel(n, 5)) {
                continue; // 过滤频道 5(与 newsnow 保持一致)
            }
            String id = textOf(n, "id");
            if (id == null) {
                continue;
            }
            JsonNode data = n.path("data");
            String raw = textOf(data, "title");
            String content = textOf(data, "content");
            if (raw == null && content != null) {
                raw = content;
            }
            if (raw == null || raw.isEmpty()) {
                continue;
            }
            String title = raw;
            String summary = null;
            Matcher m = BRACKET.matcher(raw.trim());
            if (m.matches()) {
                title = m.group(1).trim();
                summary = m.group(2).trim();
            }
            NewsItem item = new NewsItem();
            item.setId("jin10:" + id);
            item.setTitle(title);
            item.setUrl("https://flash.jin10.com/detail/" + id);
            item.setPubDate(parseTime(textOf(n, "time")));
            item.setSource(key());
            item.setSourceName(name());
            item.setSummary(summary);
            item.setImportant(n.path("important").asInt(0) != 0);
            items.add(item);
        }
        return items;
    }

    private static boolean containsChannel(JsonNode n, int target) {
        JsonNode ch = n.get("channel");
        if (ch == null || !ch.isArray()) {
            return false;
        }
        for (JsonNode c : ch) {
            if (c.asInt(-1) == target) {
                return true;
            }
        }
        return false;
    }

    private static String textOf(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        String s = v.asText().trim();
        return s.isEmpty() ? null : s;
    }

    private static Long parseTime(String s) {
        if (s == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(s, TIME_FMT).atZone(SH).toInstant().toEpochMilli();
        } catch (Exception e) {
            return null;
        }
    }
}
