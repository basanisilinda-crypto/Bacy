# MBMF verified engineering audit — 2026-11-03

Project: basanisilinda-crypto/Bacy/locked-phone-management

## Existing verified code
- Two distinct native Android packages: za.co.bacyflex.admin and za.co.bacyflex.customer.
- Both apps use the real Supabase project (publishable key in APK, server secrets not bundled).
- Admin-only contract registration and manual EFT approval RPCs exist.
- Client has no customer sign-up/login; enrolment requires an admin-created QR at initial Android setup.
- Android DevicePolicyManager is queried to verify Device Owner, but no financed-phone policy enforcement or fully tested permanent release currently exists.
- GitHub has pilot debug APK releases, not production signed releases.

## This development change
- Adds a Kotlin cents-based finance schedule with unit tests.
- Supports 30-day and calendar-month cadence, including partial payments and a final smaller instalment.
- Adds Kotlin/Jetpack Compose to the native Android project and integrates a restrained MBMF Compose brand header in both apps.
- Adds a PR-only CI check for JUnit tests, APK compilation and APK signature inspection, without publishing a customer-facing release.
- Keeps existing finance workflows intact while the UI is migrated in stages.

## Already applied to live Supabase separately
- Migration mbmf_finance_schedule_and_overpayment_hardening.
- Migration mbmf_admin_read_overpayments_rls.
- Added original due date, configurable cadence, and overpayment review ledger.
- Improved approved EFT due-date calculations after split/multiple instalments.
- Verified schema with SQL and deterministic repayment examples. Full authenticated EFT integration tests are still needed.

## Unverified blockers
- LOOP L6 actual QR Device Owner enrolment.
- Real bank receipt to approved ledger to client sync.
- Customer emergency/support access during a permitted policy restriction.
- Remote command acknowledgement and safe offline behaviour.
- Fully supported unmanagement after settlement.
- Consistent production signing, update keys, device attestation and device-level tests.
- South African credit law and POPIA review.

Never deploy to paying customers solely on the strength of build/test artifacts.

## Background account synchronisation (pilot)
- The Client app now schedules six-hour WorkManager refreshes after successful device activation.
- Refreshes require connectivity; WorkManager may defer them for battery and OS constraints.
- A successful response replaces only the encrypted locally cached snapshot; failed attempts keep the previous verified snapshot.
- This worker does NOT lock devices, approve payments, or change the financial ledger.
- Boot recovery, timing, battery behavior and encrypted credential access need tests on actual LOOP hardware.
