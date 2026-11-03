# MBMF — Mobile Business & MDM Finance

This directory holds **two separate native Android applications**, backed by the user's existing Supabase finance project.

- `:admin` — MBMF Admin: administrator authentication, finance contract registration, QR enrolment pilot, live summary, bank-detail configuration and manual EFT payment claim decisions.
- `:customer` — MBMF Client: device-assigned finance display and customer EFT reference submission **without customer signup/login**, with an encrypted cached snapshot.
- `:core` — shared native UI/network code, Kotlin/Compose branding and the cents-based finance schedule engine.

**Status: DEVELOPMENT PILOT.** Neither debug APK is safe for commercial financing deployment. Do not describe Android Device Owner provisioning, remote locking, or the permanent release as verified without a real-device acceptance test.

## Current implementation

- Both applications compile as native Android APKs, not a web wrapper.
- Customer does not create an email/password or Google account in MBMF.
- Administrator account must be created and separately authorized in `finance_admins`; registration alone never grants administrator privileges.
- Backend holds one-time enrolment information and device credentials, protected with database digest storage. Customer device credentials are encrypted using Android Keystore.
- Customer can submit an EFT payment *claim*. Only an administrator who independently checks a bank receipt can approve the claim; a submitted reference is not receipt evidence.
- Debug build uses real Android provisioning receiver/activities and has an Android setup QR path, but **LOOP device compatibility is untested**.
- Compose is being integrated incrementally. The existing functional screen flows are still partly Java/native Android Views.
- The finance schedule now preserves its original due-date anchor and supports cumulative partial/multiple approved instalments and 30-day/calendar-month calculation.
- Enrolled Client devices can use WorkManager for best-effort six-hour background snapshot refresh when online. No offline payment approval or automatic payment-triggered device restriction occurs.

## Build and test

From this directory with Java 17, Android SDK 35 and Gradle 8.9:

```
gradle :core:testDebugUnitTest :admin:assembleDebug :customer:assembleDebug
```

The PR-only workflow `.github/workflows/mbmf-pr-check.yml` builds unsigned-release-independent debug APKs and runs finance unit tests. Its outputs are build artifacts, not signed production applications. Use a stable private signing key and device enrollment checks before commercial rollout.

## Outstanding acceptance requirements

1. Real LOOP handset setup-time QR download and Device Owner verification.
2. Confirm management disclosure/consent, emergency calling and ability to get support on actual OEM firmware.
3. Real server-authenticated remote management actions and state acknowledgements.
4. Safe online/offline finance grace period, customer notices and retry behavior.
5. Independently verified, non-duplicated bank receipts and complete EFT reconciliation.
6. Final settlement with a confirmed, appropriately supported, no-loss Android device release.
7. Security review (RLS, callable privileged RPCs, rate limiting, credential rotation, MFA), production signing and legal review.
8. Integrated tests, Android UI tests and hardware testing; screen-reader/small-phone accessibility.

Never put private banking details, customers' records, or device credential secrets in this public repository.

See [Engineering audit](DEVELOPMENT_AUDIT_2026-11-03.md).
