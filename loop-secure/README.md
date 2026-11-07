# Loop Secure

Native Android customer and administrator apps, a Cloudflare-hosted administrator website, and an existing Supabase cloud backend for consented device-owner enrollment and phone-finance management.

- [Administrator website](https://loop-secure-admin.loopsecure-bacanyclinda.workers.dev) — Cloudflare Access sign-in with the configured owner email.
- [APK and Windows update downloads](https://github.com/basanisilinda-crypto/Bacy/releases/tag/loop-secure-v0.18.0).
- Customer app **0.18.0**, administrator app **1.7.0**. Android 11/API 30 minimum; target/compile Android 16/API 36. Compatibility on each handset still needs testing.

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

The customer app now offers **Make a payment → Pay online with iKhokha**. The server creates a R300 hosted payment link and verifies the provider’s paid status, amount and receipt before adding 30 days and clearing the finance lock. Duplicate callbacks cannot add duplicate credit. See [iKhokha customer steps and live acceptance check](docs/IKHOKHA_PAYMENTS.md). Complete a provider-confirmed PAID checkout and handset unlock test before relying on automated approval.

Direct bank EFT claims require bank-settlement verification before administrator approval. A submitted reference is not proof of payment. Approved payments, grace periods, manual controls and permanent release produce signed authorizations; requested changes and actual handset acknowledgements are shown separately. Automatic verification of direct bank EFT and outbound payment emails require separately configured providers and credentials. iKhokha online payments use the separately configured private merchant connection.

Banking, Wi-Fi, system Settings and emergency access remain part of the managed flow. The managed client clears Wi-Fi configuration restrictions while retaining finance reset/uninstall protections. Device-owner restrictions use supported Android APIs; recovery/firmware protection and instantaneous delivery on every manufacturer are not guaranteed. Offline reboot behavior and banking app compatibility require the handset checks in `docs/HANDSET_TESTS.md`.

## This publication

Customer **0.18.0/code 27** and administrator **1.7.0/code 12** check on opening and Wi-Fi connection, download verified updates automatically and show installation approval controls. The native admin updater never restricts its phone. Android can require installation permission/confirmation. Existing clients need this updater-capable release once; update in place to retain accounts and device ownership.

Signed required customer updates have a ten-minute maintenance recovery deadline, preserve bank/emergency/Wi-Fi access, do not interrupt payment checkout, clear on failure and never alter financial approval. See [automatic update and dashboard guide](docs/AUTOMATIC_APP_UPDATES.md) for scheduling and recovery limits.

The dashboard adds per-phone version/state reports, an older-version filter, update readiness/retry counts, recent iKhokha reconciliation and displayed-claim CSV export. Unknown or stale phone reports remain labelled; installation requests are not proof of installation.

This release also replaces all remaining app-controlled customer dialogs/buttons with colourful custom sheets and actions, uses a new folded-loop L/check logo throughout, and retains DM Sans/Fraunces. Six usability additions cover last-used bank ordering, payment-history filters, per-value EFT copying, a connection-check panel, dismissible notices and duplicate-send protection for support. [Complete customer UI guide](docs/COMPLETE_CUSTOMER_UI.md).

Validated: same-signer release builds/lint, 38 native regression/rendering cases on SDK 30/35, 12 gateway/update manifest fixtures, website authentication/relay checks, browser acceptance fixtures, live signed administrator manifest, authentication rejection and hosted APK byte verification. Real handset update prompts, network scheduling and bank authentication still require handset checks. No merchant/signing secrets or customer enrollment tokens are included. Direct bank EFT remains reviewed; hosted Instant EFT/Ozow bank availability is controlled by iKhokha.
