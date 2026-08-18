# -*- coding: utf-8 -*-
"""财报服务:参数校验、数据源选择、上游失败自动降级(mock 保底)、缓存、节流、持久化二级缓存。

数据源优先级:FINNHUB_API_KEY(免费档数据完整)> FMP_API_KEY > mock(演示)。
降级语义:FMP/Finnhub 上游任何失败对该请求回退 MockProvider,并记录降级状态;
冷却期内直接走 mock,冷却期结束后自动重试一次上游,成功则恢复真实数据。
"""
from __future__ import annotations

import asyncio
import logging
from datetime import date, datetime, timedelta, timezone
from typing import Optional

from ..db import Database
from ..errors import ApiException, UpstreamUnavailableException
from ..known_companies import get as known_get
from .providers import FinnhubProvider, FmpProvider, MockProvider
from .symbols import SymbolService

log = logging.getLogger(__name__)


class EarningsService:
    def __init__(
        self,
        fmp: FmpProvider,
        finnhub: FinnhubProvider,
        mock: MockProvider,
        symbols: SymbolService,
        persistence: Optional[Database],
        cache_ttl_seconds: float = 3600,
        max_range_days: int = 120,
        min_request_interval_ms: float = 1500,
        degraded_retry_ms: float = 60000,
    ):
        self._fmp = fmp
        self._finnhub = finnhub
        self._mock = mock
        self._symbols = symbols
        self._persistence = persistence
        self._cache_ttl_seconds = cache_ttl_seconds
        self._max_range_days = max_range_days
        self._min_interval_ms = min_request_interval_ms
        self._degraded_retry_ms = degraded_retry_ms

        # 数据源优先级:Finnhub > FMP > mock
        if finnhub.has_key:
            self._provider = finnhub
        elif fmp.has_key:
            self._provider = fmp
        else:
            self._provider = mock
        log.info("Earnings provider active: %s", self._provider.source)

        self._cache: dict[str, tuple] = {}
        self._last_call_ms = 0.0
        self._throttle_lock = asyncio.Lock()
        self._degraded = False
        self._degradation_reason: Optional[str] = None
        self._degraded_at: Optional[datetime] = None

    # ---------- 状态 ----------

    @property
    def active_source(self) -> str:
        return self._provider.source

    @property
    def is_degraded(self) -> bool:
        return self._degraded

    @property
    def degradation_reason(self) -> Optional[str]:
        return self._degradation_reason

    async def probe_upstream(self) -> None:
        """启动时轻量探测(仅真实模式):失败立即进入降级态,保证 /api/health 如实反映。"""
        if self._provider.source == "mock":
            return
        today = date.today()
        try:
            await self._provider.fetch(today, today)
            log.info("Upstream probe ok, provider=%s", self._provider.source)
        except UpstreamUnavailableException as e:
            self._mark_degraded(e)
        except Exception:
            log.warning("Upstream probe unexpected failure, degraded to mock")
            self._degraded = True
            self._degradation_reason = "上游探测失败"
            self._degraded_at = datetime.now(timezone.utc)

    # ---------- 查询 ----------

    async def query(self, from_date: date, to_date: date, refresh: bool = False) -> dict:
        self._validate_range(from_date, to_date)
        cache_key = f"{from_date}|{to_date}"
        ttl_ms = self._cache_ttl_seconds * 1000

        if not refresh:
            cached = self._cache.get(cache_key)
            if cached and not self._expired(cached, ttl_ms):
                events, _, source = cached
                return self._response(from_date, to_date, source, events)
            # 内存缓存未命中:尝试从数据库读回(持久化二级缓存),避免后端重启后整月回源
            if self._persistence is not None:
                loaded = self._load_range(from_date, to_date, ttl_ms)
                if loaded is not None:
                    events, source = loaded
                    events = _dedupe_events(events)
                    events.sort(key=lambda e: (e["date"], e["symbol"].upper()))
                    self._cache[cache_key] = (events, datetime.now(timezone.utc), source)
                    return self._response(from_date, to_date, source, events)

        # 区间跨越今天之前且是强制刷新:拆分为"过去段(复用缓存) + 今天及之后(强制回源)"
        if refresh and from_date < date.today():
            return await self._query_refresh_splitting_past(from_date, to_date)

        source: str
        events: list
        if self._degraded and not self._retry_cooldown_elapsed():
            # 降级冷却期内:直接走 mock,不再打上游
            source = "mock"
            events = await self._mock.fetch(from_date, to_date)
        else:
            try:
                await self._throttle_if_needed()
                events = list(await self._provider.fetch(from_date, to_date))
                source = self._provider.source
                if self._degraded:
                    self._degraded = False
                    self._degradation_reason = None
                    self._degraded_at = None
                    log.info("Upstream recovered, back to provider=%s", source)
            except UpstreamUnavailableException as e:
                self._mark_degraded(e)
                source = "mock"
                events = await self._mock.fetch(from_date, to_date)

        return await self._store_and_respond(from_date, to_date, source, events)

    async def query_symbol(self, symbol: str, from_date: date, to_date: date) -> dict:
        if not symbol or not symbol.strip():
            raise ApiException(400, "股票代码不能为空")
        all_resp = await self.query(from_date, to_date)
        filtered = [
            e for e in all_resp["events"] if e.get("symbol") and e["symbol"].upper() == symbol.strip().upper()
        ]
        return {
            "from": all_resp["from"],
            "to": all_resp["to"],
            "count": len(filtered),
            "source": all_resp["source"],
            "events": filtered,
        }

    # ---------- 内部 ----------

    async def _query_refresh_splitting_past(self, from_date: date, to_date: date) -> dict:
        today = date.today()
        merged: list = []
        source: Optional[str] = None

        if from_date < today:
            past_end = to_date if to_date < today else today - timedelta(days=1)
            cached = self._cache.get(f"{from_date}|{to_date}")
            if cached is not None:
                events, _, cached_source = cached
                for e in events:
                    if e["date"] <= past_end.isoformat():
                        merged.append(e)
                source = cached_source
            else:
                past = await self.query(from_date, past_end)
                merged.extend(past["events"])
                source = past["source"]
        if not to_date < today:
            future = await self.query(today, to_date, True)
            merged.extend(future["events"])
            source = future["source"]

        return await self._store_and_respond(from_date, to_date, source or "mock", merged)

    async def _store_and_respond(self, from_date: date, to_date: date, source: str, events: list) -> dict:
        # 兜底去重 + 排序 + 富化
        events = _dedupe_events(events)
        events.sort(key=lambda e: (e["date"], e["symbol"].upper()))
        for e in events:
            self._enrich(e)
        self._cache[f"{from_date}|{to_date}"] = (events, datetime.now(timezone.utc), source)
        if self._persistence is not None and source != "mock":
            try:
                self._persistence.replace_earnings(
                    from_date.isoformat(), to_date.isoformat(), source, events
                )
            except Exception:
                log.warning("财报数据持久化失败", exc_info=True)
        return self._response(from_date, to_date, source, events)

    def _enrich(self, e: dict) -> None:
        known = known_get(e.get("symbol"))
        if known is not None:
            if not e.get("name"):
                e["name"] = known.name
            e["nameZh"] = known.name_zh
            e["industry"] = known.industry
        elif not e.get("name"):
            e["name"] = self._symbols.name_of(e.get("symbol"))

    def _load_range(self, from_date: date, to_date: date, ttl_ms: float):
        range_key = f"{from_date}|{to_date}"
        cov = self._persistence.coverage(range_key)
        if cov is None:
            return None
        source, fetched_at = cov
        fetched = datetime.fromisoformat(fetched_at)
        if (datetime.now(timezone.utc) - fetched).total_seconds() * 1000 > ttl_ms:
            return None
        events = self._persistence.load_earnings(from_date.isoformat(), to_date.isoformat(), source)
        return events, source

    @staticmethod
    def _response(from_date: date, to_date: date, source: str, events: list) -> dict:
        return {
            "from": from_date.isoformat(),
            "to": to_date.isoformat(),
            "count": len(events),
            "source": source,
            "events": events,
        }

    def _validate_range(self, from_date: date, to_date: date) -> None:
        if from_date is None or to_date is None:
            raise ApiException(400, "参数 from 与 to 必填,格式 YYYY-MM-DD")
        if from_date > to_date:
            raise ApiException(400, "from 不能晚于 to")
        days = (to_date - from_date).days + 1
        if days > self._max_range_days:
            raise ApiException(400, f"查询区间不能超过 {self._max_range_days} 天")

    @staticmethod
    def parse_date(value: Optional[str], param_name: str) -> date:
        if value is None or not value.strip():
            raise ApiException(400, f"参数 {param_name} 必填,格式 YYYY-MM-DD")
        try:
            return date.fromisoformat(value.strip())
        except ValueError:
            raise ApiException(400, f"参数 {param_name} 格式非法:{value.strip()} (应为 YYYY-MM-DD)")

    def _retry_cooldown_elapsed(self) -> bool:
        if self._degraded_at is None:
            return True
        elapsed_ms = (datetime.now(timezone.utc) - self._degraded_at).total_seconds() * 1000
        return elapsed_ms >= self._degraded_retry_ms

    def _mark_degraded(self, e: UpstreamUnavailableException) -> None:
        self._degraded = True
        self._degradation_reason = str(e)
        self._degraded_at = datetime.now(timezone.utc)
        log.warning("Upstream unavailable (%s), fallback to mock provider", e)

    async def _throttle_if_needed(self) -> None:
        if self._provider.source == "mock":
            return
        while True:
            async with self._throttle_lock:
                now_ms = asyncio.get_event_loop().time() * 1000
                wait_ms = self._last_call_ms + self._min_interval_ms - now_ms
                if wait_ms <= 0:
                    self._last_call_ms = now_ms
                    return
            await asyncio.sleep(wait_ms / 1000)

    @staticmethod
    def _expired(cached: tuple, ttl_ms: float) -> bool:
        _, created_at, _ = cached
        return (datetime.now(timezone.utc) - created_at).total_seconds() * 1000 > ttl_ms


def _dedupe_events(events: list) -> list:
    seen = set()
    out = []
    for e in events:
        key = (
            f"{e['date']}\0{e.get('symbol')}\0{e.get('session')}\0"
            f"{e.get('confirmed')}\0{e.get('eps')}\0{e.get('epsEstimated')}\0"
            f"{e.get('revenue')}\0{e.get('revenueEstimated')}\0{e.get('source')}"
        )
        if key not in seen:
            seen.add(key)
            out.append(e)
    return out
