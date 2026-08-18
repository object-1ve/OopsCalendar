package com.oops.calendar.web;

import com.oops.calendar.dto.NewsFavoritesRequest;
import com.oops.calendar.dto.NewsFavoritesResponse;
import com.oops.calendar.dto.NewsPreferencesRequest;
import com.oops.calendar.dto.NewsPreferencesResponse;
import com.oops.calendar.dto.NewsResponse;
import com.oops.calendar.dto.NewsSourceMeta;
import com.oops.calendar.service.news.NewsPreferencesService;
import com.oops.calendar.service.news.NewsService;
import com.oops.calendar.service.news.NewsStreamService;
import com.oops.calendar.service.NewsFavoriteService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 财经快讯接口。
 * GET  /api/news?sources=jin10,cls   (sources 缺省 = 全部)
 * GET  /api/news/sources             可用数据源列表
 * GET  /api/news/stream              SSE 实时推送(后台每 15s 增量轮询,只推新增)
 * GET  /api/news/preferences?clientId=x   数据源偏好(configured=false = 未设置过)
 * PUT  /api/news/preferences        保存数据源偏好(持久化到 H2 数据库)
 * GET  /api/news/favorites?clientId=x     快讯收藏列表(持久化到 H2 数据库)
 * PUT  /api/news/favorites          保存快讯收藏(整表替换,快照落库)
 */
@RestController
@RequestMapping("/api/news")
public class NewsController {

    private final NewsService service;
    private final NewsStreamService streamService;
    private final NewsPreferencesService preferencesService;
    private final NewsFavoriteService favoriteService;

    public NewsController(NewsService service, NewsStreamService streamService,
                          NewsPreferencesService preferencesService, NewsFavoriteService favoriteService) {
        this.service = service;
        this.streamService = streamService;
        this.preferencesService = preferencesService;
        this.favoriteService = favoriteService;
    }

    @GetMapping
    public NewsResponse news(@RequestParam(value = "sources", required = false) String sources) {
        return service.query(sources);
    }

    @GetMapping("/sources")
    public List<NewsSourceMeta> sources() {
        return service.listSources();
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return streamService.connect();
    }

    @GetMapping("/preferences")
    public NewsPreferencesResponse getPreferences(@RequestParam("clientId") String clientId) {
        boolean configured = preferencesService.isConfigured(clientId);
        return new NewsPreferencesResponse(configured, preferencesService.get(clientId));
    }

    @PutMapping("/preferences")
    public NewsPreferencesResponse savePreferences(@RequestBody NewsPreferencesRequest req) {
        preferencesService.save(req.getClientId(), req.getSources());
        return new NewsPreferencesResponse(true, preferencesService.get(req.getClientId()));
    }

    @GetMapping("/favorites")
    public NewsFavoritesResponse getFavorites(@RequestParam("clientId") String clientId) {
        return new NewsFavoritesResponse(favoriteService.isConfigured(clientId), favoriteService.get(clientId));
    }

    @PutMapping("/favorites")
    public NewsFavoritesResponse saveFavorites(@RequestBody NewsFavoritesRequest req) {
        favoriteService.save(req.getClientId(), req.getItems());
        return new NewsFavoritesResponse(favoriteService.isConfigured(req.getClientId()),
                favoriteService.get(req.getClientId()));
    }
}
