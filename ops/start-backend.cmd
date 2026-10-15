@echo off
rem =====================================================================
rem  coc-platform : start the backend (Spring Boot, http://127.0.0.1:8090)
rem  Idempotent: does nothing if 8090 is already listening.
rem  DB password: read from C:\Users\Administrator\.tinker\db_password.txt into
rem               the COC_DB_PASSWORD environment variable (application.yml).
rem  Log: ops\logs\backend.log
rem  Launch detached with:  wscript.exe ops\start-backend.vbs
rem =====================================================================
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul
set "SCRIPT_DIR=%~dp0"
set "REPO=%SCRIPT_DIR%.."
set "LOG_DIR=%SCRIPT_DIR%logs"
set "LOG=%LOG_DIR%\backend.log"
set "PORT=8090"

if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

powershell -NoProfile -Command "if (Get-NetTCPConnection -State Listen -LocalPort %PORT% -ErrorAction SilentlyContinue) { exit 10 }"
if errorlevel 10 (
  echo [i] backend: port %PORT% is already listening - nothing to do.
  exit /b 0
)

rem ---- locate the fat jar (skip *.jar.original) ----
set "JAR="
for %%F in ("%REPO%\backend\target\*.jar") do set "JAR=%%~fF"
if "%JAR%"=="" (
  echo [ERR] no jar found in backend\target.
  echo       build it first:  cd /d "%REPO%\backend" ^&^& mvn -DskipTests package
  exit /b 1
)

rem ---- java ----
set "JAVA=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA=%JAVA_HOME%\bin\java.exe"

rem ---- db password (never echoed, never written to the repo) ----
set "COC_DB_PASSWORD="
for /f "usebackq delims=" %%P in (`powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%_dbpw.ps1"`) do set "COC_DB_PASSWORD=%%P"
if "%COC_DB_PASSWORD%"=="" (
  echo [ERR] could not read the MySQL password from .tinker\db_password.txt
  exit /b 1
)

echo [i] starting backend: %JAR%
echo [i] log -> "%LOG%"
"%JAVA%" -jar "%JAR%" >> "%LOG%" 2>&1
set "RC=%ERRORLEVEL%"
echo [i] backend exited with code %RC%
exit /b %RC%
