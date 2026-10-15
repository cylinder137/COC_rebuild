@echo off
rem =====================================================================
rem  coc-platform : create the fresh database `coc_platform`
rem  Zero-inheritance: brand new schema, nothing reused from the old site.
rem  The MySQL password is read from C:\Users\Administrator\.tinker\db_password.txt
rem  into a temporary option file under %TEMP% (deleted at the end). It is
rem  never echoed, never stored in the repository, never put on a cmd line.
rem =====================================================================
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul

set "OPS_DIR=%~dp0"
set "MYSQL=C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
set "CNF=%TEMP%\coc-my.cnf"
set "DB=coc_platform"

echo ============================================================
echo  coc-platform : init-db
echo ============================================================

if not exist "%MYSQL%" (
  echo [ERR] mysql client not found: "%MYSQL%"
  exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%OPS_DIR%_mycnf.ps1" -Out "%CNF%"
if errorlevel 1 (
  echo [ERR] could not build temporary client config ^(see message above^)
  exit /b 1
)
if not exist "%CNF%" (
  echo [ERR] temporary client config was not created: "%CNF%"
  exit /b 1
)

echo [1/3] connecting to MySQL...
"%MYSQL%" --defaults-extra-file="%CNF%" -e "SELECT VERSION() AS mysql_version;"
if errorlevel 1 (
  echo [ERR] cannot connect to MySQL ^(wrong password in .tinker\db_password.txt, or MySQL not running?^)
  del /f /q "%CNF%" >nul 2>&1
  exit /b 1
)

echo [2/3] creating database %DB% ^(utf8mb4 / utf8mb4_0900_ai_ci^)...
"%MYSQL%" --defaults-extra-file="%CNF%" -e "CREATE DATABASE IF NOT EXISTS `%DB%` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
if errorlevel 1 (
  echo [ERR] CREATE DATABASE failed
  del /f /q "%CNF%" >nul 2>&1
  exit /b 1
)

echo [3/3] verifying...
"%MYSQL%" --defaults-extra-file="%CNF%" -e "SHOW DATABASES LIKE '%DB%'; SELECT @@character_set_database AS charset, @@collation_database AS collation FROM DUAL;" %DB%
if errorlevel 1 (
  del /f /q "%CNF%" >nul 2>&1
  exit /b 1
)

del /f /q "%CNF%" >nul 2>&1
echo.
echo [OK] database %DB% is ready.
echo      Tables are created by the backend on first start (Flyway V1__init.sql),
echo      then run: ops\seed\run-seed.cmd
exit /b 0
