@echo off
setlocal
cd /d "%~dp0"
echo XM ULTRA - Guided Windows setup
echo This opens setup. It does not start trades or ask for your XM password.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Guided-Start.ps1"
if errorlevel 1 (
 echo.
 echo Setup stopped. Read the message above and share only the error text.
 pause
)
endlocal
