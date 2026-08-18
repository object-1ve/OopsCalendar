# -*- coding: utf-8 -*-
"""收藏公司持久化:全项目共享一份,存 JSON 文件(data/favorites.json),重启不丢。

空收藏等价于未配置(不写记录)。旧版按 clientId 分组的字典格式会在加载时自动合并为单份列表。
快讯收藏走 SQLite(见 db.py)。
"""
from __future__ import annotations

import json
import logging
import threading
from pathlib import Path

log = logging.getLogger(__name__)

FILE_NAME = "favorites.json"


class FavoritesService:
    def __init__(self, data_dir: str):
        self._file = Path(data_dir) / FILE_NAME
        self._symbols: list = []
        self._lock = threading.Lock()
        self._load()

    def is_configured(self) -> bool:
        return bool(self._symbols)

    def get(self) -> list:
        return list(self._symbols)

    def save(self, symbols: list | None) -> None:
        clean_list = []
        seen = set()
        for s in symbols or []:
            if s is None:
                continue
            t = s.strip().upper()
            if t and t not in seen:
                seen.add(t)
                clean_list.append(t)
        with self._lock:
            self._symbols = clean_list
            self._persist()

    def _load(self) -> None:
        try:
            if not self._file.exists():
                return
            root = json.loads(self._file.read_text(encoding="utf-8"))
            if isinstance(root, dict):
                # 旧版:按 clientId 分组 → 合并所有客户端的收藏为一份并落盘新格式
                merged: list = []
                seen: set = set()
                for syms in root.values():
                    for s in syms or []:
                        t = str(s).strip().upper()
                        if t and t not in seen:
                            seen.add(t)
                            merged.append(t)
                self._symbols = merged
                self._persist()
                log.info("收藏已迁移为共享配置: %d 个代码", len(merged))
                return
            clean_list = []
            seen = set()
            for s in root if isinstance(root, list) else []:
                t = str(s).strip().upper()
                if t and t not in seen:
                    seen.add(t)
                    clean_list.append(t)
            self._symbols = clean_list
            log.info("收藏已加载: %d 个代码", len(self._symbols))
        except Exception:
            log.warning("收藏加载失败,使用空列表", exc_info=True)

    def _persist(self) -> None:
        try:
            self._file.parent.mkdir(parents=True, exist_ok=True)
            self._file.write_text(
                json.dumps(self._symbols, ensure_ascii=False, indent=2), encoding="utf-8"
            )
        except Exception:
            log.warning("收藏保存失败", exc_info=True)