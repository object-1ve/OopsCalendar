# -*- coding: utf-8 -*-
"""财经快讯聚合:并发安全缓存(按数据源 + TTL),单源失败自动降级,合并后按时间倒序。"""
from __future__ import annotations

import logging
import time

from ..errors import clean
from .sources import all_sources

log = logging.getLogger(__name__)


class NewsService:
    def __init__(
        self,
        http,
        cache_ttl_seconds=60,
        max_items_per_source=50,
        max_items=200,
        sources=None,
        store=None,
    ):
        self._sources = sources if sources is not None else all_sources(http)
        self._by_key = {s.key: s for s in self._sources}
        self._cache_ttl_seconds = cache_ttl_seconds
        self._max_items_per_source = max_items_per_source
        self._max_items = max_items
        self._cache: dict = {}
        self._store = store

    def list_sources(self) -> list:
        return [
            {"key": s.key, "name": s.name, "icon": s.icon}
            for s in self._sources
        ]

    async def query(self, source_param: str | None) -> dict:
        items = []
        for s in self._select_sources(source_param):
            try:
                fetched = await self._fetch_cached(s)
                if fetched and self._store is not None:
                    self._store.save(fetched)
                items.extend(fetched)
            except Exception:
                log.warning("新闻源 %s 获取失败", s.key, exc_info=True)
                # 上游失败时用数据库里该源的最近快讯兜底,避免列表骤然变空
                if self._store is not None:
                    fallback = self._store.load(self._max_items_per_source, [s.key])
                    if fallback:
                        log.info("新闻源 %s 使用数据库兜底 %d 条", s.key, len(fallback))
                        items.extend(fallback)
        items.sort(key=lambda it: (it.get("pubDate") is None, -(it.get("pubDate") or 0)))
        if len(items) > self._max_items:
            items = items[: self._max_items]
        return {
            "items": clean(items),
            "sources": clean(self.list_sources()),
            "fetchedAt": int(time.time() * 1000),
        }

    def _select_sources(self, source_param: str | None) -> list:
        if source_param is None or not source_param.strip():
            return list(self._sources)
        selected = []
        for key in source_param.split(","):
            s = self._by_key.get(key.strip())
            if s is not None:
                selected.append(s)
        # 筛选参数全部无效时回退到全部源,避免"暂无快讯"的误导
        return selected if selected else list(self._sources)

    async def _fetch_cached(self, s) -> list:
        entry = self._cache.get(s.key)
        now = int(time.time() * 1000)
        if entry is not None and now - entry[1] < self._cache_ttl_seconds * 1000:
            return entry[0]
        items = await s.fetch()
        if len(items) > self._max_items_per_source:
            items = items[: self._max_items_per_source]
        self._cache[s.key] = (items, now)
        return items
