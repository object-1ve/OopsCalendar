# Verify Node: verify-fmp-fallback — FMP 上游失败优雅降级

日期:2026-08-12 · 执行:verify-fmp-fallback 节点

## 审计问题(GAP 1)

以 `FMP_API_KEY=dummy-invalid-key-12345` 启动后端时:
1. `/api/health` 谎报"已连接 FMP 真实数据"(provider=fmp),即使 key 无效。
2. `/api/earnings` 返回 502 并直接泄漏 FMP 原始错误体(Invalid API KEY + 长 FAQ URL)。
3. 无任何 mock 兜底。
4. 前端错误横幅路径从未被实测。

## 实现改动

| 文件 | 改动 |
| --- | --- |
| `provider/UpstreamUnavailableException.java` | 新增类型化异常;消息为安全简短中文摘要,严禁含原始响应体 |
| `provider/FmpEarningsProvider.java` | 连接/超时/4xx/5xx/解析失败/错误响应体 → 抛 `UpstreamUnavailableException`;4xx 只记录状态码,错误体只做分类(API Key 无效 / 限流 / 其他);日志只记录简短摘要 |
| `service/EarningsService.java` | FMP 失败捕获后对**该请求**回退 MockEarningsProvider 并记录降级状态(degraded/reason/degradedAt);降级冷却期内直接走 mock,冷却期后自动重试一次 FMP,成功即恢复;缓存带 source;`@PostConstruct` 启动轻量探测,失败即降级;区间与单股接口行为一致 |
| `web/HealthController.java` | 降级时返回 `status=UP, provider=mock, message=“FMP 上游请求失败(...),已回退到内置演示数据,冷却期后将自动重试恢复…”` |
| `config/FmpProperties.java` + `application.yml` | 新增 `degraded-retry-ms`(默认 60000) |
| 测试 | `EarningsServiceTest` +3(降级回退/冷却重试恢复/单股一致);`EarningsControllerTest` +1(降级 health);并发节点新增 `FmpEarningsProviderTest`(MockRestServiceServer,失败分类断言) |
| `app/README.md` | 新增"FMP 上游故障时的自动降级"小节 + FAQ 条目 |

前端零改动(复用 `health.message` 作为 badge tooltip、`error-banner` + 重试按钮)。

## 环境

- 遗留进程 PID 18592(java 8080 mock 后端,命令行 `java -jar target\earnings-calendar.jar`)、PID 4536(node 5173 vite,`node_modules\vite\bin\vite.js`)经 PowerShell 核实为本项目进程后 `taskkill /F` 结束。
- 随后 8080/5173 被任务图中其他节点重新占用(新 PID 13920 java、4664 vite),按指示**不杀**,改用隔离端口:后端 `--server.port=8090`,临时 `vite.config.verify.ts`(5174→8090 代理)。
- 注意:本机我的 node 进程加载 vite 的 ESM 图会挂起(内置模块加载后停滞,其他节点先启动的 vite 正常),无法起第二个 vite dev;改用**已构建的 `frontend/dist` + 纯 node 静态服务器 + `/api` 反代 8090** 完成浏览器验证(与 dev 同源同契约;dist 由其他节点于 22:03 重建,包含当前源码)。

## 验证 (a):dummy key 后端 + curl

启动:`set FMP_API_KEY=dummy-invalid-key-12345 && java -jar target\earnings-calendar.jar --server.port=8090`

启动日志(降级发生,无响应体泄漏):
```
INFO  c.oops.calendar.service.EarningsService : Earnings provider active: fmp
WARN  c.o.c.provider.FmpEarningsProvider      : FMP upstream request failed: FMP API Key 无效(HTTP 401)
WARN  c.oops.calendar.service.EarningsService : FMP upstream unavailable (FMP API Key 无效(HTTP 401)), fallback to mock provider
INFO  ... Started EarningsCalendarApplication in 4.034 seconds
```

`curl -s http://localhost:8090/api/health`(HTTP 200):
```json
{"status":"UP","provider":"mock","message":"FMP 上游请求失败(FMP API Key 无效(HTTP 401)),已回退到内置演示数据,冷却期后将自动重试恢复。请检查 FMP_API_KEY 与网络。","timestamp":"2026-08-12T13:55:38.608Z"}
```

