# -*- coding: utf-8 -*-
"""快讯数据源偏好持久化(SQLite news_preference 表,全项目共享一份),重启不丢。

保存时仅保留已知数据源 key;允许保存空列表(= 全部禁用,configured 仍为 true)。
首次启动会把旧的 news-preferences.json 一次性导入数据库(幂等),之后不再读写该文件;
旧文件若按 clientId 分组,则合并所有客户端的源(取并集)作为共享配置。
"""
from __future__ import annotations

import json
import logging
from pathlib import Path

log = logging.getLogger(__name__)

FILE_NAME = "news-preferences.json"


class NewsPreferencesService:
    def __init__(self, db, known_keys: list, data_dir: str):
        self._db = db
        self._known = set(known_keys)
        self._legacy_file = Path(data_dir) / FILE_NAME
        self._migrate_from_legacy_file()

    def _migrate_from_legacy_file(self) -> None:
        try:
            if not self._legacy_file.exists() or self._db.preference_exists():
                return
            root = json.loads(self._legacy_file.read_text(encoding="utf-8"))
            if isinstance(root, dict):
                # 旧版按 clientId 分组 → 合并所有客户端的源(取并集)
                merged: list = []
                seen: set = set()
                for keys in root.values():
                    for k in keys or []:
                        if k in self._known and k not in seen:
                            seen.add(k)
                            merged.append(k)
                self._db.save_preferences(merged)
                log.info("数据源偏好已迁移到数据库(合并 %d 个客户端的并集)", len(root))
                return
            if isinstance(root, list):
                clean = [k for k in root if k in self._known]
                self._db.save_preferences(clean)
                log.info("数据源偏好已迁移到数据库(列表格式)")
        except Exception:
            log.warning("数据源偏好迁移失败(忽略,后续以数据库为准)", exc_info=True)

    def is_configured(self) -> bool:
        return self._db.preference_exists()

    def get(self) -> list:
        sources = self._db.get_preferences()
        if sources is None:
            return []
        return [k for k in sources if k in self._known]

    def save(self, sources: list | None) -> None:
        clean = []
        seen = set()
        for k in sources or []:
            if k in self._known and k not in seen:
                seen.add(k)
                clean.append(k)
        self._db.save_preferences(clean)