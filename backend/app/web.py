# -*- coding: utf-8 -*-
"""FastAPI 应用:全部 API 路由 + CORS + 全局异常处理(与旧版契约一致)。"""
from __future__ import annotations

import json
import logging
from contextlib import asynccontextmanager
from datetime import date, timedelta
from pathlib import Path

from fastapi import FastAPI, Query, Request
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, StreamingResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

import config
from app.db import Database
from app.errors import ApiException, clean, now_iso
from app.earnings.providers import FinnhubProvider, FmpProvider, MockProvider
from app.earnings.service import EarningsService
from app.earnings.symbols import SymbolService
from app.favorites import FavoritesService
from app.news.client import NewsHttpClient
from app.news.favorites import NewsFavoriteService
from app.news.prefs import NewsPreferencesService
from app.news.service import NewsService
from app.news.store import NewsStore
from app.news.stream import NewsStreamService
from app.valuation import ValuationService

log = logging.getLogger(__name__)


def _error_body(status: int, message: str) -> dict:
    return {"error": _error_name(status), "message": message, "timestamp": now_iso()}


_STATUS_NAMES = {
    400: "Bad Request",
    401: "Unauthorized",
    403: "Forbidden",
    404: "Not Found",
    405: "Method Not Allowed",
    409: "Conflict",
    415: "Unsupported Media Type",
    422: "Unprocessable Entity",
    429: "Too Many Requests",
    500: "Internal Server Error",
    502: "Bad Gateway",
    503: "Service Unavailable",
}


def _error_name(status: int) -> str:
    return _STATUS_NAMES.get(status, "ERROR")