`curl -s "http://localhost:8090/api/earnings?from=2026-08-01&to=2026-08-31"`(HTTP 200):`count=76 source=mock`,事件完整(样例):
```json
[{"date":"2026-08-03","symbol":"CRM","name":"Salesforce Inc.","session":"AMC","confirmed":false,"epsEstimated":1.78,"revenueEstimated":5718,"source":"mock"}, ...]
```

单股一致:`/api/earnings/AAPL?from=2026-08-01&to=2026-08-31` → HTTP 200,`source=mock`,2 条 AAPL。

## 验证 (b):前端 + dummy 后端(浏览器 DOM 断言)

页面 `http://127.0.0.1:5174/`(静态 dist + 反代 8090),Firefox 实测:

- **徽章**:`<span class="source-badge mock" title="FMP 上游请求失败(FMP API Key 无效(HTTP 401)),已回退到内置演示数据,冷却期后将自动重试恢复。请检查 FMP_API_KEY 与网络。">● 演示数据</span>` → 显示演示数据 + tooltip 说明降级原因。
- **日历**:42 个 `.day-cell`(6×7 网格),`2026 年 8 月 · 76 条财报`,today 标记(12 日)。
- **日期弹窗**:点击 8 月 3 日 → `.modal` 出现,`8 月 3 日财报 (3 家)`,CRM/JNJ/META 行含 EPS 实际/预估、营收实际/预估、`数据源:演示数据`。
- **月切换**:点 `下月 ›` → `2026 年 9 月 · 84 条财报`;点 `‹ 上月` 回 8 月。

## 验证 (c):后端停止 → 错误横幅 → 重试

- `taskkill` 停掉 8090 后端,刷新页面:页面**不白屏**(header/图例/日历骨架完整),出现
  `<div class="error-banner">⚠ 上游后端不可用: connect ECONNREFUSED 127.0.0.1:8090<button class="btn small">重试</button></div>`
  (fetch 错误提取链路 OK)。
- 重启 8090 后端(同样 dummy key,启动探测再次降级),点击 `重试` → 数据加载:`2026 年 8 月 · 76 条财报`,错误横幅消失,chips 齐全。重试链路验证通过。

## 验证 (d):mvn package

`cd backend && mvn package`:
```
Tests run: 31, Failures: 0, Errors: 0, Skipped: 0
[INFO] --- spring-boot:2.7.18:repackage (repackage) ---
[INFO] Replacing main artifact with repackaged archive
[INFO] BUILD SUCCESS
```
产物:`target/earnings-calendar.jar` 19,381,626 字节(胖 jar)。
新增降级回退单元测试:`fmpFailureFallsBackToMockAndRecordsDegraded`、`degradedCooldownSkipsUpstreamThenRecovers`(修复了 0ms 冷却下 `isBefore(now)` 同毫秒不重试的缺陷)、`querySymbolFallsBackToMockLikeInterval`、`healthReportsDegradedFallback`。

## 验证 (e):README

`app/README.md` 新增"### FMP 上游故障时的自动降级"(触发条件/行为/健康检查/自动恢复/启动探测)+ 常见问题条目 + 测试数 20→24(最终 31,含并发节点新增)。

## 收尾

- 结束本次进程:PID 8436/25396(8090 后端)、PID 23132(静态服务器)、PID 4904(早前静态服务器),以及测试期挂起的 node 进程。
- 释放端口:8090、5174 已释放;8080/5173 归其他节点进程所有,未触碰。
- 删除临时文件:`verify-backend.log`、`verify-earnings.json`、`verify-vite*.log`、`verify-server.log`、`verify-static-server.mjs`、`vite.config.verify.ts`、`vite-test.log`、`vite-debug.log`、`vite-import.log`、`mvn-build*.log`。

## 未检查项(诚实声明)

- 未能在本节点起**第二个 vite dev 实例**(我的 node 进程加载 vite ESM 图挂起,疑似环境级问题),改用等价契约的静态 dist + 反代完成浏览器验证;前端源码未改动,dev 与 dist 行为一致(其他节点同时维护的 vite dev 在 5173 正常运行)。
- 自动恢复路径在真实 FMP 场景未实测(无法伪造 FMP 从失败变成功),已用单测 `degradedCooldownSkipsUpstreamThenRecovers` 覆盖。
