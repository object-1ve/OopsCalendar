package com.oops.calendar.service.news;

import com.oops.calendar.config.NewsProperties;
import com.oops.calendar.dto.NewsItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 实时快讯推送:后台定时(默认 15s)增量轮询各数据源,仅推送新增条目给所有 SSE 订阅者。
 * 各上游均为公开接口,轮询频率远低于风控阈值;单源失败自动跳过不影响其他源。
 */
@Component
public class NewsStreamService {

    private static final Logger log = LoggerFactory.getLogger(NewsStreamService.class);
    private static final Comparator<NewsItem> BY_TIME_DESC =
            Comparator.comparing(NewsItem::getPubDate, Comparator.nullsLast(Comparator.reverseOrder()));
    private static final int RECENT_LIMIT = 200;

    private final NewsProperties props;
    private final List<NewsSource> sources;
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final Set<String> seenIds = ConcurrentHashMap.newKeySet();
    private volatile List<NewsItem> recent = Collections.emptyList();

    public NewsStreamService(NewsProperties props, List<NewsSource> sources) {
        this.props = props;
        this.sources = sources;
    }

    /** 新订阅者接入:先发一份最近快照(客户端会按 id 去重)。 */
    public SseEmitter connect() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError((e) -> emitters.remove(emitter));
        List<NewsItem> snapshot = recent;
        if (!snapshot.isEmpty()) {
            try {
                emitter.send(SseEmitter.event().name("news").data(snapshot));
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }
        return emitter;
    }

    /** 定时增量轮询,推送新增条目;无新增也发心跳,让前端"更新于"按轮询周期刷新。 */
    @Scheduled(fixedDelayString = "${news.poll-ms:15000}")
    public void poll() {
        if (!props.isEnabled()) {
            return;
        }
        List<NewsItem> fresh = new ArrayList<>();
        for (NewsSource s : sources) {
            try {
                fresh.addAll(s.fetch());
            } catch (Exception e) {
                log.warn("新闻源 {} 轮询失败: {}", s.key(), e.getMessage());
            }
        }
        if (!fresh.isEmpty()) {
            List<NewsItem> news = new ArrayList<>();
            for (NewsItem it : fresh) {
                if (seenIds.add(it.getId())) {
                    news.add(it);
                }
            }
            if (!news.isEmpty()) {
                List<NewsItem> merged = new ArrayList<>(recent);
                merged.addAll(news);
                merged.sort(BY_TIME_DESC);
                if (merged.size() > RECENT_LIMIT) {
                    merged = new ArrayList<>(merged.subList(0, RECENT_LIMIT));
                }
                recent = merged;
                broadcast(news);
            }
        }
        // 每轮轮询结束无条件发心跳(无论有无新增):前端据此把"更新于"按 15s 周期刷新
        sendHeartbeat();
    }

    /** 轮询心跳:即使没有新增也推送服务端时间,客户端据此刷新"更新于"。 */
    private void sendHeartbeat() {
        if (emitters.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (SseEmitter e : emitters) {
            try {
                e.send(SseEmitter.event().name("heartbeat").data(now));
            } catch (IOException | IllegalStateException ex) {
                emitters.remove(e);
            }
        }
    }

    private void broadcast(List<NewsItem> news) {
        if (emitters.isEmpty()) {
            return;
        }
        for (SseEmitter e : emitters) {
            try {
                e.send(SseEmitter.event().name("news").data(news));
            } catch (IOException | IllegalStateException ex) {
                emitters.remove(e);
            }
        }
    }

    /** 供单元测试:当前最近快照。 */
    List<NewsItem> recent() {
        return recent;
    }

    /** 供单元测试:已见 id 集合。 */
    Set<String> seenIds() {
        return seenIds;
    }
}
