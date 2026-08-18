package com.oops.calendar.service.news;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 中文相对时间解析(格隆汇等页面):"刚刚"、"5分钟前"、"3小时前"、"2天前"、
 * "昨天 08:30"、"08:30"、"06-12 08:30"、"2026-06-12 08:30"。解析失败返回 null。
 */
final class RelativeTime {

    private static final ZoneId SH = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter MMDD_HHMM = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final DateTimeFormatter YMD_HHMM = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final Pattern MIN = Pattern.compile("^(\\d+)\\s*分钟前$");
    private static final Pattern HOUR = Pattern.compile("^(\\d+)\\s*小时前$");
    private static final Pattern DAY = Pattern.compile("^(\\d+)\\s*天前$");

    private RelativeTime() {
    }

    static Long parse(String text) {
        if (text == null) {
            return null;
        }
        String s = text.trim();
        if (s.isEmpty()) {
            return null;
        }
        long now = System.currentTimeMillis();
        try {
            if ("刚刚".equals(s)) {
                return now;
            }
            Matcher m = MIN.matcher(s);
            if (m.matches()) {
                return now - Long.parseLong(m.group(1)) * 60_000L;
            }
            m = HOUR.matcher(s);
            if (m.matches()) {
                return now - Long.parseLong(m.group(1)) * 3_600_000L;
            }
            m = DAY.matcher(s);
            if (m.matches()) {
                return now - Long.parseLong(m.group(1)) * 86_400_000L;
            }
            LocalDateTime nowLocal = LocalDateTime.now(SH);
            if (s.startsWith("昨天 ")) {
                String hhmm = s.substring(3);
                LocalDateTime t = LocalDateTime.of(nowLocal.toLocalDate().minusDays(1),
                        LocalTime.parse(hhmm, HHMM));
                return t.atZone(SH).toInstant().toEpochMilli();
            }
            return parseAbsolute(s, nowLocal);
        } catch (Exception e) {
            return null;
        }
    }

    private static Long parseAbsolute(String s, LocalDateTime nowLocal) {
        LocalDateTime ldt = tryParse(s, YMD_HHMM);
        if (ldt == null) {
            ldt = tryParse(s, MMDD_HHMM);
            if (ldt != null) {
                ldt = ldt.withYear(nowLocal.getYear());
            }
        }
        if (ldt == null) {
            ldt = tryParse(s, HHMM);
            if (ldt != null) {
                ldt = LocalDateTime.of(nowLocal.toLocalDate(), ldt.toLocalTime());
            }
        }
        return ldt == null ? null : ldt.atZone(SH).toInstant().toEpochMilli();
    }

    private static LocalDateTime tryParse(String s, DateTimeFormatter fmt) {
        try {
            return LocalDateTime.parse(s, fmt);
        } catch (Exception e) {
            return null;
        }
    }
}
