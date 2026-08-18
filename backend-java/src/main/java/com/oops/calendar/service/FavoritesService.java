package com.oops.calendar.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 收藏公司持久化:按 clientId 存 JSON 文件(默认 ./data/favorites.json),重启不丢。
 * 快讯收藏已改用 H2 数据库(NewsFavoriteService);收藏公司仍保留 JSON 文件机制。
 * 空收藏会删除该客户端的记录。
 */
@Component
public class FavoritesService {

    private static final Logger log = LoggerFactory.getLogger(FavoritesService.class);
    private static final String FILE_NAME = "favorites.json";

    private final ObjectMapper objectMapper;
    private final Path file;
    private final Map<String, Set<String>> favorites = new ConcurrentHashMap<>();

    public FavoritesService(ObjectMapper objectMapper, @Value("${news.data-dir:./data}") String dataDir) {
        this.objectMapper = objectMapper;
        this.file = Paths.get(dataDir, FILE_NAME);
        load();
    }

    public boolean isConfigured(String clientId) {
        return clientId != null && favorites.containsKey(clientId);
    }

    /** 返回该客户端的收藏(按加入顺序),未配置过返回空列表。 */
    public List<String> get(String clientId) {
        Set<String> set = favorites.get(clientId);
        return set == null ? Collections.emptyList() : new ArrayList<>(set);
    }

    /** 保存收藏:符号转大写并去重;空列表 = 删除该客户端记录(等价于无收藏)。 */
    public synchronized void save(String clientId, List<String> symbols) {
        if (clientId == null || clientId.trim().isEmpty()) {
            return;
        }
        String id = clientId.trim();
        Set<String> clean = new LinkedHashSet<>();
        if (symbols != null) {
            for (String s : symbols) {
                if (s == null) {
                    continue;
                }
                String t = s.trim().toUpperCase(Locale.ROOT);
                if (!t.isEmpty()) {
                    clean.add(t);
                }
            }
        }
        if (clean.isEmpty()) {
            favorites.remove(id);
        } else {
            favorites.put(id, Collections.unmodifiableSet(clean));
        }
        persist();
    }

    private void load() {
        try {
            if (!Files.exists(file)) {
                return;
            }
            JsonNode root = objectMapper.readTree(Files.readAllBytes(file));
            if (root == null || !root.isObject()) {
                return;
            }
            Iterator<Map.Entry<String, JsonNode>> it = root.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                Set<String> syms = new LinkedHashSet<>();
                for (JsonNode n : e.getValue()) {
                    String s = n.asText().trim().toUpperCase(Locale.ROOT);
                    if (!s.isEmpty()) {
                        syms.add(s);
                    }
                }
                if (!syms.isEmpty()) {
                    favorites.put(e.getKey(), Collections.unmodifiableSet(syms));
                }
            }
            log.info("收藏已加载: {} 个客户端", favorites.size());
        } catch (Exception e) {
            log.warn("收藏加载失败,使用空列表: {}", e.getMessage());
        }
    }

    private void persist() {
        try {
            Path dir = file.getParent();
            if (dir != null) {
                Files.createDirectories(dir);
            }
            Files.write(file, objectMapper.writeValueAsBytes(favorites));
        } catch (Exception e) {
            log.warn("收藏保存失败: {}", e.getMessage());
        }
    }
}
