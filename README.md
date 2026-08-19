# 📅 美股财报日历 (OopsCalendar)

> 项目实际位置:`C:\object1ve\OopsCalendar\app`(原计划路径 `D:\0_project\OopsCalendar\app` 在本机不存在)。

前后端分离的美股财报日历 Web 应用:

- **前端**: React 19 + Vite 8 + TypeScript + pnpm
- **后端**: Python 3 + FastAPI + uvicorn
- **数据源**:
  - 财报日历:Finnhub(优先)/ Financial Modeling Prep (FMP),未配置 key 时自动使用内置演示数据(mock),开箱可演示
  - 财经快讯(⚡ 快讯):金十数据 / 财联社 / 华尔街见闻 / 东方财富 / 同花顺 / 雪球 / 格隆汇 / 通达信,均为公开接口,无需 key

核心功能:
- 按月查看美股财报日期,清晰区分 **盘前 (BMO) / 盘后 (AMC) / 盘中 (DNH)**,并标注**已公布 / 未公布**状态。
- **URL 路由**:日历与快讯使用不同地址 —— `/`(或 `/calendar`)为财报日历,`/news` 为财经快讯;顶部「📅 日历 / ⚡ 快讯」切换,支持浏览器前进/后退与直接访问。
- **财经快讯**页聚合 8 个中文财经平台的实时快讯(金十、财联社、华尔街见闻、东方财富 7x24、同花顺、雪球热股、格隆汇公告、通达信资讯),按时间倒序合并,支持按源筛选与 60s 自动刷新。
- **收藏公司**:财报详情里点 ☆ 即可收藏(黄色标识),全项目共享一份,持久化到服务端(`backend/data/favorites.json`),同时缓存浏览器本地;换端口 / 清缓存 / 重启都不丢。
- **收藏快讯**:快讯页每条快讯右侧点 ☆ 即可收藏,「★ 收藏」页签集中查看;收藏时保存整条快讯快照并持久化到 SQLite(`backend/data/earnings.db`,全项目共享一份),即使快讯已从实时流滚动淘汰仍可完整展示,重启不丢。

## 项目结构

```
app/
├── backend/            # Python 后端 (FastAPI + uvicorn, 端口 8080)
│   ├── main.py         # 入口(uvicorn)
│   ├── config.py       # 配置(.env.local / 环境变量)
│   ├── app/
│   │   ├── earnings/   # 财报:FMP / Finnhub / Mock + 服务(缓存/降级/节流)
│   │   ├── news/       # 财经快讯:7 个数据源 + 聚合 + SSE(含财联社签名)
│   │   ├── db.py       # SQLite 持久化(财报二级缓存/快讯偏好/快讯收藏)
│   │   ├── favorites.py / valuation.py / known_companies.py
│   │   └── web.py      # FastAPI 路由 + CORS + 全局异常处理
│   └── data/           # SQLite(earnings.db)+ favorites.json
├── backend-java/       # 旧版 Java(Spring Boot)后端,保留作参考,不再运行
└── frontend/           # React + Vite 前端 (端口 5174)
    └── src/
        ├── api.ts              # /api 封装(经 vite 代理)
        ├── hooks/useEarnings.ts # 按月缓存 + 月份导航
        ├── components/          # 日历网格 / 徽章 / 图例 / 详情弹窗 / 快讯列表
        └── public/icons/        # 快讯源图标(金十/财联社/华尔街见闻/雪球/格隆汇)
```

## 快速开始

环境要求:Python 3.10+, Node 18+, pnpm 8+

### 0. 一键启动(推荐)

双击 `start-all.cmd`,会自动:

1. 在独立窗口启动**后端**(`start-backend.cmd`,Python FastAPI,端口 8080);
2. 在独立窗口启动**前端**(`start-frontend.cmd`,Vite dev server,端口 5174);
3. 等前端就绪后自动打开浏览器 `http://localhost:5174`。

停止服务:直接关闭对应的「OopsCalendar-Backend / Frontend」窗口即可。两个启动脚本都内置端口占用检测:端口被占用时会给出中文提示并退出,不会裸堆栈。

