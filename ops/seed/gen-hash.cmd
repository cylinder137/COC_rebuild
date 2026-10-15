@echo off
rem coc-platform : generate a BCrypt hash for seed data (bcryptjs, pure JS)
rem   gen-hash.cmd                 -> hash for the default trial password (Coc@2026)
rem   gen-hash.cmd MyPass123       -> hash for a custom password
rem   gen-hash.cmd MyPass123 sql   -> "SET @pw := '<hash>';" ready to paste into seed.sql
setlocal EnableExtensions
chcp 65001 >nul
set "SEED_DIR=%~dp0"
pushd "%SEED_DIR%"
if not exist "node_modules\bcryptjs" (
  echo [i] bcryptjs not installed, installing...
  call npm i bcryptjs --no-audit --no-fund || (echo [ERR] npm install failed & popd & exit /b 1)
)
node gen-hash.js %*
set RC=%ERRORLEVEL%
popd
exit /b %RC%
