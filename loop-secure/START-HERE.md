# Start with Loop Secure

Open https://loop-secure-admin.loopsecure-bacanyclinda.workers.dev and sign in using the configured owner email and Cloudflare email code.

Download the signed customer/admin APKs or Windows update ZIP at https://github.com/basanisilinda-crypto/Bacy/releases/tag/loop-secure-v0.12.1 . The update ZIP preserves an enrolled phone's customer link. New enrollments require a private customer-specific ZIP generated in the dashboard; no public download contains an enrollment code.

For a new enrollment: customer name and phone number → Setup QR / Windows USB → download fresh ZIP → extract into its own folder → run Enroll-Windows.cmd → approve USB debugging → tap Connect customer account after Android confirms ownership.

If Android refuses because accounts exist, check phone eligibility after backing up/syncing and confirming credentials. If the helper cannot verify ownership, read Ownership-check.txt. A new download alone cannot repair unreadable storage or a permission failure. Do not force ownership or reset the phone to solve an unexplained error.

For an already released test phone, preserve the old account's history, confirm release on the handset and uninstall the old local app before using a new enrollment record.
