# -*- coding: utf-8 -*-
"""估值(PE)服务单元测试:仅知名公司拉取、缓存、节流、失败降级、无 key 短路。"""
import asyncio
from datetime import date
from decimal import Decimal

from app.valuation import ValuationService


def run(coro):
    return asyncio.run(coro)


class FakeEarnings:
    def __init__(self, events):
        self.events = events

    async def query(self, f, t):
        return {"events": list(self.events)}


class FakeResp:
    def __init__(self, payload):
        self._payload = payload

    def raise_for_status(self):
        pass

    def json(self):
        return self._payload


class FakeHttp:
    def __init__(self, pe_map):
        self.pe_map = pe_map
        self.calls = []

    async def get_bytes(self, url, headers=None):
        self.calls.append(url)
        symbol = url.split("symbol=")[1].split("&")[0]
        pe = self.pe_map.get(symbol)
        return FakeResp({"metric": {"peTTM": pe}} if pe is not None else {"metric": {}})


def ev(symbol, d="2026-08-12"):
    return {"date": d, "symbol": symbol, "source": "finnhub", "session": "AMC", "confirmed": False}


def make_service(events=None, pe_map=None, api_key="k", earnings=None):
    svc = ValuationService("https://fin.example", api_key, 1000, 1000,
                           earnings if earnings is not None else FakeEarnings(events or []))
    svc.http = FakeHttp(pe_map or {})
    return svc


def test_no_key_short_circuits():
    svc = make_service(events=[ev("AAPL")], api_key="")
    result = run(svc.valuations_for_date(date(2026, 8, 12)))
    assert result == {}
    assert svc.http.calls == []


def test_known_companies_only_and_value():
    # AAPL 是知名公司 → 拉取;ZZZZ 非知名 → 跳过(不发请求)
    svc = make_service(events=[ev("AAPL"), ev("ZZZZ")], pe_map={"AAPL": "30.5"})
    result = run(svc.valuations_for_date(date(2026, 8, 12)))
    assert result == {"AAPL": Decimal("30.5")}
    urls = svc.http.calls
    assert len(urls) == 1 and "symbol=AAPL" in urls[0]


def test_pe_missing_or_failure_excluded():
    svc = make_service(events=[ev("AAPL"), ev("MSFT")], pe_map={"AAPL": "10.2"})
    result = run(svc.valuations_for_date(date(2026, 8, 12)))
    assert result == {"AAPL": Decimal("10.2")}  # MSFT 无 peTTM → 排除


def test_valuation_cache():
    svc = make_service(events=[ev("AAPL")], pe_map={"AAPL": "20.0"})
    run(svc.valuations_for_date(date(2026, 8, 12)))
    run(svc.valuations_for_date(date(2026, 8, 12)))
    assert len(svc.http.calls) == 1  # 缓存命中


def test_http_exception_excluded():
    class BoomResp:
        def json(self):
            raise RuntimeError("boom")

    class BoomHttp(FakeHttp):
        async def get_bytes(self, url, headers=None):
            self.calls.append(url)
            raise RuntimeError("network")

    svc = make_service(events=[ev("AAPL")], pe_map={"AAPL": "1.0"})
    svc.http = BoomHttp({"AAPL": "1.0"})
    result = run(svc.valuations_for_date(date(2026, 8, 12)))
    assert result == {}