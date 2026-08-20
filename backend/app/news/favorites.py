# -*- coding: utf-8 -*-
"""快讯收藏持久化(SQLite news_favorite 表):全项目共享一份,保存整条快讯快照,重启不丢。

同一快讯重复收藏保持首次时间(再次收藏快照刷新不把收藏时间推后);
整表替换语义:不在请求列表中的收藏会被移除。缺 id / title / source 的畸形条目静默跳过。
"""
from __future__ import annotations

from datetime import datetime, timezone


class NewsFavoriteService:
    def __init__(self, db):
        self._db = db

    def is_configured(self) -> bool:
        return self._db.news_favorites_exist()

    def get(self) -> list:
        """返回收藏列表,最近收藏的在前;未配置过返回空列表。"""
        return self._db.get_news_favorites()

    def save(self, items: list | None) -> None:
        # 按 itemId 去重(保留首次出现)并过滤非法条目:缺 id / title / source 的快讯无法成行落库
        incoming = {}
        for it in items or []:
            if not isinstance(it, dict):
                continue
            iid = (it.get("id") or "").strip()
            title = (it.get("title") or "").strip()
            source = (it.get("source") or "").strip()
            if not iid or not title or not source:
                continue
            if iid not in incoming:
                item = dict(it)
                item["id"] = iid
                item["title"] = title
                item["source"] = source
                incoming[iid] = item

        # 保留首次收藏时间:已存在条目用其 created_at,新条目用现在
        now_iso = datetime.now(timezone.utc).isoformat()
        existing_by_id = self._db.news_favorite_created_map()

        final = []
        created_map = {}
        for iid, item in incoming.items():
            created_map[iid] = existing_by_id.get(iid, now_iso)
            final.append(item)

        self._db.replace_news_favorites(final, created_map)

    def merge(self, items: list | None) -> dict:
        """去重导入:把导入条目合并进现有收藏(不删现有),按 itemId 去重。

        - 批内重复(itemId 相同)与已收藏的条目跳过(保留原快照与首藏时间);
        - 缺 id / title / source 的畸形条目跳过;
        - 新条目按当前时间收藏,与现有收藏合并后按收藏时间倒序返回。
        返回 {"imported": 新增条数, "skipped": 跳过条数(重复/畸形), "items": 合并后完整列表}。
        """
        incoming: dict = {}
        skipped = 0
        for it in items or []:
            if not isinstance(it, dict):
                skipped += 1
                continue
            iid = (it.get("id") or "").strip()
            title = (it.get("title") or "").strip()
            source = (it.get("source") or "").strip()
            if not iid or not title or not source:
                skipped += 1
                continue
            if iid in incoming:
                skipped += 1
                continue
            item = dict(it)
            item["id"] = iid
            item["title"] = title
            item["source"] = source
            incoming[iid] = item

        existing = self._db.get_news_favorites()
        existing_by_id = {i["id"]: i for i in existing}
        created_map = self._db.news_favorite_created_map()
        now_iso = datetime.now(timezone.utc).isoformat()

        merged: dict = {}
        imported = 0
        for iid, item in incoming.items():
            if iid in existing_by_id:
                merged[iid] = existing_by_id[iid]  # 已收藏:保留原快照,不算新增
                skipped += 1
            else:
                merged[iid] = item
                created_map[iid] = now_iso
                imported += 1
        # 现有收藏中不在导入列表里的保留
        for iid, item in existing_by_id.items():
            merged.setdefault(iid, item)

        final = sorted(
            merged.values(), key=lambda it: (created_map.get(it["id"], now_iso), it["id"]), reverse=True
        )
        self._db.replace_news_favorites(final, created_map)
        return {"imported": imported, "skipped": skipped, "items": final}