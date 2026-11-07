@echo off
setlocal EnableExtensions DisableDelayedExpansion
cd /d "%~dp0"
setlocal EnableDelayedExpansion
chcp 65001 >nul
title Loop Secure - Client update
echo Loop Secure client update
echo This updates the installed app while retaining its existing customer account.
echo No reset, account deletion or new enrollment is performed.
echo.
set "BACY_ADB_CACHE=%LOCALAPPDATA%\LoopSecure\adb-path.txt"
if not exist "%~dp0Prepare-Tools.ps1" (
 echo Download the full current setup ZIP. Keep its files in the extracted folder.
 pause
 exit /b 1
)
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Prepare-Tools.ps1"
if errorlevel 1 (
 echo USB tools could not be prepared. Check the error above and the laptop internet connection.
 pause
 exit /b 1
)
set "ADB="
set /p "ADB="<"%BACY_ADB_CACHE%"
if not defined ADB exit /b 1
:adbready
echo Connect ONE phone by USB, enable USB debugging and approve this laptop on the phone.
:checkdevice
set "BACY_DEVICE_LIST=%TEMP%\bacy-usb-%RANDOM%-%RANDOM%.txt"
"!ADB!" devices > "!BACY_DEVICE_LIST!"
if errorlevel 1 (
 del /q "!BACY_DEVICE_LIST!" >nul 2>nul
 goto failed
)
type "!BACY_DEVICE_LIST!"
set "COUNT=0"
set "CONNECTED=0"
set "UNAUTHORIZED=0"
for /f "usebackq skip=1 tokens=2" %%S in ("!BACY_DEVICE_LIST!") do (
 set /a CONNECTED+=1 >nul
 if "%%S"=="device" set /a COUNT+=1 >nul
 if "%%S"=="unauthorized" set /a UNAUTHORIZED+=1 >nul
)
del /q "!BACY_DEVICE_LIST!" >nul 2>nul
if not "!CONNECTED!"=="1" goto reconnect
if "!UNAUTHORIZED!"=="1" goto authorize
if not "!COUNT!"=="1" goto reconnect
if not exist "Bacy-Client.apk" (
 echo Bacy-Client.apk is missing. Extract the complete update ZIP into its own folder.
 pause
 exit /b 1
)
"!ADB!" install -r "Bacy-Client.apk"
if errorlevel 1 goto failed
"!ADB!" shell am start -n za.co.loopmanager/.MainActivity
if errorlevel 1 goto failed
echo.
echo The client update is installed. Keep the phone online and check its account screen.
echo Installation alone does not confirm that the account data loaded or restrictions work.
pause
exit /b 0
:authorize
echo.
echo The phone is detected, but USB debugging has not been approved.
echo Unlock the phone and tap Allow on the Allow USB debugging prompt.
echo If no prompt appears, revoke USB debugging authorizations in Developer options,
echo then disconnect and reconnect the USB cable and approve this laptop.
echo After approving on the phone, press a key here to check again.
pause
goto checkdevice
:reconnect
echo.
echo Exactly ONE connected and authorized Android device is required.
echo Disconnect other Android devices. Keep this phone unlocked and connected by USB.
echo If the phone is offline or missing, reconnect the cable and approve USB debugging.
echo Press a key to check again, or close this window to stop.
pause
goto checkdevice
:failed
echo.
echo Update stopped. Keep the message above for support. No reset was performed.
pause
exit /b 1
