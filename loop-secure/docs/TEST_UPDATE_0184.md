# Customer 0.18.4: small update for the background channel

Two small changes:
- **My plan → Copy payment summary** copies the signed account's total price, verified paid amount, remaining balance, repayment percentage and next payment. Missing summary data requests an account refresh. The text describes the last verified account check and excludes pending claims from paid funds; copying does not change the financial account.
- Manual bank-transfer details use the amount due from the signed account, including a smaller final instalment. Fresh-link guidance no longer describes every payment as R300.

Keep **0.18.3 installed and enrolled**, and leave the phone online using Wi-Fi or mobile data. The management connection/background scheduler can discover, download and attempt installation without opening the app. A payment checkout defers installation. Android controls background timing and may still require approval. Do not install the APK manually first if you want to test the unattended channel.

After allowing time for the update, open Loop Secure to confirm **v0.18.4** on Home, then **My plan → Copy payment summary**. Opening afterwards confirms the result; it is not required to trigger the 0.18.3 background updater. Older versions still need the one-time updater-capable installation described in BACKGROUND_CUSTOMER_UPDATES.md.

Admin remains 1.7.1. R1,700 totals, payment approval, signing identity and account/device ownership are retained. Validation includes release build/lint, existing SDK 30/35 native UI/updater regressions, signer and hosted-byte checks. Actual unattended download/install success must be confirmed on the user's phone.
