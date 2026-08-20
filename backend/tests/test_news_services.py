# -*- coding: utf-8 -*-
"""快讯聚合 / SSE 推送 / 收藏 / 偏好 / 相对时间单元测试(全部使用假源,不联网)。"""
import asyncio
import json
import sqlite3
from datetime import datetime, timedelta, timezone

from app.db import Database
from app.favorites import FavoritesService
from app.news.favorites import NewsFavoriteService
from app.news.prefs import NewsPreferencesService
from app.news.reltime import parse as parse_relative_time
from app.news.service import NewsService
from app.news.store import NewsStore
from app.news.stream import NewsStreamService


def run(coro):
    return asyncio.run(coro)


class FakeSource:
    def __init__(self, key, items):
        self.key = key
        self.name = f"源{key}"
        self.icon = f"{key}.png"
        self._items = items
        self.calls = 0

    async def fetch(self):
        self.calls += 1
        return [dict(i) for i in self._items]


def item(iid, source, pub_date, title="标题"):
    return {"id": iid, "title": title, "url": "http://x/" + iid, "pubDate": pub_date,
            "source": source, "sourceName": f"源{source}", "summary": None, "important": False}


# ---------- 聚合 ----------


def test_news_sort_nulls_last():
    a = item("a", "s1", 100)
    b = item("b", "s1", None)
    c = item("c", "s1", 300)
    svc = NewsService(None, sources=[FakeSource("s1", [a, b, c])])
    resp = run(svc.query(None))
    assert [i["id"] for i in resp["items"]] == ["c", "a", "b"]  # 时间倒序,null 最后


def test_news_filter_and_fallback():
    s1 = FakeSource("s1", [item("1", "s1", 1)])
    s2 = FakeSource("s2", [item("2", "s2", 2)])
    svc = NewsService(None, sources=[s1, s2])
    resp = run(svc.query("s1"))
    assert [i["source"] for i in resp["items"]] == ["s1"]
    # 全部无效 key → 回退全部源
    resp = run(svc.query("nosuch"))
    assert {i["source"] for i in resp["items"]} == {"s1", "s2"}


def test_news_cache_per_source():
    s1 = FakeSource("s1", [item("1", "s1", 1)])
    svc = NewsService(None, cache_ttl_seconds=60, sources=[s1])
    run(svc.query("s1"))
    run(svc.query("s1"))
    assert s1.calls == 1  # 命中缓存


def test_news_max_items():
    # 源返回新→旧 50 条(与真实源一致);截断到每源 10 条,合并后取前 5 条
    items = [item(f"i{i}", "s1", 1000 - i) for i in range(50)]
    s1 = FakeSource("s1", items)
    svc = NewsService(None, max_items_per_source=10, max_items=5, sources=[s1])
    resp = run(svc.query(None))
    assert len(resp["items"]) == 5
    assert resp["items"][0]["pubDate"] == 1000  # 最新在前
    assert resp["items"][-1]["pubDate"] == 996


def test_news_list_sources():
    s1 = FakeSource("s1", [])
    svc = NewsService(None, sources=[s1])
    metas = svc.list_sources()
    assert metas == [{"key": "s1", "name": "源s1", "icon": "s1.png"}]


def test_news_query_skips_failed_source():
    class Boom(FakeSource):
        async def fetch(self):
            raise RuntimeError("boom")

    good = FakeSource("g", [item("1", "g", 1)])
    svc = NewsService(None, sources=[good, Boom("b", [])])
    resp = run(svc.query(None))
    assert [i["source"] for i in resp["items"]] == ["g"]  # 单源失败不影响其他源


# ---------- 通达信数据源(TQL 协议) ----------


class FakeTdxHttp:
    def __init__(self, root):
        self.root = root
        self.calls = 0

    async def post_json(self, url, content="", headers=None):
        self.calls += 1
        return self.root


def test_all_sources_includes_tdx():
    from app.news.sources import all_sources

    keys = [s.key for s in all_sources(None)]
    assert "tdx" in keys


