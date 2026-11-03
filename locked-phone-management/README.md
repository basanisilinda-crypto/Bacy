# Locked Phone Management by Basani Selinda
Two separate native Android apps connected to the existing live Supabase project. This is real application source, not simulated payment data. Build validation and real-device tests remain outstanding.

## Current capabilities
- Customer email registration/login and secure RLS-scoped profile/plan lookups.
- Customer can view verified EFT beneficiary details when configured, submit a reference and amount; the claim remains pending.
- Administrator login with database-enforced admin membership, view pending claims, approve or reject only after bank verification.
- Administrator can create a financed device plan and configure EFT beneficiary details through secured server RPC functions.
- A server-side transaction advances the next payment date on approved full instalments and marks a fully settled plan/device as released.

## Still required before production
- The initial finance administrator must be explicitly assigned by a trusted operator using an authenticated user UUID; self-registration does not grant admin rights.
- Configure verified Capitec beneficiary details using the admin app. Never commit private account data or server secrets.
- Properly provision Device Owner and test all supported Android/Hisense models. A server-set 'released' status alone does NOT remove Android Device Owner restrictions.
- Payment and device scheduling confirmations, signature-checked policy delivery, consent screen, safety grace periods, offline policy enforcement, QA, publishing, production signing, incident response.
- No exact-time lock or universal factory-reset prevention is guaranteed.

## Build
Requires Android SDK 35, Java 17 and Gradle 8.9.
From this directory: `gradle :customer:assembleDebug :admin:assembleDebug`.
A GitHub Actions build also produces debug APK artifacts if the workflow passes.

## Security
- Only a public Supabase publishable client key is included.
- Database RLS and server-side RPC enforce client vs administrator authorization.
- Customers cannot mark claims approved. Their entry of an EFT reference does not establish payment.
- The financier must provide clear financing terms and gain informed Device Owner consent during setup. Emergency services must remain available.

