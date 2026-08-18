# -*- coding: utf-8 -*-
"""财经快讯数据源:金十 / 财联社 / 华尔街见闻 / 东方财富 / 同花顺 / 雪球 / 格隆汇 / 通达信。"""
from __future__ import annotations

import hashlib
import re
from datetime import datetime
from zoneinfo import ZoneInfo

from bs4 import BeautifulSoup

from .client import NewsHttpClient
from .reltime import parse as parse_relative_time

SH = ZoneInfo("Asia/Shanghai")
_BRACKET = re.compile(r"^【([^】]*)】(.*)$")


def _trim_to_none(s):
    if s is None:
        return None
    t = s.strip()
    return t if t else None


class Jin10Source:
    key = "jin10"
    name = "金十数据"
    icon = "jin10.png"

    def __init__(self, http: NewsHttpClient):
        self.http = http

    async def fetch(self):
        import time as _time

        url = "https://www.jin10.com/flash_newest.js?t=" + str(int(_time.time() * 1000))
        text = await self.http.get_text(url)
        eq = text.find("=")
        json_text = (text[eq + 1 :] if eq >= 0 else text)
        json_text = re.sub(r";+\s*$", "", json_text).strip()
        arr = self.http.parse(json_text)
        items = []
        if not isinstance(arr, list):
            return items
        for n in arr:
            if not isinstance(n, dict):
                continue
            if _contains_channel(n, 5):
                continue  # 过滤频道 5(与 newsnow 保持一致)
            nid = _trim_to_none(str(n.get("id") or ""))
            if not nid:
                continue
            data = n.get("data") or {}
            if not isinstance(data, dict):
                data = {}
            raw = _trim_to_none(data.get("title"))
            content = _trim_to_none(data.get("content"))
            if raw is None and content is not None:
                raw = content
            if raw is None:
                continue
            title = raw
            summary = None
            m = _BRACKET.match(raw.strip())
            if m:
                title = m.group(1).strip()
                summary = m.group(2).strip() or None
            items.append(
                {
                    "id": "jin10:" + nid,
                    "title": title,
                    "url": "https://flash.jin10.com/detail/" + nid,
                    "pubDate": _parse_jin10_time(_trim_to_none(n.get("time"))),
                    "source": self.key,
                    "sourceName": self.name,
                    "summary": summary,
                    "important": bool(int(n.get("important") or 0)),
                }
            )
        return items


def _contains_channel(n: dict, target: int) -> bool:
    ch = n.get("channel")
    if not isinstance(ch, list):
        return False
    return any(c == target for c in ch)


def _parse_jin10_time(s):
    if not s:
        return None
    try:
        dt = datetime.strptime(s, "%Y-%m-%d %H:%M:%S").replace(tzinfo=SH)
        return int(dt.timestamp() * 1000)
    except Exception:
        return None


class ClsSource:
    key = "cls"
    name = "财联社"
    icon = "cls.png"

    def __init__(self, http: NewsHttpClient):
        self.http = http

    async def fetch(self):
        import time as _time

        now_sec = int(_time.time())
        params = {
            "appName": "CailianpressWeb",
            "os": "web",
            "sv": "7.7.5",
            "last_time": str(now_sec),
            "refresh_type": "1",
            "rn": "30",
        }
        qs = "&".join(f"{k}={v}" for k, v in sorted(params.items()))
        sign = hashlib.md5(hashlib.sha1(qs.encode("utf-8")).hexdigest().encode("utf-8")).hexdigest()
        url = "https://www.cls.cn/v1/roll/get_roll_list?" + qs + "&sign=" + sign
        root = await self.http.get_json(url, {"Referer": "https://www.cls.cn/telegraph"})
        items = []
        if not isinstance(root, dict):
            return items
        data = root.get("data") or {}
        lst = data.get("roll_data") if isinstance(data, dict) else None
        if not isinstance(lst, list):
            return items
        for n in lst:
            if not isinstance(n, dict):
                continue
            if int(n.get("is_ad") or 0) == 1:
                continue
            nid = _trim_to_none(str(n.get("id") or ""))
            if not nid:
                continue
            title = _trim_to_none(n.get("title"))
            brief = _trim_to_none(n.get("brief"))
            if title is None:
                title = brief
            if title is None:
                continue
            ctime = int(n.get("ctime") or 0)
            items.append(
                {
                    "id": "cls:" + nid,
                    "title": title,
                    "url": "https://www.cls.cn/detail/" + nid,
                    "pubDate": ctime * 1000 if ctime > 0 else None,
                    "source": self.key,
                    "sourceName": self.name,
                    "summary": brief if brief is not None and brief != title else None,
                    "important": False,
                }
            )
        return items


