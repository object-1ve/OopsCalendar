@echo off
rem 启动后端 (Python FastAPI + uvicorn, 端口 8080)
rem 真实数据 key 写入 backend\.env.local(FINNHUB_API_KEY / FMP_API_KEY, 每行 KEY=VALUE)

rem 检测 8080 端口是否已被占用
netstat -ano | findstr /R /C:":8080 .*LISTENING" >nul 2>&1
if %errorlevel%==0 goto :port_busy

cd /d %~dp0backend
call run-backend.cmd
exit /b %errorlevel%

:port_busy
echo [错误] 8080 端口已被占用,可能已有后端在运行,请先关闭或改用其他端口。
echo 排查: netstat -ano ^| findstr :8080 查看占用进程 PID,再用 taskkill /PID ^<PID^> /F 关闭。
exit /b 1
