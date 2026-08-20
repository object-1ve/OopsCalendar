# -*- coding: utf-8 -*-
"""SQLite 持久化层:替代旧版 H2 文件库。

表:
  earnings_event    财报事件(整段替换,无唯一约束,与上游一一对应)
  earnings_coverage 覆盖记录(range_key 主键)
  news_preference   快讯数据源偏好(单行,全项目共享一份)
  news_favorite     快讯收藏(item_id 主键,全项目共享一份)
  news_item         快讯落库(item_id 主键,去重存储抓取到的快讯)
"""
from __future__ import annotations

import json
import sqlite3
import threading
from datetime import datetime, timedelta, timezone
from decimal import Decimal
from pathlib import Path
from typing import Optional


def _now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


class Database:
    """轻量线程安全的 SQLite 封装(每个操作独立连接,天然支持并发读)。

    旧版按 client_id 分行的 news_preference / news_favorite 会在首次启动时自动迁移为
    全项目共享一份(合并去重),详情见 _migrate_schema。
    """

    def __init__(self, path: Path):
        self.path = Path(path)
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self._lock = threading.Lock()
        self._init_schema()
        self._migrate_schema()

    def _connect(self) -> sqlite3.Connection:
        conn = sqlite3.connect(str(self.path), timeout=30)
        conn.row_factory = sqlite3.Row
        conn.execute("PRAGMA journal_mode=WAL")
        conn.execute("PRAGMA foreign_keys=ON")
        return conn

    def _init_schema(self) -> None:
        with self._lock, self._connect() as conn:
            conn.executescript(
                """
                CREATE TABLE IF NOT EXISTS earnings_event (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    event_date TEXT NOT NULL,
                    symbol TEXT NOT NULL,
                    name TEXT,
                    name_zh TEXT,
                    industry TEXT,
                    session TEXT NOT NULL,
                    confirmed INTEGER NOT NULL,
                    eps TEXT,
                    eps_estimated TEXT,
                    revenue TEXT,
                    revenue_estimated TEXT,
                    source TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                );
                CREATE INDEX IF NOT EXISTS idx_earnings_range
                    ON earnings_event (event_date, source);

                CREATE TABLE IF NOT EXISTS earnings_coverage (
                    range_key TEXT PRIMARY KEY,
                    source TEXT NOT NULL,
                    fetched_at TEXT NOT NULL
                );

                CREATE TABLE IF NOT EXISTS news_preference (
                    id INTEGER PRIMARY KEY CHECK (id = 1),
                    sources_json TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                );

                CREATE TABLE IF NOT EXISTS news_favorite (
                    item_id TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    url TEXT,
                    pub_date INTEGER,
                    source TEXT NOT NULL,
                    source_name TEXT,
                    summary TEXT,
                    important INTEGER NOT NULL DEFAULT 0,
                    group_name TEXT,
                    created_at TEXT NOT NULL
                );

                CREATE TABLE IF NOT EXISTS news_favorite_group (
                    name TEXT PRIMARY KEY,
                    created_at TEXT NOT NULL
                );

                CREATE TABLE IF NOT EXISTS news_item (
                    item_id TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    url TEXT,
                    pub_date INTEGER,
                    source TEXT NOT NULL,
                    source_name TEXT,
                    summary TEXT,
                    important INTEGER NOT NULL DEFAULT 0,
                    added_at TEXT NOT NULL
                );
                CREATE INDEX IF NOT EXISTS idx_news_item_pub ON news_item (pub_date DESC);
                CREATE INDEX IF NOT EXISTS idx_news_item_source ON news_item (source);
                """
            )

    def _migrate_schema(self) -> None:
        """旧版按 client_id 分行的表 → 全项目单行/单份配置。

        - news_preference:保留 updated_at 最新的那一行作为共享配置;
        - news_favorite:合并所有客户端的收藏,按 item_id 去重(保留最早 created_at)。
        """
        with self._lock, self._connect() as conn:
            prefs_cols = {r["name"] for r in conn.execute("PRAGMA table_info(news_preference)")}
            if "client_id" in prefs_cols:
                rows = conn.execute(
                    "SELECT client_id, sources_json, updated_at FROM news_preference ORDER BY updated_at DESC"
                ).fetchall()
                pick = rows[0] if rows else None
                conn.execute("DROP TABLE news_preference")
                conn.execute(
                    "CREATE TABLE news_preference ("
                    " id INTEGER PRIMARY KEY CHECK (id = 1),"
                    " sources_json TEXT NOT NULL,"
                    " updated_at TEXT NOT NULL)"
                )
                if pick is not None:
                    conn.execute(
                        "INSERT INTO news_preference (id, sources_json, updated_at) VALUES (1,?,?)",
                        (pick["sources_json"], pick["updated_at"]),
                    )

            fav_cols = {r["name"] for r in conn.execute("PRAGMA table_info(news_favorite)")}
            if "client_id" in fav_cols:
                rows = conn.execute(
                    "SELECT item_id, title, url, pub_date, source, source_name, summary, important, created_at "
                    "FROM news_favorite ORDER BY created_at ASC, item_id ASC"
                ).fetchall()
                conn.execute("DROP TABLE news_favorite")
                conn.execute(
                    "CREATE TABLE news_favorite ("
                    " item_id TEXT PRIMARY KEY,"
                    " title TEXT NOT NULL,"
                    " url TEXT,"
                    " pub_date INTEGER,"
                    " source TEXT NOT NULL,"
                    " source_name TEXT,"
                    " summary TEXT,"
                    " important INTEGER NOT NULL DEFAULT 0,"
                    " group_name TEXT,"
                    " created_at TEXT NOT NULL)"
                )
                seen: set = set()
                for r in rows:
                    if r["item_id"] in seen:
                        continue
                    seen.add(r["item_id"])
                    conn.execute(
                        "INSERT OR REPLACE INTO news_favorite (item_id, title, url, pub_date, source, "
                        "source_name, summary, important, group_name, created_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
                        (r["item_id"], r["title"], r["url"], r["pub_date"], r["source"],
                         r["source_name"], r["summary"], r["important"], None, r["created_at"]),
                    )
            elif "group_name" not in fav_cols:
                # 中间版本表:无组别列,直接补列(数据不丢)
                conn.execute("ALTER TABLE news_favorite ADD COLUMN group_name TEXT")

    # ---------- 财报持久化(二级缓存) ----------

    def load_earnings(self, from_date: str, to_date: str, source: str):
        """读回 [from, to] 区间事件,返回 dict 列表(按日期+代码升序)。"""
        with self._lock, self._connect() as conn:
            rows = conn.execute(
                "SELECT * FROM earnings_event WHERE event_date >= ? AND event_date <= ? AND source = ? "
                "ORDER BY event_date ASC, symbol ASC",
                (from_date, to_date, source),
            ).fetchall()
        return [self._event_row_to_dict(r) for r in rows]

    def coverage(self, range_key: str) -> Optional[tuple]:
        with self._lock, self._connect() as conn:
            row = conn.execute(
                "SELECT source, fetched_at FROM earnings_coverage WHERE range_key = ?", (range_key,)
            ).fetchone()
        return (row["source"], row["fetched_at"]) if row else None

    def replace_earnings(self, from_date: str, to_date: str, source: str, events: list) -> None:
        """整段替换 [from, to] 事件并记录覆盖时间(调用方保证 source 非 mock)。"""
        now = _now_iso()
        with self._lock, self._connect() as conn:
            conn.execute(
                "DELETE FROM earnings_event WHERE event_date >= ? AND event_date <= ? AND source = ?",
                (from_date, to_date, source),
            )
            conn.executemany(
                "INSERT INTO earnings_event (event_date, symbol, name, name_zh, industry, session, confirmed, "
                "eps, eps_estimated, revenue, revenue_estimated, source, updated_at) "
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                [
                    (
                        e["date"],
                        e["symbol"],
                        e.get("name"),
                        e.get("nameZh"),
                        e.get("industry"),
                        e.get("session", "UNKNOWN"),
                        1 if e.get("confirmed") else 0,
                        self._dec_to_str(e.get("eps")),
                        self._dec_to_str(e.get("epsEstimated")),
                        self._dec_to_str(e.get("revenue")),
                        self._dec_to_str(e.get("revenueEstimated")),
                        e.get("source", source),
                        now,
                    )
                    for e in events
                ],
            )
            conn.execute(
                "INSERT OR REPLACE INTO earnings_coverage (range_key, source, fetched_at) VALUES (?,?,?)",
                (f"{from_date}|{to_date}", source, now),
            )

    # ---------- 快讯数据源偏好(全项目共享一份) ----------

    def get_preferences(self) -> Optional[list]:
        with self._lock, self._connect() as conn:
            row = conn.execute("SELECT sources_json FROM news_preference WHERE id = 1").fetchone()
        if row is None:
            return None
        try:
            val = json.loads(row["sources_json"])
            return val if isinstance(val, list) else []
        except Exception:
            return []

    def preference_exists(self) -> bool:
        with self._lock, self._connect() as conn:
            row = conn.execute("SELECT 1 FROM news_preference WHERE id = 1").fetchone()
        return row is not None

    def save_preferences(self, sources: list) -> None:
        now = _now_iso()
        with self._lock, self._connect() as conn:
            conn.execute(
                "INSERT OR REPLACE INTO news_preference (id, sources_json, updated_at) VALUES (1,?,?)",
                (json.dumps(sources, ensure_ascii=False), now),
            )

    # ---------- 快讯收藏(全项目共享一份) ----------

    def get_news_favorites(self) -> list:
        with self._lock, self._connect() as conn:
            rows = conn.execute(
                "SELECT * FROM news_favorite ORDER BY created_at DESC, item_id DESC"
            ).fetchall()
        out = []
        for r in rows:
            item = {
                "id": r["item_id"],
                "title": r["title"],
                "url": r["url"],
                "pubDate": r["pub_date"],
                "source": r["source"],
                "sourceName": r["source_name"],
                "summary": r["summary"],
                "important": bool(r["important"]),
                "groupName": r["group_name"],
            }
            out.append(item)
        return out

    def news_favorites_exist(self) -> bool:
        with self._lock, self._connect() as conn:
            row = conn.execute("SELECT 1 FROM news_favorite LIMIT 1").fetchone()
        return row is not None

    def news_favorite_created_map(self) -> dict:
        """{item_id: created_at ISO} 首次收藏时间映射(整表替换时保留原时间)。"""
        with self._lock, self._connect() as conn:
            rows = conn.execute("SELECT item_id, created_at FROM news_favorite").fetchall()
        return {r["item_id"]: r["created_at"] for r in rows}

    def replace_news_favorites(self, items: list, created_at_map: dict) -> None:
        """整表替换快讯收藏。created_at_map: {item_id: 首次收藏 ISO 时间}。"""
        now = _now_iso()
        with self._lock, self._connect() as conn:
            conn.execute("DELETE FROM news_favorite")
            conn.executemany(
                "INSERT OR REPLACE INTO news_favorite (item_id, title, url, pub_date, source, "
                "source_name, summary, important, group_name, created_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
                [
                    (
                        it["id"],
                        it.get("title") or "",
                        it.get("url"),
                        it.get("pubDate"),
                        it.get("source") or "",
                        it.get("sourceName"),
                        it.get("summary"),
                        1 if it.get("important") else 0,
                        (it.get("groupName") or "").strip() or None,
                        created_at_map.get(it["id"], now),
                    )
                    for it in items
                ],
            )

    # ---------- 快讯收藏组别(全项目共享一份) ----------

    def get_favorite_groups(self) -> list:
        """返回组别名称列表(按创建时间升序)。"""
        with self._lock, self._connect() as conn:
            rows = conn.execute(
                "SELECT name FROM news_favorite_group ORDER BY created_at ASC, name ASC"
            ).fetchall()
        return [r["name"] for r in rows]

    def favorite_groups_exist(self) -> bool:
        with self._lock, self._connect() as conn:
            row = conn.execute("SELECT 1 FROM news_favorite_group LIMIT 1").fetchone()
        return row is not None

    def favorite_group_created_map(self) -> dict:
        """{name: created_at ISO} 组别创建时间映射(整表替换时保留原时间)。"""
        with self._lock, self._connect() as conn:
            rows = conn.execute("SELECT name, created_at FROM news_favorite_group").fetchall()
        return {r["name"]: r["created_at"] for r in rows}

    def replace_favorite_groups(self, names: list) -> None:
        """整表替换组别列表。names 去重并过滤空白;同批新建的组别按输入顺序排列(时间戳递增)。"""
        now = datetime.now(timezone.utc)
        existing = self.favorite_group_created_map()
        base = now - timedelta(microseconds=len(names))
        with self._lock, self._connect() as conn:
            conn.execute("DELETE FROM news_favorite_group")
            new_idx = 0
            rows = []
            for n in names:
                if n in existing:
                    created = existing[n]
                else:
                    created = (base + timedelta(microseconds=new_idx)).isoformat()
                    new_idx += 1
                rows.append((n, created))
            conn.executemany(
                "INSERT OR REPLACE INTO news_favorite_group (name, created_at) VALUES (?,?)",
                rows,
            )

    def clear_group_from_favorites(self, name: str) -> None:
        """删除组别时,把该组下的收藏移回未分组(group_name 置空)。"""
        with self._lock, self._connect() as conn:
            conn.execute("UPDATE news_favorite SET group_name = NULL WHERE group_name = ?", (name,))

    def rename_favorite_group(self, old: str, new: str) -> None:
        """重命名组别:更新组别表并同步收藏的 group_name。"""
        now = _now_iso()
        with self._lock, self._connect() as conn:
            row = conn.execute(
                "SELECT created_at FROM news_favorite_group WHERE name = ?", (old,)
            ).fetchone()
            created_at = row["created_at"] if row else now
            conn.execute(
                "INSERT OR REPLACE INTO news_favorite_group (name, created_at) VALUES (?,?)",
                (new, created_at),
            )
            if old != new:
                conn.execute("DELETE FROM news_favorite_group WHERE name = ?", (old,))
                conn.execute(
                    "UPDATE news_favorite SET group_name = ? WHERE group_name = ?", (new, old)
                )

    # ---------- 快讯落库(去重存储抓取到的快讯) ----------

    def upsert_news_items(self, items: list, retention_days: float = 30) -> None:
        """将抓取到的快讯去重写入 news_item(item_id 主键,重复忽略),并清理超期旧数据。"""
        now = _now_iso()
        cutoff = (datetime.now(timezone.utc) - timedelta(days=retention_days)).isoformat()
        with self._lock, self._connect() as conn:
            conn.execute("DELETE FROM news_item WHERE added_at < ?", (cutoff,))
            conn.executemany(
                "INSERT OR IGNORE INTO news_item "
                "(item_id, title, url, pub_date, source, source_name, summary, important, added_at) "
                "VALUES (?,?,?,?,?,?,?,?,?)",
                [
                    (
                        it["id"],
                        it.get("title") or "",
                        it.get("url"),
                        it.get("pubDate"),
                        it.get("source") or "",
                        it.get("sourceName"),
                        it.get("summary"),
                        1 if it.get("important") else 0,
                        now,
                    )
                    for it in items
                    if it.get("id")
                ],
            )

    def load_news_items(
        self, limit: int, sources: Optional[list] = None, offset: int = 0, search: Optional[str] = None
    ) -> list:
        """分页读已入库快讯(按时间倒序;支持按源过滤与标题/摘要模糊搜索)。"""
        conds: list = []
        params: list = []
        if sources:
            conds.append("source IN (" + ",".join("?" for _ in sources) + ")")
            params.extend(sources)
        if search:
            conds.append("(title LIKE ? OR summary LIKE ?)")
            params.extend([f"%{search}%", f"%{search}%"])
        sql = "SELECT * FROM news_item"
        if conds:
            sql += " WHERE " + " AND ".join(conds)
        sql += " ORDER BY pub_date DESC, item_id DESC LIMIT ? OFFSET ?"
        params.extend([limit, offset])
        with self._lock, self._connect() as conn:
            rows = conn.execute(sql, params).fetchall()
        out = []
        for r in rows:
            out.append(
                {
                    "id": r["item_id"],
                    "title": r["title"],
                    "url": r["url"],
                    "pubDate": r["pub_date"],
                    "source": r["source"],
                    "sourceName": r["source_name"],
                    "summary": r["summary"],
                    "important": bool(r["important"]),
                }
            )
        return out

    def count_news_items(self, sources: Optional[list] = None, search: Optional[str] = None) -> int:
        """统计已入库快讯数量(与 load_news_items 同口径)。"""
        conds: list = []
        params: list = []
        if sources:
            conds.append("source IN (" + ",".join("?" for _ in sources) + ")")
            params.extend(sources)
        if search:
            conds.append("(title LIKE ? OR summary LIKE ?)")
            params.extend([f"%{search}%", f"%{search}%"])
        sql = "SELECT COUNT(*) AS c FROM news_item"
        if conds:
            sql += " WHERE " + " AND ".join(conds)
        with self._lock, self._connect() as conn:
            row = conn.execute(sql, params).fetchone()
        return int(row["c"]) if row else 0

    def existing_news_item_ids(self) -> set:
        """返回 news_item 表中全部 item_id(用于导入时统计重复)。"""
        with self._lock, self._connect() as conn:
            rows = conn.execute("SELECT item_id FROM news_item").fetchall()
        return {r["item_id"] for r in rows}

    def size_bytes(self) -> int:
        """数据库文件在磁盘上的占用(主库 + WAL + SHM,单位字节;不存在按 0 计)。"""
        total = 0
        for suffix in ("", "-wal", "-shm"):
            p = Path(str(self.path) + suffix)
            try:
                total += p.stat().st_size
            except OSError:
                pass
        return total

    # ---------- 工具 ----------

    @staticmethod
    def _dec_to_str(v) -> Optional[str]:
        if v is None:
            return None
        if isinstance(v, Decimal):
            return str(v)
        return str(v)

    @staticmethod
    def _event_row_to_dict(r: sqlite3.Row) -> dict:
        def dec(v):
            if v is None:
                return None
            try:
                return Decimal(v)
            except Exception:
                return None

        return {
            "date": r["event_date"],
            "symbol": r["symbol"],
            "name": r["name"],
            "nameZh": r["name_zh"],
            "industry": r["industry"],
            "session": r["session"],
            "confirmed": bool(r["confirmed"]),
            "eps": dec(r["eps"]),
            "epsEstimated": dec(r["eps_estimated"]),
            "revenue": dec(r["revenue"]),
            "revenueEstimated": dec(r["revenue_estimated"]),
            "source": r["source"],
        }
