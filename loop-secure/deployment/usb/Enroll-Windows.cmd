@echo off
setlocal EnableExtensions DisableDelayedExpansion
cd /d "%~dp0"
chcp 65001 >nul
title Loop Secure - USB enrollment
echo Loop Secure USB enrollment
echo.
echo Use only for a phone you own or are authorized to enroll.
echo Android may require a new/reset phone with no accounts or other device owner.
echo This script does not reset the phone or remove accounts.
echo.
set "ADB=adb.exe"
if exist "%~dp0adb.exe" set "ADB=.\adb.exe"
"%ADB%" version >nul 2>nul
if not errorlevel 1 goto adbready
set "BACY_ADB_CACHE=%LOCALAPPDATA%\LoopSecure\adb-path.txt"
if exist "%BACY_ADB_CACHE%" set /p "ADB="<"%BACY_ADB_CACHE%"
"%ADB%" version >nul 2>nul
if not errorlevel 1 goto adbready
echo Choose adb.exe from your extracted Google platform-tools folder once.
echo This laptop will remember that location for future customer setups.
powershell.exe -NoProfile -STA -Command "$ErrorActionPreference='Stop';$cache=Join-Path $env:LOCALAPPDATA 'LoopSecure\adb-path.txt';[IO.Directory]::CreateDirectory((Split-Path -Parent $cache))|Out-Null;$downloads=Join-Path $env:USERPROFILE 'Downloads';$candidates=@((Join-Path $downloads 'platform-tools\adb.exe'),(Join-Path $downloads 'platform-tools-latest-windows\platform-tools\adb.exe'));$adb=$candidates|Where-Object{Test-Path -LiteralPath $_ -PathType Leaf}|Select-Object -First 1;if($null -eq $adb){Add-Type -AssemblyName System.Windows.Forms;$pick=New-Object System.Windows.Forms.OpenFileDialog;$pick.Title='Loop Secure: choose adb.exe in Google platform-tools';$pick.Filter='Android Debug Bridge (adb.exe)|adb.exe';$pick.InitialDirectory=$downloads;if($pick.ShowDialog() -ne [System.Windows.Forms.DialogResult]::OK){exit 2};$adb=$pick.FileName};[IO.File]::WriteAllText($cache,$adb,[System.Text.UTF8Encoding]::new($false))"
if errorlevel 1 (
 echo No Android tool was selected. Nothing was installed or reset.
 echo Download Google's Windows Platform Tools first if needed:
 echo https://developer.android.com/tools/releases/platform-tools
 pause
 exit /b 1
)
set /p "ADB="<"%BACY_ADB_CACHE%"
"%ADB%" version >nul 2>nul
if errorlevel 1 (
 echo The selected adb.exe could not run. Run this helper again and select Google's adb.exe.
 pause
 exit /b 1
)
:adbready
echo Connect ONE phone by USB, enable USB debugging and approve this laptop on the phone.
:checkdevice
set "BACY_DEVICE_LIST=%TEMP%\loop-usb-%RANDOM%-%RANDOM%.txt"
set "BACY_SERIAL_RESULT=%TEMP%\loop-serial-%RANDOM%-%RANDOM%.txt"
"%ADB%" devices > "%BACY_DEVICE_LIST%"
if errorlevel 1 goto failed
type "%BACY_DEVICE_LIST%"
powershell.exe -NoProfile -Command "$ErrorActionPreference='Stop';$lines=@(Get-Content -LiteralPath $env:BACY_DEVICE_LIST | Where-Object {$_.Trim() -ne '' -and $_ -notmatch '^List of devices attached'});if($lines.Count -ne 1){exit 1};$m=[regex]::Match($lines[0],'^(?<serial>[A-Za-z0-9._:-]+)\s+(?<state>[A-Za-z]+)\s*$');if(-not $m.Success){exit 1};if($m.Groups['state'].Value -eq 'unauthorized'){exit 2};if($m.Groups['state'].Value -ne 'device'){exit 1};[IO.File]::WriteAllText($env:BACY_SERIAL_RESULT,$m.Groups['serial'].Value,[Text.Encoding]::ASCII)"
set "BACY_DEVICE_STATUS=%ERRORLEVEL%"
del /q "%BACY_DEVICE_LIST%" >nul 2>nul
if "%BACY_DEVICE_STATUS%"=="2" goto authorize
if not "%BACY_DEVICE_STATUS%"=="0" goto reconnect
set "BACY_SERIAL="
set /p "BACY_SERIAL="<"%BACY_SERIAL_RESULT%"
del /q "%BACY_SERIAL_RESULT%" >nul 2>nul
if not defined BACY_SERIAL goto reconnect
if not exist "enrollment-code.txt" (
 echo The enrollment code file is missing. Download a fresh bundle from your admin dashboard.
 pause
 exit /b 1
)
powershell.exe -NoProfile -Command "$t=[IO.File]::ReadAllText('enrollment-code.txt').Trim();if($t -notmatch '^[A-Za-z0-9_-]{20,100}$'){exit 1}"
if errorlevel 1 (
 echo Invalid enrollment code file.
 pause
 exit /b 1
)
set /p BACY_TOKEN=<enrollment-code.txt
"%ADB%" -s "%BACY_SERIAL%" install -r "Bacy-Client.apk"
if errorlevel 1 goto failed
echo App installed. Checking Android ownership separately...
call :ownershipcheck
if "%BACY_CHECK_STATUS%"=="20" goto alreadylinked
if "%BACY_CHECK_STATUS%"=="21" goto linkedwithoutowner
if "%BACY_CHECK_STATUS%"=="0" goto bootstrap
if not "%BACY_CHECK_STATUS%"=="10" goto ownerunknown
"%ADB%" -s "%BACY_SERIAL%" shell dpm set-device-owner za.co.loopmanager/.LoopAdminReceiver > "Enrollment-error.txt" 2>&1
type "Enrollment-error.txt"
rem ADB can report exit code zero even when Android rejected ownership.
rem Always obtain a new actual ownership result before starting enrollment.
call :ownershipcheck
if "%BACY_CHECK_STATUS%"=="20" goto alreadylinked
if "%BACY_CHECK_STATUS%"=="21" goto linkedwithoutowner
if "%BACY_CHECK_STATUS%"=="10" goto ownerfailed
if not "%BACY_CHECK_STATUS%"=="0" goto ownerunknown
:bootstrap
echo Android confirmed device owner and active administrator. Connecting the customer...
"%ADB%" -s "%BACY_SERIAL%" shell am start -W -n za.co.loopmanager/.AdbEnrollmentActivity --es enrollment_token "%BACY_TOKEN%"
if errorlevel 1 goto failed
echo.
echo On the phone, tap Connect customer account.
echo Keep internet connected, then check the actual device report in your admin dashboard.
echo Customer connection and restrictions must still be verified on the phone.
pause
exit /b 0
:alreadylinked
echo.
echo This phone is already managed and connected to an existing customer.
echo Its app was updated. No new customer code was used or account replaced.
"%ADB%" -s "%BACY_SERIAL%" shell am start -n za.co.loopmanager/.MainActivity
pause
exit /b 0
:linkedwithoutowner
echo.
echo This app already has a customer account, but Android ownership is missing.
echo Setup stopped to preserve that account. Do not clear the app or enroll another customer.
echo Check the phone's management state with your administrator.
pause
exit /b 1
:ownerunknown
echo.
echo Android ownership could not be verified. Customer enrollment was NOT started.
echo The exact Android response is saved in Ownership-check.txt beside this helper.
echo Keep the phone unlocked and check the response below before downloading again.
echo If it names a missing OwnershipCheckReceiver, use the full current setup ZIP.
echo If it reports linked=unknown, the saved account cannot be read: preserve the app data.
echo If it reports a permission or USB error, resolve that reported error first.
if exist "Ownership-check.txt" type "Ownership-check.txt"
echo No reset or account removal was attempted.
pause
exit /b 1
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
:ownerfailed
echo.
echo The app installed, but Android did NOT assign device ownership.
echo Customer enrollment was NOT started. No reset or account removal was attempted.
echo The Android reason is shown above and saved in Enrollment-error.txt.
echo If it says accounts already exist, check Settings - Accounts on the PHONE.
echo Use an eligible phone with no accounts or other device owner. Back up first.
echo Do not reset or remove accounts until you understand what is stored on the phone.
pause
exit /b 1
:failed
echo.
echo Setup stopped. Check the ADB message above. No factory reset was attempted.
pause
exit /b 1
:ownershipcheck
set "BACY_OWNER_NONCE=%RANDOM%%RANDOM%"
set "BACY_OWNER_RESULT=%TEMP%\loop-owner-%RANDOM%-%RANDOM%.txt"
set "BACY_CHECK_STATUS=30"
"%ADB%" -s "%BACY_SERIAL%" shell am broadcast --include-stopped-packages --receiver-foreground -n za.co.loopmanager/.OwnershipCheckReceiver --es nonce "%BACY_OWNER_NONCE%" > "%BACY_OWNER_RESULT%" 2>&1
powershell.exe -NoProfile -Command "$ErrorActionPreference='Stop';try{$q=[char]34;$pattern='^Broadcast completed:\s*result=1,\s*data='+$q+'LoopSecureOwnerCheck:v1:'+([regex]::Escape($env:BACY_OWNER_NONCE))+':owner=(?<owner>true|false|unknown);active=(?<active>true|false|unknown);linked=(?<linked>true|false|unknown)'+$q+'$';$results=@(Get-Content -LiteralPath $env:BACY_OWNER_RESULT | ForEach-Object {$_.Trim()} | Where-Object {$_ -match '^Broadcast completed:'});if($results.Count -ne 1){exit 30};$m=[regex]::Match($results[0],$pattern);if(-not $m.Success){exit 30};$owner=$m.Groups['owner'].Value;$active=$m.Groups['active'].Value;$linked=$m.Groups['linked'].Value;if(@($owner,$active,$linked) -contains 'unknown'){exit 30};if($linked -eq 'true'){if($owner -eq 'true' -and $active -eq 'true'){exit 20};exit 21};if($owner -eq 'true' -and $active -eq 'true'){exit 0};exit 10}catch{exit 30}"
set "BACY_CHECK_STATUS=%ERRORLEVEL%"
:ownershipdone
copy /y "%BACY_OWNER_RESULT%" "Ownership-check.txt" >nul 2>nul
echo Local checker status: %BACY_CHECK_STATUS%>>"Ownership-check.txt"
del /q "%BACY_OWNER_RESULT%" >nul 2>nul
exit /b 0
