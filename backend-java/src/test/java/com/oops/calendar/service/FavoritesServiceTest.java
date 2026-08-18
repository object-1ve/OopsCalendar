package com.oops.calendar.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FavoritesServiceTest {

    @TempDir
    Path tempDir;

    private FavoritesService newService(Path dir) {
        return new FavoritesService(new ObjectMapper(), dir.toString());
    }

    @Test
    void savesAndLoadsAcrossRestart() {
        Path dir = tempDir.resolve("a");
        FavoritesService s1 = newService(dir);
        s1.save("c_test", Arrays.asList("aapl", " nvda ", "TSLA", "aapl"));

        // 大写、去空格、去重、保持加入顺序
        assertEquals(Arrays.asList("AAPL", "NVDA", "TSLA"), s1.get("c_test"));

        // 模拟重启:新实例从同一目录加载
        FavoritesService s2 = newService(dir);
        assertTrue(s2.isConfigured("c_test"));
        assertEquals(Arrays.asList("AAPL", "NVDA", "TSLA"), s2.get("c_test"));
    }

    @Test
    void unknownClientIsNotConfigured() {
        FavoritesService s = newService(tempDir.resolve("b"));
        assertFalse(s.isConfigured("nobody"));
        assertTrue(s.get("nobody").isEmpty());
    }

    @Test
    void savingEmptyListClearsEntry() {
        FavoritesService s = newService(tempDir.resolve("c"));
        s.save("c_x", Arrays.asList("AAPL"));
        assertTrue(s.isConfigured("c_x"));

        s.save("c_x", Collections.emptyList());
        assertFalse(s.isConfigured("c_x"));
        assertTrue(s.get("c_x").isEmpty());
    }

    @Test
    void blankClientIdIsIgnored() {
        FavoritesService s = newService(tempDir.resolve("d"));
        s.save("  ", Arrays.asList("AAPL"));
        assertFalse(s.isConfigured("  "));
        assertTrue(s.get("  ").isEmpty());
        assertTrue(s.get("c_other").isEmpty());
    }

    @Test
    void independentClientsDoNotInterfere() {
        FavoritesService s = newService(tempDir.resolve("e"));
        s.save("c_a", Arrays.asList("AAPL"));
        s.save("c_b", Arrays.asList("NVDA"));
        assertEquals(Collections.singletonList("AAPL"), s.get("c_a"));
        assertEquals(Collections.singletonList("NVDA"), s.get("c_b"));
        assertTrue(s.get("c_c").isEmpty());
    }
}
