# -*- coding: utf-8 -*-
"""快讯落库:抓取到的快讯去重写入 SQLite(news_item 表),上游失败时用库里数据兜底。

落库失败只记日志降级,不影响实时推送与查询主流程。
"""
from __future__ import annotations

import logging

log = logging.getLogger(__name__)


class NewsStore:
    def __init__(self, db, retention_days: float = 30):
        self._db = db
        self._retention_days = retention_days

    def save(self, items: list) -> None:
        """去重入库(按 item_id,重复忽略)。items 为空不处理。"""
        if not items:
            return
        try:
            self._db.upsert_news_items(items, self._retention_days)
        except Exception:
            log.warning("快讯落库失败", exc_info=True)

    def load(self, limit: int, sources: list | None = None) -> list:
        """读最近 limit 条已入库快讯;失败返回空列表。"""
        try:
            return self._db.load_news_items(limit, sources)
        except Exception:
            log.warning("快讯兜底加载失败", exc_info=True)
            return []

    def load_history(self, limit: int, sources: list | None = None, offset: int = 0, search: str | None = None) -> list:
        """分页读历史快讯(时间倒序),供前端无限滚动使用。"""
        try:
            return self._db.load_news_items(limit, sources, offset, search)
        except Exception:
            log.warning("快讯历史加载失败", exc_info=True)
            return []

    def count(self, sources: list | None = None, search: str | None = None) -> int:
        """统计匹配的历史快讯数量,用于前端判断是否还有更多。"""
        try:
            return self._db.count_news_items(sources, search)
        except Exception:
            return 0