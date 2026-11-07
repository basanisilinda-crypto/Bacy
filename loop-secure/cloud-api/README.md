# Deployed cloud API

The installed customer app uses a real Supabase Edge Function and isolated Postgres tables. The private administrator Site proxies authenticated owner requests to that API. Native device calls use one-time enrollment codes and per-device credentials; they do not require ChatGPT sign-in or a private-Site bypass token.

- Private admin: https://phone-finance-manager-bc.bacanyclinda.chatgpt.site
- Device API base: https://ourxezawbfclmhbggskv.supabase.co/functions/v1/loop-manager
- Signed installer: device API base plus `/downloads/client.apk`

`index.ts` is the deployed API. `installer.ts` contains the actual signed APK and its checksum, not an example installer. It must be regenerated from a verified release for updates. Dependencies are pinned in the source imports.

`schema.sql` records the initial schema; `dispatch_current.sql` is the current function definition including subsequent fixes. The live Supabase migration history records the applied versions. Do not re-run initial table creation on an existing installation. The pre-existing MBMF tables and API have not been changed.

All new tables have RLS enabled and direct public grants revoked. Only the service role can invoke the database dispatcher. The security advisor's informational “RLS enabled, no policy” notices are intentional deny-by-default behavior for these server-only tables, not missing browser access. Reference: https://supabase.com/docs/guides/database/database-linter?lint=0008_rls_enabled_no_policy

The Edge Function disables only the platform JWT gateway check because it validates its own administrator/device credentials. Every protected route checks its intended credential. The service role stays in the function's managed environment; it is never embedded in the APK or browser. Administrator bearer credentials live in Sites secrets, with only a hash in Postgres.

Customer identity numbers, device identifiers and the entitlement private key are encrypted using AES-GCM derived from the function's service-role secret. Plan and verify key rewrapping before rotating that secret; otherwise the persisted encrypted values cannot be read after the old secret is lost. Public entitlement keys are pinned on the handset during QR enrollment.

The hosted dashboard is owner-only and uses the Sites/ChatGPT authentication boundary, with a server-side owner-email check. Authentication and MFA on that account are managed by its account provider. The standalone Python deployment separately supports owner/reviewer/viewer accounts with an app-level authenticator flow.

`check_transactions.sql` tests payment credit, duplicate rejection, message receipts, fleet policy, banking, support and release in an exception-rolled-back transaction. No fixture customer or payment rows remain committed. `tools/check_cloud.py` verifies the live service, authentication, exact installer bytes, QR renderer and invalid-code rejection.

The connected account's existing quotas apply. No project, paid plan or billing upgrade was created. Frequent phone connections consume function/database quotas; this is not an unlimited-free-service guarantee.
