# MDM Test Android — Native app
This is an offline, consent-based Android device administration demo. Not a consumer-financing lock and not PayJoy/Knox Guard.

## Install from a GitHub Actions build
In GitHub, open **Actions > Build MDM Test APK > latest successful run > Artifacts > MDM-Test-Android-APK**. Download the ZIP and extract **app-debug.apk**. Open that APK on your Android phone and approve the normal Android installation prompts.

If the run failed, do not assume an APK exists.

## Safe screen-lock test (no ADB)
1. Install the APK.
2. Set your phone's screen PIN, if desired, and remember it.
3. Open MDM Test, tap **Allow screen locking**, and approve Android's device-administration dialog.
4. Tap **Lock my screen**. Unlock using your usual PIN.

## Advanced Device Owner app restriction test
WARNING: Use a spare or fully backed-up device. Device Owner may be difficult to remove without a factory reset. Do not reset or provision your everyday phone without backup.

On an eligible test phone (with Android Platform Tools / ADB):
```powershell
.\adb.exe devices
.\adb.exe shell dpm set-device-owner za.co.mdmtest/.AdminReceiver
```
This command only works if Android permits Device Owner provisioning on that device, generally during initial setup / clean provisioned state. Never bypass manufacturer restrictions. In MDM Test the status must say **Device Owner ON** before restricting an installed nonessential app using its package ID. The app blocks several critical system package identifiers.

This project does NOT implement QR enrollment, remote commands, surveillance, payment-linked locking, uninstall resistance, or protections surviving factory reset. Those require additional platform support and explicit user agreements.

## Source and build
Java 17, Android Gradle Plugin 8.7.3, Gradle 8.9, Android SDK 35. GitHub Actions uploads a debug APK after a successful build; it is a test-signed APK, not a Play Store release.
