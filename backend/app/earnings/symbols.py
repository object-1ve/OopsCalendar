# -*- coding: utf-8 -*-
"""Finnhub 全量美股上市公司列表(symbol -> 公司全称),内存缓存 24 小时。

加载失败静默降级(仅知名公司表兜底),不阻塞主流程。
"""
from __future__ import annotations

import asyncio
import logging
from datetime import datetime, timedelta, timezone
from typing import Optional

from ..http import HttpClient

log = logging.getLogger(__name__)

CACHE_TTL_SECONDS = 24 * 3600


class SymbolService:
    def __init__(self, base_url: str, api_key: str, connect_ms: int, read_ms: int):
        self.base_url = base_url.rstrip("/")
        self.api_key = api_key
        self.http = HttpClient(connect_ms, read_ms)
        self._names: Optional[dict] = None
        self._loaded_at: Optional[datetime] = None
        self._loading = False
        self._lock = asyncio.Lock()

    @property
    def has_key(self) -> bool:
        return bool(self.api_key)

    def name_of(self, symbol: Optional[str]) -> Optional[str]:
        """同步查询公司全称;未加载或失败返回 None(调用方为同步上下文)。"""
        if not symbol or not self.has_key:
            return None
        names = self._names
        if names is None:
            return None
        return names.get(symbol.upper())

    async def ensure_loaded(self) -> None:
        """异步预加载(供启动时预热);失败静默降级。"""
        if self._names is not None and not self._cache_expired():
            return
        async with self._lock:
            if self._names is not None and not self._cache_expired():
                return
            if self._loading:
                return
            self._loading = True
            try:
                self._names = await self._fetch_all()
                self._loaded_at = datetime.now(timezone.utc)
                log.info("Finnhub symbol list loaded: %d companies", len(self._names))
            except Exception:
                log.warning("Finnhub symbol list load failed, known-companies table only", exc_info=True)
            finally:
                self._loading = False

    def _cache_expired(self) -> bool:
        if self._loaded_at is None:
            return True
        return datetime.now(timezone.utc) - self._loaded_at > timedelta(seconds=CACHE_TTL_SECONDS)

    async def _fetch_all(self) -> dict:
        url = f"{self.base_url}/stock/symbol?exchange=US&token={self.api_key}"
        resp = await self.http.get_bytes(url)
        resp.raise_for_status()
        root = resp.json()
        if not isinstance(root, list):
            raise RuntimeError("Finnhub symbol list: unexpected response shape")
        names = {}
        for node in root:
            if not isinstance(node, dict):
                continue
            symbol = node.get("symbol")
            desc = node.get("description")
            if symbol and desc:
                names[symbol.upper()] = desc
        return names
