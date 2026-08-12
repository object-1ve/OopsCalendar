# 📅 美股财报日历 (OopsCalendar)

> 项目实际位置:`C:\object1ve\OopsCalendar\app`(原计划路径 `D:\0_project\OopsCalendar\app` 在本机不存在)。

前后端分离的美股财报日历 Web 应用:

- **前端**: React 19 + Vite 8 + TypeScript + pnpm
- **后端**: Java 8 + Spring Boot 2.7 (Maven)
- **数据源**: Financial Modeling Prep (FMP) 财报日历;未配置 key 时自动使用内置演示数据(mock),开箱可演示

核心功能:按月查看美股财报日期,清晰区分 **盘前 (BMO) / 盘后 (AMC) / 盘中 (DNH)**,并标注**已公布 / 未公布**状态。

## 项目结构

```
app/
├── backend/            # Spring Boot 后端 (端口 8080)
│   ├── src/main/java/com/oops/calendar/
│   │   ├── config/     # FMP 配置、CORS
│   │   ├── dto/        # EarningsEvent / Session / 响应体
│   │   ├── provider/   # FmpEarningsProvider(真实) / MockEarningsProvider(演示)
│   │   ├── service/    # 校验、缓存、节流
│   │   └── web/        # REST 控制器 + 全局异常处理
│   └── src/test/       # 41 个单元/接口测试
└── frontend/           # React + Vite 前端 (端口 5173)
    └── src/
        ├── api.ts              # /api 封装(经 vite 代理)
        ├── hooks/useEarnings.ts # 按月缓存 + 月份导航
        └── components/          # 日历网格 / 徽章 / 图例 / 详情弹窗
```

## 快速开始

环境要求:JDK 8+, Maven 3.6+, Node 18+, pnpm 8+

### 1. 启动后端

```bash
cd backend
mvn spring-boot:run        # 或 mvn package 后 java -jar target/earnings-calendar.jar
```

健康检查: <http://localhost:8080/api/health>

### 2. 启动前端

```bash
cd frontend
pnpm install
pnpm dev                   # http://localhost:5173
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

## 生产构建/部署

```bash
# 1. 后端:打 jar 并启动(监听 8080)
cd backend
mvn package                       # 产物: backend/target/earnings-calendar.jar
java -jar target/earnings-calendar.jar

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
    }
    ```

  - 或直接复用本地预览:后端 `java -jar target/earnings-calendar.jar` + 前端 `pnpm preview`。
- **端口一览**:后端 8080、前端开发 `pnpm dev` 5173、生产预览 `pnpm preview` 4173、示例静态站点 80。端口被占用时按"常见问题"排查。

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
| `GET /api/earnings?from=YYYY-MM-DD&to=YYYY-MM-DD` | 区间财报日历(≤120 天),按日期升序 |
| `GET /api/earnings/{symbol}?from=&to=` | 单只股票财报(默认今天前后各 30 天) |

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

## 数据说明与限流

- 免费 FMP 档位 250 次请求/天。后端已内置保护:
  - 按 `(from, to)` 内存缓存(默认 TTL 1 小时)
  - 上游调用最小间隔 1.5s
  - 单次查询区间上限 120 天
- 营收单位为百万美元(随数据源口径,展示时带单位)。
- 已公布判定:`eps` 或 `revenue` 任一非空即视为已公布。
- mock 模式数据为确定性生成:工作日 2-5 条/天,盘前 45% / 盘后 50% / 盘中 5%,约 30% 已公布,同一天内代码不重复。

## 常见问题

- **端口被占用(8080/5173/4173 已被占用)**:先确认是否已有本项目的后端/前端在运行,是则无需重复启动。排查:`netstat -ano | findstr :8080`(换成实际端口)查看占用进程的 PID,再用 `taskkill /PID <PID> /F` 结束;或改用隔离端口(后端 `java -jar target/earnings-calendar.jar --server.port=8081`,前端 `pnpm dev --port 5174`,并同步调整代理/反代目标)。`start-backend.cmd` 与 `start-frontend.cmd` 已内置端口占用检测:被占用时会打印中文提示并直接退出,不会出现裸堆栈。
- **前端打不开**:确认后端已在 8080 启动;若浏览器报连接失败,用 `http://127.0.0.1:5173` 访问(Windows 上 localhost 解析 IPv6 时 vite 默认绑定 `127.0.0.1`)。
- **想换数据源**:实现 `EarningsProvider` 接口并替换 `EarningsService` 中的装配即可,前端契约无需改动。
- **FMP API Key 无效或上游故障**:后端会自动降级到演示数据(mock),`/api/health` 会如实说明“FMP 上游请求失败(...),已回退到内置演示数据”,前端徽章显示“演示数据”并在 tooltip 中给出原因;日志只输出简短摘要,不会泄露 FMP 原始错误体。检查 `FMP_API_KEY` 是否正确、网络是否可达,修复后无需重启(冷却期 60 秒后自动重试恢复),详见上文“FMP 上游故障时的自动降级”。
