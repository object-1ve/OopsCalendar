# -*- coding: utf-8 -*-
"""财报服务单元测试:缓存、刷新拆分、降级与冷却恢复、节流、富化、去重、持久化二级缓存。"""
import asyncio
from datetime import date, timedelta

import pytest

from app.db import Database
from app.errors import ApiException, UpstreamUnavailableException
from app.earnings.providers import MockProvider
from app.earnings.service import EarningsService, _dedupe_events
from app.earnings.symbols import SymbolService


class FakeProvider:
    source = "fake"
    has_key = True

    def __init__(self, events=None, fail=False):
        self.events = events or []
        self.fail = fail
        self.calls = []

    async def fetch(self, f, t):
        self.calls.append((f, t))
        if self.fail:
            raise UpstreamUnavailableException("上游挂了")
        return [dict(e) for e in self.events]


def make_event(symbol, d="2026-08-10", **kw):
    e = {
        "date": d,
        "symbol": symbol,
        "name": None,
        "nameZh": None,
        "industry": None,
        "session": "AMC",
        "confirmed": False,
        "eps": None,
        "epsEstimated": None,
        "revenue": None,
        "revenueEstimated": None,
        "source": "fake",
    }
    e.update(kw)
    return e


def make_service(fmp=None, finnhub=None, db=None, **kw):
    mock = MockProvider()
    symbols = SymbolService("http://x", "", 1000, 1000)
    return EarningsService(
        fmp or FakeProvider(),
        finnhub or FakeProvider(),
        mock,
        symbols,
        db,
        cache_ttl_seconds=kw.get("cache_ttl_seconds", 3600),
        max_range_days=kw.get("max_range_days", 120),
        min_request_interval_ms=kw.get("min_request_interval_ms", 0),
        degraded_retry_ms=kw.get("degraded_retry_ms", 60000),
    )


def run(coro):
    return asyncio.run(coro)


# ---------- 参数校验 ----------


def test_validate_range_inverted():
    svc = make_service()
    with pytest.raises(ApiException) as ei:
        run(svc.query(date(2026, 8, 31), date(2026, 8, 1)))
    assert "from 不能晚于 to" in ei.value.message


def test_validate_range_max_days():
    svc = make_service(max_range_days=120)
    with pytest.raises(ApiException) as ei:
        run(svc.query(date(2026, 1, 1), date(2026, 12, 31)))
    assert "120" in ei.value.message


def test_parse_date_invalid():
    with pytest.raises(ApiException):
        EarningsService.parse_date("bad", "from")
    with pytest.raises(ApiException):
        EarningsService.parse_date("", "from")
    assert EarningsService.parse_date("2026-08-01", "from") == date(2026, 8, 1)


# ---------- 缓存与刷新 ----------


def test_cache_avoids_redundant_fetch():
    provider = FakeProvider(events=[make_event("AAPL")])
    svc = make_service(finnhub=provider)
    r1 = run(svc.query(date(2026, 8, 1), date(2026, 8, 10)))
    r2 = run(svc.query(date(2026, 8, 1), date(2026, 8, 10)))
    assert len(provider.calls) == 1
    assert r1["count"] == r2["count"] == 1
    assert r1["source"] == "fake"


def test_refresh_bypasses_cache():
    provider = FakeProvider(events=[make_event("AAPL")])
    svc = make_service(finnhub=provider)
    # 未来区间:refresh=true 绕过缓存强制回源
    today = date.today()
    run(svc.query(today, today + timedelta(days=2)))
    run(svc.query(today, today + timedelta(days=2), refresh=True))
    assert len(provider.calls) == 2


def test_refresh_splitting_past_keeps_past_cache():
    """全局刷新跨过去日期:过去段复用缓存,只对未来段回源。"""
    provider = FakeProvider(events=[make_event("PAST", d=str(date.today() - timedelta(days=2)))])
    svc = make_service(finnhub=provider)
    # 预填充过去区间缓存(通过一次普通查询,仅过去段)
    past_end = date.today() - timedelta(days=1)
    run(svc.query(date.today() - timedelta(days=5), past_end))
    calls_before = len(provider.calls)
    # 强制刷新整段:过去段应命中缓存,未来段回源
    run(svc.query(date.today() - timedelta(days=5), date.today(), refresh=True))
    # 回源只应发生在未来段(一天窗口)
    assert len(provider.calls) == calls_before + 1


