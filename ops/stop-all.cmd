@echo off
rem =====================================================================
rem  coc-platform : gracefully stop the four trial-run services
rem   8090 backend   |  8085 frontend   |  8086 frontend-user   |  8091 packer
rem
rem  Note: this only touches those four ports. It never touches nginx, MySQL,
rem  cloudflared or the older collectionofcreations services on
rem  80/3306/8080/8081/8082/8083.
rem
rem  Usage:  stop-all.cmd              stop all four
rem          stop-all.cmd 8091         stop just one port
rem =====================================================================
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul

set "PORTS=8090 8085 8086 8091"
if not "%~1"=="" set "PORTS=%~1"

echo ============================================================
echo  coc-platform : stop services
echo ============================================================
echo.

for %%P in (%PORTS%) do (
  call :stop_port %%P
)

echo.
echo [OK] done. remaining listeners on those ports:
powershell -NoProfile -Command "$p=@(8090,8085,8086,8091); $r=Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object { $p -contains $_.LocalPort }; if ($r) { $r | Select-Object LocalAddress,LocalPort,OwningProcess | Format-Table -AutoSize | Out-String } else { '  (none)' }"
exit /b 0

rem ---------------------------------------------------------------- helpers
:stop_port
set "P=%~1"
set "PIDS="
for /f "usebackq delims=" %%X in (`powershell -NoProfile -Command "(Get-NetTCPConnection -State Listen -LocalPort %P% -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique) -join ' '"`) do set "PIDS=%%X"
if "%PIDS%"=="" (
  echo   [%P%] not running
  exit /b 0
)
for %%X in (%PIDS%) do (
  echo   [%P%] pid %%X : sending graceful close...
  taskkill /PID %%X >nul 2>&1
)
rem give the process a moment, then force if it ignored the request
ping -n 4 127.0.0.1 >nul
for %%X in (%PIDS%) do (
  tasklist /FI "PID eq %%X" 2>nul | findstr /i "%%X" >nul
  if not errorlevel 1 (
    echo   [%P%] pid %%X : still alive, forcing...
    taskkill /F /PID %%X >nul 2>&1
  )
)
echo   [%P%] stopped ^(pids: %PIDS%^)
exit /b 0
