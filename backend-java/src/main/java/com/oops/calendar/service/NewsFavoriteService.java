package com.oops.calendar.service;

import com.oops.calendar.dto.NewsItem;
import com.oops.calendar.persistence.NewsFavoriteEntity;
import com.oops.calendar.persistence.NewsFavoriteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 快讯收藏持久化(H2 文件库):按 clientId 保存整条快讯快照,重启不丢。
 * 与财报收藏(FavoritesService,JSON 文件)相比,快讯内容来自上游流且会滚动淘汰,
 * 因此必须落完整快照,收藏列表才能离线/持久展示。同一快讯重复收藏保持首次时间,
 * 再次收藏(快照刷新)不会把收藏时间推后;整表替换语义:不在请求列表中的收藏会被移除。
 */
@Service
public class NewsFavoriteService {

    private final NewsFavoriteRepository repository;

    public NewsFavoriteService(NewsFavoriteRepository repository) {
        this.repository = repository;
    }

    public boolean isConfigured(String clientId) {
        return clientId != null && repository.existsByClientId(clientId.trim());
    }

    /** 返回该客户端的收藏,最近收藏的在前;未配置过返回空列表。 */
    public List<NewsItem> get(String clientId) {
        if (clientId == null || clientId.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return repository.findByClientIdOrderByCreatedAtDescIdDesc(clientId.trim())
                .stream()
                .map(NewsFavoriteEntity::toDto)
                .collect(Collectors.toList());
    }

    /**
     * 保存收藏(整表替换):已存在的条目刷新快照并保留首次收藏时间,新条目记 now,
     * 不在请求列表中的旧收藏被移除。空列表 = 清空该客户端收藏。
     * 缺 id / title / source 的畸形条目会被静默跳过,不影响其余条目落库。
     * synchronized:同一实例内并发整表替换串行执行,避免 delete+insert 交错触发唯一约束冲突。
     */
    @Transactional
    public synchronized void save(String clientId, List<NewsItem> items) {
        if (clientId == null || clientId.trim().isEmpty()) {
            return;
        }
        String id = clientId.trim();

        // 按 itemId 去重(保留首次出现)并过滤非法条目:缺 id / title / source 的快讯无法成行落库,
        // 跳过而非报错,避免畸形请求把整次保存打回 500
        Map<String, NewsItem> incoming = new LinkedHashMap<>();
        if (items != null) {
            for (NewsItem it : items) {
                if (it == null
                        || it.getId() == null || it.getId().trim().isEmpty()
                        || it.getTitle() == null || it.getTitle().trim().isEmpty()
                        || it.getSource() == null || it.getSource().trim().isEmpty()) {
                    continue;
                }
                incoming.putIfAbsent(it.getId().trim(), it);
            }
        }

        List<NewsFavoriteEntity> existing = repository.findByClientIdOrderByCreatedAtDescIdDesc(id);
        Map<String, NewsFavoriteEntity> byItem = existing.stream()
                .collect(Collectors.toMap(NewsFavoriteEntity::getItemId, e -> e, (a, b) -> a, LinkedHashMap::new));

        List<NewsFavoriteEntity> toRemove = new ArrayList<>();
        for (NewsFavoriteEntity e : existing) {
            if (!incoming.containsKey(e.getItemId())) {
                toRemove.add(e);
            }
        }
        repository.deleteAll(toRemove);

        Instant now = Instant.now();
        List<NewsFavoriteEntity> toSave = new ArrayList<>();
        for (Map.Entry<String, NewsItem> entry : incoming.entrySet()) {
            NewsFavoriteEntity entity = byItem.get(entry.getKey());
            if (entity != null) {
                entity.updateSnapshot(entry.getValue());
                toSave.add(entity);
            } else {
                toSave.add(NewsFavoriteEntity.from(entry.getValue(), id, now));
            }
        }
        repository.saveAll(toSave);
    }
}
