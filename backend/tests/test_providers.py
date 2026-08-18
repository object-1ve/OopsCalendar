# -*- coding: utf-8 -*-
"""数据源解析单元测试:Session 映射、百万美元换算、Finnhub 分段拉取、FMP 错误分类。"""
import asyncio
import hashlib
from datetime import date, timedelta
from decimal import Decimal

import pytest

from app.earnings.providers import (
    FinnhubProvider,
    FmpProvider,
    SESSION_AMC,
    SESSION_BMO,
    SESSION_DNH,
    SESSION_UNKNOWN,
    _to_millions,
    session_from_time,
)
from app.errors import UpstreamUnavailableException
from app.news.sources import ClsSource


def run(coro):
    return asyncio.run(coro)


# ---------- Session 映射 ----------


def test_session_mapping():
    assert session_from_time("bmo") == SESSION_BMO
    assert session_from_time("BMO") == SESSION_BMO
    assert session_from_time("before-market") == SESSION_BMO
    assert session_from_time("amc") == SESSION_AMC
    assert session_from_time("after-market") == SESSION_AMC
    assert session_from_time("dnh") == SESSION_DNH
    assert session_from_time("dmh") == SESSION_DNH
    assert session_from_time("during") == SESSION_DNH
    assert session_from_time(None) == SESSION_UNKNOWN
    assert session_from_time("weird") == SESSION_UNKNOWN


# ---------- 数值换算 ----------


def test_to_millions():
    assert _to_millions(Decimal("67015000")) == Decimal("67.02")
    assert _to_millions(Decimal("1000000")) == Decimal("1.00")
    assert _to_millions(None) is None


# ---------- FMP:解析与错误分类 ----------


class FakeResp:
    def __init__(self, status_code=200, payload=None, exc=None):
        self.status_code = status_code
        self._payload = payload
        self._exc = exc

    def json(self):
        if self._exc:
            raise self._exc
        return self._payload


class FakeHttp:
    def __init__(self, resp):
        self._resp = resp

    async def get_bytes(self, url, headers=None):
        return self._resp


def fmp_provider(payload, status=200):
    p = FmpProvider("https://fmp.example", "key", 1000, 1000)
    p.http = FakeHttp(FakeResp(status, payload))
    return p


def test_fmp_parse_events():
    payload = [
        {"date": "2026-08-10", "symbol": "AAPL", "time": "amc", "eps": 1.5, "epsEstimated": 1.4,
         "revenue": 1000000, "revenueEstimated": 900000},
        {"date": "2026-08-11", "symbol": "MSFT"},  # 缺关键字段应产出 UNKNOWN 未公布
    ]
    provider = fmp_provider(payload)
    events = run(provider.fetch(date(2026, 8, 10), date(2026, 8, 11)))
    assert len(events) == 2
    aapl = events[0]
    assert aapl["session"] == SESSION_AMC
    assert aapl["confirmed"] is True
    assert aapl["source"] == "fmp"
    msft = events[1]
    assert msft["session"] == SESSION_UNKNOWN
    assert msft["confirmed"] is False


def test_fmp_error_response_classified():
    provider = fmp_provider({"Error Message": "Invalid API key. Please contact support@..."}, 200)
    with pytest.raises(UpstreamUnavailableException) as ei:
        run(provider.fetch(date(2026, 8, 10), date(2026, 8, 11)))
    assert "API Key 无效" in str(ei.value)


def test_fmp_http_401():
    provider = fmp_provider([], status=401)
    with pytest.raises(UpstreamUnavailableException) as ei:
        run(provider.fetch(date(2026, 8, 10), date(2026, 8, 11)))
    assert "401" in str(ei.value)


def test_fmp_http_500():
    provider = fmp_provider([], status=503)
    with pytest.raises(UpstreamUnavailableException) as ei:
        run(provider.fetch(date(2026, 8, 10), date(2026, 8, 11)))
    assert "503" in str(ei.value)


