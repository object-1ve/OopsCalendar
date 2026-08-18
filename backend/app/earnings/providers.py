# -*- coding: utf-8 -*-
"""财报数据源:SESSION 映射、FMP / Finnhub 真实源、Mock 确定性演示源。"""
from __future__ import annotations

import asyncio
from datetime import date, timedelta
from decimal import ROUND_HALF_UP, Decimal

from ..errors import UpstreamUnavailableException
from ..http import HttpClient

# ============================== Session ==============================

SESSION_BMO = "BMO"
SESSION_AMC = "AMC"
SESSION_DNH = "DNH"
SESSION_UNKNOWN = "UNKNOWN"

_SESSION_BY_TIME = {
    "bmo": SESSION_BMO,
    "before-market": SESSION_BMO,
    "amc": SESSION_AMC,
    "after-market": SESSION_AMC,
    "dnh": SESSION_DNH,
    "dmh": SESSION_DNH,
    "during": SESSION_DNH,
}


def session_from_time(time_str: str | None) -> str:
    """将 FMP time / Finnhub hour 字段映射为 Session 枚举名。"""
    if not time_str:
        return SESSION_UNKNOWN
    return _SESSION_BY_TIME.get(time_str.strip().lower(), SESSION_UNKNOWN)


# ============================== 数值工具 ==============================


def _decimal_or_none(v) -> Decimal | None:
    if v is None:
        return None
    try:
        return Decimal(str(v))
    except Exception:
        return None


def _to_millions(dollars: Decimal | None) -> Decimal | None:
    """美元 -> 百万美元(2 位小数),保持与 FMP/mock 口径一致。"""
    if dollars is None:
        return None
    return (dollars / 1000000).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)


# ============================== FMP ==============================

class FmpProvider:
    source = "fmp"

    def __init__(self, base_url: str, api_key: str, connect_ms: int, read_ms: int):
        self.base_url = base_url.rstrip("/")
        self.api_key = api_key
        self.http = HttpClient(connect_ms, read_ms)

    @property
    def has_key(self) -> bool:
        return bool(self.api_key)

    async def fetch(self, from_date: date, to_date: date) -> list[dict]:
        url = f"{self.base_url}/earnings-calendar?from={from_date}&to={to_date}&apikey={self.api_key}"
        try:
            resp = await self.http.get_bytes(url)
        except Exception as e:
            raise UpstreamUnavailableException(self._io_failure_reason(e)) from e

        if resp.status_code >= 500:
            raise UpstreamUnavailableException(f"FMP 上游服务异常(HTTP {resp.status_code})")
        if resp.status_code >= 400:
            if resp.status_code in (401, 403):
                raise UpstreamUnavailableException(f"FMP API Key 无效(HTTP {resp.status_code})")
            raise UpstreamUnavailableException(f"FMP 上游拒绝请求(HTTP {resp.status_code})")

        try:
            root = resp.json()
        except Exception as e:
            raise UpstreamUnavailableException("FMP 上游响应解析失败") from e

        if not isinstance(root, list):
            raw = None
            if isinstance(root, dict):
                raw = root.get("Error Message") or root.get("error")
            raise UpstreamUnavailableException(self._classify_error_body(raw))

        events = []
        for node in root:
            if not isinstance(node, dict):
                continue
            d = node.get("date")
            symbol = node.get("symbol")
            if not d or not symbol:
                continue
            eps = _decimal_or_none(node.get("eps"))
            eps_est = _decimal_or_none(node.get("epsEstimated"))
            revenue = _decimal_or_none(node.get("revenue"))
            revenue_est = _decimal_or_none(node.get("revenueEstimated"))
            confirmed = eps is not None or revenue is not None
            events.append(
                {
                    "date": str(d),
                    "symbol": str(symbol),
                    "name": None,
                    "nameZh": None,
                    "industry": None,
                    "session": session_from_time(node.get("time")),
                    "confirmed": confirmed,
                    "eps": eps,
                    "epsEstimated": eps_est,
                    "revenue": revenue,
                    "revenueEstimated": revenue_est,
                    "source": self.source,
                }
            )
        return events

    @staticmethod
    def _io_failure_reason(e: Exception) -> str:
        msg = str(e).lower()
        if "timed out" in msg or "timeout" in msg:
            return "FMP 请求超时"
        if "connection" in msg or "connect" in msg:
            return "无法连接 FMP 服务"
        return "FMP 网络请求失败"

    @staticmethod
    def _classify_error_body(raw) -> str:
        if not raw:
            return "FMP 上游返回错误响应"
        lower = str(raw).lower()
        if "invalid api key" in lower or "invalid key" in lower:
            return "FMP API Key 无效(请检查 FMP_API_KEY 配置)"
        if any(k in lower for k in ("limit", "quota", "rate", "max")):
            return "FMP 免费档请求次数已用尽"
        return "FMP 上游返回错误响应"


