package com.oops.calendar.service.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oops.calendar.persistence.NewsPreferenceEntity;
import com.oops.calendar.persistence.NewsPreferenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 快讯数据源偏好持久化(H2 文件库,news_preference 表),重启不丢。
 * 每个客户端一行,启用的数据源 key 以 JSON 数组存于 sources 列;
 * 保存时仅保留已知数据源 key;允许保存空列表(= 全部禁用,configured 仍为 true)。
 * 首次启动会把旧的 JSON 文件(news.data-dir/news-preferences.json)一次性导入数据库,
 * 之后不再读写该文件。
 */
@Component
public class NewsPreferencesService {

    private static final Logger log = LoggerFactory.getLogger(NewsPreferencesService.class);
    private static final String FILE_NAME = "news-preferences.json";

    private final NewsPreferenceRepository repository;
    private final Set<String> knownKeys;
    private final ObjectMapper objectMapper;
    private final Path legacyFile;

    public NewsPreferencesService(NewsPreferenceRepository repository, List<NewsSource> sources,
                                  ObjectMapper objectMapper,
                                  @Value("${news.data-dir:./data}") String dataDir) {
        this.repository = repository;
        Set<String> keys = new HashSet<>();
        for (NewsSource s : sources) {
            keys.add(s.key());
        }
        this.knownKeys = Collections.unmodifiableSet(keys);
        this.objectMapper = objectMapper;
        this.legacyFile = Paths.get(dataDir, FILE_NAME);
    }

    /** 一次性迁移:旧 JSON 文件中尚未入库的客户端配置导入数据库。幂等,可重复执行。 */
    @PostConstruct
    public void migrateFromLegacyFile() {
        try {
            if (!Files.exists(legacyFile)) {
                return;
            }
            JsonNode root = objectMapper.readTree(Files.readAllBytes(legacyFile));
            if (root == null || !root.isObject()) {
                return;
            }
            int migrated = 0;
            Iterator<Map.Entry<String, JsonNode>> it = root.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                String clientId = e.getKey().trim();
                if (clientId.isEmpty() || repository.existsByClientId(clientId)) {
                    continue;
                }
                List<String> keys = new ArrayList<>();
                for (JsonNode n : e.getValue()) {
                    String k = n.asText();
                    if (knownKeys.contains(k)) {
                        keys.add(k);
                    }
                }
                save(clientId, keys);
                migrated++;
            }
            if (migrated > 0) {
                log.info("数据源偏好已迁移到数据库: {} 个客户端(来源 {})", migrated, legacyFile);
            }
        } catch (Exception e) {
            log.warn("数据源偏好迁移失败(忽略,后续以数据库为准): {}", e.getMessage());
        }
    }

    public boolean isConfigured(String clientId) {
        return clientId != null && repository.existsByClientId(clientId.trim());
    }

    /** 返回该客户端保存的数据源 key(按保存顺序),未配置过返回空列表。 */
    public List<String> get(String clientId) {
        if (clientId == null || clientId.trim().isEmpty()) {
            return Collections.emptyList();
        }
        Optional<NewsPreferenceEntity> row = repository.findByClientId(clientId.trim());
        return row.isPresent() ? parseSources(row.get().getSourcesJson()) : Collections.emptyList();
    }

    /** 保存数据源偏好:去重 + 仅保留已知源;空列表 = 全部禁用(仍记为已配置)。 */
    @Transactional
    public synchronized void save(String clientId, List<String> sources) {
        if (clientId == null || clientId.trim().isEmpty()) {
            return;
        }
        String id = clientId.trim();
        List<String> clean = new ArrayList<>();
        if (sources != null) {
            clean.addAll(new LinkedHashSet<>(sources));
        }
        clean.removeIf(k -> !knownKeys.contains(k));

        NewsPreferenceEntity row = repository.findByClientId(id).orElseGet(NewsPreferenceEntity::new);
        row.setClientId(id);
        row.setSourcesJson(writeSources(clean));
        row.setUpdatedAt(Instant.now());
        repository.save(row);
    }

    private List<String> parseSources(String json) {
        try {
            JsonNode arr = objectMapper.readTree(json);
            if (arr == null || !arr.isArray()) {
                return Collections.emptyList();
            }
            List<String> keys = new ArrayList<>();
            for (JsonNode n : arr) {
                String k = n.asText();
                if (knownKeys.contains(k)) {
                    keys.add(k);
                }
            }
            return keys;
        } catch (Exception e) {
            log.warn("数据源偏好 JSON 解析失败,按空配置处理: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private String writeSources(List<String> clean) {
        try {
            return objectMapper.writeValueAsString(clean);
        } catch (Exception e) {
            log.warn("数据源偏好 JSON 序列化失败,按空配置处理: {}", e.getMessage());
            return "[]";
        }
    }
}