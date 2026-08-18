package com.oops.calendar.service;

import com.oops.calendar.dto.NewsItem;
import com.oops.calendar.persistence.NewsFavoriteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 快讯收藏持久化测试(内存 H2):
 * 快照落库、整表替换、去重、保留首次收藏时间、客户端隔离、空列表清空。
 */
@DataJpaTest
class NewsFavoriteServiceTest {

    @Autowired
    private NewsFavoriteRepository repository;

    private NewsFavoriteService svc;

    @BeforeEach
    void setUp() {
        svc = new NewsFavoriteService(repository);
    }

    private static NewsItem item(String id, String title, String source) {
        NewsItem it = new NewsItem();
        it.setId(id);
        it.setTitle(title);
        it.setUrl("https://example.com/" + id);
        it.setPubDate(1_700_000_000_000L);
        it.setSource(source);
        it.setSourceName("源-" + source);
        it.setSummary("摘要 " + title);
        it.setImportant(false);
        return it;
    }

    @Test
    void saveThenGetRoundTripsSnapshot() {
        svc.save("c1", Collections.singletonList(item("jin10-1", "标题A", "jin10")));

        assertTrue(svc.isConfigured("c1"));
        List<NewsItem> got = svc.get("c1");
        assertEquals(1, got.size());
        NewsItem fav = got.get(0);
        assertEquals("jin10-1", fav.getId());
        assertEquals("标题A", fav.getTitle());
        assertEquals("https://example.com/jin10-1", fav.getUrl());
        assertEquals("jin10", fav.getSource());
        assertEquals("源-jin10", fav.getSourceName());
        assertEquals("摘要 标题A", fav.getSummary());
        assertEquals(Long.valueOf(1_700_000_000_000L), fav.getPubDate());
    }

    @Test
    void saveReplacesWholeList() {
        svc.save("c2", Arrays.asList(item("a", "A", "jin10"), item("b", "B", "cls")));
        svc.save("c2", Arrays.asList(item("b", "B2", "cls"), item("c", "C", "xueqiu")));

        List<NewsItem> got = svc.get("c2");
        assertEquals(2, got.size(), "整表替换:不在新列表中的 a 应被移除");
        List<String> ids = got.stream().map(NewsItem::getId).collect(Collectors.toList());
        assertTrue(ids.contains("b"));
        assertTrue(ids.contains("c"));
        assertFalse(ids.contains("a"));
        // b 的快照被刷新为最新内容
        assertEquals("B2", got.stream().filter(x -> x.getId().equals("b")).findFirst().get().getTitle());
    }

    @Test
    void keepsOriginalFavoriteTimeOnResave() throws InterruptedException {
        svc.save("c3", Collections.singletonList(item("a", "A1", "jin10")));
        Thread.sleep(5);
        svc.save("c3", Arrays.asList(item("a", "A2", "jin10"), item("b", "B", "cls")));

        List<NewsItem> got = svc.get("c3");
        // b 是后收藏的,应排在 a 前面(收藏时间倒序)
        assertEquals("b", got.get(0).getId());
        assertEquals("a", got.get(1).getId());
        // a 的快照已刷新,但收藏时间未被推到最新
        assertEquals("A2", got.get(1).getTitle());
    }

    @Test
    void dedupesDuplicateItemsInOneSave() {
        svc.save("c4", Arrays.asList(item("x", "X", "jin10"), item("x", "X2", "jin10")));
        assertEquals(1, svc.get("c4").size(), "同一快讯在请求中重复时应去重");
        assertEquals("X", svc.get("c4").get(0).getTitle(), "保留首次出现的快照");
    }

    @Test
    void blankClientIdIsIgnored() {
        svc.save("  ", Collections.singletonList(item("a", "A", "jin10")));
        assertFalse(svc.isConfigured("  "));
        assertFalse(svc.isConfigured("nobody"));
        assertTrue(svc.get("nobody").isEmpty());
    }

    @Test
    void malformedItemsAreSkippedQuietly() {
        // 缺 title / 缺 source / 缺 id / null 的条目都应被跳过,不整批回滚,有效条目照常落库
        NewsItem noTitle = item("t1", " ", "jin10");
        NewsItem noSource = item("s1", "有摘要", "  ");
        NewsItem noId = item(" ", "无 id", "jin10");
        svc.save("c_malformed", Arrays.asList(noTitle, noSource, noId, null, item("ok", "有效", "cls")));

        assertTrue(svc.isConfigured("c_malformed"));
        assertEquals(1, svc.get("c_malformed").size(), "仅有效条目落库");
        assertEquals("ok", svc.get("c_malformed").get(0).getId());
    }

    @Test
    void longSummaryRoundTripsUnchanged() {
        // 真实快讯摘要可达数百字,验证 CLOB 字段与 UTF-8 往返无损
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            sb.append("财联社8月17日电,市场消息面持续发酵,板块轮动加快,投资者需关注政策与基本面的边际变化。");
        }
        String longSummary = sb.toString();
        NewsItem it = item("clob-1", "长摘要", "cls");
        it.setSummary(longSummary);
        it.setTitle("长摘要标题「「「测试」」」🚀");

        svc.save("c_clob", Collections.singletonList(it));
        NewsItem got = svc.get("c_clob").get(0);
        assertEquals(it.getTitle(), got.getTitle(), "中文/特殊字符/emoji 标题应无损");
        assertEquals(longSummary, got.getSummary(), "超长摘要应完整往返");
    }

    @Test
    void independentClientsDoNotInterfere() {
        svc.save("c_a", Collections.singletonList(item("a", "A", "jin10")));
        svc.save("c_b", Collections.singletonList(item("b", "B", "cls")));
        assertEquals(1, svc.get("c_a").size());
        assertEquals(1, svc.get("c_b").size());
        assertTrue(svc.get("c_c").isEmpty());
        assertEquals("a", svc.get("c_a").get(0).getId());
    }

    @Test
    void savingEmptyListClearsEntry() {
        svc.save("c5", Collections.singletonList(item("a", "A", "jin10")));
        assertTrue(svc.isConfigured("c5"));

        svc.save("c5", Collections.emptyList());
        assertFalse(svc.isConfigured("c5"));
        assertTrue(svc.get("c5").isEmpty());
    }
}