class EastmoneySource:
    key = "eastmoney"
    name = "东方财富"
    icon = "eastmoney.png"

    def __init__(self, http: NewsHttpClient):
        self.http = http

    async def fetch(self):
        url = "https://newsapi.eastmoney.com/kuaixun/v1/getlist_102_ajaxResult_50_1_.html"
        text = await self.http.get_text(url)
        eq = text.find("=")
        json_text = (text[eq + 1 :] if eq >= 0 else text)
        json_text = re.sub(r";+\s*$", "", json_text).strip()
        root = self.http.parse(json_text)
        items = []
        if not isinstance(root, dict):
            return items
        lst = root.get("LivesList")
        if not isinstance(lst, list):
            return items
        for n in lst:
            if not isinstance(n, dict):
                continue
            nid = _trim_to_none(n.get("id"))
            title = _trim_to_none(n.get("title"))
            if not nid or title is None:
                continue
            url_w = _trim_to_none(n.get("url_w"))
            items.append(
                {
                    "id": "eastmoney:" + nid,
                    "title": title,
                    "url": url_w if url_w else f"https://finance.eastmoney.com/a/{nid}.html",
                    "pubDate": _parse_micros(_trim_to_none(n.get("sort"))),
                    "source": self.key,
                    "sourceName": self.name,
                    "summary": _trim_to_none(n.get("digest")),
                    "important": False,
                }
            )
        return items


def _parse_micros(s):
    if not s:
        return None
    try:
        v = int(s)
        return v // 1000 if v > 0 else None
    except ValueError:
        return None


class TonghuashunSource:
    key = "tonghuashun"
    name = "同花顺"
    icon = "tonghuashun.png"

    def __init__(self, http: NewsHttpClient):
        self.http = http

    async def fetch(self):
        url = "http://news.10jqka.com.cn/tapp/news/push/stock/?page=1&tag=&track=website&pagesize=50"
        root = await self.http.get_json(url)
        items = []
        if not isinstance(root, dict) or str(root.get("code")) != "200":
            return items
        data = root.get("data") or {}
        lst = data.get("list") if isinstance(data, dict) else None
        if not isinstance(lst, list):
            return items
        for n in lst:
            if not isinstance(n, dict):
                continue
            nid = _trim_to_none(str(n.get("id") or ""))
            title = _trim_to_none(n.get("title"))
            if not nid or title is None:
                continue
            link = _trim_to_none(n.get("url"))
            items.append(
                {
                    "id": "tonghuashun:" + nid,
                    "title": title,
                    "url": link if link else f"https://news.10jqka.com.cn/{nid}.shtml",
                    "pubDate": _parse_seconds(_trim_to_none(n.get("ctime"))),
                    "source": self.key,
                    "sourceName": self.name,
                    "summary": _trim_to_none(n.get("digest")),
                    "important": False,
                }
            )
        return items


def _parse_seconds(s):
    if not s:
        return None
    try:
        return int(s.strip()) * 1000
    except ValueError:
        return None


class WallstreetcnSource:
    key = "wallstreetcn"
    name = "华尔街见闻"
    icon = "wallstreetcn.png"

    def __init__(self, http: NewsHttpClient):
        self.http = http

    async def fetch(self):
        url = "https://api-one.wallstcn.com/apiv1/content/lives?channel=global-channel&limit=30"
        root = await self.http.get_json(url)
        items = []
        if not isinstance(root, dict):
            return items
        data = root.get("data") or {}
        lst = data.get("items") if isinstance(data, dict) else None
        if not isinstance(lst, list):
            return items
        for n in lst:
            if not isinstance(n, dict):
                continue
            nid = _trim_to_none(str(n.get("id") or ""))
            if not nid:
                continue
            title = _trim_to_none(n.get("title"))
            content = _trim_to_none(n.get("content_text"))
            if title is None:
                title = content
            if title is None:
                continue
            uri = _trim_to_none(n.get("uri"))
            if uri is None:
                continue
            display_time = int(n.get("display_time") or 0)
            items.append(
                {
                    "id": "wallstreetcn:" + nid,
                    "title": title,
                    "url": uri if uri.startswith("http") else "https://wallstreetcn.com" + uri,
                    "pubDate": display_time * 1000 if display_time > 0 else None,
                    "source": self.key,
                    "sourceName": self.name,
                    "summary": content if content is not None and content != title else None,
                    "important": False,
                }
            )
        return items


