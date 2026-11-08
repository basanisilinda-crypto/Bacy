# Loop Secure downloads

Customer **0.23.0/code 36**, administrator **1.11.0/code 17**. Android 11+.

- **Loop-Secure-Client.apk**: signed customer update. Install over the existing app; do not clear data or uninstall.
- **Loop-Secure-Client-Update.zip**: extract, connect one enrolled phone, then double-click START-HERE.cmd. Account and device ownership stay in place.
- **Loop-Secure-Admin.apk**: signed administrator update, including separate five-minute access-code controls and a 15-minute Wi-Fi background update backstop.
- **Loop-Secure-Source.zip**: sanitized public source and bundled-font licenses.
- **Loop-Secure-USB-Helper.zip**: helper sources. New enrollment still requires the private customer-specific dashboard bundle.

[Five-minute codes and offline locking](../docs/FIVE_MINUTE_ACCESS_AND_OFFLINE_LOCK.md): online verification once, then local monotonic expiry. Developer options/ADB are restricted during financing. Use managed updates after activation. No payment is credited by courtesy access. Restarting with an unverifiable clock requires a time check. Banking, Wi-Fi/settings, support and emergency remain available. Exact alarm/installation timing depends on Android. The admin phone is never restricted.

Enrolled customers and connected admin apps can receive published updates through their existing update channels where Android permits. Older admin releases use the older background interval until this update is installed. Installation approval may be required. Runtime does not depend on ChatGPT/Codex credits. The separate preview APK is an older disconnected design preview.
