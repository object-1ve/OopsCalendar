package com.oops.calendar.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 数据源偏好接口集成测试(内存 H2):
 * PUT 保存 → GET 读回;空列表 = 全部禁用(configured 仍为 true);未知客户端 configured=false。
 */
@SpringBootTest
@AutoConfigureMockMvc
class NewsPreferencesControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void preferencesRoundTrip() throws Exception {
        String clientId = "c_pref_" + System.nanoTime();

        mvc.perform(put("/api/news/preferences").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"" + clientId + "\",\"sources\":[\"jin10\",\"cls\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.sources[0]").value("jin10"))
                .andExpect(jsonPath("$.sources[1]").value("cls"));

        mvc.perform(get("/api/news/preferences").param("clientId", clientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.sources.length()").value(2));

        // 未配置的客户端
        mvc.perform(get("/api/news/preferences").param("clientId", "nobody"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.sources").isEmpty());

        // 空列表 = 全部禁用,但仍记为已配置
        mvc.perform(put("/api/news/preferences").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"" + clientId + "\",\"sources\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.sources").isEmpty());
    }

    @Test
    void unknownKeysAreFilteredByServer() throws Exception {
        String clientId = "c_filt_" + System.nanoTime();
        mvc.perform(put("/api/news/preferences").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"" + clientId + "\",\"sources\":[\"jin10\",\"not-a-source\",\"cls\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.sources.length()").value(2))
                .andExpect(jsonPath("$.sources[0]").value("jin10"))
                .andExpect(jsonPath("$.sources[1]").value("cls"));
    }
}
