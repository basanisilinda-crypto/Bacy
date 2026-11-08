# Cloud independence and storage maintenance

## Runtime independence

The published Android apps use the configured Supabase backend, Cloudflare admin site and iKhokha merchant connection. ChatGPT/Codex credits are not an application authentication requirement. Ending a ChatGPT subscription does not itself shut these services down. Cloud-provider quotas, billing, account access and service availability remain separate responsibilities. No guarantee of unlimited free operation is possible.

## Observed storage, 8 November 2026

The Loop installer bucket contained 65 objects totaling 1,008,780,493 bytes. Loop application database tables were under 1 MB. Installer accumulation is the immediate storage issue, rather than customer database records. This inventory does not measure bandwidth, provider billing or the complete Supabase project quota usage.

## Prepared cleanup

`cloud-api/maintenance.ts` selects only known installer APK names. It retains every currently configured installer, the latest three files per client/admin/preview family, all files younger than 24 hours, unknown objects and objects without valid dates. It rechecks current pointers before deleting via the supported Storage API. It does not delete Storage metadata directly using SQL. A complete inventory is required; a 1,000-object page stops cleanup for review.

The endpoint is restricted to the website/deployment owner and defaults to dry-run. Normal admin overview activity and future uploads can request background maintenance, at most once per day after successful completion. Cleanup is activity-triggered, not a scheduled job that runs while the project is idle. The report is stored in one bounded config record. Existing update checks and downloaded installer caching remain intact.

`cloud-api/maintenance.sql` installs a service-role-only helper removing expired mobile pairings, mobile sessions and recovery/courtesy code records only after an extra 24-hour retention period. Malformed/missing expiry values are retained. Payment attempts, claims, customer/device records, financial idempotency records and audit history are preserved.

## Deployment status

The database helper was installed and its permissions verified; anon/authenticated cannot execute it. The storage selection tests passed (4 tests). The new maintenance endpoint and automatic background hooks are **not deployed**: automatic approval review rejected replacement of the shared production Edge Function, citing outage and payment/device-management risk without full end-to-end validation. No installer storage deletion has been performed and no freed-storage claim is made. Current apps remain customer 0.25.0 and native admin 1.13.0.

Enrollment custom-plan fields, the expanded online order dashboard and phone-name display are separate unfinished work, paused at the user's request to prioritise cloud costs. Their local sources must not be included in the maintenance deployment without separate validation.

## Next action

Review and approve the concrete 14-file Edge Function update prepared with maintenance.ts and the two background hooks, then run the protected dry-run before deletion. Recheck current downloads, update manifests, cloud health and storage totals after cleanup. Keep provider spending caps enabled and monitor request/bandwidth quotas as the fleet grows. Storage cleanup cannot prevent all possible Cloudflare or Supabase limits.
