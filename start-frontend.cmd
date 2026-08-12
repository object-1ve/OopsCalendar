@echo off
rem 启动前端 (Vite dev server, 端口 5173, /api 代理到 8080)
rem 直接调用 vite 二进制,避免 pnpm 子进程在某些环境下无法拉起长驻进程

rem 检测 5173 端口是否已被占用
netstat -ano | findstr /R /C:":5173 .*LISTENING" >nul 2>&1
if %errorlevel%==0 goto :port_busy

cd /d %~dp0frontend
if not exist node_modules (
  echo [提示] 首次运行,正在安装依赖...
  call pnpm install
)
node node_modules\vite\bin\vite.js
exit /b %errorlevel%

:port_busy
echo [错误] 5173 端口已被占用,可能已有前端在运行,请先关闭或改用其他端口。
echo 排查: netstat -ano ^| findstr :5173 查看占用进程 PID,再用 taskkill /PID ^<PID^> /F 关闭。
exit /b 1
