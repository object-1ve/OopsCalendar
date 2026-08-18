package com.oops.calendar.service.news;

import com.oops.calendar.config.NewsProperties;
import com.oops.calendar.dto.NewsItem;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NewsStreamServiceTest {

    private static NewsItem item(String id) {
        NewsItem it = new NewsItem();
        it.setId(id);
        it.setTitle("t-" + id);
        it.setUrl("https://example.com/" + id);
        it.setSource(id.split(":")[0]);
        it.setSourceName(id.split(":")[0]);
        it.setPubDate(1000L);
        return it;
    }

    @Test
    void pollPushesOnlyNewIds() {
        NewsProperties props = new NewsProperties();
        props.setEnabled(true);
        FakeSource src = new FakeSource("a", Arrays.asList(item("a:1"), item("a:2")));
        NewsStreamService svc = new NewsStreamService(props, Arrays.asList(src));

        svc.poll();
        assertEquals(2, svc.recent().size());
        assertEquals(2, svc.seenIds().size());

        // 同一批数据再次轮询:无新增,快照不变
        svc.poll();
        assertEquals(2, svc.recent().size());

        // 出现新条目:只增量加入
        src.setItems(Arrays.asList(item("a:1"), item("a:2"), item("a:3")));
        svc.poll();
        assertEquals(3, svc.recent().size());
        assertEquals(3, svc.seenIds().size());
    }

    @Test
    void pollDisabledDoesNothing() {
        NewsProperties props = new NewsProperties();
        props.setEnabled(false);
        NewsStreamService svc =
                new NewsStreamService(props, Arrays.asList(new FakeSource("a", Arrays.asList(item("a:1")))));
        svc.poll();
        assertEquals(0, svc.recent().size());
    }

    private static final class FakeSource implements NewsSource {
        private final String key;
        private List<NewsItem> items;

        FakeSource(String key, List<NewsItem> items) {
            this.key = key;
            this.items = items;
        }

        void setItems(List<NewsItem> items) {
            this.items = items;
        }

        @Override public String key() { return key; }
        @Override public String name() { return key; }
        @Override public List<NewsItem> fetch() { return new ArrayList<>(items); }
    }
}
