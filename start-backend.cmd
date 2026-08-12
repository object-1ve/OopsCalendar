@echo off
rem 启动后端 (Spring Boot, 端口 8080)
rem 如需真实数据: 先 set FMP_API_KEY=你的key

rem 检测 8080 端口是否已被占用
netstat -ano | findstr /R /C:":8080 .*LISTENING" >nul 2>&1
if %errorlevel%==0 goto :port_busy

cd /d %~dp0backend
mvn spring-boot:run
exit /b %errorlevel%

:port_busy
echo [错误] 8080 端口已被占用,可能已有后端在运行,请先关闭或改用其他端口。
echo 排查: netstat -ano ^| findstr :8080 查看占用进程 PID,再用 taskkill /PID ^<PID^> /F 关闭。
exit /b 1
