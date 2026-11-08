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

Live Edge Function version 30 was deployed after the user's explicit approval. Its dry-run selected 42 superseded installers, and cleanup removed those 42 files, reclaiming 585,003,886 bytes. One expired temporary credential was removed. Storage now contains 23 objects totaling 423,776,607 bytes. All 15 customer/device records and 4 payment claims remain.

Current customer 0.25.0 and admin 1.13.0 downloads were retrieved successfully; signed client bytes, provisioning checksum, cloud health, admin authentication and invalid enrollment rejection passed. A second cleanup request was skipped by the daily throttle. No handset reinstall is required.

The larger per-customer plan/order migration remains staged: automatic approval review rejected replacing core enrollment, sync, payment-confirmation and recovery functions despite successful transactional rollback tests. It is not live. Native light/dark controls are also unfinished. Website light/dark/system selection is a separate deployed appearance update.

Cleanup cannot guarantee that request, bandwidth or storage limits will never be reached. Keep provider usage and spending limits under review as device numbers increase.