class XueqiuSource:
    key = "xueqiu"
    name = "雪球"
    icon = "xueqiu.png"

    def __init__(self, http: NewsHttpClient):
        self.http = http

    async def fetch(self):
        import time as _time

        cookies = await self.http.get_set_cookies("https://xueqiu.com/hq")
        headers = {"Cookie": "; ".join(cookies)}
        url = "https://stock.xueqiu.com/v5/stock/hot_stock/list.json?size=30&_type=10&type=10"
        root = await self.http.get_json(url, headers)
        items = []
        if not isinstance(root, dict):
            return items
        data = root.get("data") or {}
        lst = data.get("items") if isinstance(data, dict) else None
        if not isinstance(lst, list):
            return items
        now = int(_time.time() * 1000)
        for n in lst:
            if not isinstance(n, dict):
                continue
            if int(n.get("ad") or 0) == 1:
                continue
            code = _trim_to_none(n.get("code"))
            name = _trim_to_none(n.get("name"))
            if not code or name is None:
                continue
            percent = float(n.get("percent") or 0)
            current = float(n.get("current") or 0)
            items.append(
                {
                    "id": "xueqiu:" + code,
                    "title": name,
                    "url": "https://xueqiu.com/s/" + code,
                    "pubDate": now,
                    "source": self.key,
                    "sourceName": self.name,
                    "summary": f"现价 {current:.2f} · {percent:+.2f}%",
                    "important": False,
                }
            )
        return items


class GelonghuiSource:
    key = "gelonghui"
    name = "格隆汇"
    icon = "gelonghui.png"
    base = "https://www.gelonghui.com"

    def __init__(self, http: NewsHttpClient):
        self.http = http

    async def fetch(self):
        html = await self.http.get_text(self.base + "/news/")
        soup = BeautifulSoup(html, "html.parser")
        items = []
        for block in soup.select(".article-content"):
            a = block.select_one(".detail-right>a")
            h2 = a.select_one("h2") if a is not None else None
            if a is None or h2 is None:
                continue
            href = _trim_to_none(a.get("href"))
            title = _trim_to_none(h2.get_text())
            if href is None or title is None:
                continue
            spans = block.select(".time > span")
            time_text = _trim_to_none(spans[2].get_text()) if len(spans) >= 3 else None
            info = _trim_to_none(spans[0].get_text()) if len(spans) >= 1 else None
            items.append(
                {
                    "id": "gelonghui:" + href,
                    "title": title,
                    "url": href if href.startswith("http") else self.base + href,
                    "pubDate": parse_relative_time(time_text),
                    "source": self.key,
                    "sourceName": self.name,
                    "summary": info,
                    "important": False,
                }
            )
        return items


class TdxSource:
    """通达信资讯(TQL 协议,资讯中心「财经·最新」列表)。

    官方资讯中心页面自身通过 POST /TQLEX?Entry=CWServ.tdxi_zxxwnews 取数,
    body 为 {"Params":["1","200",page,pageSize,""]}。返回 JSON 的
    ResultSets[table1] 每行:Title / Issue_date / Summary / rec_id / Info_Src /
    Key_Words / Url / AttachFile / bProc。
    """
    key = "tdx"
    name = "通达信"
    icon = "tdx.png"
    _url = "https://fk.tdx.com.cn/TQLEX?Entry=CWServ.tdxi_zxxwnews"
    _body = '{"Params":["1","200","1","20",""]}'
    _referer = "https://fk.tdx.com.cn/site/zx/zx_xw_news.htm?query1=1&query2=200"

    def __init__(self, http: NewsHttpClient):
        self.http = http

    async def fetch(self):
        root = await self.http.post_json(
            self._url,
            self._body,
            headers={"Referer": self._referer, "Content-Type": "text/plain"},
        )
        items = []
        if not isinstance(root, dict) or root.get("ErrorCode") != 0:
            return items
        rows = []
        for rs in root.get("ResultSets") or []:
            if not isinstance(rs, dict) or rs.get("ResultSetKey") != "table1":
                continue
            cols = rs.get("ColName") or []
            for content in rs.get("Content") or []:
                if not isinstance(content, list) or not isinstance(cols, list):
                    continue
                row = {}
                for i, name in enumerate(cols):
                    if i < len(content):
                        row[name] = content[i]
                rows.append(row)
        for r in rows:
            nid = _trim_to_none(str(r.get("rec_id") or ""))
            title = _trim_to_none(str(r.get("Title") or ""))
            url = _trim_to_none(str(r.get("Url") or ""))
            if not nid or title is None or not url:
                continue
            items.append(
                {
                    "id": "tdx:" + nid,
                    "title": title,
                    "url": url,
                    "pubDate": _parse_tdx_time(_trim_to_none(str(r.get("Issue_date") or ""))),
                    "source": self.key,
                    "sourceName": self.name,
                    "summary": _trim_to_none(str(r.get("Summary") or "")),
                    "important": False,
                }
            )
        return items


def _parse_tdx_time(s):
    """通达信时间格式:2026-08-18 08:54:29(东八区)。"""
    if not s:
        return None
    try:
        dt = datetime.strptime(s, "%Y-%m-%d %H:%M:%S").replace(tzinfo=SH)
        return int(dt.timestamp() * 1000)
    except Exception:
        return None


def all_sources(http: NewsHttpClient) -> list:
    return [
        Jin10Source(http),
        ClsSource(http),
        WallstreetcnSource(http),
        EastmoneySource(http),
        TonghuashunSource(http),
        XueqiuSource(http),
        GelonghuiSource(http),
        TdxSource(http),
    ]
