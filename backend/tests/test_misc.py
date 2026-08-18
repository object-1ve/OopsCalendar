# -*- coding: utf-8 -*-
"""杂项单元测试:知名公司表完整性、配置(.env.local 优先级/环境变量解析)、SQLite 并发整段替换。"""
import threading

import config
from app.db import Database
from app.known_companies import KNOWN, get


# ---------- 知名公司表 ----------


def test_known_companies_count_and_spot():
    # 与旧版 Java KnownCompanies.java 的行数一致(提取生成,防手抄遗漏)
    assert len(KNOWN) == 105
    nvda = get("nvda")
    assert nvda is not None
    assert nvda.name == "NVIDIA Corp."
    assert nvda.name_zh == "英伟达"
    assert nvda.industry == "半导体 / AI 芯片"
    aapl = get("AAPL")
    assert aapl.name_zh == "苹果"
    assert get("zzzz") is None
    assert get(None) is None


def test_known_companies_all_returns_same_map():
    assert config is not None  # 导入兜底
    assert all(get(s) is not None for s in list(KNOWN)[:5])


# ---------- 配置解析与 .env.local 优先级 ----------


def test_env_helpers(monkeypatch):
    monkeypatch.setenv("TEST_INT", "30")
    monkeypatch.setenv("TEST_FLOAT", "1.5")
    monkeypatch.setenv("TEST_EMPTY", "")
    assert config._env_int("TEST_INT", 7) == 30
    assert config._env_float("TEST_FLOAT", 9.9) == 1.5
    assert config._env_int("MISSING_KEY", 7) == 7
    assert config._env("TEST_EMPTY", "default") == ""
    assert config._env("MISSING_KEY", "default") == "default"


def test_dotenv_local_overrides_env(monkeypatch, tmp_path):
    (tmp_path / ".env.local").write_text(
        "FMP_API_KEY=fromfile\n# 注释行\nEMPTY_KEY=\n", encoding="utf-8"
    )
    monkeypatch.setenv("FMP_API_KEY", "fromenv")
    monkeypatch.setattr(config, "__file__", str(tmp_path / "main.py"))
    config._load_dotenv_local()
    # .env.local 优先级高于环境变量(与旧版 start-backend.cmd 的 set 行为一致)
    assert config._env("FMP_API_KEY") == "fromfile"
    assert config._env("EMPTY_KEY") == ""


# ---------- SQLite 并发整段替换 ----------


def make_db_event(symbol, d="2026-08-03"):
    return {
        "date": d, "symbol": symbol, "name": None, "nameZh": None, "industry": None,
        "session": "AMC", "confirmed": False, "eps": None, "epsEstimated": None,
        "revenue": None, "revenueEstimated": None, "source": "fake",
    }


def test_db_concurrent_replace_no_duplicates(tmp_path):
    db = Database(tmp_path / "earnings.db")
    d1, d2 = "2026-08-01", "2026-08-10"
    errors = []

    def worker(prefix):
        try:
            events = [make_db_event(f"{prefix}{i}") for i in range(20)]
            db.replace_earnings(d1, d2, "fake", events)
        except Exception as e:  # pragma: no cover
            errors.append(e)

    threads = [threading.Thread(target=worker, args=(f"w{n}",)) for n in range(4)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()

    assert not errors
    rows = db.load_earnings(d1, d2, "fake")
    # 并发整段替换串行化:最终为某一次替换的完整结果(20 条),无重复
    assert len(rows) == 20
    symbols = [r["symbol"] for r in rows]
    assert len(set(symbols)) == 20
    # 覆盖记录存在且来源正确
    cov = db.coverage(f"{d1}|{d2}")
    assert cov is not None and cov[0] == "fake"
