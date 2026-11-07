# Current customer behaviour: 0.18.3

Customer 0.18.3 supersedes the customer Wi-Fi/manual-install instructions below: it checks and downloads in the background on Wi-Fi or mobile data, and attempts unattended installation only as device owner with explicit signed server permission. Older clients need one installation approval to receive this behaviour. Android may still require approval or delay background work. See [background customer update guide](BACKGROUND_CUSTOMER_UPDATES.md). Administrator behaviour is unchanged.

# Automatic app updates — customer 0.18.0 / administrator 1.7.0

Both apps check immediately when opened with a connected account on validated, unmetered Wi-Fi. A connection callback checks again when Wi-Fi becomes available while the app or customer connection service is running. Android schedules a persisted six-hour background check as a backstop; Android controls its exact timing. Offline, battery-restricted or stopped apps cannot promise immediate checks. A manual check can use the current connection.

New APKs download automatically into private app storage. Progress appears in app update controls; a notification opens the installation controls. The user taps **Install update** after download. Android may ask for installation permission or confirmation. Administrator installation permission opens only after the user taps Install. No store subscription or ChatGPT subscription is involved.

Existing versions require this updater-capable release once. Customers on 0.16+ can use their existing **App updates → Check and install** flow, or the account-preserving Windows update ZIP. Older admin apps need the signed administrator APK once. Use **Update**, never uninstall, reset, clear data or create a second enrollment. Installation preserves package identity, signing certificate, account data and device ownership.

## Customer update restriction

A signed required update temporarily shows a maintenance screen on managed customer phones. Banking, emergency calls and Wi-Fi remain available. An active payment checkout is not interrupted. The maintenance gate expires after ten minutes, does not extend on repeated checks for the same version, and clears on installation failure. Ordinary financial policy then resumes; maintenance does not approve payments, change paid time or clear a real financial restriction. The gate does not apply to released or unmanaged phones and does not survive reboot. These recovery limits prevent an unavailable download from indefinitely trapping a paid customer.

## Administrator update

Administrator updates are optional installation prompts. They do not use device ownership, lock task, screen locking, navigation restrictions or any customer policy. The administrator's phone remains usable throughout. Its seven-day owner-approved account session must still be valid to fetch an authenticated update manifest.

## Authenticity and operational limits

The authenticated server returns an Ed25519-signed, one-hour manifest bound to the device or administrator installation UUID. Both native apps verify signature, installed-package name, increasing version code, minimum Android SDK, a fixed HTTPS storage origin/path, maximum length, SHA-256 and the installed app signing certificate. Redirects are rejected. APKs are verified again before installation; a failed update retains the old app and account. Admin update manifests explicitly set `required: false`.

No APK, signing private key, provider secret or administrator credential is accepted from a web page or customer input. Public APK storage does not grant access to private customer data.

## Dashboard additions

- Latest customer release and last reported version/state per phone.
- Filter for known older customer apps; unknown versions remain unknown.
- Fleet counts of reported versions, ready downloads and failed checks, with report timestamps.
- Explicit verification of up to ten recent pending iKhokha checkouts; only verified provider PAID results credit accounts.
- CSV export of the currently displayed EFT claim records, with spreadsheet formula escaping. Claims are not payment settlement.

Older clients do not report versions until updated. A reported installation request is not proof of successful installation; a new installed version must report back. Direct bank EFT remains manually reviewed. iKhokha controls the Instant EFT/Ozow methods and banks on its hosted checkout: Loop Secure cannot add a bank that the merchant's provider has not enabled. No undocumented API field or direct Ozow connection is fabricated.

Build and validation: release builds/lint, SDK 30 and 35 regression cases for signed manifests and bounded maintenance, administrator no-restriction checks, gateway replay/amount/link fixtures, dashboard browser fixtures and authentication/relay tests. Actual Android installation prompts, network scheduling and real bank authentication still require handset testing.