def test_cache_ttl_expiry():
    provider = FakeProvider(events=[make_event("AAPL")])
    svc = make_service(finnhub=provider, cache_ttl_seconds=0)
    run(svc.query(date(2026, 8, 1), date(2026, 8, 10)))
    run(svc.query(date(2026, 8, 1), date(2026, 8, 10)))
    assert len(provider.calls) == 2


# ---------- 降级与恢复 ----------


def test_degraded_falls_back_to_mock_and_recovers():
    failing = FakeProvider(events=[], fail=True)
    svc = make_service(finnhub=failing, degraded_retry_ms=0)
    resp = run(svc.query(date(2026, 8, 10), date(2026, 8, 11)))
    assert resp["source"] == "mock"
    assert svc.is_degraded
    assert svc.degradation_reason == "上游挂了"
    # 冷却期已过(0ms):下一个未命中缓存的请求重试上游,成功恢复
    # (旧 Java 语义相同:已缓存的降级区间直接返回缓存,恢复需一次缓存未命中请求)
    failing.fail = False
    failing.events = [make_event("AAPL")]
    resp2 = run(svc.query(date(2026, 8, 12), date(2026, 8, 13)))  # 新区间,缓存未命中
    assert resp2["source"] == "fake"
    assert not svc.is_degraded


def test_degraded_cooldown_skips_upstream():
    """冷却期内直接走 mock,不再打上游。"""
    failing = FakeProvider(events=[], fail=True)
    svc = make_service(finnhub=failing, degraded_retry_ms=60000)
    run(svc.query(date(2026, 8, 10), date(2026, 8, 10)))
    calls = len(failing.calls)
    resp = run(svc.query(date(2026, 8, 11), date(2026, 8, 11)))  # 新的缓存键,冷却期内
    assert resp["source"] == "mock"
    assert len(failing.calls) == calls  # 未打上游


def test_probe_failure_marks_degraded():
    failing = FakeProvider(events=[], fail=True)
    svc = make_service(finnhub=failing, degraded_retry_ms=60000)
    run(svc.probe_upstream())
    assert svc.is_degraded


def test_probe_mock_skips():
    class NoKey:
        source = "nokey"
        has_key = False

        async def fetch(self, f, t):
            raise AssertionError("不应调用")

    svc = EarningsService(NoKey(), NoKey(), MockProvider(), SymbolService("http://x", "", 1000, 1000), None)
    assert svc.active_source == "mock"
    run(svc.probe_upstream())
    assert not svc.is_degraded


# ---------- 排序 / 去重 / 富化 ----------


def test_sort_and_enrich():
    events = [
        make_event("zzz", d="2026-08-10"),
        make_event("aapl", d="2026-08-09", eps=None, epsEstimated=None),
    ]
    provider = FakeProvider(events=events)
    svc = make_service(finnhub=provider)
    resp = run(svc.query(date(2026, 8, 9), date(2026, 8, 10)))
    assert [e["symbol"] for e in resp["events"]] == ["aapl", "zzz"]  # 按日期+代码升序
    aapl = resp["events"][0]
    assert aapl["name"] == "Apple Inc."  # 富化自知名公司表
    assert aapl["nameZh"] == "苹果"
    assert aapl["industry"] == "消费电子 / 科技"


def test_dedupe_exact():
    ev = make_event("AAPL", eps=None)
    ev2 = make_event("AAPL", eps=None)
    deduped = _dedupe_events([ev, ev2, make_event("AAPL", eps="1.0")])
    assert len(deduped) == 2  # 完全相同副本去掉,内容不同的保留


def test_query_symbol():
    provider = FakeProvider(events=[make_event("AAPL"), make_event("MSFT")])
    svc = make_service(finnhub=provider)
    resp = run(svc.query_symbol("aapl", date(2026, 8, 1), date(2026, 8, 31)))
    assert resp["count"] == 1
    assert resp["events"][0]["symbol"] == "AAPL"
    with pytest.raises(ApiException):
        run(svc.query_symbol("", date(2026, 8, 1), date(2026, 8, 31)))


# ---------- 持久化二级缓存 ----------


