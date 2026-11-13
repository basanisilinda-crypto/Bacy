# Original Loop Secure: owner and customer setup

Use the protected business dashboard: https://loop-secure-admin.loopsecure-bacanyclinda.workers.dev/ . Sign in with the configured owner account. This is the real production system, not the independent Rain evaluation.

## New customer phone

1. Create or select the intended customer in the dashboard and check the plan details.
2. Choose that customer's Setup QR or Windows USB setup. Download a fresh customer-specific bundle when needed; no public source/download ZIP contains a customer enrollment code.
3. For QR, use Android's welcome-screen setup scanner on an eligible phone. A normal camera scan is not Device Owner provisioning.
4. For Windows, extract the fresh ZIP into its own folder, connect one authorised phone, enable/approve USB debugging and run START-HERE.cmd. Do not mix it with old ADB/enrollment files.
5. Keep internet connected and wait for the actual phone/account report. Installation and Device Owner assignment alone do not confirm customer linking.
6. If Android reports accounts or another owner, resolve that exact condition. Back up before any reset. Do not clear an existing customer app's data or reset to solve an unexplained error.

## Already enrolled phone

Use the signed in-app update path. Updates must retain the original package and release signer. Customer0.27.0/code40 is published; admin1.14.1/code21 is unchanged. Android controls background execution and may require approval for an unmanaged admin install. A rebuild with the same version code does not trigger an update. Do not uninstall a linked customer app to solve an update conflict.

If the phone is Device Owner but unlinked, customer0.27 offers Connect enrollment code. Use a fresh code for the correct customer. Preserve any saved account; see Ownership-check.txt for USB errors.

## Engineering continuation

Read PRODUCTION-CONTINUATION.md and AGENTS.md. The owner authorised Codex to sync this production repository in the current session. Future ChatGPT sessions must use a separate working copy and leave original Bacy files untouched unless the owner explicitly authorises a change. Keep the Rain test package and its self-release/no-payment policy out of production.
