# -*- coding: utf-8 -*-
"""新闻抓取公共异步 HTTP 客户端:统一 UA、超时、UTF-8 解码与 JSON 解析。"""
from __future__ import annotations

from typing import Optional

from ..http import HttpClient


class NewsSourceException(Exception):
    """新闻源抓取失败(由聚合服务捕获后单源降级)。"""


class NewsHttpClient:
    def __init__(self, connect_timeout_ms: int, read_timeout_ms: int):
        self._http = HttpClient(connect_timeout_ms, read_timeout_ms)

    async def get_text(self, url: str, headers: Optional[dict] = None) -> str:
        try:
            resp = await self._http.get_bytes(url, headers)
            resp.raise_for_status()
            return resp.text
        except Exception as e:
            raise NewsSourceException(f"请求失败: {url} ({e})") from e

    async def get_json(self, url: str, headers: Optional[dict] = None):
        text = await self.get_text(url, headers)
        return self.parse(text)

    async def post_text(self, url: str, content: str, headers: Optional[dict] = None) -> str:
        try:
            resp = await self._http.post(url, content=content, headers=headers)
            resp.raise_for_status()
            return resp.text
        except Exception as e:
            raise NewsSourceException(f"请求失败: {url} ({e})") from e

    async def post_json(self, url: str, content: str, headers: Optional[dict] = None):
        return self.parse(await self.post_text(url, content, headers))

    def parse(self, text: str):
        """纯文本解析(JSONP 剥壳后的 JSON 字符串等),不发起请求。"""
        try:
            import json

            return json.loads(text)
        except Exception as e:
            raise NewsSourceException("JSON 解析失败") from e

    async def get_set_cookies(self, url: str) -> list:
        """GET 并返回 Set-Cookie 列表(如雪球热股需先取 cookie)。"""
        try:
            resp = await self._http.get_bytes(url)
            resp.raise_for_status()
            return resp.headers.get_list("set-cookie")
        except Exception as e:
            raise NewsSourceException(f"请求失败: {url} ({e})") from e