def test_tdx_source_parses_rows():
    from app.news.sources import TdxSource

    resp = {
        "ErrorCode": 0,
        "ResultSets": [
            {"ResultSetKey": "table0", "ColName": ["count"], "Content": [[2]]},
            {
                "ResultSetKey": "table1",
                "ColName": ["Title", "Issue_date", "Summary", "rec_id", "Info_Src",
                            "Key_Words", "Url", "AttachFile", "bProc"],
                "Content": [
                    ["第一条", "2026-08-18 08:54:29", "摘要一", 111, "格隆汇", "",
                     "https://x/1", "", 0],
                    ["第二条", "2026-08-18 08:55:00", "摘要二", 222, "财联社", "",
                     "", "", 1],  # 无 Url → 跳过
                ],
            },
        ],
    }
    fake = FakeTdxHttp(resp)
    src = TdxSource(fake)
    items = run(src.fetch())
    assert fake.calls == 1
    assert len(items) == 1
    it = items[0]
    assert it["id"] == "tdx:111"
    assert it["source"] == "tdx" and it["sourceName"] == "通达信"
    assert it["title"] == "第一条" and it["summary"] == "摘要一"
    expected = int(datetime(2026, 8, 18, 8, 54, 29, tzinfo=timezone(timedelta(hours=8))).timestamp() * 1000)
    assert it["pubDate"] == expected


def test_tdx_source_error_code_returns_empty():
    from app.news.sources import TdxSource

    src = TdxSource(FakeTdxHttp({"ErrorCode": 1, "ErrorInfo": "fail"}))
    assert run(src.fetch()) == []


# ---------- SSE 推送 ----------


def test_stream_incremental_and_heartbeat():
    s1 = FakeSource("s1", [item("1", "s1", 1)])
    stream = NewsStreamService(None, poll_ms=60000, sources=[s1])
    q = stream.connect()
    run(stream.poll())
    # 首帧:快照(新订阅时 connect 已入队)或本轮新增广播
    first = q.get_nowait()
    assert first["type"] == "news"
    hb1 = q.get_nowait()
    assert hb1["type"] == "heartbeat"
    # 第二轮无新增:只发心跳
    run(stream.poll())
    hb2 = q.get_nowait()
    assert hb2["type"] == "heartbeat" and q.empty()


def test_stream_seen_ids_dedupe_new_types():
    s1 = FakeSource("s1", [item("a", "s1", 1)])
    stream = NewsStreamService(None, poll_ms=60000, sources=[s1])
    stream.connect()
    run(stream.poll())  # 新增 a
    run(stream.poll())  # 仍是 a,不再推
    assert len(stream.recent) == 1
    # 模拟新条目
    s1._items.append(item("b", "s1", 2))
    run(stream.poll())
    assert len(stream.recent) == 2


def test_stream_single_source_failure_skips():
    class Boom(FakeSource):
        async def fetch(self):
            raise RuntimeError("boom")

    s1 = FakeSource("s1", [item("1", "s1", 1)])
    stream = NewsStreamService(None, poll_ms=60000, sources=[s1, Boom("s2", [])])
    run(stream.poll())  # 不应抛异常
    assert len(stream.recent) == 1


def test_stream_recent_capped_at_200():
    items = [item(f"i{j}", "s1", j) for j in range(250)]
    s1 = FakeSource("s1", items)
    stream = NewsStreamService(None, poll_ms=60000, sources=[s1])
    run(stream.poll())
    assert len(stream.recent) == 200
    # 最旧的被淘汰:保留 pubDate 50..249(新在前)
    assert stream.recent[0]["pubDate"] == 249
    assert stream.recent[-1]["pubDate"] == 50


def test_stream_broadcasts_to_all_subscribers():
    s1 = FakeSource("s1", [item("1", "s1", 1)])
    stream = NewsStreamService(None, poll_ms=60000, sources=[s1])
    q1 = stream.connect()
    q2 = stream.connect()
    run(stream.poll())
    assert q1.get_nowait()["type"] == "news"
    assert q2.get_nowait()["type"] == "news"
    assert q1.get_nowait()["type"] == "heartbeat"
    assert q2.get_nowait()["type"] == "heartbeat"


# ---------- 收藏公司(全项目共享一份) ----------


def test_favorites_uppercase_dedupe_persist(tmp_path):
    svc = FavoritesService(str(tmp_path))
    svc.save(["aapl", "AAPL", "msft", "  xom  "])
    assert svc.get() == ["AAPL", "MSFT", "XOM"]
    assert svc.is_configured()
    # 重新加载(模拟重启)
    svc2 = FavoritesService(str(tmp_path))
    assert svc2.get() == ["AAPL", "MSFT", "XOM"]
    # 空列表清空
    svc2.save([])
    assert not svc2.is_configured()


