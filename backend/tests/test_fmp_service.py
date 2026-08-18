# -*- coding: utf-8 -*-
"""FMP 作为激活数据源的整链路测试:选择逻辑、真实数据落库、失败降级到 mock。"""
import asyncio
from datetime import date

from app.db import Database
from app.earnings.providers import FmpProvider, MockProvider
from app.earnings.service import EarningsService
from app.earnings.symbols import SymbolService


def run(coro):
    return asyncio.run(coro)


class NoKeyProvider:
    """无 key 的假 Finnhub:不应被选中或调用。"""
    source = "nokey"
    has_key = False

    async def fetch(self, f, t):
        raise AssertionError("Finnhub 不应被调用")


class FakeResp:
    status_code = 200

    def __init__(self, payload):
        self._payload = payload

    def json(self):
        return self._payload


class FakeHttp:
    def __init__(self, fail=False):
        self.fail = fail
        self.calls = []

    async def get_bytes(self, url, headers=None):
        self.calls.append(url)
        if self.fail:
            raise Exception("Connection refused")
        return FakeResp([
            {"date": "2026-08-10", "symbol": "AAPL", "time": "bmo", "eps": 1.5,
             "epsEstimated": 1.4, "revenue": 1000000, "revenueEstimated": 900000},
        ])


def make_service(db, fail=False):
    fmp = FmpProvider("https://fmp.example", "key", 1000, 1000)
    fmp.http = FakeHttp(fail=fail)
    return EarningsService(
        fmp, NoKeyProvider(), MockProvider(),
        SymbolService("http://x", "", 1000, 1000), db,
    )


def test_fmp_selected_when_finnhub_missing(tmp_path):
    svc = make_service(None)
    assert svc.active_source == "fmp"


def test_fmp_fetch_enriches_and_persists(tmp_path):
    db = Database(tmp_path / "earnings.db")
    svc = make_service(db)
    resp = run(svc.query(date(2026, 8, 10), date(2026, 8, 10)))
    assert resp["source"] == "fmp"
    assert len(resp["events"]) == 1
    ev = resp["events"][0]
    assert ev["symbol"] == "AAPL"
    assert ev["session"] == "BMO"
    assert ev["confirmed"] is True
    assert ev["name"] == "Apple Inc."  # 富化自知名公司表
    assert ev["nameZh"] == "苹果"
    assert db.coverage("2026-08-10|2026-08-10") is not None  # 真实源落库
    rows = db.load_earnings("2026-08-10", "2026-08-10", "fmp")
    assert len(rows) == 1 and rows[0]["symbol"] == "AAPL"


def test_fmp_failure_degrades_to_mock(tmp_path):
    db = Database(tmp_path / "earnings.db")
    svc = make_service(db, fail=True)
    resp = run(svc.query(date(2026, 8, 10), date(2026, 8, 11)))
    assert resp["source"] == "mock"
    assert svc.is_degraded
    assert "FMP" in (svc.degradation_reason or "")
    # mock 不落库
    assert db.coverage("2026-08-10|2026-08-11") is None
