# -*- coding: utf-8 -*-
"""配置:从环境变量与 backend/.env.local 加载(FastAPI + uvicorn 后端)。

.env.local 每行 KEY=VALUE(# 开头为注释),优先级高于系统环境变量,
与旧 Java 版 start-backend.cmd 的加载行为一致。
"""
import os
from pathlib import Path


def _load_dotenv_local() -> None:
    """把 backend/.env.local 读入环境(已存在的值会被覆盖,与旧脚本 set 行为一致)。"""
    here = Path(__file__).resolve().parent  # app/backend
    dotenv = here / ".env.local"
    if not dotenv.exists():
        return
    for raw in dotenv.read_text(encoding="utf-8", errors="replace").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, value = line.partition("=")
        key = key.strip()
        value = value.strip().strip('"').strip("'")
        if key:
            os.environ[key] = value


_load_dotenv_local()

_BASE_DIR = Path(__file__).resolve().parent


def _env(name: str, default: str = "") -> str:
    return os.environ.get(name, default).strip()


def _env_int(name: str, default: int) -> int:
    try:
        return int(_env(name, str(default)))
    except ValueError:
        return default


def _env_float(name: str, default: float) -> float:
    try:
        return float(_env(name, str(default)))
    except ValueError:
        return default


class FmpConfig:
    api_key = _env("FMP_API_KEY")
    base_url = _env("FMP_BASE_URL", "https://financialmodelingprep.com/stable")
    connect_timeout_ms = _env_int("FMP_CONNECT_TIMEOUT_MS", 8000)
    read_timeout_ms = _env_int("FMP_READ_TIMEOUT_MS", 15000)
    min_request_interval_ms = _env_float("FMP_MIN_REQUEST_INTERVAL_MS", 1500)
    cache_ttl_seconds = _env_float("FMP_CACHE_TTL_SECONDS", 3600)
    max_range_days = _env_int("FMP_MAX_RANGE_DAYS", 120)
    degraded_retry_ms = _env_float("FMP_DEGRADED_RETRY_MS", 60000)

    @property
    def has_api_key(self) -> bool:
        return bool(self.api_key)


class FinnhubConfig:
    api_key = _env("FINNHUB_API_KEY")
    base_url = _env("FINNHUB_BASE_URL", "https://finnhub.io/api/v1")
    connect_timeout_ms = _env_int("FINNHUB_CONNECT_TIMEOUT_MS", 8000)
    read_timeout_ms = _env_int("FINNHUB_READ_TIMEOUT_MS", 15000)

    @property
    def has_api_key(self) -> bool:
        return bool(self.api_key)


class NewsConfig:
    cache_ttl_seconds = _env_float("NEWS_CACHE_TTL_SECONDS", 60)
    max_items_per_source = _env_int("NEWS_MAX_ITEMS_PER_SOURCE", 50)
    max_items = _env_int("NEWS_MAX_ITEMS", 200)
    poll_ms = _env_float("NEWS_POLL_MS", 15000)
    enabled = _env("NEWS_ENABLED", "true").lower() in ("1", "true", "yes", "on")
    data_dir = _env("NEWS_DATA_DIR", str(_BASE_DIR / "data"))
    retention_days = _env_float("NEWS_RETENTION_DAYS", 30)
    connect_timeout_ms = _env_int("NEWS_CONNECT_TIMEOUT_MS", 8000)
    read_timeout_ms = _env_int("NEWS_READ_TIMEOUT_MS", 15000)


class CorsConfig:
    raw = _env("CORS_ALLOWED_ORIGINS", "http://localhost:5174,http://127.0.0.1:5174")
    allowed_origins = [o.strip() for o in raw.split(",") if o.strip()]


# 服务端口:uvicorn 默认 8080,也可用 PORT 覆盖(与旧版 --server.port 语义一致)
PORT = _env_int("PORT", 8080)
# 监听地址:本地默认回环;Docker/服务器部署设 HOST=0.0.0.0
HOST = _env("HOST", "127.0.0.1") or "127.0.0.1"
