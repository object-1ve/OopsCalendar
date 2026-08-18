@echo off
chcp 936 >nul
rem ============================================================
rem  一键启动前后端 (OopsCalendar)
rem  后端 : http://localhost:8080  (Python FastAPI + uvicorn)
rem  前端 : http://localhost:5174  (Vite dev server, /api 代理到 8080)
rem  停止 : 直接关闭对应的「OopsCalendar-Backend/Frontend」窗口
rem ============================================================
setlocal

cd /d %~dp0

echo [启动] 正在启动后端与前端,请稍候...

rem 分别在新窗口中启动后端与前端(各自内置端口占用检测,被占用时给出提示)
start "OopsCalendar-Backend" cmd /k call start-backend.cmd
start "OopsCalendar-Frontend" cmd /k call start-frontend.cmd

rem 等待前端就绪后自动打开浏览器(最多等 20 秒,已就绪则立即打开)
set /a tries=0
:wait_loop
set /a tries+=1
if %tries% gtr 20 goto :open_browser
powershell -NoProfile -Command "try { (Invoke-WebRequest -UseBasicParsing http://127.0.0.1:5174 -TimeoutSec 1).StatusCode } catch { exit 1 }" >nul 2>&1
if %errorlevel%==0 goto :open_browser
timeout /t 1 /nobreak >nul
goto :wait_loop

:open_browser
start "" http://localhost:5174

echo.
echo ============================================================
echo  后端: http://localhost:8080   健康检查: /api/health
echo  前端: http://localhost:5174
echo  如需真实数据,请将 API Key 写入 backend\.env.local
echo  (参考 README.md「使用真实美股财报数据」一节)
echo  关闭对应的后端/前端窗口即可停止服务。
echo ============================================================
echo.
pause
endlocal