def test_favorites_migrate_legacy_dict(tmp_path):
    # 旧版按 clientId 分组的字典 → 合并为单份列表并落盘新格式
    (tmp_path / "favorites.json").write_text(
        json.dumps({"c1": ["aapl", "msft"], "c2": ["AAPL", "tsla"]}), encoding="utf-8"
    )
    svc = FavoritesService(str(tmp_path))
    assert svc.get() == ["AAPL", "MSFT", "TSLA"]
    assert json.loads((tmp_path / "favorites.json").read_text(encoding="utf-8")) == ["AAPL", "MSFT", "TSLA"]


# ---------- 快讯收藏(整表替换,保留首藏时间) ----------


def test_news_favorites_whole_replace(tmp_path):
    db = Database(tmp_path / "earnings.db")
    svc = NewsFavoriteService(db)
    item_a = {"id": "jin10:1", "title": "一", "url": "u1", "pubDate": 1, "source": "jin10",
              "sourceName": "金十", "summary": "s", "important": True}
    item_b = dict(item_a, id="jin10:2", title="二")
    svc.save([item_a, item_b])
    assert svc.is_configured()
    got = svc.get()
    assert len(got) == 2

    # 重新保存:移除 b、刷新 a 快照 → 仅剩 a;a 的首藏时间保留(created_at 不变)
    first_created = db.news_favorite_created_map()["jin10:1"]
    item_a_upd = dict(item_a, title="一(新)")
    svc.save([item_a_upd])
    got = svc.get()
    assert len(got) == 1 and got[0]["title"] == "一(新)"
    assert db.news_favorite_created_map()["jin10:1"] == first_created

    # 清空
    svc.save([])
    assert not svc.is_configured()


def test_news_favorites_skips_malformed(tmp_path):
    db = Database(tmp_path / "earnings.db")
    svc = NewsFavoriteService(db)
    good = {"id": "x:1", "title": "好", "source": "jin10"}
    bad_no_id = {"title": "无id", "source": "jin10"}
    bad_no_title = {"id": "x:2", "source": "jin10"}
    bad_no_source = {"id": "x:3", "title": "无源"}
    svc.save([good, bad_no_id, bad_no_title, bad_no_source])
    got = svc.get()
    assert len(got) == 1 and got[0]["id"] == "x:1"


def test_news_favorites_dedup_import_merge(tmp_path):
    db = Database(tmp_path / "earnings.db")
    svc = NewsFavoriteService(db)
    a = {"id": "jin10:1", "title": "一", "url": "u1", "pubDate": 1, "source": "jin10",
         "sourceName": "金十", "summary": "s", "important": True}
    b = dict(a, id="jin10:2", title="二")
    svc.save([a])
    first_created = db.news_favorite_created_map()["jin10:1"]

    # 导入:[a(已收藏快照刷新)、b(新增)、批内重复 c、畸形(无 title)] → 合并去重,不删现有
    res = svc.merge([dict(a, title="一(新)"), b, {"id": "jin10:3", "title": "三", "source": "jin10"},
                     dict(a, id="jin10:3", title="三(批内重复)"), {"id": "jin10:4"}])
    assert res["imported"] == 2  # b、c 新增
    assert res["skipped"] == 3   # a 已收藏 + c 批内重复 + 畸形
    ids = [i["id"] for i in res["items"]]
    assert ids == ["jin10:3", "jin10:2", "jin10:1"]  # 收藏时间倒序,新导入在最前
    # a 保留原快照(未用导入里的"一(新)"覆盖)与原首藏时间
    by_id = {i["id"]: i for i in res["items"]}
    assert by_id["jin10:1"]["title"] == "一"
    assert db.news_favorite_created_map()["jin10:1"] == first_created
    # 落库后重新读取一致(去重导入对现有收藏是合并而非替换)
    got = {i["id"] for i in svc.get()}
    assert got == {"jin10:1", "jin10:2", "jin10:3"}


def test_news_favorites_dedup_import_empty_and_bare_array_noop(tmp_path):
    db = Database(tmp_path / "earnings.db")
    svc = NewsFavoriteService(db)
    res = svc.merge([])
    assert res == {"imported": 0, "skipped": 0, "items": []}
    assert not svc.is_configured()
    # 空数组/None 不改变现有收藏
    svc.save([{"id": "x:1", "title": "好", "source": "jin10"}])
    res = svc.merge(None)
    assert res["imported"] == 0 and res["skipped"] == 0
    assert len(res["items"]) == 1


