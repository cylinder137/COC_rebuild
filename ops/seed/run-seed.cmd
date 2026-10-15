@echo off
rem =====================================================================
rem  coc-platform : load the demo seed data (idempotent, safe to re-run)
rem
rem  Steps
rem    1. (re)generate local assets : covers, demo project payloads, view stats
rem    2. wait until the backend has created the tables (Flyway V1__init.sql),
rem       polling for up to 15 minutes
rem    3. run  assets/files.generated.sql + assets/views.generated.sql + seed.sql
rem    4. print row counts per table
rem
rem  The MySQL password is read from .tinker\db_password.txt into a temporary
rem  option file; it never appears in this script, on a command line or in git.
rem =====================================================================
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul

set "SCRIPT_DIR=%~dp0"
set "SEED_DIR=%SCRIPT_DIR%"
set "OPS_DIR=%SCRIPT_DIR%..\"
set "REPO=%SCRIPT_DIR%..\.."
set "CNF=%TEMP%\coc-my.cnf"
set "MYSQL=C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
set "COMBINED=%TEMP%\coc-seed-combined.sql"
set "DB=coc_platform"
set "MAX_WAIT_MIN=15"
set "POLL_SEC=20"

echo ============================================================
echo  coc-platform : run-seed
echo ============================================================

if not exist "%MYSQL%" ( echo [ERR] mysql client not found: "%MYSQL%" & exit /b 1 )

rem ---------- 1. local assets ----------
echo [1/5] generating local assets ^(covers / demo payloads / view stats^)...
pushd "%SEED_DIR%"
node gen-covers.js "%REPO%" || (echo [ERR] gen-covers.js failed & popd & exit /b 1)
node gen-demo-files.js "%REPO%" || (echo [ERR] gen-demo-files.js failed & popd & exit /b 1)
node gen-views.js || (echo [ERR] gen-views.js failed & popd & exit /b 1)
popd

rem ---------- client config ----------
powershell -NoProfile -ExecutionPolicy Bypass -File "%OPS_DIR%_mycnf.ps1" -Out "%CNF%"
if errorlevel 1 ( echo [ERR] could not build temporary client config & exit /b 1 )

rem ---------- 2. wait for tables ----------
echo [2/5] waiting for the backend to migrate the schema ^(max %MAX_WAIT_MIN% min, poll %POLL_SEC% s^)...
set /a TRIES=0
set /a MAX_TRIES=%MAX_WAIT_MIN%*60/%POLL_SEC%
:waitloop
set "READY="
"%MYSQL%" --defaults-extra-file="%CNF%" -N -B -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='%DB%' AND table_name='project';" > "%TEMP%\coc-ready.txt" 2>nul
set /p READY=<"%TEMP%\coc-ready.txt"
if "%READY%"=="1" goto ready
set /a TRIES+=1
if !TRIES! GEQ %MAX_TRIES% (
  echo.
  echo [ERR] timed out after %MAX_WAIT_MIN% minutes: table `project` does not exist in %DB%.
  echo       start the backend first ^(ops\start-backend.cmd^) so Flyway can run V1__init.sql,
  echo       or run ops\init-db.cmd if the database itself is missing.
  del /f /q "%CNF%" >nul 2>&1
  exit /b 1
)
<nul set /p "=."
ping -n %POLL_SEC% 127.0.0.1 >nul
goto waitloop

:ready
echo.
echo [OK] schema is ready ^(attempt !TRIES!^)

rem ---------- 3. clean the backend self-test fixtures ----------
if /i "%COC_SEED_KEEP_SELFTEST%"=="1" (
  echo [3/5] skipping self-test fixture cleanup ^(COC_SEED_KEEP_SELFTEST=1^)
) else (
  echo [3/5] removing backend self-test fixtures ^(qq 9000xx^)...
  "%MYSQL%" --defaults-extra-file="%CNF%" -t %DB% < "%SEED_DIR%\clean-selftest.sql"
  if errorlevel 1 (
    echo [ERR] self-test cleanup failed
    del /f /q "%CNF%" >nul 2>&1
    exit /b 1
  )
)

rem ---------- 4. run the seed ----------
echo [4/5] executing seed SQL...
copy /b "%SEED_DIR%\assets\files.generated.sql"+"%SEED_DIR%\seed.sql"+"%SEED_DIR%\assets\views.generated.sql"+"%SEED_DIR%\seed-finalize.sql" "%COMBINED%" >nul
"%MYSQL%" --defaults-extra-file="%CNF%" --show-warnings %DB% < "%COMBINED%"
if errorlevel 1 (
  echo [ERR] seed SQL failed ^(see the MySQL error above^)
  del /f /q "%CNF%" >nul 2>&1
  exit /b 1
)

rem ---------- 5. summary ----------
echo.
echo [5/5] row counts
"%MYSQL%" --defaults-extra-file="%CNF%" -t %DB% -e "SELECT 'user' AS tbl, COUNT(*) AS rows_ FROM `user` UNION ALL SELECT 'developer_profile', COUNT(*) FROM developer_profile UNION ALL SELECT 'category', COUNT(*) FROM category UNION ALL SELECT 'tag', COUNT(*) FROM tag UNION ALL SELECT 'project', COUNT(*) FROM project UNION ALL SELECT 'project_version', COUNT(*) FROM project_version UNION ALL SELECT 'project_file', COUNT(*) FROM project_file UNION ALL SELECT 'project_tag', COUNT(*) FROM project_tag UNION ALL SELECT 'project_review', COUNT(*) FROM project_review UNION ALL SELECT 'comment', COUNT(*) FROM comment UNION ALL SELECT 'project_like', COUNT(*) FROM project_like UNION ALL SELECT 'project_favorite', COUNT(*) FROM project_favorite UNION ALL SELECT 'project_view_daily', COUNT(*) FROM project_view_daily UNION ALL SELECT 'project_view_hourly', COUNT(*) FROM project_view_hourly UNION ALL SELECT 'im_session', COUNT(*) FROM im_session UNION ALL SELECT 'im_message', COUNT(*) FROM im_message UNION ALL SELECT 'im_session_member', COUNT(*) FROM im_session_member UNION ALL SELECT 'notification', COUNT(*) FROM notification UNION ALL SELECT 'report', COUNT(*) FROM report UNION ALL SELECT 'follow', COUNT(*) FROM follow UNION ALL SELECT 'build_task', COUNT(*) FROM build_task UNION ALL SELECT 'agreement_version', COUNT(*) FROM agreement_version;"

echo.
"%MYSQL%" --defaults-extra-file="%CNF%" -t %DB% -e "SELECT project_id, title, project_type, status, audit_mode, like_count, comment_count, favorite_count, view_count_total FROM project WHERE project_id BETWEEN 201 AND 206 ORDER BY project_id;"

del /f /q "%CNF%" >nul 2>&1
del /f /q "%COMBINED%" >nul 2>&1
echo.
echo [OK] seed data loaded. Test accounts and ports: ops\README-ports.md
exit /b 0
