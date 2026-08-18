package com.oops.calendar.service.news;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oops.calendar.dto.NewsItem;
import com.oops.calendar.persistence.NewsPreferenceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数据源偏好持久化测试(内存 H2):
 * 落库往返、空列表 = 全部禁用(仍视为已配置)、未知源过滤、客户端隔离、旧 JSON 一次性迁移。
 */
@DataJpaTest
class NewsPreferenceServiceTest {

    @Autowired
    private NewsPreferenceRepository repository;

    @TempDir
    Path tempDir;

    /** NewsSource 为多方法接口,测试用 stub 实现。 */
    private static NewsSource src(String key) {
        return new NewsSource() {
            @Override
            public String key() { return key; }

            @Override
            public String name() { return key; }

            @Override
            public List<NewsItem> fetch() { return Collections.emptyList(); }
        };
    }

    private NewsPreferencesService newService(String dataDir) {
        List<NewsSource> sources = Arrays.asList(
                src("jin10"), src("cls"), src("wallstreetcn"), src("eastmoney"),
                src("xueqiu"), src("gelonghui"), src("tonghuashun"));
        return new NewsPreferencesService(repository, sources, new ObjectMapper(), dataDir);
    }

    @Test
    void saveThenGetRoundTrips() {
        NewsPreferencesService svc = newService(tempDir.toString());
        svc.save("c1", Arrays.asList("jin10", "cls", "jin10"));

        assertTrue(svc.isConfigured("c1"));
        assertEquals(Arrays.asList("jin10", "cls"), svc.get("c1"), "应去重并保持顺序");
    }

    @Test
    void emptyListMeansConfiguredAllDisabled() {
        NewsPreferencesService svc = newService(tempDir.toString());
        svc.save("c_empty", Collections.emptyList());

        assertTrue(svc.isConfigured("c_empty"), "保存空列表 = 全部禁用,仍应视为已配置");
        assertTrue(svc.get("c_empty").isEmpty());
    }

    @Test
    void unknownKeysAreFiltered() {
        NewsPreferencesService svc = newService(tempDir.toString());
        svc.save("c_f", Arrays.asList("jin10", "not-a-source", "cls", "bogus"));

        assertEquals(Arrays.asList("jin10", "cls"), svc.get("c_f"), "未知数据源 key 应被丢弃");
    }

    @Test
    void blankClientIdIsIgnored() {
        NewsPreferencesService svc = newService(tempDir.toString());
        svc.save("  ", Collections.singletonList("jin10"));
        assertFalse(svc.isConfigured("  "));
        assertFalse(svc.isConfigured("nobody"));
        assertTrue(svc.get("nobody").isEmpty());
    }

    @Test
    void independentClientsDoNotInterfere() {
        NewsPreferencesService svc = newService(tempDir.toString());
        svc.save("c_a", Collections.singletonList("jin10"));
        svc.save("c_b", Collections.singletonList("cls"));
        assertEquals(Collections.singletonList("jin10"), svc.get("c_a"));
        assertEquals(Collections.singletonList("cls"), svc.get("c_b"));
        assertTrue(svc.get("c_c").isEmpty());
    }

    @Test
    void overwriteReplacesPreviousConfig() {
        NewsPreferencesService svc = newService(tempDir.toString());
        svc.save("c_o", Collections.singletonList("jin10"));
        svc.save("c_o", Collections.singletonList("cls"));
        assertEquals(Collections.singletonList("cls"), svc.get("c_o"), "再次保存应覆盖而非追加");
    }

    @Test
    void legacyJsonFileIsMigratedOnce() throws Exception {
        // 构造旧版 JSON 文件(与老实现同格式):{clientId: [keys]}
        Path dataDir = tempDir.resolve("legacy");
        Files.createDirectories(dataDir);
        String json = "{\"c_old\":[\"jin10\",\"not-a-source\",\"cls\"],\"c_empty\":[]}";
        Files.write(Paths.get(dataDir.toString(), "news-preferences.json"),
                json.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        NewsPreferencesService svc = newService(dataDir.toString());
        svc.migrateFromLegacyFile();

        assertTrue(svc.isConfigured("c_old"));
        assertEquals(Arrays.asList("jin10", "cls"), svc.get("c_old"), "迁移时应过滤未知源");
        assertTrue(svc.isConfigured("c_empty"), "旧文件中空列表也应迁移(全部禁用)");
        assertTrue(svc.get("c_empty").isEmpty());

        // 幂等:再次迁移不应产生重复/覆盖已有配置
        svc.save("c_old", Collections.singletonList("eastmoney"));
        svc.migrateFromLegacyFile();
        assertEquals(Collections.singletonList("eastmoney"), svc.get("c_old"),
                "已入库的客户端不应被旧文件覆盖");
    }

    @Test
    void survivesRestartThroughRepository() {
        NewsPreferencesService s1 = newService(tempDir.toString());
        s1.save("c_r", Arrays.asList("jin10", "cls"));

        // 模拟重启:同一仓库新实例仍能读回(数据在 H2 表中)
        NewsPreferencesService s2 = newService(tempDir.toString());
        assertTrue(s2.isConfigured("c_r"));
        assertEquals(Arrays.asList("jin10", "cls"), s2.get("c_r"));
    }
}
