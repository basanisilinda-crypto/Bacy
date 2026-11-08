# Customer 0.20.0 / Admin 1.9.0 — recovery codes and support

Support number: **063 724 6209** (+27 63 724 6209). WhatsApp: https://wa.me/27637246209. The customer Support screen provides Call, WhatsApp and Enter unlock code. The administrator app Settings and website footer also expose contact links.

## Administrator workflow

1. Verify the actual payment. iKhokha checks the exact provider PAID result; manual bank EFT must be approved against settled funds. Generating a code also reconciles recent online attempts first.
2. Open the customer in the administrator website and select **Generate unlock code**, or use the same button on that customer's native admin record.
3. Copy the code or choose **Share on WhatsApp**. This opens a message composer for the recorded customer phone; the administrator decides when to send. It is not an automatic message to every customer.
4. The updated customer app (0.20.0+) opens **Support → Enter unlock code**. Restricted Home also has an Enter administrator code shortcut. Enter all ten digits and select Verify and unlock. Internet is required.

## What the code authorizes

The code recovers only already-recorded paid access. The server requires an approved payment and an unexpired paid period, or a verified full R1,700 payoff. It does not add a payment, extend a due date, turn a pending claim into a paid claim, remove a bank PIN or bypass an Android password. Valid redemption clears a manual financial lock and returns a fresh signed account. Automatic online payment/unlock remains the primary path.

Each code is specific to one phone, expires within 30 minutes (earlier if paid time ends), and is single-use. A new code replaces the old one. Five wrong attempts disable it. A newer payment/control revision or device credential rotation invalidates an unused code. Issuance is limited to five per phone per hour. A network retry with the same private request key is idempotent and returns current account state rather than unlocking again; a later admin lock is not bypassed.

The server generates ten unbiased random decimal digits, preserving leading zeros. Only a device-bound HMAC keyed by the server secret is stored in the private RLS-protected configuration table; the PIN is returned once to the authenticated administrator. Activity/audit records contain the event but not the code. Browser storage/exports never retain the PIN, and closing the dialog clears its display/sharing link. Customer redemption requires the phone's own authenticated device token. Database helper execution is service-role only with an empty search path and invoker security.

## Restricted-phone support access

While restricted, an explicit Call or WhatsApp action permits the genuine default dialer or installed com.whatsapp/com.whatsapp.w4b for at most three minutes, or until returning to Loop Secure. Boot-bound monotonic timing prevents a reboot from extending the lease. Financial/reset/uninstall protections remain active; payment updates defer during the contact session. The app does not become the default calling app, place a call automatically, read messages, read call logs or automatically send WhatsApp messages.

Android's standard dialer does not offer a per-number allow rule: opening a support-call session temporarily permits outgoing calls in that dialer, so the user can edit the prefilled support number. WhatsApp similarly allows its own app navigation during the short session. Ordinary restrictions resume after return/expiry; emergency calling remains available. There is no unrestricted browser fallback on a locked phone when WhatsApp is absent—Call support is offered instead.

## Updated visual direction

The new release removes red/burgundy branding in favour of white and violet, with soft lavender structure and gold warning states. Customer Home puts the verified plan ledger before payment actions. The website switches from a sidebar to compact horizontal navigation and white summary surfaces; native navigation uses a slim violet destination marker. Existing Manrope/Outfit fonts and meaningful phone-padlock logo remain. Customer/financial data is preserved.

## Deployment and validation

Apply `cloud-api/unlock-codes.sql` after the existing schema/plan migrations, then deploy the Edge Function including `unlock-codes.ts`. Run `check_unlock_codes.sql` with its explicit rollback: unpaid rejection, wrong code/phone, expiry, replay, idempotence, new-lock invalidation, attempt/issue limits and unchanged paid amount/deadline are checked without committing fixture records.

Native builds/lint and SDK 30/35 owner/payment/update/support-lease checks, web authentication/browser fixtures and server HMAC fixtures verify the implementation. Handset call/WhatsApp opening, actual code redemption and background installation still require device confirmation. Runtime updates and the hosted service operate independently of ChatGPT/Codex credits, subject to the separate hosting/payment services remaining available.
