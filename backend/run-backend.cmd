@echo off
rem 启动 Python 后端 (FastAPI + uvicorn, 端口 8080)
rem API Key 自动从 backend\.env.local 加载(见 config.py)
cd /d %~dp0

if not exist .venv (
  echo [首次运行] 正在创建 Python 虚拟环境并安装依赖,请稍候...
  python -m venv .venv
  if errorlevel 1 (
    echo [错误] 创建虚拟环境失败,请确认已安装 Python 3.10+ 并在 PATH 中。
    exit /b 1
  )
  .venv\Scripts\python.exe -m pip install --disable-pip-version-check -r requirements.txt
  if errorlevel 1 (
    echo [错误] 依赖安装失败,请检查网络后重试。
    exit /b 1
  )
  echo [完成] 依赖安装完成。
)

.venv\Scripts\python.exe main.py
exit /b %errorlevel%
