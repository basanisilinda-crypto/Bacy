# USB ownership verification

Loop Secure 0.12.1 fixes a Windows helper path that could open customer bootstrap after `dpm set-device-owner` had printed an exception despite a zero ADB exit status. APK installation alone is not management enrollment.

The helper selects one authorized device, validates its serial, and pins subsequent ADB commands to that device. A DUMP-protected, read-only ordered broadcast reports actual DevicePolicyManager owner/admin state and encrypted customer-token presence. Each check uses a new nonce and result file; the helper accepts exactly one matching response. Missing, malformed, duplicate, stale or unknown state stops enrollment. Existing linked accounts are preserved. The phone activity independently checks ownership and existing enrollment before saving any customer code.

The helper probes before and after any ownership attempt, regardless of the DPM command exit code. It never resets phones, deletes accounts, or bypasses Android eligibility. If Android rejects existing accounts, resolve phone eligibility in Settings after backing up/syncing data and confirming account credentials, then download a fresh complete customer ZIP. The updated helper requires its matching updated APK.

Validation: Robolectric receiver tests on API 30 and 35; actual embedded PowerShell parser tests with real PowerShell; signed release build and Android lint; bundle serializer and live hosted byte/checksum checks. Physical Windows CMD/USB and device-owner provisioning still require a handset test.

Parser checks: `python tools/check_usb_helper.py /path/to/pwsh`.
