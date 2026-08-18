package com.oops.calendar.service.news;

import com.oops.calendar.config.NewsProperties;
import com.oops.calendar.dto.NewsItem;
import com.oops.calendar.dto.NewsResponse;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NewsServiceTest {

    private static NewsItem item(String id, Long pubDate) {
        NewsItem it = new NewsItem();
        it.setId(id);
        it.setTitle("t-" + id);
        it.setUrl("https://example.com/" + id);
        it.setPubDate(pubDate);
        it.setSource(id.split(":")[0]);
        it.setSourceName(id.split(":")[0]);
        return it;
    }

    private static NewsService service(NewsSource... sources) {
        return new NewsService(new NewsProperties(), Arrays.asList(sources));
    }

    @Test
    void mergesAndSortsDescending() {
        NewsSource a = new FakeSource("a", Arrays.asList(item("a:1", 100L), item("a:2", 300L)));
        NewsSource b = new FakeSource("b", Arrays.asList(item("b:1", 200L)));
        NewsResponse resp = service(a, b).query(null);
        assertEquals(3, resp.getItems().size());
        assertEquals("a:2", resp.getItems().get(0).getId()); // 300
        assertEquals("b:1", resp.getItems().get(1).getId()); // 200
        assertEquals("a:1", resp.getItems().get(2).getId()); // 100
        assertEquals(2, resp.getSources().size());
    }

    @Test
    void filtersBySourceParam() {
        NewsSource a = new FakeSource("a", Arrays.asList(item("a:1", 100L)));
        NewsSource b = new FakeSource("b", Arrays.asList(item("b:1", 200L)));
        NewsResponse resp = service(a, b).query("b");
        assertEquals(1, resp.getItems().size());
        assertEquals("b:1", resp.getItems().get(0).getId());
    }

    @Test
    void unknownSourceParamFallsBackToAll() {
        NewsSource a = new FakeSource("a", Arrays.asList(item("a:1", 100L)));
        NewsResponse resp = service(a).query("no-such-source");
        assertEquals(1, resp.getItems().size());
    }

    @Test
    void failedSourceIsIsolated() {
        NewsSource a = new FakeSource("a", Arrays.asList(item("a:1", 100L)));
        NewsSource bad = new NewsSource() {
            @Override public String key() { return "bad"; }
            @Override public String name() { return "bad"; }
            @Override public List<NewsItem> fetch() { throw new NewsSourceException("boom"); }
        };
        NewsResponse resp = service(a, bad).query(null);
        assertEquals(1, resp.getItems().size());
        assertEquals("a:1", resp.getItems().get(0).getId());
    }

    @Test
    void cachesPerSourceWithinTtl() {
        AtomicInteger calls = new AtomicInteger();
        NewsSource a = new NewsSource() {
            @Override public String key() { return "a"; }
            @Override public String name() { return "a"; }
            @Override public List<NewsItem> fetch() {
                calls.incrementAndGet();
                return Arrays.asList(item("a:1", 100L));
            }
        };
        NewsService svc = service(a);
        svc.query(null);
        svc.query(null);
        assertEquals(1, calls.get(), "TTL 内第二次查询应命中缓存");
    }

    @Test
    void sortsNullPubDateLast() {
        NewsSource a = new FakeSource("a", Arrays.asList(item("a:1", null), item("a:2", 100L)));
        NewsResponse resp = service(a).query(null);
        assertEquals("a:2", resp.getItems().get(0).getId());
        assertEquals("a:1", resp.getItems().get(1).getId());
    }

    @Test
    void appliesMaxItems() {
        List<NewsItem> many = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            many.add(item("a:" + i, (long) (1000 - i)));
        }
        NewsProperties props = new NewsProperties();
        props.setMaxItems(10);
        NewsService svc = new NewsService(props, Arrays.asList(new FakeSource("a", many)));
        assertEquals(10, svc.query(null).getItems().size());
    }

    @Test
    void digestSignMatchesKnownVector() {
        // MD5(SHA1("abc")) 由外部独立计算:74076346dee6d87ffe0f2f069dda57bb
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", DigestUtil.sha1Hex("abc"));
        assertEquals("900150983cd24fb0d6963f7d28e17f72", DigestUtil.md5Hex("abc"));
        assertEquals("74076346dee6d87ffe0f2f069dda57bb", DigestUtil.md5Hex(DigestUtil.sha1Hex("abc")));
    }

    private static final class FakeSource implements NewsSource {
        private final String key;
        private final List<NewsItem> items;

        FakeSource(String key, List<NewsItem> items) {
            this.key = key;
            this.items = items;
        }

        @Override public String key() { return key; }
        @Override public String name() { return key; }
        @Override public List<NewsItem> fetch() { return new ArrayList<>(items); }
    }
}

