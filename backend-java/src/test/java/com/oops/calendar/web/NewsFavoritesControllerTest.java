package com.oops.calendar.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oops.calendar.dto.NewsItem;
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
 * 快讯收藏接口集成测试(内存 H2):
 * PUT 保存快照 → GET 读回 → PUT 空列表清空(configured 回 false)。
 */
@SpringBootTest
@AutoConfigureMockMvc
class NewsFavoritesControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void favoritesRoundTrip() throws Exception {
        String clientId = "c_it_" + System.nanoTime();

        NewsItem item = new NewsItem();
        item.setId("jin10-1");
        item.setTitle("美联储维持利率不变");
        item.setUrl("https://example.com/jin10-1");
        item.setPubDate(1_700_000_000_000L);
        item.setSource("jin10");
        item.setSourceName("金十数据");
        item.setSummary("美联储维持利率不变");
        item.setImportant(true);
        String body = "{\"clientId\":\"" + clientId + "\",\"items\":[" + objectMapper.writeValueAsString(item) + "]}";

        mvc.perform(put("/api/news/favorites").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.items[0].id").value("jin10-1"))
                .andExpect(jsonPath("$.items[0].title").value("美联储维持利率不变"));

        mvc.perform(get("/api/news/favorites").param("clientId", clientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.items[0].source").value("jin10"))
                .andExpect(jsonPath("$.items[0].summary").value("美联储维持利率不变"))
                .andExpect(jsonPath("$.items[0].important").value(true));

        // 未收藏的客户端:configured=false,空列表
        mvc.perform(get("/api/news/favorites").param("clientId", "nobody"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.items").isEmpty());

        // 空列表 = 清空该客户端收藏
        mvc.perform(put("/api/news/favorites").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"" + clientId + "\",\"items\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void malformedItemsDoNotFailTheWholeSave() throws Exception {
        String clientId = "c_mal_" + System.nanoTime();
        // 缺 source 的畸形条目应被跳过,有效条目照常落库,整体返回 200 而不是 500
        String body = "{\"clientId\":\"" + clientId + "\",\"items\":["
                + "{\"id\":\"bad-1\",\"title\":\"缺 source 的快讯\"},"
                + "{\"id\":\"cls-ok\",\"title\":\"有效快讯\",\"url\":\"https://www.cls.cn/detail/1\","
                + "\"pubDate\":1700000000000,\"source\":\"cls\",\"sourceName\":\"财联社\",\"summary\":\"摘要\",\"important\":false}"
                + "]}";

        mvc.perform(put("/api/news/favorites").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value("cls-ok"));
    }
}