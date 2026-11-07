# Loop Secure downloads

Managed client 0.16.1, native administrator 1.6.1 and optional disconnected preview 1.3.0.

Install signed updates over the matching existing app. Do not uninstall an enrolled client to update it. The Windows update ZIP retains the customer account and does not assign ownership again.

The separate preview only displays the interface; it cannot enroll, lock devices or submit payments. New customer enrollment ZIPs contain private one-time codes and must be generated in the authenticated dashboard.

SHA256SUMS.txt lists the public file checksums. Source and deployment archives exclude private signing keys and administrator credentials.

Windows ZIPs now use START-HERE.cmd with automatic verified Google tools preparation. Android USB authorization and phone eligibility still apply.

Customer 0.15.0 adds **Make a payment → Pay online with iKhokha** and **Check online payment**. Bank EFT remains available with manual settlement review. Read `docs/IKHOKHA_PAYMENTS.md` in the source ZIP for verification details and the first live payment test.

Version 0.16.0 fixes app access on unrestricted phones and adds checkout Messages / OTP, Banking app, browser recovery and protected future automatic updates. Install this update once over the existing client; do not uninstall or enroll again. See `docs/PAYMENT_RECOVERY_AND_UPDATES.md` in the source ZIP.

Version 0.16.1 adds failed-link replacement, clearer payment status and an Instant EFT checkout shortcut. Automatic updates remain available to enrolled devices on version 0.16 and later. Provider method availability is separate from this client shortcut.
