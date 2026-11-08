# Loop Secure downloads

Customer **0.26.0/code 39**, administrator **1.14.0/code 20**. Android 11+.

New: native Light, Dark and Follow phone controls and reported phone identity. Website appearance and automatic storage cleanup are already live. Custom finance plans and expanded online orders remain staged pending migration approval. See ../docs/NATIVE_APPEARANCE.md.

- **Loop-Secure-Client.apk**: signed customer update. Install over the existing app; do not uninstall or clear data.
- **Loop-Secure-Admin.apk**: signed administrator update. Installation may need approval; the admin phone is never restricted.
- **Loop-Secure-Client-Update.zip**: Windows update helper for phones where USB debugging remains available. Financed phones with ADB protection use the managed update channel instead.
- **Loop-Secure-Source.zip**: sanitized source and bundled-font licenses.
- **Loop-Secure-USB-Helper.zip**: generic helper sources. New enrollment needs the private customer-specific bundle from the dashboard.

Customer apps check published updates in the background and can install verified packages automatically where Android permits. Admin apps check on Wi-Fi and may require installation approval. Android scheduling and active checkout can delay installation. Connect the phone to the internet; Support / Your phone includes the manual update check. Runtime does not depend on ChatGPT/Codex credits. The separate preview APK is an older disconnected preview.

Developer-options/ADB protection and offline finance deadlines remain. Five-minute codes verify online once, then expire on the local monotonic timer. An unverifiable clock after restart requires a time check. Banking, Wi-Fi/settings, support and emergency access remain available. Recovery/firmware resets are not blocked by ordinary device-owner policy.
