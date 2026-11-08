# Loop Secure downloads

Managed client 0.20.0, native administrator 1.9.0 and optional disconnected preview 1.3.0.

Install signed updates over the matching existing app. Do not uninstall an enrolled client to update it. The Windows update ZIP retains the customer account and does not assign ownership again.

The separate preview only displays the interface; it cannot enroll, lock devices or submit payments. New customer enrollment ZIPs contain private one-time codes and must be generated in the authenticated dashboard.

SHA256SUMS.txt lists the public file checksums. Source and deployment archives exclude private signing keys and administrator credentials.

Windows ZIPs now use START-HERE.cmd with automatic verified Google tools preparation. Android USB authorization and phone eligibility still apply.

Customer 0.15.0 adds **Make a payment → Pay online with iKhokha** and **Check online payment**. Bank EFT remains available with manual settlement review. Read `docs/IKHOKHA_PAYMENTS.md` in the source ZIP for verification details and the first live payment test.

Version 0.16.0 fixes app access on unrestricted phones and adds checkout Messages / OTP, Banking app, browser recovery and protected future automatic updates. Install this update once over the existing client; do not uninstall or enroll again. See `docs/PAYMENT_RECOVERY_AND_UPDATES.md` in the source ZIP.

Version 0.16.1 adds failed-link replacement, clearer payment status and an Instant EFT checkout shortcut. Automatic updates remain available to enrolled devices on version 0.16 and later. Provider method availability is separate from this client shortcut.

Install customer 0.18.0 or admin 1.7.0 once to enable checks on opening/Wi-Fi and automatic downloads. Tap Install after download; Android may need installation approval. Admin updates never restrict the admin phone. Update in place; keep account data and existing ownership. [Update guide](../docs/AUTOMATIC_APP_UPDATES.md).

Customer 0.18.1 is a visible update test: installed version on Home, gold release-notes strip and EFT-reference copy shortcut. Keep 0.18.0 installed to test automatic download before approving installation. See [test instructions](../docs/TEST_UPDATE_0181.md).

Customer 0.18.2 is an 18.6 MB test release with the realistic phone-padlock logo and R1,700 repayment plan. Admin 1.7.1 includes the new logo and plan reporting. Keep an older updater-capable app installed to test automatic downloading. Tap Install after download; Android may need installation approval. Admin updates never restrict the admin phone. Update in place; keep account data and existing ownership. [Update guide](../docs/AUTOMATIC_APP_UPDATES.md).

Customer 0.18.3 adds background Wi-Fi/mobile downloads and signed device-owner unattended installation. Existing versions need one approval to receive it. Future customer updates do not need the account screen open where Android permits background work and installation. Admin stays 1.7.1 with approval and no restrictions. Tap Install after download; Android may need installation approval. Admin updates never restrict the admin phone. Update in place; keep account data and existing ownership. [Update guide](../docs/AUTOMATIC_APP_UPDATES.md).

Customer 0.18.4 adds Copy payment summary in My plan and exact final-payment wording. Keep 0.18.3 installed and enrolled to test background updating while the phone remains online. See [test instructions](../docs/TEST_UPDATE_0184.md).

Customer 0.19.0 and Admin 1.8.0 introduce the ivory/coral/burgundy layouts, new Manrope/Outfit fonts, clearer payment access and restricted-only awake/startup handling. Background updates remain active independently of ChatGPT credits. See ../docs/IVORY_CORAL_REDESIGN.md. Tap Install after download; Android may need installation approval. Admin updates never restrict the admin phone. Update in place; keep account data and existing ownership. [Update guide](../docs/AUTOMATIC_APP_UPDATES.md).

Customer 0.20.0 and Admin 1.9.0 add one-use paid-access codes, support Call/WhatsApp at 063 724 6209, and new white/violet layouts with no red/burgundy branding. See ../docs/UNLOCK_CODES_AND_SUPPORT.md. Existing 0.18.3+ enrolled clients can receive background updates where Android permits. Tap Install after download; Android may need installation approval. Admin updates never restrict the admin phone. Update in place; keep account data and existing ownership. [Update guide](../docs/AUTOMATIC_APP_UPDATES.md).
