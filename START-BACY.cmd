@echo off
setlocal
cd /d "%~dp0"
where node >nul 2>&1
if errorlevel 1 (
  echo.
  echo Bacy needs Node.js to run.
  echo Install the current Node.js LTS version, then double-click START-BACY.cmd again.
  echo.
  pause
  exit /b 1
)
if not exist "server.cjs" (
  echo Bacy files are incomplete. Extract the whole ZIP into one folder first.
  pause
  exit /b 1
)
set "NO_PROXY=localhost,127.0.0.1"
set "no_proxy=localhost,127.0.0.1"
set "BACY_AUTO_OPEN=1"
cls
echo ================================================
echo                 BACY ONLINE
echo ================================================
echo Address : http://127.0.0.1:3000
echo AI      : Google Gemini ^(online^)
echo Voice   : Fast device voice / ElevenLabs optional
echo Email   : Gmail MCP optional
echo.
echo Keep this black window OPEN while using Bacy.
echo Closing it stops Bacy.
echo ================================================
echo.
node server.cjs
if errorlevel 1 (
  echo.
  echo Bacy stopped. The error is shown above.
)
pause
