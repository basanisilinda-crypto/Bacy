# Loop Secure downloads

Managed client 0.15.0, native administrator 1.6.1 and optional disconnected preview 1.3.0.

Install signed updates over the matching existing app. Do not uninstall an enrolled client to update it. The Windows update ZIP retains the customer account and does not assign ownership again.

The separate preview only displays the interface; it cannot enroll, lock devices or submit payments. New customer enrollment ZIPs contain private one-time codes and must be generated in the authenticated dashboard.

SHA256SUMS.txt lists the public file checksums. Source and deployment archives exclude private signing keys and administrator credentials.

Windows ZIPs now use START-HERE.cmd with automatic verified Google tools preparation. Android USB authorization and phone eligibility still apply.

Customer 0.15.0 adds **Make a payment → Pay online with iKhokha** and **Check online payment**. Bank EFT remains available with manual settlement review. Read `docs/IKHOKHA_PAYMENTS.md` in the source ZIP for verification details and the first live payment test.
