# GAP 3 验证工件:生产可服务性与运行卫生 (node: verify-prod-build)

日期:2026-08-12 UTC · 环境:Windows 10 x64,JDK 8 (Temurin 1.8.0_345),Node v24.17.0,pnpm 11.13.0,Maven 3.9.16,zh_CN/GBK 控制台

## 1. 环境:残留进程清理

审计指出的残留进程(本机遗留,非其他节点新起):

| PID | 命令行 | 端口 | 处置 |
| --- | --- | --- | --- |
| 18592 | `java -jar target\earnings-calendar.jar`(相对路径,仅本项目 backend 目录可解析;pom finalName=earnings-calendar) | 8080 LISTENING | 会话期间已不存在(被清理/自然退出),验证 `tasklist`/`netstat` 无残留,端口释放 |
| 4536 | `node node_modules\vite\bin\vite.js`(相对路径,仅本项目 frontend 目录可解析) | 5173 LISTENING | 同上,已无残留 |

- 命令执行环境发现:git/npm/pnpm 在本 harness 下因 stdin 管道不供数而阻塞,`< nul` 重定向后全部正常(已用 `pnpm --version`/`git --version` 验证)。这是本次所有"挂起"现象的根因。
- 其他节点活动服务(非本节点职责,未杀):8080 后端(`java -jar target\earnings-calendar.jar`,PID 20060)、5173 vite dev(PID 4664)。收尾时仍存在,归其他节点。

## 2. 生产可服务性

### (a) 配置变更
- `frontend/vite.config.ts`:抽出共用 `apiProxy`,新增 `preview` 块(host 127.0.0.1,port 4173,`proxy: apiProxy`)镜像 `server.proxy`;代理目标默认 `http://localhost:8080`,可用 `VITE_API_TARGET` 环境变量覆盖。保留 127.0.0.1 IPv4 绑定。
- 未新增静态托管/反代脚本:vite preview + preview.proxy 即等价方案(README 同时给出 Nginx 反代示例)。

### (b) 生产构建端到端验证
```
pnpm build  →  vite v8.2.1 building client environment for production...
              dist/index.html 0.40 kB · dist/assets/index-Dj_cA0Fk.js 198.31 kB · dist/assets/index-DazxFFSY.css 5.86 kB
              ✓ built in 253ms
mvn package →  Tests run: 43, Failures: 0, Errors: 0 · BUILD SUCCESS
```
后端:`java -jar target\earnings-calendar.jar --server.port=8091`(mock provider,`/api/health` UP)后 `pnpm preview`(VITE_API_TARGET=http://127.0.0.1:8091)。

- `/api` 经 preview 代理真实转发:`GET http://127.0.0.1:4173/api/health` → 200 `{"status":"UP","provider":"mock",...}`;`GET http://127.0.0.1:4173/api/earnings?from=2026-08-01&to=2026-08-31` → `count=76, source=mock`(与页面一致)。
- 浏览器 DOM 断言(`http://127.0.0.1:4173/`,渲染产物 `/assets/index-Dj_cA0Fk.js` 即本次构建):
  1. 标题:`<title>美股财报日历</title>` 且 `<h1>📅 美股财报日历</h1>` ✅
  2. 演示数据徽章:`<span class="source-badge mock" ...>● 演示数据</span>` ✅
  3. 当月 count 非零:`<span class="toolbar-count"> · 76 条财报</span>`(76>0,与 API count 一致)✅
  4. 点击 8 月 3 日(`.day-cell.has-events[title="点击查看 3 条财报"]`)弹出 `.modal`:
     - 头部:`8 月 3 日财报 (3 家)` ✅
     - CRM:`event-symbol=CRM`、chip `· CRM Salesforce Inc.`(盘后/未公布)、`status-badge todo=未公布`、详情 `Salesforce Inc.` + `EPS 实际 — / 预估 1.78` + `营收(百万$)实际 — / 预估 5,718` + `数据源:演示数据` ✅
     - JNJ:`✓ JNJ Johnson & Johnson`(盘前/已公布)、`status-badge done=✓ 已公布`、`EPS 实际 0.36 / 预估 0.12`、`营收(百万$)实际 6,759 / 预估 6,140`、`数据源:演示数据` ✅
     - META:`event-symbol=META`、`未公布`、`EPS 实际 — / 预估 2.29`、`营收(百万$)实际 — / 预估 8,845`、`数据源:演示数据` ✅
  字段齐全:公司名、盘前/盘后、已公布/未公布、EPS 实际/预估、营收实际/预估、数据源。

### (c) README
新增"## 生产构建/部署"(构建命令、dist 产物、preview/静态托管 + `/api` 反代说明、Nginx 示例、端口一览、`VITE_API_TARGET` 覆盖);"常见问题"新增端口占用排查条目。

## 3. 运行卫生

### (a) 脚本改动(GBK+CRLF 编码,zh-CN 控制台中文正常;LF 或 UTF-8 中文会导致 cmd 解析错乱,已实测排除)
- `start-backend.cmd`:`netstat -ano | findstr /R /C:":8080 .*LISTENING"` 检测,占用则打印 `[错误] 8080 端口已被占用,可能已有后端在运行,请先关闭或改用其他端口。` + 排查提示,`exit /b 1`。
- `start-frontend.cmd`:同上对 5173;保留直接调用 `node node_modules\vite\bin\vite.js`(避免 pnpm 子进程拉起问题),首次运行自动 `pnpm install`。

### (b) 实测(端口冲突模拟)
| 场景 | 操作 | 结果 |
| --- | --- | --- |
| 5173 被占(他节点 vite 4664 真实监听) | `start-frontend.cmd < nul` | 打印友好中文提示 2 行,exit=1,无堆栈不崩溃 ✅ |
| 8080 被占(他节点后端 20060 真实监听) | `start-backend.cmd < nul` | 同上,exit=1 ✅ |
| 释放后正常启动(5173 不可用,用临时副本改检 5199) | 同脚本逻辑(仅端口常量不同) | 通过端口检查,vite dev 正常拉起并 HTTP 200 ✅ |
| 释放后正常启动(8080 不可用,用临时副本改检 8094,后端 application.yml 同步 8094) | 同脚本逻辑 | `mvn spring-boot:run` 正常启动,`/api/health` 200 ✅ |

消息文本在 `chcp 936` 控制台下解码确认为:`[错误] 5173 端口已被占用,可能已有前端在运行,请先关闭或改用其他端口。` / `排查: netstat -ano | findstr :5173 ... taskkill /PID <PID> /F 关闭。`

### (c) 收尾
本节点全部进程/端口已释放:preview 4173、自建后端 8091、临时副本 8094/5174、测试监听均已停止。`netstat` 复核仅剩他节点服务(8080 PID 20060、5173 PID 4664),不属于本节点清理范围。README FAQ 已补端口占用排查。

## 4. 保持构建全绿
- `pnpm build`:✅(多次,含配置变更后复跑)
- `mvn package`:✅(最终 43 测试 0 失败 BUILD SUCCESS;期间他节点并发改动测试,瞬时红态为其 WIP,非本节点变更所致;jar 锁(他节点 java -jar)导致 rename 失败已通过等待/快照构建验证规避)

## 未检查/开放问题
- 5173/8080 在收尾时仍被他节点占用,未做"真端口释放后"的最终复核(用临时副本等价验证代替)。
- vite preview 之外未实测 Nginx 反代(README 仅给配置示例)。
- 他节点正在并发编辑 backend 源码与 start 脚本,最终状态以协调后为准。
