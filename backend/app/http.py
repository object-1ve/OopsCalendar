# -*- coding: utf-8 -*-
"""公共异步 HTTP 客户端(httpx):统一 UA、超时、UTF-8 解码与 JSON 解析。"""
from __future__ import annotations

from typing import Optional

import httpx

USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/126.0 Safari/537.36"
)


class HttpClient:
    """共享的 httpx.AsyncClient 封装,按用途选择超时与响应体处理。"""

    def __init__(self, connect_timeout_ms: int, read_timeout_ms: int):
        self._timeout = httpx.Timeout(read_timeout_ms / 1000.0, connect=connect_timeout_ms / 1000.0)

    async def get_bytes(
        self, url: str, headers: Optional[dict] = None, client: Optional[httpx.AsyncClient] = None
    ) -> httpx.Response:
        h = {"User-Agent": USER_AGENT, "Accept": "application/json, text/plain, */*"}
        if headers:
            h.update(headers)
        own = client is None
        if own:
            client = httpx.AsyncClient(timeout=self._timeout, follow_redirects=True)
        try:
            resp = await client.get(url, headers=h)
            return resp
        finally:
            if own:
                await client.aclose()

    async def get_text(
        self, url: str, headers: Optional[dict] = None, client: Optional[httpx.AsyncClient] = None
    ) -> str:
        resp = await self.get_bytes(url, headers, client)
        resp.raise_for_status()
        return resp.text

    async def get_json(
        self, url: str, headers: Optional[dict] = None, client: Optional[httpx.AsyncClient] = None
    ):
        resp = await self.get_bytes(url, headers, client)
        resp.raise_for_status()
        return resp.json()

    async def post(
        self,
        url: str,
        content: str = "",
        headers: Optional[dict] = None,
        client: Optional[httpx.AsyncClient] = None,
    ) -> httpx.Response:
        """POST 原始文本 body(如 TQL 协议),返回响应对象。"""
        h = {"User-Agent": USER_AGENT, "Accept": "application/json, text/plain, */*"}
        if headers:
            h.update(headers)
        own = client is None
        if own:
            client = httpx.AsyncClient(timeout=self._timeout, follow_redirects=True)
        try:
            resp = await client.post(url, content=content, headers=h)
            return resp
        finally:
            if own:
                await client.aclose()
