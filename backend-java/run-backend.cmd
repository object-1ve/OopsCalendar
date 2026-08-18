@echo off
rem 以 java -jar 启动后端(端口 8080),先加载 .env.local 中的 API Key
cd /d %~dp0
if exist .env.local (
  for /f "usebackq eol=# tokens=1,* delims==" %%a in (".env.local") do set "%%a=%%b"
)
java -jar target\earnings-calendar.jar