### 1. 启动后端

```bash
cd backend
run-backend.cmd            # 或 .venv\Scripts\python main.py(首次自动建 venv 并装依赖)
```

健康检查: <http://localhost:8080/api/health>

### 2. 启动前端

```bash
cd frontend
pnpm install
pnpm dev                   # http://localhost:5174
```

> 提示:若 `pnpm dev` 在你的环境下无法拉起长驻进程(子进程被回收),可直接运行
> `node node_modules\vite\bin\vite.js` 启动,效果相同;`start-frontend.cmd` 已默认使用该方式。

前端已配置 Vite 代理,`/api/*` 自动转发到 `http://localhost:8080`。

### 3. 使用真实美股财报数据(可选)

数据源优先级:**Finnhub > FMP > mock(演示)**。两个 key 都配置时自动用 Finnhub(免费档财报日历覆盖完整);都没配置时用内置演示数据。

**推荐:Finnhub**(免费注册 <https://finnhub.io>,财报日历覆盖完整,含 NBIS/Cerebras 等全部公司):

```bash
# Windows (PowerShell)
$env:FINNHUB_API_KEY="你的key"; mvn spring-boot:run
```

**持久化保存 key(推荐)**:把 key 写入 `backend/.env.local`(每行 `KEY=VALUE`,`#` 开头为注释),`start-backend.cmd` 会自动加载,重启后无需再手动设置环境变量。该文件已被 `.gitignore` 忽略,不会提交到版本库。例如:

```bash
# backend/.env.local
FINNHUB_API_KEY=你的finnhub_key
FMP_API_KEY=你的fmp_key   # 可选,同时配置时优先使用 Finnhub
```

> 也可用 `setx FINNHUB_API_KEY "你的key"` 写入 Windows 用户环境变量(全局生效,但需新开终端)。

**FMP**(免费注册 <https://site.financialmodelingprep.com>,免费档日历数据较稀疏):

```bash
# Windows (PowerShell)
$env:FMP_API_KEY="你的key"; mvn spring-boot:run
# 或在 backend/src/main/resources/application.yml 中填写 fmp.api-key
```

配置后重启,`/api/health` 的 provider 变为 `finnhub` / `fmp`。所有数据都带 `source` 字段,前端会显示数据来源。

### 上游故障时的自动降级

即使配置了 API key,如果上游不可用,后端会**自动降级到演示数据(mock 保底)**,保证应用始终可用:

- **触发条件**:上游连接失败 / 超时 / 返回 4xx、5xx / 响应解析失败 / 返回错误响应体(如 API Key 无效)。
- **行为**:该请求回退 `MockEarningsProvider` 返回演示数据(`/api/earnings` 仍为 200,`source=mock`),区间与单股接口行为一致;后端记录降级状态,日志只记录简短摘要,不会输出上游原始响应体。
- **健康检查**:降级时 `/api/health` 返回 `{"status":"UP","provider":"mock","message":"财报数据源请求失败(...),已回退到内置演示数据,冷却期后将自动重试恢复。请检查 API Key 与网络。"}`,前端徽章会显示"演示数据"并将该说明作为 tooltip。
- **自动恢复**:降级后进入冷却期(默认 60 秒,配置项 `fmp.degraded-retry-ms`),冷却期结束后下个请求会自动重试一次 FMP,成功则切回真实数据并退出降级态。
- **启动探测**:应用启动时会用 `FMP_API_KEY` 做一次轻量探测(查当天数据),失败立即进入降级态,避免 `/api/health` 谎报"已连接"。

> 提示:免费 FMP 档位 250 次/天,启动探测每次重启消耗 1 次;若网络完全不可达,启动最多额外等待连接超时时间(默认 8 秒)。

## Docker 部署(推荐上服务器)

浏览器只访问 **80 端口**。推荐在本机构建镜像后传到服务器运行,服务器不需要源码、也不需要 `docker compose build`。

### 1. 服务器准备

- Linux(推荐 Ubuntu 22.04+)+ Docker Engine + Compose v2
- 安全组放行 **80** 与 SSH

```bash
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER   # 重新登录后生效
```

### 2. 本机构建镜像并导出(推荐)

在 `app/` 目录:

```powershell
cd C:\object1ve\OopsCalendar\app
cp .env.example .env   # 已有 .env 可跳过
docker compose build
docker save oops-calendar-backend:latest oops-calendar-web:latest -o oops-calendar-images.tar
```

传到服务器(只需这三样:`images.tar`、`docker-compose.prod.yml`、`.env`):

```powershell
scp oops-calendar-images.tar docker-compose.prod.yml .env user@服务器IP:~/oops-calendar/
```

### 3. 服务器加载镜像并启动

```bash
cd ~/oops-calendar
docker load -i oops-calendar-images.tar
docker compose -f docker-compose.prod.yml up -d
docker compose -f docker-compose.prod.yml ps
curl http://127.0.0.1/api/health
```

浏览器打开 `http://<服务器公网IP>/`。

80 被占用时改 `.env` 的 `HTTP_PORT=8088`,安全组同步放行。

更新:本机重新 `build` + `save`,服务器再 `docker load` 后 `docker compose -f docker-compose.prod.yml up -d`。

### 4. 可选:推到镜像仓库再 pull

有 Docker Hub / 阿里云 ACR 时,把 `.env` 改成仓库地址再构建推送:

```env
BACKEND_IMAGE=你的仓库/oops-calendar-backend:latest
WEB_IMAGE=你的仓库/oops-calendar-web:latest
```

```powershell
docker compose build
docker compose push
```

服务器同一份 `.env` + `docker-compose.prod.yml`:

```bash
docker login
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
```

国内直连 Docker Hub 常超时,优先用上面的 `save`/`load`,或推到阿里云 ACR。

### 4b. GitHub Actions 自动构建到 GHCR(推荐)

仓库已带 `.github/workflows/docker-publish.yml`。推送到 `master` 分支(或打 `v*.*` 标签,或 Actions 页面手动 Run workflow)时,CI 自动把前端(Nginx)和 Python 后端(FastAPI)打成镜像并推送到 GitHub Container Registry(GHCR),无需手动 build。

镜像地址(以 GHCR 用户命名空间,不是仓库路径):

- `ghcr.io/object-1ve/oops-calendar-frontend`(Nginx + 静态前端)
- `ghcr.io/object-1ve/oops-calendar-backend`(Python FastAPI)

每个镜像带 `latest`(默认分支)、`master`、`sha-<短哈希>` 标签;打 `v*.*.*` 标签(如 `v1.2.3`)时还会生成干净的语义版本标签 `1.2.3`、`1.2`、`1`。

**首次使用**:在 GitHub 右上角头像 → Your packages 里把 `oops-calendar-frontend` 和 `oops-calendar-backend` 设为 **public**(仓库是公开的,建议直接公开;不公开则服务器 pull 需用 PAT 登录)。GHCR 包默认私有,设成 public 后服务器即可免登录 pull。

服务器用 `docker-compose.prod.yml` 直接 pull 运行(镜像名已在 compose 里写死,想换仓库时用 `.env` 的 `BACKEND_IMAGE` / `WEB_IMAGE` 覆盖):

```bash
# 包已设为 public:免登录直接 pull;否则先登录(用 GitHub PAT,需 read:packages 权限)
echo $GHCR_PAT | docker login ghcr.io -u object-1ve --password-stdin
cd ~/oops-calendar
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
curl http://127.0.0.1/api/health
```

**固定到某个版本号**(不跟随 `latest`,适合生产锁定版本):在服务器 `.env` 里用 `BACKEND_IMAGE` / `WEB_IMAGE` 指定带版本号的标签,再 pull。

```bash
# ~/oops-calendar/.env
BACKEND_IMAGE=ghcr.io/object-1ve/oops-calendar-backend:1.2.3
WEB_IMAGE=ghcr.io/object-1ve/oops-calendar-frontend:1.2.3
```

```bash
cd ~/oops-calendar
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
```

> 默认(不设 `BACKEND_IMAGE`/`WEB_IMAGE`)时拉取 `latest`,每次推送自动更新。若 pull 到旧缓存,加 `--force-recreate` 重新 `up -d`。

### 5. 日常运维

```bash
docker compose -f docker-compose.prod.yml logs -f
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml down          # 保留数据卷
docker compose -f docker-compose.prod.yml down -v      # 清空收藏/SQLite
```

要点:

- 数据在 named volume `oops_calendar_data`,换镜像不丢收藏。
- 后端只在容器网络听 `8080`,不映射到宿主机。
- 不要把含 Key 的 `.env` 提交到 git。
- 本机开发仍可用带 `build` 的 `docker compose up -d --build`。

### 备选:在服务器上用源码构建

没有导出镜像时,把整个 `app/` 拷到服务器后 `docker compose up -d --build`(需能拉取基础镜像;国内用 `.env` 里 `DOCKER_HUB=docker.m.daocloud.io`)。

## 生产构建/部署(不使用 Docker)

```bash
# 1. 后端:直接启动(监听 8080;对外网卡请设 HOST=0.0.0.0)
cd backend
run-backend.cmd                   # 直接启动(监听 8080),无编译步骤

# 2. 前端:构建静态产物并本地预览(监听 4173)
cd frontend
pnpm install
pnpm build                        # 产物: frontend/dist(纯静态文件)
pnpm preview                      # 本地验证生产构建: http://127.0.0.1:4173
```

要点:

- **生产可服务性**:`vite preview` 与开发态 `server` 使用同一份 `/api` 代理配置(`preview.proxy` 镜像 `server.proxy`),所以 `dist` 产物在预览或任何静态托管下,`/api/*` 都能转发到 `http://localhost:8080` 正常取数,无需修改前端代码。若后端不在 8080(如隔离端口联调),可用环境变量覆盖:`VITE_API_TARGET=http://localhost:8091 pnpm preview`(对 `pnpm dev` 同样生效)。
- **正式部署**(二选一):
  - 一体化:把 `frontend/dist` 交给任意静态服务器(Nginx / Caddy / CDN 等),并将 `/api` 反向代理到后端 8080。Nginx 示例:

    ```nginx
    server {
      listen 80;
      root /path/to/frontend/dist;
      location /api/ {
        proxy_pass http://127.0.0.1:8080;
      }
      # 前端 history 路由(/)与(/news):未命中静态文件时回退到 index.html
      location / {
        try_files $uri $uri/ /index.html;
      }
    }
    ```

  - 或直接复用本地预览:后端 `run-backend.cmd` + 前端 `pnpm preview`。
- **端口一览**:后端 8080、前端开发 `pnpm dev` 5174、生产预览 `pnpm preview` 4173、示例静态站点 80。端口被占用时按"常见问题"排查。

## 配色与状态说明

| 徽章 | 含义 |
| --- | --- |
| 蓝色 **盘前** | BMO,开盘前发布 |
| 紫色 **盘后** | AMC,收盘后发布 |
| 灰色 **盘中** | DNH,交易时段内发布 |
| 实心 + ✓ | **已公布**(有实际 EPS/营收) |
| 空心虚线 + · | **未公布**(仅市场预估) |

点击任意有财报的日期,弹出当日全部财报详情:公司名、盘前/盘后、已/未公布、EPS 实际/预估、营收实际/预估、数据源。

## 后端 API

| 接口 | 说明 |
| --- | --- |
| `GET /api/health` | 服务状态与当前数据源(fmp/mock) |
| `GET /api/earnings?from=YYYY-MM-DD&to=YYYY-MM-DD[&refresh=true]` | 区间财报日历(≤120 天),按日期升序;`refresh=true` 绕过缓存强制拉取上游(用于"单独刷新某一天") |
| `GET /api/earnings/{symbol}?from=&to=` | 单只股票财报(默认今天前后各 30 天) |
| `GET /api/news?sources=jin10,cls` | 财经快讯(缺省 = 全部 8 个源),按时间倒序 |
| `GET /api/news/sources` | 可用快讯数据源列表 |
| `GET /api/news/stream` | SSE 实时推送:后端每 15s 增量轮询,只推新增条目 |
| `GET /api/news/preferences` | 读取数据源偏好(全项目共享一份) |
| `PUT /api/news/preferences` | 保存数据源偏好(`{sources}`),持久化到 SQLite 数据库 |
| `GET /api/news/favorites` | 读取快讯收藏(整条快讯快照,按收藏时间倒序;未收藏过返回 `configured:false`) |
| `PUT /api/news/favorites` | 保存快讯收藏(`{items}`),快照持久化到 SQLite 数据库(空列表 = 清空) |

返回示例:

```json
{
  "from": "2026-08-01", "to": "2026-08-31", "count": 76, "source": "mock",
  "events": [{
    "date": "2026-08-21", "symbol": "AAPL", "name": "Apple Inc.",
    "session": "BMO", "confirmed": true,
    "eps": 3.53, "epsEstimated": 3.81,
    "revenue": 6807, "revenueEstimated": 6364, "source": "mock"
  }]
}
```

- `session`: `BMO`(盘前) / `AMC`(盘后) / `DNH`(盘中) / `UNKNOWN`(待定)
- `confirmed`: `true` = 已公布(实际 EPS/营收已发布),`false` = 未公布
- 错误统一返回 `{"error","message","timestamp"}`,参数非法为 400

## 财经快讯(⚡ 快讯)

点击页头「⚡ 快讯」进入快讯页,聚合 8 个中文财经平台的最新消息,按时间倒序合并展示:

| key | 平台 | 内容 | 实现 |
| --- | --- | --- | --- |
| `jin10` | 金十数据 | 7x24 快讯(含重要 ★) | JSON(`flash_newest.js`) |
| `cls` | 财联社 | 电报(telegraph) | JSON + MD5(SHA1) 签名,参考 RSSHub |
| `wallstreetcn` | 华尔街见闻 | 7x24 快讯 | JSON |
| `eastmoney` | 东方财富 | 7x24 快讯 | JSON(微秒时间戳已转毫秒) |
| `tonghuashun` | 同花顺 | 股票/财经快讯 | JSON(`news.10jqka.com.cn`) |
| `xueqiu` | 雪球 | 热股榜(现价/涨跌幅) | JSON(先取 cookie) |
| `gelonghui` | 格隆汇 | A股/港股公告摘要 | HTML 解析(jsoup) |
| `tdx` | 通达信 | 资讯中心聚合快讯 | JSON(TQL 协议,POST 取数) |

要点:

- 前端支持按源筛选、**SSE 实时推送**(页面显示「● 实时」,新快讯约 15 秒内自动到达,无需刷新;断线由浏览器 EventSource 自动重连)、来源图标/色标、相对时间(刚刚 / X 分钟前 / X 小时前)。
- **数据源配置持久化(后端数据库)**:数据源开关以服务端 SQLite 数据库为准(`news_preference` 表,全项目共享一份),每次勾选即写入数据库,换浏览器/换设备/清缓存 / 重启都能恢复。浏览器 localStorage 仅作为后端不可达时的回退缓存,不再作为权威存储;首次升级启动时后端会自动把旧的按 clientId 分组的 `news-preferences.json` 合并导入数据库,之后不再读写该文件。
- **收藏持久化(服务端)**:收藏公司同样存到 `backend/data/favorites.json`(全项目共享一份,本地 localStorage 即时生效,服务端兜底),重启 / 清缓存 / 换端口都能恢复;服务端有存档时以服务端为准,删除操作也能跨会话同步。
- **收藏快讯**:每条快讯右侧点 ☆ 收藏 / 取消收藏,顶部「★ 收藏」页签集中查看(最近收藏的在前)。收藏持久化到 SQLite(`news_favorite` 表,快照保存标题/链接/摘要/来源/时间,全项目共享一份),本地 localStorage 即时生效、服务端兜底;服务端有存档时以服务端为准。由于保存的是完整快照,即使快讯已从实时流(上限 200 条)滚动淘汰,收藏列表仍能正常展示和打开原文。
- 实时实现:后端 `NewsStreamService` 每 15 秒(`news.poll-ms`)增量轮询各源,仅向 `/api/news/stream` 订阅者推送新增条目;`/api/news` 仍保留按源缓存(默认 60 秒)供初次加载。
- **限流**:上游均为公开接口,无 key、无配额;后端缓存全局共享,轮询每源仅 4 次/分钟(合计 32 次/分钟),远低于风控阈值。
- 后端按源缓存(默认 60 秒,`news.cache-ttl-seconds`),单源失败自动降级不影响其他源;合并后默认最多返回 200 条(`news.max-items`)。
- **快讯落库(去重)**:每次抓取到的快讯按 `item_id` 去重写入 SQLite(`news_item` 表),默认保留 30 天(`NEWS_RETENTION_DAYS` 可调);某数据源上游失败时,自动用库内该源的最近快讯兜底,列表不会骤然变空。
- **前端搜索与无限滚动**:快讯页支持按标题 / 摘要 / 来源关键词搜索(实时过滤),列表底部自动加载更多,滚动即可翻页。
- 实现新源:在 `app/news/sources.py` 实现一个 source 类并加入 `all_sources()` 即可,无需改路由。
- 快讯接口全部为公开接口,无需 API Key;若个别上游被风控,该源会短暂降级并自动恢复。
- 生产部署若用 Nginx 反代,SSE 需关闭缓冲:`proxy_buffering off;`(否则实时推送会被缓冲延迟)。
- 测试环境默认关闭轮询(`src/test/resources/application.yml` 中 `news.enabled: false`)。

## 数据说明与限流

- 免费 FMP 档位 250 次请求/天。后端已内置保护:
  - 按 `(from, to)` 内存缓存(默认 TTL 1 小时)
  - 上游调用最小间隔 1.5s
  - 单次查询区间上限 120 天
- **Finnhub 单次最多返回 1500 条**:整月拉取会被截断(丢最旧,如 8 月上旬缺失)。后端已自动按 3 天窗口并行分段拉取再合并,保证整月完整;窗口仍达上限时按天补查。
- 营收单位为百万美元(随数据源口径,展示时带单位)。
- 已公布判定:`eps` 或 `revenue` 任一非空即视为已公布。
- mock 模式数据为确定性生成:工作日 2-5 条/天,盘前 45% / 盘后 50% / 盘中 5%,约 30% 已公布,同一天内代码不重复。

## 常见问题

- **端口被占用(8080/5174/4173 已被占用)**:先确认是否已有本项目的后端/前端在运行,是则无需重复启动。排查:`netstat -ano | findstr :8080`(换成实际端口)查看占用进程的 PID,再用 `taskkill /PID <PID> /F` 结束;或改用隔离端口(后端 `java -jar target/earnings-calendar.jar --server.port=8081`,前端 `pnpm dev --port 5174`,并同步调整代理/反代目标)。`start-backend.cmd` 与 `start-frontend.cmd` 已内置端口占用检测:被占用时会打印中文提示并直接退出,不会出现裸堆栈。
- **前端打不开**:确认后端已在 8080 启动;若浏览器报连接失败,用 `http://127.0.0.1:5174` 访问(Windows 上 localhost 解析 IPv6 时 vite 默认绑定 `127.0.0.1`)。
- **想换数据源**:在 `app/earnings/providers.py` 新增 Provider 并调整 `EarningsService` 的数据源优先级即可,前端契约无需改动。
- **FMP API Key 无效或上游故障**:后端会自动降级到演示数据(mock),`/api/health` 会如实说明“FMP 上游请求失败(...),已回退到内置演示数据”,前端徽章显示“演示数据”并在 tooltip 中给出原因;日志只输出简短摘要,不会泄露 FMP 原始错误体。检查 `FMP_API_KEY` 是否正确、网络是否可达,修复后无需重启(冷却期 60 秒后自动重试恢复),详见上文“FMP 上游故障时的自动降级”。
