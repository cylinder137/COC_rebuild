@echo off
rem coc-platform : remove the backend self-test fixtures (standalone runner)
rem   clean-selftest.cmd                 -> clean rows + delete .data\projects\<n> leftovers
rem   clean-selftest.cmd --keep-files    -> clean DB rows only
rem
rem run-seed.cmd already calls the SQL part of this automatically; use this
rem script when you only want the cleanup.
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul

set "SCRIPT_DIR=%~dp0"
set "OPS_DIR=%SCRIPT_DIR%..\"
set "REPO=%SCRIPT_DIR%..\.."
set "MYSQL=C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
set "CNF=%TEMP%\coc-my.cnf"
set "DB=coc_platform"

if not exist "%MYSQL%" ( echo [ERR] mysql client not found: "%MYSQL%" & exit /b 1 )

powershell -NoProfile -ExecutionPolicy Bypass -File "%OPS_DIR%_mycnf.ps1" -Out "%CNF%"
if errorlevel 1 ( echo [ERR] could not build temporary client config & exit /b 1 )

echo [1/2] cleaning self-test rows...
"%MYSQL%" --defaults-extra-file="%CNF%" -t %DB% < "%SCRIPT_DIR%clean-selftest.sql"
if errorlevel 1 (
  echo [ERR] cleanup SQL failed
  del /f /q "%CNF%" >nul 2>&1
  exit /b 1
)
del /f /q "%CNF%" >nul 2>&1

if /i "%~1"=="--keep-files" (
  echo [2/2] skipping leftover file cleanup ^(--keep-files^)
  exit /b 0
)

echo [2/2] removing leftover self-test storage directories...
for %%D in ("%REPO%\.data\projects\2") do (
  if exist "%%~fD" (
    rmdir /s /q "%%~fD"
    echo   removed %%~fD
  ) else (
    echo   nothing to remove ^(%%~fD^)
  )
)
echo [OK] self-test fixtures removed.
exit /b 0