# ============================== Finnhub ==============================

MAX_PER_REQUEST = 1500
WINDOW_DAYS = 3


class FinnhubProvider:
    source = "finnhub"

    def __init__(self, base_url: str, api_key: str, connect_ms: int, read_ms: int):
        self.base_url = base_url.rstrip("/")
        self.api_key = api_key
        self.http = HttpClient(connect_ms, read_ms)

    @property
    def has_key(self) -> bool:
        return bool(self.api_key)

    async def fetch(self, from_date: date, to_date: date) -> list[dict]:
        """按 3 天窗口并行拉取再合并,保证整段完整(早期日期不被 1500 上限截掉)。"""
        windows = []
        start = from_date
        while start <= to_date:
            end = min(start + timedelta(days=WINDOW_DAYS - 1), to_date)
            windows.append((start, end))
            start = end + timedelta(days=1)

        results = await asyncio.gather(*[self._fetch_window(w[0], w[1]) for w in windows])
        events = [e for chunk in results for e in chunk]
        return events

    async def _fetch_window(self, from_date: date, to_date: date) -> list[dict]:
        events = await self._fetch_once(from_date, to_date)
        if len(events) >= MAX_PER_REQUEST and from_date < to_date:
            # 仍达到上限(极端峰值):按天补查保证完整
            merged = []
            d = from_date
            while d <= to_date:
                merged.extend(await self._fetch_once(d, d))
                d += timedelta(days=1)
            return merged
        return events

    async def _fetch_once(self, from_date: date, to_date: date) -> list[dict]:
        url = f"{self.base_url}/calendar/earnings?from={from_date}&to={to_date}&token={self.api_key}"
        try:
            resp = await self.http.get_bytes(url)
        except Exception as e:
            raise UpstreamUnavailableException(self._io_failure_reason(e)) from e

        if resp.status_code >= 500:
            raise UpstreamUnavailableException(f"Finnhub 上游服务异常(HTTP {resp.status_code})")
        if resp.status_code >= 400:
            if resp.status_code in (401, 403):
                raise UpstreamUnavailableException(f"Finnhub API Key 无效(HTTP {resp.status_code})")
            raise UpstreamUnavailableException(f"Finnhub 上游拒绝请求(HTTP {resp.status_code})")

        try:
            root = resp.json()
        except Exception as e:
            raise UpstreamUnavailableException("Finnhub 上游响应解析失败") from e

        calendar = root.get("earningsCalendar") if isinstance(root, dict) else None
        if not isinstance(calendar, list):
            err = root.get("error") if isinstance(root, dict) else None
            raise UpstreamUnavailableException(
                f"Finnhub 返回错误:{err}" if err else "Finnhub 上游返回错误响应"
            )

        events = []
        for node in calendar:
            if not isinstance(node, dict):
                continue
            d = node.get("date")
            symbol = node.get("symbol")
            if not d or not symbol:
                continue
            eps_actual = _decimal_or_none(node.get("epsActual"))
            eps_est = _decimal_or_none(node.get("epsEstimate"))
            revenue_actual = _to_millions(_decimal_or_none(node.get("revenueActual")))
            revenue_est = _to_millions(_decimal_or_none(node.get("revenueEstimate")))
            confirmed = eps_actual is not None or revenue_actual is not None
            events.append(
                {
                    "date": str(d),
                    "symbol": str(symbol),
                    "name": None,
                    "nameZh": None,
                    "industry": None,
                    "session": session_from_time(node.get("hour")),
                    "confirmed": confirmed,
                    "eps": eps_actual,
                    "epsEstimated": eps_est,
                    "revenue": revenue_actual,
                    "revenueEstimated": revenue_est,
                    "source": self.source,
                }
            )
        return events

    @staticmethod
    def _io_failure_reason(e: Exception) -> str:
        msg = str(e).lower()
        if "timed out" in msg or "timeout" in msg:
            return "Finnhub 请求超时"
        if "connection" in msg or "connect" in msg:
            return "无法连接 Finnhub 服务"
        return "Finnhub 网络请求失败"


# ============================== Mock(确定性演示) ==============================

MOCK_SYMBOLS = [
    "AAPL", "MSFT", "GOOGL", "AMZN", "META", "NVDA", "TSLA", "NFLX",
    "JPM", "V", "UNH", "XOM", "WMT", "JNJ", "PG", "KO", "DIS", "CRM",
    "ORCL", "AMD", "INTC", "QCOM", "ADBE", "PYPL", "BA", "GE", "PFE",
    "T", "CSCO", "CMCSA", "NBIS", "CBRS",
]

