package com.oops.calendar.service.news;

import com.oops.calendar.config.NewsProperties;
import com.oops.calendar.dto.NewsItem;
import com.oops.calendar.dto.NewsResponse;
import com.oops.calendar.dto.NewsSourceMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 财经快讯聚合:并发安全缓存(按数据源 + TTL),单源失败自动降级,合并后按时间倒序。
 */
@Service
public class NewsService {

    private static final Logger log = LoggerFactory.getLogger(NewsService.class);

    private static final Comparator<NewsItem> BY_TIME_DESC =
            Comparator.comparing(NewsItem::getPubDate, Comparator.nullsLast(Comparator.reverseOrder()));

    private final NewsProperties props;
    private final Map<String, NewsSource> byKey;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public NewsService(NewsProperties props, List<NewsSource> sources) {
        this.props = props;
        Map<String, NewsSource> map = new LinkedHashMap<>();
        for (NewsSource s : sources) {
            map.put(s.key(), s);
        }
        this.byKey = Collections.unmodifiableMap(map);
    }

    public List<NewsSourceMeta> listSources() {
        List<NewsSourceMeta> metas = new ArrayList<>();
        for (NewsSource s : byKey.values()) {
            metas.add(new NewsSourceMeta(s.key(), s.name(), s.icon()));
        }
        return metas;
    }

    /**
     * @param sourceParam 逗号分隔的数据源 key;为空或空白表示全部。
     */
    public NewsResponse query(String sourceParam) {
        List<NewsItem> items = new ArrayList<>();
        for (NewsSource s : selectSources(sourceParam)) {
            try {
                items.addAll(fetchCached(s));
            } catch (Exception e) {
                log.warn("新闻源 {} 获取失败: {}", s.key(), e.getMessage());
            }
        }
        items.sort(BY_TIME_DESC);
        int max = props.getMaxItems();
        if (items.size() > max) {
            items = new ArrayList<>(items.subList(0, max));
        }
        return new NewsResponse(items, listSources(), System.currentTimeMillis());
    }

    private List<NewsSource> selectSources(String sourceParam) {
        if (sourceParam == null || sourceParam.trim().isEmpty()) {
            return new ArrayList<>(byKey.values());
        }
        List<NewsSource> selected = new ArrayList<>();
        for (String key : sourceParam.split(",")) {
            NewsSource s = byKey.get(key.trim());
            if (s != null) {
                selected.add(s);
            }
        }
        // 筛选参数全部无效时回退到全部源,避免"暂无快讯"的误导
        return selected.isEmpty() ? new ArrayList<>(byKey.values()) : selected;
    }

    private List<NewsItem> fetchCached(NewsSource s) {
        CacheEntry entry = cache.get(s.key());
        long now = System.currentTimeMillis();
        if (entry != null && now - entry.fetchedAt < props.getCacheTtlSeconds() * 1000L) {
            return entry.items;
        }
        List<NewsItem> items = s.fetch();
        int perSource = props.getMaxItemsPerSource();
        if (items.size() > perSource) {
            items = new ArrayList<>(items.subList(0, perSource));
        }
        cache.put(s.key(), new CacheEntry(items, now));
        return items;
    }

    private static final class CacheEntry {
        final List<NewsItem> items;
        final long fetchedAt;

        CacheEntry(List<NewsItem> items, long fetchedAt) {
            this.items = items;
            this.fetchedAt = fetchedAt;
        }
    }
}
