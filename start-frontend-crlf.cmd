@echo off
rem 鍚姩鍓嶇 (Vite dev server, 绔彛 5173, /api 浠ｇ悊鍒?8080)

rem 妫€娴?5173 绔彛鏄惁宸茶鍗犵敤
netstat -ano | findstr /R /C:":5173 .*LISTENING" >nul 2>&1
if %errorlevel%==0 goto :port_busy

cd /d %~dp0frontend
pnpm install
pnpm dev
exit /b %errorlevel%

:port_busy
echo [閿欒] 5173 绔彛宸茶鍗犵敤,鍙兘宸叉湁鍓嶇鍦ㄨ繍琛?璇峰厛鍏抽棴鎴栨敼鐢ㄥ叾浠栫鍙ｃ€?echo 鎺掓煡: netstat -ano ^| findstr :5173 鏌ョ湅鍗犵敤杩涚▼ PID,鍐嶇敤 taskkill /PID ^<PID^> /F 鍏抽棴銆?exit /b 1
