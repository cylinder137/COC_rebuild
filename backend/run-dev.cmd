@echo off
rem =====================================================================
rem  coc-platform backend dev launcher (port 8090)
rem  DB password is read from C:\Users\Administrator\.tinker\db_password.txt
rem  into the environment variable COC_DB_PASSWORD (never stored in repo).
rem
rem  usage:
rem    run-dev.cmd            -> run the built jar (target\coc-platform-backend-0.1.0.jar)
rem    run-dev.cmd mvn        -> mvn spring-boot:run (dev, auto compile)
rem    run-dev.cmd package    -> mvn -q -DskipTests package
rem =====================================================================
setlocal enabledelayedexpansion
chcp 65001 >nul

set "BACKEND_DIR=%~dp0"
set "PWFILE=C:\Users\Administrator\.tinker\db_password.txt"

if not exist "%PWFILE%" (
  echo [ERROR] password file not found: %PWFILE%
  exit /b 1
)
set /p COC_DB_PASSWORD=<"%PWFILE%"
set "COC_DB_PASSWORD=!COC_DB_PASSWORD!"

set "JAVA_EXE=java"
if exist "C:\Program Files\Java\jdk-21\bin\java.exe" set "JAVA_EXE=C:\Program Files\Java\jdk-21\bin\java.exe"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"

cd /d "%BACKEND_DIR%"

if /i "%~1"=="mvn" (
  echo [INFO] mvn spring-boot:run ...
  call mvn spring-boot:run
  exit /b %ERRORLEVEL%
)

if /i "%~1"=="package" (
  echo [INFO] mvn package ...
  call mvn -DskipTests package
  exit /b %ERRORLEVEL%
)

set "JAR=%BACKEND_DIR%target\coc-platform-backend-0.1.0.jar"
if not exist "%JAR%" (
  echo [ERROR] jar not found: %JAR%
  echo         run:  run-dev.cmd package
  exit /b 1
)

echo [INFO] starting coc-platform backend on port 8090 ...
echo [INFO] health: http://127.0.0.1:8090/api/v1/system/health
"%JAVA_EXE%" -jar "%JAR%"
