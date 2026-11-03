# BACY FLEX — connected Android finance apps (work in progress)

Owned project: Locked Phone Management by Basani Selinda.

Two **different native Android packages** live in `mdm-test-android/`:
- `app/` — customer sign-in/signup, live account balance and due date, verified EFT bank instructions, banking-app handoff, EFT reference submission and pending/approved history.
- `admin/` — manager sign-in, finance-account registration, verified bank destination, pending EFT review, manual approval/rejection. **Only the server can change an account balance.**

These screens use real Supabase Auth, PostgreSQL and PostgREST RPC endpoints. They do **not** create fake or local payments. If the backend is not configured, the customer app explicitly says so.

## Configure the live cloud (required)

1. Create a Supabase project with Auth email/password enabled and email confirmation. Keep the **project URL** and **anon/public API key**. Never put your `service_role` key, banking screenshot or private credentials in GitHub.
2. In the Supabase SQL editor run in this order:
   - `financing/supabase/20261103_eft_manual_approval.sql`
   - `financing/supabase/20261103_eft_destination.sql`
   - `financing/supabase/20261103_admin_operations.sql`
3. Create and verify your manager login through Supabase Auth; find its UUID in Authentication → Users. In the **private** Supabase SQL editor, grant management access with
   `insert into public.finance_managers(user_id) values ('<MANAGER_AUTH_USER_UUID>');`
   Only do this for an account you control. Manager permissions never come from the Android client.
4. In GitHub repository **Settings → Secrets and variables → Actions**, add:
   - `BACY_SUPABASE_URL`: the HTTPS Supabase project URL.
   - `BACY_SUPABASE_ANON_KEY`: the project's public/anon API key (**not** service_role).
   These build-time settings are compiled into both APKs. RLS and RPC server-side role checks are the security boundary.
5. Use the repository's **Actions → Build BACY FLEX Android apps** workflow to build and collect:
   - `BACY-FLEX-Customer-APK`
   - `BACY-FLEX-Admin-APK`
   The outputs are debug APKs for testing, not signed production releases.

## Run a real EFT test

1. Sign in to the admin Android app with the manager account from step 3.
2. Save your **verified** Capitec EFT destination inside the admin app; it is stored in Supabase, not GitHub. Ensure the bank account holder and account number are accurate.
3. A customer creates a login in the customer Android app, verifies their email, and signs in.
4. Only after you verify a signed finance agreement, use the admin app to register that email, their phone model, sale amount and instalment amount.
5. Customer signs in again, sees the live account and verified bank instructions, can open an installed Capitec/TymeBank app, makes the EFT themselves, and submits the actual bank reference.
6. The admin app displays a **PENDING** claim. Independently check the bank for cleared funds, exact amount and reference before pressing **Approve**.
7. The database transaction reduces the balance and sets the next due date to the approval date **plus 30 days**. At zero balance the status becomes **paid_in_full** and no next payment date is set. The customer can refresh to see the change online.

**Do not send actual EFT money to any destination until those details have been verified by the seller and agreed to by the customer.**

## Not yet completed (do not describe as functional)

- No live Supabase instance or credentials have been configured through this source-code change.
- No production-signed APK, hosted APK download URL, or Android setup QR has been released.
- No automatic offline restriction/remote release. The existing Device Owner receiver is only a local experimental foundation, not a financing lock. **Approving a payment updates the cloud account but DOES NOT yet change device restrictions.**
- No complete device-owner QR provisioning flow or verified emergency-call-safe restricted mode.
- No production audit/security review, real bank reconciliation integration, or device testing.
- A debug APK compiled without the two build variables deliberately shows a setup warning rather than fake account data.

For QR Device Owner provisioning, eligible factory-reset Android devices require a correctly signed and hosted downloadable Device Policy Controller (DPC), a valid checksum and an enrolment payload with clear consent. Do not issue a provisioning QR that claims to work before those are in place.

Both apps are written in native Java and require Java 17, Android SDK 35 and Gradle 8.9. The GitHub Actions build is the source of APK compile status.
