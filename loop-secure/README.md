# Loop Secure

Native Android customer and administrator apps, a Cloudflare-hosted administrator website, and an existing Supabase cloud backend for consented device-owner enrollment and phone-finance management.

- [Administrator website](https://loop-secure-admin.loopsecure-bacanyclinda.workers.dev) — Cloudflare Access sign-in with the configured owner email.
- [APK and Windows update downloads](https://github.com/basanisilinda-crypto/Bacy/releases/tag/loop-secure-v0.22.0).
- Customer app **0.22.0**, administrator app **1.10.0**. Android 11/API 30 minimum; target/compile Android 16/API 36. Compatibility on each handset still needs testing.

## Phone setup

Create or reopen the intended customer in the dashboard and download its fresh Windows enrollment ZIP. Extract it into its own folder, connect one authorized phone, and run `START-HERE.cmd`. The helper prepares verified official stable Google Platform Tools automatically, rejects obsolete ADB clients, verifies the exact selected phone and actual device-owner/admin state, and starts the administrator-recorded customer connection automatically only when verified. New enrollment codes expire after 30 minutes. Installation alone does not assign device ownership.

Android can refuse ownership when accounts or another device owner exist. Back up/sync phone data and confirm credentials before changing accounts. The helper never resets the phone or removes accounts. If ownership cannot be verified, inspect `Ownership-check.txt`; repeated downloads do not repair an unreadable local account or a permission failure.

Already-enrolled phones use the Windows **update** ZIP, preserving the signing identity, customer connection and device owner. A released test handset requires completed release and removal of the old local app before a new enrollment record; preserve the old payment and audit history.

## Structure

| Directory | Purpose |
| --- | --- |
| `android/app` | Signed native customer app and device-owner policies |
| `android/admin` | Native administrator app |
| `android/shared-ui` | Shared design and bundled fonts/logo |
| `cloud-api` | Supabase Edge Function and database sources |
| `cloudflare-admin` | Cloudflare Access-protected website and deployment configuration |
| `backend` | Optional Python/local server and shared website assets |
| `deployment/usb` | Windows enrollment and update helpers |
| `docs` | Hosting, EFT, enrollment and handset verification guidance |

## Build and deployment

Use JDK 17+ and Android SDK 36. In `android/`, run `./gradlew :app:assembleRelease :app:lintRelease :app:testDebugUnitTest` with the private signing environment configured as documented in `deployment/README.md`. Signing keys and runtime credentials are supplied separately and are never committed. Debug builds cannot update a release installation.

For the website, follow `cloudflare-admin/README.md`; dependencies are pinned and locked. `npm ci`, `npm test` and `npm run build` generate and verify the deployable Worker. Keep the backend administrator token in Cloudflare secrets. The production website and native-admin pairing links use Cloudflare Access. The backend default and legacy-address migration also point to Cloudflare. Neither application checks a ChatGPT or Codex subscription. See `docs/INDEPENDENT_HOSTING.md`.

## Payments and device controls

The customer app now offers **Make a payment → Pay online with iKhokha**. The server creates a hosted payment link for the verified amount due (including the smaller final balance) and verifies the provider’s paid status, amount and receipt before adding 30 days and clearing the finance lock. Duplicate callbacks cannot add duplicate credit. See [iKhokha customer steps and live acceptance check](docs/IKHOKHA_PAYMENTS.md). Complete a provider-confirmed PAID checkout and handset unlock test before relying on automated approval.

Direct bank EFT claims require bank-settlement verification before administrator approval. A submitted reference is not proof of payment. Approved payments, grace periods, manual controls and permanent release produce signed authorizations; requested changes and actual handset acknowledgements are shown separately. Automatic verification of direct bank EFT and outbound payment emails require separately configured providers and credentials. iKhokha online payments use the separately configured private merchant connection.

Banking, Wi-Fi, system Settings and emergency access remain part of the managed flow. The managed client clears Wi-Fi configuration restrictions while retaining finance reset/uninstall protections. Device-owner restrictions use supported Android APIs; recovery/firmware protection and instantaneous delivery on every manufacturer are not guaranteed. Offline reboot behavior and banking app compatibility require the handset checks in `docs/HANDSET_TESTS.md`.

## This publication

Customer **0.22.0/code 35**, native admin **1.10.0/code 16** and the administrator website add separate five-minute courtesy access codes. Open the intended restricted customer and choose **Generate five-minute code**. The client enters it under **Support → Five-minute access code**. It needs online validation once, then a local elapsed-time timer restores the original finance restriction without a cloud request. No payment or paid deadline is added. Paid-access recovery codes remain separate.

The Home screen now shows a live restriction countdown, including existing grace. Local elapsed-realtime alarms, an independent foreground enforcement loop and screen-on recovery enforce verified deadlines offline. Editable date/time/timezone cannot extend access. A bounded partial wake lock keeps temporary-access checks responsive. Exact alarms are used where Android permits; the fallback is inexact.

After reboot, an unverifiable clock requires a fresh signed time check before normal access; this does not claim a new debt. Banking, Wi-Fi/settings, support and emergency access remain available. Permanently released and enforcement-disabled accounts are excluded. New remote commands still require delivery. Existing real payment dates and R1,700 contract rules are preserved. [Full guide, testing and platform limits](docs/FIVE_MINUTE_ACCESS_AND_OFFLINE_LOCK.md).

Admin background updates use a persisted 15-minute Wi-Fi scheduling backstop and installation approval where Android requires it; the admin phone is never restricted. Customer background updates continue through the existing signed managed channel. Install updates in place, without clearing enrollment/account data.

Validated: customer/admin release builds and lint, Android monotonic-expiry/alarm/restart/payment/banking/update tests, browser fixtures for both code types, server signature/gateway fixtures and rollback-only SQL for one-use codes, revocation, unchanged financial records and rate/attempt limits. Handset sleep/wake, updates and banking handoff still require physical-device acceptance checks. No firmware or partition changes are included.
