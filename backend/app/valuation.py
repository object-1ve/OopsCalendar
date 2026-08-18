# -*- coding: utf-8 -*-
"""市盈率(PE)服务:按日期对当日财报公司拉取 Finnhub /stock/metric 的 peTTM。

免费档 60 次/分钟,逐家调用带 1s 节流;结果内存缓存 1 小时。
为避免一次拉几百家(需数分钟),默认只对内置知名公司表内的公司拉取。
"""
from __future__ import annotations

import asyncio
import logging
from datetime import date, datetime, timezone
from decimal import Decimal

from .http import HttpClient
from .known_companies import get as known_get

log = logging.getLogger(__name__)

CACHE_TTL_SECONDS = 3600
MIN_INTERVAL_MS = 1000  # 1s,免费档 60/min


class ValuationService:
    def __init__(self, base_url: str, api_key: str, connect_ms: int, read_ms: int, earnings_service):
        self.base_url = base_url.rstrip("/")
        self.api_key = api_key
        self.http = HttpClient(connect_ms, read_ms)
        self._earnings = earnings_service
        self._cache: dict = {}
        self._last_call_ms = 0.0
        self._lock = asyncio.Lock()

    @property
    def has_key(self) -> bool:
        return bool(self.api_key)

    async def valuations_for_date(self, d: date) -> dict:
        key = d.isoformat()
        cached = self._cache.get(key)
        if cached is not None and not self._expired(cached):
            return cached[0]
        if not self.has_key:
            return {}

        resp = await self._earnings.query(d, d)
        result = {}
        for e in resp["events"]:
            if known_get(e.get("symbol")) is None:
                continue  # 免费档限流:只对知名公司拉取
            pe = await self._fetch_pe(e["symbol"])
            if pe is not None:
                result[e["symbol"]] = pe
        self._cache[key] = (result, datetime.now(timezone.utc))
        log.info("Valuation for %s: %d companies with PE", key, len(result))
        return result

    async def _fetch_pe(self, symbol: str) -> Decimal | None:
        await self._throttle()
        url = (
            f"{self.base_url}/stock/metric?symbol={symbol}&metric=all&token={self.api_key}"
        )
        try:
            resp = await self.http.get_bytes(url)
            resp.raise_for_status()
            root = resp.json()
            metric = root.get("metric") if isinstance(root, dict) else None
            pe = metric.get("peTTM") if isinstance(metric, dict) else None
            if pe is None:
                return None
            return Decimal(str(pe))
        except Exception:
            log.warning("PE fetch failed for %s", symbol, exc_info=True)
            return None

    async def _throttle(self) -> None:
        while True:
            async with self._lock:
                now_ms = asyncio.get_event_loop().time() * 1000
                wait_ms = self._last_call_ms + MIN_INTERVAL_MS - now_ms
                if wait_ms <= 0:
                    self._last_call_ms = now_ms
                    return
            await asyncio.sleep(wait_ms / 1000)

    @staticmethod
    def _expired(cached: tuple) -> bool:
        _, created_at = cached
        return (datetime.now(timezone.utc) - created_at).total_seconds() > CACHE_TTL_SECONDS