# ---------- 数据源偏好(全项目共享一份)+ 迁移 ----------


def test_prefs_filter_known_and_migrate(tmp_path):
    data_dir = tmp_path
    # 写入旧版 JSON(模拟升级迁移):多客户端取并集并过滤非法 key
    (data_dir / "news-preferences.json").write_text(
        json.dumps({"c1": ["jin10", "badkey"], "c2": ["cls"]}), encoding="utf-8"
    )
    db = Database(tmp_path / "earnings.db")
    p1 = NewsPreferencesService(db, ["jin10", "cls"], str(data_dir))
    assert p1.is_configured()
    assert p1.get() == ["jin10", "cls"]
    # 幂等:再次实例化不重复迁移
    p2 = NewsPreferencesService(db, ["jin10", "cls"], str(data_dir))
    assert p2.get() == ["jin10", "cls"]

    # 保存空列表 = 已配置但全部禁用
    p1.save([])
    assert p1.is_configured() and p1.get() == []
    # 保存仅保留已知 key 并去重
    p1.save(["jin10", "cls", "badkey", "jin10"])
    assert p1.get() == ["jin10", "cls"]


# ---------- SQLite 旧 schema 迁移(去掉 client_id) ----------


def _build_legacy_db(path):
    conn = sqlite3.connect(str(path))
    conn.executescript(
        """
        CREATE TABLE news_preference (
            client_id TEXT PRIMARY KEY,
            sources_json TEXT NOT NULL,
            updated_at TEXT NOT NULL
        );
        CREATE TABLE news_favorite (
            client_id TEXT NOT NULL,
            item_id TEXT NOT NULL,
            title TEXT NOT NULL,
            url TEXT,
            pub_date INTEGER,
            source TEXT NOT NULL,
            source_name TEXT,
            summary TEXT,
            important INTEGER NOT NULL DEFAULT 0,
            created_at TEXT NOT NULL,
            PRIMARY KEY (client_id, item_id)
        );
        """
    )
    conn.execute("INSERT INTO news_preference VALUES ('c1','[\"jin10\",\"cls\"]','2026-01-01T00:00:00+00:00')")
    conn.execute("INSERT INTO news_preference VALUES ('c2','[\"cls\",\"eastmoney\"]','2026-01-02T00:00:00+00:00')")
    conn.execute(
        "INSERT INTO news_favorite (client_id, item_id, title, url, pub_date, source, source_name, "
        "summary, important, created_at) VALUES "
        "('c1','jin10:1','a','u',1,'jin10','金十',NULL,0,'2026-01-01T00:00:00+00:00')"
    )
    conn.execute(
        "INSERT INTO news_favorite (client_id, item_id, title, url, pub_date, source, source_name, "
        "summary, important, created_at) VALUES "
        "('c2','jin10:1','a','u',1,'jin10','金十',NULL,0,'2026-01-01T00:00:00+00:00')"
    )
    conn.execute(
        "INSERT INTO news_favorite (client_id, item_id, title, url, pub_date, source, source_name, "
        "summary, important, created_at) VALUES "
        "('c1','cls:2','b','u',2,'cls','财联社',NULL,0,'2026-01-02T00:00:00+00:00')"
    )
    conn.commit()
    conn.close()


def test_db_migrates_legacy_client_rows(tmp_path):
    _build_legacy_db(tmp_path / "earnings.db")
    db = Database(tmp_path / "earnings.db")
    # 偏好:保留 updated_at 最新的客户端作为共享配置
    assert db.get_preferences() == ["cls", "eastmoney"]
    # 快讯收藏:合并所有客户端的行,按 item_id 去重
    assert db.news_favorites_exist()
    got = {i["id"] for i in db.get_news_favorites()}
    assert got == {"jin10:1", "cls:2"}
    # 新 schema:不再有 client_id 列
    conn = sqlite3.connect(str(tmp_path / "earnings.db"))
    pref_cols = {r[1] for r in conn.execute("PRAGMA table_info(news_preference)")}
    fav_cols = {r[1] for r in conn.execute("PRAGMA table_info(news_favorite)")}
    conn.close()
    assert "client_id" not in pref_cols and "client_id" not in fav_cols


# ---------- 快讯落库(去重) + 上游失败兜底 ----------