def create_app() -> FastAPI:
    data_dir = Path(config.NewsConfig.data_dir)
    data_dir.mkdir(parents=True, exist_ok=True)

    db = Database(data_dir / "earnings.db")

    # ---- 财报 ----
    fmp_provider = FmpProvider(
        config.FmpConfig.base_url,
        config.FmpConfig.api_key,
        config.FmpConfig.connect_timeout_ms,
        config.FmpConfig.read_timeout_ms,
    )
    finnhub_provider = FinnhubProvider(
        config.FinnhubConfig.base_url,
        config.FinnhubConfig.api_key,
        config.FinnhubConfig.connect_timeout_ms,
        config.FinnhubConfig.read_timeout_ms,
    )
    mock_provider = MockProvider()
    symbols = SymbolService(
        config.FinnhubConfig.base_url,
        config.FinnhubConfig.api_key,
        config.FinnhubConfig.connect_timeout_ms,
        config.FinnhubConfig.read_timeout_ms,
    )
    earnings = EarningsService(
        fmp_provider,
        finnhub_provider,
        mock_provider,
        symbols,
        db,
        cache_ttl_seconds=config.FmpConfig.cache_ttl_seconds,
        max_range_days=config.FmpConfig.max_range_days,
        min_request_interval_ms=config.FmpConfig.min_request_interval_ms,
        degraded_retry_ms=config.FmpConfig.degraded_retry_ms,
    )
    valuation = ValuationService(
        config.FinnhubConfig.base_url,
        config.FinnhubConfig.api_key,
        config.FinnhubConfig.connect_timeout_ms,
        config.FinnhubConfig.read_timeout_ms,
        earnings,
    )

    # ---- 快讯 ----
    news_http = NewsHttpClient(config.NewsConfig.connect_timeout_ms, config.NewsConfig.read_timeout_ms)
    news_store = NewsStore(db, retention_days=config.NewsConfig.retention_days)
    news = NewsService(
        news_http,
        cache_ttl_seconds=config.NewsConfig.cache_ttl_seconds,
        max_items_per_source=config.NewsConfig.max_items_per_source,
        max_items=config.NewsConfig.max_items,
        store=news_store,
    )
    stream = NewsStreamService(
        news_http, poll_ms=config.NewsConfig.poll_ms, enabled=config.NewsConfig.enabled, store=news_store
    )
    prefs = NewsPreferencesService(db, [s["key"] for s in news.list_sources()], str(data_dir))
    news_favs = NewsFavoriteService(db)

    # ---- 收藏公司 ----
    favorites = FavoritesService(str(data_dir))

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        # 启动探测:真实数据源失败即进入降级态;并预热符号表(失败静默)
        await earnings.probe_upstream()
        await symbols.ensure_loaded()
        stream.start()
        yield
        stream.stop()

    app = FastAPI(title="OopsCalendar Backend (Python)", lifespan=lifespan)
    app.state.earnings = earnings
    app.state.news = news
    app.state.stream = stream
    app.state.prefs = prefs
    app.state.news_favs = news_favs
    app.state.favorites = favorites
    app.state.valuation = valuation

    app.add_middleware(
        CORSMiddleware,
        allow_origins=config.CorsConfig.allowed_origins,
        allow_methods=["GET", "POST", "PUT", "OPTIONS"],
        allow_headers=["*"],
        max_age=3600,
    )

    # ---------- 全局异常处理 ----------
    @app.exception_handler(ApiException)
    async def handle_api(_: Request, exc: ApiException):
        return JSONResponse(status_code=exc.status, content=_error_body(exc.status, exc.message))

    @app.exception_handler(RequestValidationError)
    async def handle_validation(_: Request, exc: RequestValidationError):
        return JSONResponse(status_code=400, content=_error_body(400, "参数格式非法"))

    @app.exception_handler(StarletteHTTPException)
    async def handle_http(_: Request, exc: StarletteHTTPException):
        return JSONResponse(status_code=exc.status_code, content=_error_body(exc.status_code, str(exc.detail)))

    @app.exception_handler(Exception)
    async def handle_other(_: Request, exc: Exception):
        log.error("Unhandled exception", exc_info=True)
        return JSONResponse(status_code=500, content=_error_body(500, "服务器内部错误:" + str(exc)))

    # ---------- 路由 ----------

    @app.get("/api/health")
    async def health():
        service = earnings
        if service.is_degraded:
            message = (
                "财报数据源请求失败(" + (service.degradation_reason or "") + "),已回退到内置演示数据,"
                "冷却期后将自动重试恢复。请检查 API Key 与网络。"
            )
            return {"status": "UP", "provider": "mock", "message": message, "timestamp": now_iso()}
        source = service.active_source
        mock = source == "mock"
        message = (
            "未配置数据源 API Key,当前使用内置演示数据(确定性生成)。"
            "设置 FINNHUB_API_KEY 或 FMP_API_KEY 环境变量后重启即可切换为真实美股财报数据。"
            if mock
            else "已连接 " + ("Financial Modeling Prep" if source == "fmp" else "Finnhub") + " 真实财报数据。"
        )
        return {"status": "UP", "provider": source, "message": message, "timestamp": now_iso()}

    @app.get("/api/earnings")
    async def earnings_range(
        from_: str | None = Query(None, alias="from"),
        to: str | None = Query(None, alias="to"),
        refresh: bool = False,
    ):
        f = EarningsService.parse_date(from_, "from")
        t = EarningsService.parse_date(to, "to")
        return clean(await earnings.query(f, t, refresh))

    @app.get("/api/earnings/{symbol}")
    async def earnings_symbol(
        symbol: str,
        from_: str | None = Query(None, alias="from"),
        to: str | None = Query(None, alias="to"),
    ):
        base = date.today()
        f = EarningsService.parse_date(from_, "from") if from_ else base - timedelta(days=30)
        t = EarningsService.parse_date(to, "to") if to else base + timedelta(days=30)
        return clean(await earnings.query_symbol(symbol, f, t))

    @app.get("/api/valuation")
    async def valuation_route(date_str: str | None = Query(None, alias="date")):
        if not date_str:
            raise ApiException(400, "参数 date 必填,格式 YYYY-MM-DD")
        try:
            d = date.fromisoformat(date_str.strip())
        except ValueError:
            raise ApiException(400, f"参数 date 格式非法:{date_str.strip()} (应为 YYYY-MM-DD)")
        values = await valuation.valuations_for_date(d)
        return clean({"date": d.isoformat(), "count": len(values), "values": values})

    @app.get("/api/news")
    async def news_route(sources: str | None = None):
        return await news.query(sources)

    @app.get("/api/news/sources")
    async def news_sources():
        return clean(news.list_sources())

    @app.get("/api/news/count")
    async def news_count():
        """数据库中已入库快讯的总条数(全部数据源,不受筛选影响)。"""
        return clean({"count": news_store.count()})

    @app.get("/api/news/history")
    async def news_history(
        limit: int = Query(40, ge=1, le=200),
        offset: int = Query(0, ge=0),
        sources: str | None = None,
        search: str | None = None,
    ):
        """已入库快讯历史分页(时间倒序),供前端无限滚动;search 对标题/摘要模糊匹配。"""
        keys = [k.strip() for k in sources.split(",") if k.strip()] if sources else None
        q = (search or "").strip() or None
        items = news_store.load_history(limit, keys, offset, q)
        total = news_store.count(keys, q)
        return clean({"items": items, "total": total, "offset": offset, "limit": limit})

    @app.get("/api/news/stream")
    async def news_stream():
        q = stream.connect()

        async def gen():
            try:
                while True:
                    msg = await q.get()
                    event = msg["type"]
                    data = json.dumps(clean(msg["data"]), ensure_ascii=False, separators=(",", ":"))
                    yield f"event: {event}\ndata: {data}\n\n"
            finally:
                stream.disconnect(q)

        return StreamingResponse(gen(), media_type="text/event-stream")

    @app.get("/api/news/preferences")
    async def get_preferences():
        return clean({"configured": prefs.is_configured(), "sources": prefs.get()})

    @app.put("/api/news/preferences")
    async def save_preferences(request: Request):
        body = await _read_body(request)
        prefs.save(body.get("sources"))
        return clean({"configured": True, "sources": prefs.get()})

    @app.get("/api/news/favorites")
    async def get_news_favorites():
        return clean({"configured": news_favs.is_configured(), "items": news_favs.get()})

    @app.put("/api/news/favorites")
    async def save_news_favorites(request: Request):
        body = await _read_body(request)
        news_favs.save(body.get("items"))
        return clean({"configured": news_favs.is_configured(), "items": news_favs.get()})

    @app.get("/api/favorites")
    async def get_favorites():
        return clean({"configured": favorites.is_configured(), "symbols": favorites.get()})

    @app.put("/api/favorites")
    async def save_favorites(request: Request):
        body = await _read_body(request)
        favorites.save(body.get("symbols"))
        return clean({"configured": favorites.is_configured(), "symbols": favorites.get()})

    return app


async def _read_body(request: Request) -> dict:
    try:
        raw = await request.body()
        body = json.loads(raw) if raw else {}
        return body if isinstance(body, dict) else {}
    except Exception:
        raise ApiException(400, "请求体解析失败")


def create_app_instance():
    """模块级单例(uvicorn --factory 或 -m app.web 均可用)。"""
    return create_app()

