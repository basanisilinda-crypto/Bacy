# Locked Phone Management — production implementation plan

Status: planning document; not a deployed or tested app.

## Architecture
- Customer Android app and administrator Android app authenticate separately to Supabase Auth.
- Supabase Postgres stores customers, consent/enrolment records, managed devices, instalment plans, payment claims, administrator decisions and an immutable audit trail.
- Row-level security restricts customers to their own records. Administrative privileges are assigned server-side only, never by a first-signup rule.
- Customer EFT screen displays verified bank details from secure configuration and a unique payment reference. Opening a banking app must not be treated as proof of payment.
- Customers submit a payment reference; status remains pending until an authenticated administrator verifies the actual bank receipt and approves.
- A server-side transaction records approval once, updates the next due date according to contract terms, and issues a short-lived signed device-policy update. Prevent duplicate approvals and replay.
- Android managed-device enrollment requires informed consent and compatible Device Owner provisioning, via QR setup or supported ADB testing. Avoid promises of universal factory-reset prevention or exact-time offline restrictions.
- Local offline enforcement uses last verified signed policy, a transparent grace period and emergency-access protections. Reconnection synchronizes decisions. On full settlement, permanently release financing restrictions under the contract.

## Next checks
1. Confirm restored Supabase project is HEALTHY.
2. Inspect existing Android modules and CI workflows before editing source.
3. Create additive schema migration, RLS policies and approval RPC with tests.
4. Integrate customer/admin clients and compile signed test APKs.
5. Test consent, enrolment, payment claims, offline expiry, approvals and release on real devices.

Never commit private bank account details, service-role keys or signing credentials to this public repository.