def test_news_persists_dedupe_to_db(tmp_path):
    db = Database(tmp_path / "earnings.db")
    store = NewsStore(db)
    s1 = FakeSource("s1", [item("a", "s1", 100), item("b", "s1", 200)])
    svc = NewsService(None, sources=[s1], store=store)
    run(svc.query("s1"))
    assert {i["id"] for i in store.load(100)} == {"a", "b"}
    # 去重:再次拉同一批数据,不产生重复行
    run(svc.query("s1"))
    assert len(db.load_news_items(100)) == 2


def test_news_fallback_to_db_on_source_failure(tmp_path):
    db = Database(tmp_path / "earnings.db")
    store = NewsStore(db)
    store.save([item("b1", "b", 2), item("b2", "b", 1)])

    class Boom(FakeSource):
        async def fetch(self):
            raise RuntimeError("boom")

    svc = NewsService(None, sources=[Boom("b", [])], store=store)
    resp = run(svc.query("b"))
    # 上游失败 → 用库里该源的最近快讯兜底
    assert [i["id"] for i in resp["items"]] == ["b1", "b2"]


def test_stream_persists_fetched(tmp_path):
    db = Database(tmp_path / "earnings.db")
    store = NewsStore(db)
    s1 = FakeSource("s1", [item("a", "s1", 1), item("b", "s1", 2)])
    stream = NewsStreamService(None, poll_ms=60000, sources=[s1], store=store)
    stream.connect()
    run(stream.poll())
    assert {i["id"] for i in db.load_news_items(100)} == {"a", "b"}


def test_news_history_pagination_and_search(tmp_path):
    db = Database(tmp_path / "earnings.db")
    store = NewsStore(db)
    for i in range(25):
        store.save([item("s1:%02d" % i, "s1", 1000 - i, title="普通%02d" % i)])
    store.save([item("s2:1", "s2", 500, title="特斯拉大涨")])
    # 分页:每页 10 条,时间倒序,offset 正确翻页且不重复
    p1 = store.load_history(10, offset=0)
    p2 = store.load_history(10, offset=10)
    p3 = store.load_history(10, offset=20)
    assert len(p1) == 10 and len(p2) == 10 and len(p3) == 6
    ids = [i["id"] for i in p1 + p2 + p3]
    assert len(set(ids)) == 26  # 无重复
    # 统计
    assert store.count() == 26
    # 按源过滤 + 搜索
    s1_only = store.load_history(100, sources=["s1"])
    assert len(s1_only) == 25
    hit = store.load_history(100, sources=["s2"], search="特斯拉")
    assert [i["id"] for i in hit] == ["s2:1"]
    assert store.count(sources=["s1"]) == 25


def test_news_history_dedup_import_merge(tmp_path):
    db = Database(tmp_path / "earnings.db")
    store = NewsStore(db)
    store.save([item("a", "s1", 1), item("b", "s1", 2)])
    # 导入:a 已存在、c 新增、批内重复 d、畸形(无 title)→ 合并去重
    res = store.merge([item("a", "s1", 1), item("c", "s1", 3),
                       item("d", "s1", 4), item("d", "s1", 5), {"id": "e", "source": "s1"}])
    assert res["imported"] == 2  # c、d 新增
    assert res["skipped"] == 3   # a 已存在 + d 批内重复 + e 畸形
    assert {i["id"] for i in res["items"]} == {"a", "c", "d"}
    assert {i["id"] for i in store.load(100)} == {"a", "b", "c", "d"}
    # 幂等:再导同一批不重复入库
    res2 = store.merge([item("c", "s1", 3)])
    assert res2["imported"] == 0 and res2["skipped"] == 1
    assert store.count() == 4


def test_news_history_dedup_import_empty(tmp_path):
    db = Database(tmp_path / "earnings.db")
    store = NewsStore(db)
    res = store.merge([])
    assert res["imported"] == 0 and res["skipped"] == 0 and res["items"] == []
    assert store.count() == 0
    res = store.merge(None)
    assert res["imported"] == 0 and res["skipped"] == 0


# ---------- 相对时间 ----------


def test_relative_time():
    just_now = parse_relative_time("刚刚")
    assert just_now is not None
    assert parse_relative_time("5分钟前") <= just_now
    assert parse_relative_time("3小时前") < just_now
    assert parse_relative_time("2天前") < just_now
    assert parse_relative_time("2026-06-12 08:30") is not None
    assert parse_relative_time("06-12 08:30") is not None
    assert parse_relative_time("08:30") is not None
    assert parse_relative_time("昨天 08:30") is not None
    assert parse_relative_time(None) is None
    assert parse_relative_time("garbage!!") is None
