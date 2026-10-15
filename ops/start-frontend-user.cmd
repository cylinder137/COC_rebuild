@echo off
rem =====================================================================
rem  coc-platform : start the USER-CONTENT frontend (Vite, 8086, --mode user)
rem  Idempotent: does nothing if 8086 is already listening.
rem  Log: ops\logs\frontend-user.log
rem  Launch detached with:  wscript.exe ops\start-frontend-user.vbs
rem =====================================================================
setlocal EnableExtensions
chcp 65001 >nul
set "SCRIPT_DIR=%~dp0"
set "REPO=%SCRIPT_DIR%.."
set "LOG_DIR=%SCRIPT_DIR%logs"
set "LOG=%LOG_DIR%\frontend-user.log"
set "PORT=8086"

if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

powershell -NoProfile -Command "if (Get-NetTCPConnection -State Listen -LocalPort %PORT% -ErrorAction SilentlyContinue) { exit 10 }"
if errorlevel 10 (
  echo [i] frontend-user: port %PORT% is already listening - nothing to do.
  exit /b 0
)

if not exist "%REPO%\frontend\node_modules" (
  echo [i] frontend\node_modules missing, running npm install...
  pushd "%REPO%\frontend"
  call npm install --no-audit --no-fund
  popd
)

echo [i] starting frontend (user zone) on port %PORT%, log -> "%LOG%"
pushd "%REPO%\frontend"
call npm run dev:user >> "%LOG%" 2>&1
set "RC=%ERRORLEVEL%"
popd
echo [i] frontend-user exited with code %RC%
exit /b %RC%