MOCK_NAMES = [
    "Apple Inc.", "Microsoft Corp.", "Alphabet Inc.", "Amazon.com Inc.",
    "Meta Platforms Inc.", "NVIDIA Corp.", "Tesla Inc.", "Netflix Inc.",
    "JPMorgan Chase & Co.", "Visa Inc.", "UnitedHealth Group Inc.",
    "Exxon Mobil Corp.", "Walmart Inc.", "Johnson & Johnson",
    "Procter & Gamble Co.", "Coca-Cola Co.", "Walt Disney Co.",
    "Salesforce Inc.", "Oracle Corp.", "Advanced Micro Devices Inc.",
    "Intel Corp.", "Qualcomm Inc.", "Adobe Inc.", "PayPal Holdings Inc.",
    "Boeing Co.", "GE Aerospace", "Pfizer Inc.", "AT&T Inc.",
    "Cisco Systems Inc.", "Comcast Corp.", "Nebius Group N.V.", "Cerebras Systems Inc.",
]


class _JavaRandom:
    """兼容 java.util.Random 的 PRNG,保证 mock 演示数据与旧版完全一致。"""

    def __init__(self, seed: int):
        self._seed = (seed ^ 0x5DEECE66D) & ((1 << 48) - 1)

    def next(self, bits: int) -> int:
        self._seed = (self._seed * 0x5DEECE66D + 0xB) & ((1 << 48) - 1)
        return self._seed >> (48 - bits)

    def next_int(self, n: int) -> int:
        if n <= 0:
            raise ValueError("n must be positive")
        if (n & -n) == n:  # 2 的幂
            return (n * self.next(31)) >> 31
        while True:
            bits = self.next(31)
            val = bits % n
            if bits - val + (n - 1) >= 0:
                return val


def _shuffle_java(lst: list, rnd: _JavaRandom) -> None:
    """与 java.util.Collections.shuffle(Random) 一致的 Fisher-Yates 洗牌。"""
    for i in range(len(lst), 1, -1):
        j = rnd.next_int(i)
        lst[i - 1], lst[j] = lst[j], lst[i - 1]


class MockProvider:
    source = "mock"

    async def fetch(self, from_date: date, to_date: date) -> list[dict]:
        events = []
        day = from_date
        while day <= to_date:
            events.extend(self._generate_day(day))
            day += timedelta(days=1)
        return events

    def _generate_day(self, d: date) -> list[dict]:
        if d.weekday() >= 5:  # 周六/周日:美股财报只在交易日
            return []
        # 与旧版一致:seed = epochDay * 2654435761L ^ 0x9E3779B97F4A7C15L
        epoch_day = (d - date(1970, 1, 1)).days
        rnd = _JavaRandom(epoch_day * 2654435761 ^ 0x9E3779B97F4A7C15)
        count = 2 + rnd.next_int(4)  # 2..5
        pool = list(MOCK_SYMBOLS)
        _shuffle_java(pool, rnd)
        events = []
        for i in range(count):
            symbol = pool[i]
            session = self._pick_session(rnd)
            confirmed = rnd.next_int(10) < 3
            eps_est = Decimal(repr(0.10 + rnd.next_int(400) / 100.0)).quantize(
                Decimal("0.01"), rounding=ROUND_HALF_UP
            )
            rev_est = Decimal(500 + rnd.next_int(20000))
            if confirmed:
                eps = (eps_est + Decimal(rnd.next_int(61) - 30) / 100).quantize(
                    Decimal("0.01"), rounding=ROUND_HALF_UP
                )
                rev = rev_est + Decimal(rnd.next_int(2001) - 1000)
            else:
                eps = None
                rev = None
            events.append(
                {
                    "date": d.isoformat(),
                    "symbol": symbol,
                    "name": MOCK_NAMES[MOCK_SYMBOLS.index(symbol)],
                    "nameZh": None,
                    "industry": None,
                    "session": session,
                    "confirmed": confirmed,
                    "eps": eps,
                    "epsEstimated": eps_est,
                    "revenue": rev,
                    "revenueEstimated": rev_est,
                    "source": self.source,
                }
            )
        return events

    @staticmethod
    def _pick_session(rnd: _JavaRandom) -> str:
        r = rnd.next_int(20)
        if r < 9:
            return SESSION_BMO  # 45% 盘前
        if r < 19:
            return SESSION_AMC  # 50% 盘后
        return SESSION_DNH  # 5% 盘中