def test_persistence_roundtrip(tmp_path):
    db = Database(tmp_path / "earnings.db")
    e = make_event("AAPL", d="2026-08-03")  # 事件日期必须在查询区间内(与真实上游一致)
    e["source"] = "fake"
    d1 = date(2026, 8, 1)
    d2 = date(2026, 8, 5)

    provider1 = FakeProvider(events=[e])
    svc1 = make_service(finnhub=provider1, db=db)
    run(svc1.query(d1, d2))
    assert db.coverage(f"{d1}|{d2}") is not None

    # 新实例(内存缓存为空)从库读回,无需回源
    provider2 = FakeProvider(events=[])
    svc2 = make_service(finnhub=provider2, db=db)
    resp = run(svc2.query(d1, d2))
    assert resp["count"] == 1
    assert resp["events"][0]["symbol"] == "AAPL"
    assert len(provider2.calls) == 0


def test_persistence_expired_coverage_refetches(tmp_path):
    db = Database(tmp_path / "earnings.db")
    e = make_event("AAPL", d="2026-08-03")
    d1 = date(2026, 8, 1)
    d2 = date(2026, 8, 5)
    svc1 = make_service(finnhub=FakeProvider(events=[e]), db=db)
    run(svc1.query(d1, d2))
    # 覆盖过期 → 应回源
    provider2 = FakeProvider(events=[make_event("MSFT", d="2026-08-03")])
    svc2 = make_service(finnhub=provider2, db=db, cache_ttl_seconds=0)
    resp = run(svc2.query(d1, d2))
    assert len(provider2.calls) == 1
    assert resp["events"][0]["symbol"] == "MSFT"


def test_mock_not_persisted(tmp_path):
    db = Database(tmp_path / "earnings.db")
    d1 = date(2026, 8, 10)
    d2 = date(2026, 8, 11)
    mock = MockProvider()

    class NoKey:
        source = "nokey"
        has_key = False

        async def fetch(self, f, t):
            return []

    svc = EarningsService(NoKey(), NoKey(), mock, SymbolService("http://x", "", 1000, 1000), db)
    run(svc.query(d1, d2))
    assert db.coverage(f"{d1}|{d2}") is None  # mock 不落库


# ---------- FMP 作为主数据源(服务级) ----------


class Fmpish(FakeProvider):
    source = "fmp"


class NoKeyFinnhub:
    source = "finnhub"
    has_key = False

    async def fetch(self, f, t):
        raise AssertionError("finnhub 不应被选为 provider")


def test_fmp_active_selection_persist_and_degrade(tmp_path):
    db = Database(tmp_path / "earnings.db")
    d1, d2 = date(2026, 8, 1), date(2026, 8, 5)
    event = make_event("AAPL", d="2026-08-03")

    # 1) finnhub 无 key、fmp 有 key → 选择 fmp 并落库
    fmp_ok = Fmpish(events=[event])
    svc = EarningsService(fmp_ok, NoKeyFinnhub(), MockProvider(),
                          SymbolService("http://x", "", 1000, 1000), db, degraded_retry_ms=0)
    assert svc.active_source == "fmp"
    resp = run(svc.query(d1, d2))
    assert resp["source"] == "fmp"
    cov = db.coverage(f"{d1}|{d2}")
    assert cov is not None and cov[0] == "fmp"

    # 2) fmp 上游失败 → 降级 mock,健康检查如实反映
    fmp_fail = Fmpish(events=[], fail=True)
    svc2 = EarningsService(fmp_fail, NoKeyFinnhub(), MockProvider(),
                           SymbolService("http://x", "", 1000, 1000), None, degraded_retry_ms=0)
    resp2 = run(svc2.query(d1, d2))
    assert resp2["source"] == "mock"
    assert svc2.is_degraded
    assert svc2.active_source == "fmp"  # 数据源不变,只是降级
    # 冷却期已过 → 恢复(新区间,缓存未命中才会上游重试;已缓存的降级区间直接返回)
    fmp_fail.fail = False
    fmp_fail.events = [event]
    resp3 = run(svc2.query(d1 + timedelta(days=6), d2 + timedelta(days=6)))
    assert resp3["source"] == "fmp"
    assert not svc2.is_degraded