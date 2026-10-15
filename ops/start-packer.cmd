@echo off
rem =====================================================================
rem  coc-platform : start the packer (Node, http://127.0.0.1:8091)
rem  Idempotent: does nothing if 8091 is already listening.
rem  Log: ops\logs\packer.log
rem  Launch detached with:  wscript.exe ops\start-packer.vbs
rem =====================================================================
setlocal EnableExtensions
chcp 65001 >nul
set "SCRIPT_DIR=%~dp0"
set "REPO=%SCRIPT_DIR%.."
set "LOG_DIR=%SCRIPT_DIR%logs"
set "LOG=%LOG_DIR%\packer.log"
set "PORT=8091"

if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

powershell -NoProfile -Command "if (Get-NetTCPConnection -State Listen -LocalPort %PORT% -ErrorAction SilentlyContinue) { exit 10 }"
if errorlevel 10 (
  echo [i] packer: port %PORT% is already listening - nothing to do.
  exit /b 0
)

pushd "%REPO%\packer"
echo [i] starting packer on port %PORT%, log -> "%LOG%"
node --max-old-space-size=768 server.js >> "%LOG%" 2>&1
set "RC=%ERRORLEVEL%"
popd
echo [i] packer exited with code %RC%
exit /b %RC%
