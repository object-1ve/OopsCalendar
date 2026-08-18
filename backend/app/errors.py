# -*- coding: utf-8 -*-
"""业务异常与 JSON 工具:与旧版 {"error","message","timestamp"} 契约一致。"""
from __future__ import annotations

import math
from datetime import datetime, timezone
from decimal import Decimal


class ApiException(Exception):
    """业务异常,由全局异常处理器转为带中文信息的 HTTP 响应。"""

    def __init__(self, status: int, message: str):
        super().__init__(message)
        self.status = status
        self.message = message


class UpstreamUnavailableException(Exception):
    """上游(财报数据源)不可用,由 EarningsService 捕获后回退 mock。"""


def _clean_value(v):
    if isinstance(v, dict):
        return {k: _clean_value(val) for k, val in v.items() if val is not None}
    if isinstance(v, (list, tuple)):
        return [_clean_value(x) for x in v if x is not None]
    if isinstance(v, Decimal):
        if v == v.to_integral_value():
            return int(v)
        return float(v)
    if isinstance(v, float):
        if math.isnan(v) or math.isinf(v):
            return None
        return v
    return v


def clean(obj):
    """递归删除 None 字段(对应旧版 Jackson default-property-inclusion: non_null)。"""
    return _clean_value(obj)


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def now_epoch_ms() -> int:
    return int(datetime.now(timezone.utc).timestamp() * 1000)
