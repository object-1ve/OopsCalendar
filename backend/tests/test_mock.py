# -*- coding: utf-8 -*-
"""Mock 数据源确定性 + 结构不变量测试。"""
import asyncio
from datetime import date

from app.earnings.providers import MockProvider, _JavaRandom, _shuffle_java

WEEK = [date(2026, 8, 10), date(2026, 8, 11), date(2026, 8, 12), date(2026, 8, 13), date(2026, 8, 14)]


def run(coro):
    return asyncio.run(coro)


def fetch_all(days):
    provider = MockProvider()
    events = []
    for d in days:
        events.extend(run(provider.fetch(d, d)))
    return events


def test_mock_deterministic():
    a = fetch_all(WEEK)
    b = fetch_all(WEEK)
    assert a == b


def test_mock_weekday_only():
    events = fetch_all([date(2026, 8, 15), date(2026, 8, 16)])  # 周六/周日
    assert events == []


def test_mock_structure_invariants():
    events = fetch_all(WEEK)
    assert 5 * 2 <= len(events) <= 5 * 5  # 工作日 2-5 条/天
    for d in WEEK:
        day_events = [e for e in events if e["date"] == d.isoformat()]
        assert 2 <= len(day_events) <= 5
        syms = [e["symbol"] for e in day_events]
        assert len(set(syms)) == len(syms)  # 同一天代码不重复
        for e in day_events:
            assert e["session"] in ("BMO", "AMC", "DNH")
            assert e["source"] == "mock"
            assert e["epsEstimated"] is not None
            assert isinstance(e["date"], str)
            if e["confirmed"]:
                assert e["eps"] is not None and e["revenue"] is not None
            else:
                assert e["eps"] is None and e["revenue"] is None


def test_mock_confirmed_ratio_sane():
    events = fetch_all(WEEK)
    confirmed = [e for e in events if e["confirmed"]]
    ratio = len(confirmed) / len(events)
    assert 0.15 <= ratio <= 0.45  # ~30%


def test_java_random_power_of_two():
    # n=4(2 的幂)与 n=3(非幂)都能稳定产生 [0, n) 结果
    for n in (3, 4, 20, 10):
        rnd = _JavaRandom(12345)
        for _ in range(1000):
            v = rnd.next_int(n)
            assert 0 <= v < n


def test_shuffle_java_permutation():
    lst = list(range(32))
    rnd = _JavaRandom(987654)
    _shuffle_java(lst, rnd)
    assert sorted(lst) == list(range(32))