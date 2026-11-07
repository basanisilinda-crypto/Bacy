# Rain Loop handset acceptance procedure

Use a dedicated test handset owned by the business. Record actual model, Android build, security patch, setup-wizard version and test evidence. Keep manufacturer recovery instructions available. Do not use customer banking credentials in development logs or screenshots.

## Provisioning and identity

- Check device-owner eligibility and the permitted manufacturer financing integration route.
- Reset only the dedicated test handset; confirm the setup-wizard QR reader is available.
- Create an enrollment with enforcement disabled, record identity-check evidence and consent, scan its QR, and verify automatic app download and admin-extra import.
- Check app signature/checksum validation, enrollment-code expiry, and rejection of code reuse on a second handset.
- Confirm the customer name, reference, amount and initial 30-day period are real server records.
- Confirm that a normally installed APK reports management unsupported rather than claiming ownership.

## Managed policies

- Exercise each policy separately and record the observed OS behavior.
- Confirm uninstall restriction and that the app does not claim recovery-reset protection from a Settings restriction.
- Test Wi-Fi configuration without removing all routes for banking, support and approval delivery.
- Confirm the selected daily installation window is applied to system updates, and verify that indefinite update blocking is unavailable.
- Grant or deny phone-state permission and check actual IMEI availability, encrypted storage, owner-only access and explicit unavailable status. SIM serial-number retrieval is not implemented.

## Restriction and banking

- Use a controlled test backend/database to establish an expired test entitlement; never change a real customer's account to test locking.
- Verify the app is actually in managed lock-task mode, not screen pinning.
- Verify Home, Recents, notification launches, deep links and reboot behavior.
- Test every approved official bank app, authentication, network dependencies and return-to-app payment prompt.
- Confirm emergency calling using an approved non-emergency test procedure or carrier/manufacturer guidance. Do not place test calls to emergency services.
- Confirm the in-app support message reaches the administrator while restricted.
- Test missing bank packages and verify that configuration errors are visible to the administrator.

## Payment correctness

- Submit a claim offline and confirm the app does not falsely show it as sent or approved.
- Submit a claim online; verify the reference and amount in the admin inbox.
- Verify that the claim itself does not change the deadline.
- Reject it, check the customer-visible reason, and ensure the paid period stays unchanged.
- Approve a new claim against a settled test transaction. Confirm one extension, then an applied device acknowledgement.
- Retry approval concurrently, reuse the bank reference and resubmit the claim. Confirm no duplicate entitlement.
- Disconnect the phone during approval: dashboard must continue showing delivery pending until synchronization.

## Time, restart and background operation

- Change the user wall clock forward and backward in the current boot: paid time must follow monotonic elapsed time.
- Restart offline: app must show that an online check is required. Record the known enforcement gap rather than claiming tamper resistance.
- Restore network and confirm a fresh signed authorization establishes time again.
- Run in Doze, battery saver, with the screen off and after app/process restart; measure actual expiry and approval delivery latency.
- Validate bank access and support after prolonged offline operation and after an update.

## Release and recovery

- After a confirmed full-payoff test, request release and verify acknowledgement, kiosk exit, removal of app-imposed policies, device-owner removal and ability to uninstall.
- Confirm subsequent policies cannot reapply finance restrictions to the released account.
- Restore the backend database and keys from an encrypted backup to a private test host and confirm a test handset can validate the original signing identity.
- Complete a documented lost-admin-authenticator and network-outage recovery plan before customer rollout.

Record pass/fail, evidence and unresolved issues for each item. A passing backend test suite or APK build does not replace these checks.

## Live connection, messaging and fleet changes

- Confirm the visible device-management foreground notification appears only after real device-owner enrollment. An ordinary installation must not claim that exemption.
- Put the app in the background and send a targeted administrator message. Confirm notification, native inbox content, and separate downloaded/read receipts.
- Disconnect networking, queue several messages, reconnect and verify receipt and ordering without duplicate notifications. Repeat after a process restart.
- Send a message to all active phones and verify that a released phone is excluded. Inspect per-device receipt isolation.
- Apply a fleet policy and confirm revised policies on each connected handset. Keep offline phones visibly pending until reconnection and a device report.
- Change the verified business EFT destination in the admin UI and confirm each handset receives the signed update.
- Test battery saver, Doze, service termination and manufacturer battery controls; measure delivery latency. Do not label a queued update as applied.
