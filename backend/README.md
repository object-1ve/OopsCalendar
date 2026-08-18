# OopsCalendar 后端(Python)

FastAPI + uvicorn 实现的美股财报日历后端,API 契约与旧版 Java (Spring Boot) 完全一致。

## 运行

```bash
cd backend
python -m venv .venv                # 首次
.venv\Scripts\pip install -r requirements.txt   # 首次
.venv\Scripts\python main.py        # 监听 127.0.0.1:8080
```

或直接双击/调用 `..\start-backend.cmd`(自动建 venv、装依赖、检测端口)。

API Key 写入 `backend\.env.local`(每行 `KEY=VALUE`,`#` 为注释),启动时自动加载:

```ini
FINNHUB_API_KEY=你的finnhub_key
FMP_API_KEY=你的fmp_key
```

数据源优先级:**Finnhub > FMP > mock(演示)**。未配置 key 时使用内置确定性演示数据,开箱可演示;
上游失败自动降级到 mock,冷却期(默认 60s)后自动重试恢复。

## 目录结构

```
backend/
├── main.py               # 入口(uvicorn)
├── config.py             # 环境变量 + .env.local 配置
├── requirements.txt
├── run-backend.cmd
├── data/                 # SQLite(earnings.db)+ favorites.json(收藏公司)
└── app/
    ├── db.py             # SQLite 持久化(财报二级缓存/快讯偏好/快讯收藏)
    ├── errors.py         # 业务异常 + JSON 清理(去除 null 字段)
    ├── known_companies.py# 内置知名公司表(名称/中文名/行业)
    ├── http.py           # 公共异步 HTTP 客户端
    ├── earnings/         # 财报:FMP / Finnhub / Mock 源 + EarningsService
    ├── news/             # 快讯:8 个数据源 + 聚合 + SSE 实时推送
    ├── favorites.py      # 收藏公司(JSON 文件)
    ├── valuation.py      # 市盈率(PE)服务
    └── web.py            # FastAPI 路由 / CORS / 异常处理
```

## 接口

| 接口 | 说明 |
| --- | --- |
| `GET /api/health` | 服务状态与当前数据源(finnhub/fmp/mock) |
| `GET /api/earnings?from=YYYY-MM-DD&to=YYYY-MM-DD[&refresh=true]` | 区间财报日历(≤120 天) |
| `GET /api/earnings/{symbol}?from=&to=` | 单只股票财报(默认今天前后各 30 天) |
| `GET /api/news?sources=jin10,cls` | 财经快讯(缺省 = 全部) |
| `GET /api/news/sources` | 可用快讯数据源列表 |
| `GET /api/news/stream` | SSE 实时推送(每 15s 增量轮询) |
| `GET/PUT /api/news/preferences` | 快讯数据源偏好(全项目共享一份) |
| `GET/PUT /api/news/favorites` | 快讯收藏(全项目共享,整表替换) |
| `GET/PUT /api/favorites` | 收藏公司(全项目共享) |
| `GET /api/valuation?date=` | 当日财报公司的市盈率(仅知名公司) |

错误统一返回 `{"error","message","timestamp"}`。

## 测试

```bash
.venv\Scripts\python -m pytest tests -q          # 单元测试(假源/假 provider,不联网,46 项)
.venv\Scripts\python ..\..\scripts\smoke_test.py   # 端到端冒烟测试(需后端已启动)
.venv\Scripts\python ..\..\scripts\compare_mock.py # 校验 mock 数据与旧版一致(需旧版 Java 后端跑在 8081)
```

## 说明

- 旧版 Java 后端完整保留在 `../backend-java`,作为参考实现,不再参与运行。
- 持久化从 H2 换为 SQLite(`data/earnings.db`),语义一致:财报为二级缓存(仅真实数据源落库),
  快讯偏好/收藏全项目共享一份,重启不丢;首次启动自动迁移旧的按 clientId 分组的 `news-preferences.json`
  与旧版 SQLite 表(合并去重)。抓取到的快讯同样按 item_id 去重落库到 `news_item` 表(默认保留 30 天,
  `NEWS_RETENTION_DAYS` 可调),某数据源上游失败时自动用库内数据兜底。
- mock 演示数据与旧版完全一致(同一 Java Random 算法 + 同一种子公式,按日期确定性生成)。
