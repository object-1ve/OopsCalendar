# -*- coding: utf-8 -*-
"""实时快讯推送:后台定时(默认 15s)增量轮询各数据源,仅推送新增条目给所有 SSE 订阅者。

单源失败自动跳过不影响其他源;每轮轮询结束无条件发心跳(即使无新增)。
"""
from __future__ import annotations

import asyncio
import logging
import time

from .sources import all_sources

log = logging.getLogger(__name__)

RECENT_LIMIT = 200


class NewsStreamService:
    def __init__(self, http, poll_ms=15000, enabled=True, sources=None, store=None):
        self._sources = sources if sources is not None else all_sources(http)
        self._poll_ms = poll_ms
        self._enabled = enabled
        self._subscribers: list[asyncio.Queue] = []
        self._seen_ids: set = set()
        self._recent: list = []
        self._lock = asyncio.Lock()
        self._task: asyncio.Task | None = None
        self._store = store

    def start(self) -> None:
        if not self._enabled:
            return
        self._task = asyncio.create_task(self._loop())

    def stop(self) -> None:
        if self._task is not None:
            self._task.cancel()
            self._task = None

    @property
    def recent(self) -> list:
        return list(self._recent)

    def connect(self) -> asyncio.Queue:
        """新订阅者接入:返回消息队列(若已有快照,先由生产者补发一份)。"""
        q: asyncio.Queue = asyncio.Queue(maxsize=200)
        self._subscribers.append(q)
        snapshot = self._recent
        if snapshot:
            q.put_nowait({"type": "news", "data": snapshot})
        return q

    def disconnect(self, q: asyncio.Queue) -> None:
        if q in self._subscribers:
            self._subscribers.remove(q)

    async def _loop(self) -> None:
        while True:
            try:
                await self.poll()
            except asyncio.CancelledError:
                raise
            except Exception:
                log.warning("新闻轮询失败", exc_info=True)
            await asyncio.sleep(self._poll_ms / 1000)

    async def poll(self) -> None:
        """定时增量轮询,推送新增条目;无新增也发心跳。"""
        fresh = []
        for s in self._sources:
            try:
                fresh.extend(await s.fetch())
            except Exception:
                log.warning("新闻源 %s 轮询失败", s.key, exc_info=True)

        # 轮询到的快讯去重落库(重复自动忽略)
        if fresh and self._store is not None:
            self._store.save(fresh)

        news = []
        if fresh:
            for it in fresh:
                if it.get("id") not in self._seen_ids:
                    self._seen_ids.add(it.get("id"))
                    news.append(it)
            if news:
                merged = list(self._recent) + news
                merged.sort(key=lambda it: (it.get("pubDate") is None, -(it.get("pubDate") or 0)))
                if len(merged) > RECENT_LIMIT:
                    merged = merged[:RECENT_LIMIT]
                self._recent = merged
                await self._broadcast({"type": "news", "data": news})

        # 每轮轮询结束无条件发心跳(无论有无新增):前端据此把"更新于"按轮询周期刷新
        await self._broadcast({"type": "heartbeat", "data": int(time.time() * 1000)})

    async def _broadcast(self, msg: dict) -> None:
        if not self._subscribers:
            return
        dead = []
        for q in self._subscribers:
            try:
                q.put_nowait(msg)
            except asyncio.QueueFull:
                dead.append(q)
        for q in dead:
            self.disconnect(q)
