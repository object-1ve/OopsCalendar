package com.oops.calendar.web;

import com.oops.calendar.dto.FavoritesRequest;
import com.oops.calendar.dto.FavoritesResponse;
import com.oops.calendar.service.FavoritesService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 收藏公司接口(按 clientId 持久化到 JSON 文件,重启不丢)。
 * GET /api/favorites?clientId=x   读取收藏(configured=false = 从未保存过)
 * PUT /api/favorites              保存收藏(空列表 = 清空该客户端收藏)
 */
@RestController
@RequestMapping("/api/favorites")
public class FavoritesController {

    private final FavoritesService service;

    public FavoritesController(FavoritesService service) {
        this.service = service;
    }

    @GetMapping
    public FavoritesResponse get(@RequestParam("clientId") String clientId) {
        return new FavoritesResponse(service.isConfigured(clientId), service.get(clientId));
    }

    @PutMapping
    public FavoritesResponse save(@RequestBody FavoritesRequest req) {
        service.save(req.getClientId(), req.getSymbols());
        return new FavoritesResponse(service.isConfigured(req.getClientId()), service.get(req.getClientId()));
    }
}