def test_fmp_parse_failure():
    class BadJsonResp:
        status_code = 200

        def json(self):
            raise ValueError("bad json")

    provider = fmp_provider(None, status=200)
    provider.http = FakeHttp(BadJsonResp())
    with pytest.raises(UpstreamUnavailableException) as ei:
        run(provider.fetch(date(2026, 8, 10), date(2026, 8, 11)))
    assert "解析失败" in str(ei.value)


# ---------- Finnhub:分段窗口 ----------


class RecordingFinnhub(FinnhubProvider):
    def __init__(self):
        super().__init__("https://fin.example", "token", 1000, 1000)
        self.ranges = []
        self.per_day = {}

    async def _fetch_once(self, f, t):
        self.ranges.append((f, t))
        if (f, t) in self.per_day:
            return [dict(e) for e in self.per_day[(f, t)]]
        # 每天 3 条(与 mock 的确定性演示口径类似)
        events = []
        d = f
        while d <= t:
            for _ in range(3):
                events.append({"date": d.isoformat(), "symbol": "S", "session": SESSION_BMO,
                                "confirmed": False, "source": "finnhub"})
            d += timedelta(days=1)
        return events


def test_finnhub_three_day_windows():
    provider = RecordingFinnhub()
    n = 10
    d = date(2026, 8, 1)
    events = run(provider.fetch(d, d.replace(day=d.day + n - 1)))
    assert len(events) == 3 * n  # 3 条/天 × 10 天
    # 窗口:3 天一段 → 4 个窗口(3+3+3+1)
    assert len(provider.ranges) == 4
    assert provider.ranges[0] == (d, d.replace(day=3))
    assert provider.ranges[3] == (d.replace(day=10), d.replace(day=10))


def test_finnhub_max_truncation_backfills_per_day():
    provider = RecordingFinnhub()
    real_once = provider._fetch_once

    async def fake_once(f, t):
        if (f, t) == (date(2026, 8, 1), date(2026, 8, 3)):
            provider.ranges.append((f, t))  # 记录特判窗口(real 不参与)
            return [{"date": f.isoformat(), "symbol": "S", "session": SESSION_BMO,
                     "confirmed": False, "source": "finnhub"} for _ in range(1500)]
        return await real_once(f, t)  # real 内部会记录本次调用

    provider._fetch_once = fake_once
    d = date(2026, 8, 1)
    events = run(provider.fetch(d, d.replace(day=5)))
    # 窗口 2 个(Aug1-3 / Aug4-5);首窗达上限按天补查 3 次 → 共 5 次请求
    assert len(provider.ranges) == 5
    assert (d, d + timedelta(days=2)) in provider.ranges
    # 按天补查取代被截断的整段:2 天窗口各 3 条/天 + 补查 3 天各 3 条/天 = 15
    assert len(events) == 15


# ---------- 财联社签名(与旧版/参考实现一致) ----------


def test_cls_signature_matches_rsshub_pattern():
    """sign = md5(sha1(按字典序拼接的查询串 hex)),与 RSSHub/参考实现一致。"""
    captured = []

    class FakeClient:
        async def get_json(self, url, headers=None):
            captured.append(url)
            return {"data": {"roll_data": []}}

    src = ClsSource(FakeClient())
    run(src.fetch())
    url = captured[0]
    assert url.startswith("https://www.cls.cn/v1/roll/get_roll_list?")
    query = url.split("?", 1)[1]
    sign = query.split("&sign=", 1)[1]
    params_qs = query.split("&sign=", 1)[0]
    # 参数必须按字典序排序(appName, last_time, os, refresh_type, rn, sv)
    keys = [kv.split("=")[0] for kv in params_qs.split("&")]
    assert keys == sorted(keys)
    expect = hashlib.md5(hashlib.sha1(params_qs.encode("utf-8")).hexdigest().encode("utf-8")).hexdigest()
    assert sign == expect
    assert len(sign) == 32 and all(c in "0123456789abcdef" for c in sign)