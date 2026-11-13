# Continue the ORIGINAL Loop Secure app in a separate working copy

## For the owner

1. Keep this source ZIP and your encrypted owner backup somewhere safe outside ChatGPT. Store the backup password separately. Do not send the backup/password to Rain or paste them in chat.
2. Start a coding session with GitHub access to **basanisilinda-crypto/Bacy** as a read-only baseline and provide this source ZIP.
3. Paste the text in **START-NEXT-CHAT.txt**. This explicitly selects the production client/admin/website, not the Rain test app.
4. Let the assistant compare the latest GitHub/runtime changes before applying this handoff. Do not replace the whole Bacy repository: it contains unrelated projects.
5. A compatible app update requires the original signer. Configure credentials privately using your provider/GitHub secrets settings, never by committing them.

## For the next engineering assistant

- Repository: https://github.com/basanisilinda-crypto/Bacy ; normal branch main ; app source path loop-secure. Known source baseline68f7286. Compare remote HEAD first. Preserve unrelated root projects and changed files. This archive is the complete Loop Secure source tree, not the complete multi-project Bacy repo.
- Read loop-secure/AGENTS.md, STATUS.md, CREDENTIALS.md, docs/ONLINE_ORDER_HISTORY.md, and this file. Keep the evaluation repository/package separate. Do not let testing self-release/no-payment settings replace real customer policy.
- Keep the original Bacy repository/files untouched. Use a separate local working copy or a separate owner-approved repository; do not push to Bacy without a later explicit instruction. Compare/apply reviewed source with original baseline. The desired native repair files are app/build.gradle, MainActivity.java, AdbEnrollmentActivity.java, SyncJobService.java, ManagedUpdates.java and EnrollmentRecoveryTest.java; portable test-output fixes are separate. Do not blindly overwrite newer edits. Keep original0.27.0/code40 if merely syncing published source; use greater codes for new runtime changes. Admin current1.14.1/code21.
- Deployments were changed outside Git: production loop-manager v39 and November12 Cloudflare website. Read-only v39 source is under loop-secure/deployment/runtime-reference. Compare current provider deployments/config/database before changes. Do not redeploy old baseline or assume source archive fully reconstructs current database/website configuration.
- Fix/reproduce the owner's actual symptom; run app/admin unit tests, release lint and builds, then verify package/signer and hosted update checksum. Verify backend auth/payment/order/state paths appropriate to the change. No real handset is present here; ask for actual Windows/phone output for physical acceptance without fabricating success.
- Review changes in the separate PRODUCTION working copy. Prepare a patch/commit for the owner, but do not push or modify original Bacy files. If source publication is requested, use an explicitly owner-approved separate production repository, never the Rain evaluation. Existing deployed apps can receive compatible updates from the same signed release channel without editing the original GitHub repository.

## Compatible automatic native publication

The included workflow-template/loop-secure-native-release.yml uses the production subfolder paths and is a template for a separately approved production repository. Do not install it into original Bacy without later explicit authorisation. It builds/tests/lints the two PRODUCTION apps, verifies package/cert/version and publishes only newer versions through loop-secure/tools/ci_publish.py. It has not been installed/activated in GitHub. Before enabling it in a separate owner-approved production repository, review the workflow and set its production environment secrets:

- LOOP_KEYSTORE_B64 (original keystore, base64)
- LOOP_STORE_PASSWORD, LOOP_KEY_PASSWORD, LOOP_KEY_ALIAS
- LOOP_DEPLOY_TOKEN (existing owner deployment token)

Configure directly in GitHub's secret manager with owner access; never put values into source/chat. The workflow must fail clearly if secrets are missing or no version code changed. It does not deploy the drifted website/backend. In-app update checks need a newer signed APK; Android controls background scheduling and may require administrator install approval.

## Local private recovery

Install Python cryptography and use loop-secure/tools/restore_private_backup.py with the encrypted backup and password FILE paths, choosing destination loop-secure. It restores .signing privately and adjusts the keystore path. The encrypted backup contains the local original signer/passwords and local owner/merchant credential snapshots; compare before replacing any newer provider configuration. Server entitlement keys stay in the owner's provider configuration, and OAuth tokens are not exported. Retain your Supabase, Cloudflare, merchant and GitHub account access.

## What is not already done

Original Bacy must remain read-only. This handoff accompanies Codex’s owner-authorised production source sync; check Git history for its commit/push. No CI activation is claimed. Current website/database drift still needs reconciliation; no physical phone acceptance is claimed. The runtime has no ChatGPT subscription dependency. Finishing a chat does not stop deployed hosting, but hosting quotas/credentials must remain valid.
