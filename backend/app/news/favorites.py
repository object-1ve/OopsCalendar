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