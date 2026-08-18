# -*- coding: utf-8 -*-
"""中文相对时间解析(格隆汇等页面):"刚刚"、"5分钟前"、"3小时前"、"2天前"、
"昨天 08:30"、"08:30"、"06-12 08:30"、"2026-06-12 08:30"。解析失败返回 None。"""
from __future__ import annotations

import re
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

SH = ZoneInfo("Asia/Shanghai")

_MIN = re.compile(r"^(\d+)\s*分钟前$")
_HOUR = re.compile(r"^(\d+)\s*小时前$")
_DAY = re.compile(r"^(\d+)\s*天前$")


def parse(text: str | None) -> int | None:
    """解析为 epoch 毫秒;无法解析返回 None。"""
    if text is None:
        return None
    s = text.strip()
    if not s:
        return None
    now = int(datetime.now(SH).timestamp() * 1000)
    try:
        if s == "刚刚":
            return now
        m = _MIN.match(s)
        if m:
            return now - int(m.group(1)) * 60_000
        m = _HOUR.match(s)
        if m:
            return now - int(m.group(1)) * 3_600_000
        m = _DAY.match(s)
        if m:
            return now - int(m.group(1)) * 86_400_000
        now_local = datetime.now(SH)
        if s.startswith("昨天 "):
            hhmm = s[3:]
            h, mi = _parse_hhmm(hhmm)
            t = now_local.date() - timedelta(days=1)
            return int(datetime(t.year, t.month, t.day, h, mi, tzinfo=SH).timestamp() * 1000)
        return _parse_absolute(s, now_local)
    except Exception:
        return None


def _parse_hhmm(s: str) -> tuple:
    h, _, mi = s.partition(":")
    return int(h), int(mi)


def _epoch(y, m, d, hh, mi) -> int:
    dt = datetime(y, m, d, hh, mi, tzinfo=SH)
    return int(dt.timestamp() * 1000)


def _parse_absolute(s: str, now_local: datetime) -> int | None:
    # yyyy-MM-dd HH:mm
    if re.fullmatch(r"\d{4}-\d{2}-\d{2} \d{2}:\d{2}", s):
        y, m, d, rest = s[:4], s[5:7], s[8:10], s[11:]
        hh, mi = rest.split(":")
        return _epoch(int(y), int(m), int(d), int(hh), int(mi))
    # MM-dd HH:mm(补当年)
    m_ = re.fullmatch(r"(\d{2})-(\d{2}) (\d{2}):(\d{2})", s)
    if m_:
        mo, d, hh, mi = map(int, m_.groups())
        return _epoch(now_local.year, mo, d, hh, mi)
    # HH:mm(补今天)
    m_ = re.fullmatch(r"(\d{2}):(\d{2})", s)
    if m_:
        hh, mi = map(int, m_.groups())
        return _epoch(now_local.year, now_local.month, now_local.day, hh, mi)
    return None
