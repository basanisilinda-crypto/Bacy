# iKhokha online payments

Loop Secure uses **Payment API → iK Pay hosted payment links**. Customers choose when to pay; this is not a recurring debit. The customer app is version 0.16.0 (Android 11+). Existing installations must be updated in place, retaining their customer connection and device ownership.

## Customer steps

1. Install the signed customer update over the existing app. Do not uninstall or enroll again.
2. Keep the phone online. Tap **Make a payment → Pay online with iKhokha**.
3. Complete the R300 checkout on iKhokha's hosted page. Available payment methods depend on the merchant account and checkout.
4. Return to Loop Secure. A verified payment adds 30 days, clears the finance lock and updates the phone at its next successful sync. **Make a payment → Check online payment** retries confirmation if needed.

Checkout includes **Messages / OTP**, **Banking app**, **Check payment** and **Open in browser**. Use Messages to copy a bank verification code and return with Back or Recents. The physical-card flow can use the browser on an unrestricted phone; if a bank step cannot complete on a restricted phone, use **Copy link** and pay from another device. The payment still belongs to the original customer. Do not submit the same iKhokha payment as a separate EFT claim. Direct bank EFT remains available but requires the existing administrator settlement review.

## Administrator steps

Open the administrator website, then **Payments → iKhokha connection**. It shows whether a merchant connection is saved and enabled. Blank credential fields retain the saved values; secrets are never returned to the browser. The supplied credentials have been configured privately in the live backend.

To send a customer a link yourself, open that customer's **Manage** panel and choose **Create R300 payment link**, then **Copy payment link**. Verify the intended customer before sharing. Approved online payments appear in the payment register automatically, with reviewer `ikpay-verified`. Existing native administrator apps see these approved claims through their normal account refresh.

## Verification and data handling

- API amount is fixed server-side to 30,000 ZAR cents. Browser or app amounts cannot override it.
- The server signs the exact request body with HMAC-SHA256 using the provider's documented escaping. Credentials are encrypted in private server configuration, excluded from APKs, browser assets and GitHub.
- Signed callbacks must match the configured merchant application and a stored customer/payment-link pair. A success callback does not itself credit an account: the server fetches the provider's status and requires `PAID`, the exact payment-link ID and integer amount of 30,000 cents. Returned currency/external reference must match when present.
- Only the service role can run the financial confirmation function. It locks the intent and device, records one unique approved receipt and advances the paid deadline exactly once in the same transaction. Duplicate callbacks do not create duplicate paid periods.
- A permanently released phone is never re-enrolled or re-locked by a late payment; its payment is flagged for settlement/refund review.
- Device sync and administrator refresh reconcile pending links if a callback was missed. Reconciliation is throttled and bounded. The phone must connect to receive a signed updated entitlement; a payment cannot remotely unlock a phone that remains offline.
- Checkout redirects, screenshots, submitted references and this return page never prove payment. Card entry happens on the provider's hosted checkout, not in Loop Secure forms.

## Production endpoints and deployment

- Callback: `https://ourxezawbfclmhbggskv.supabase.co/functions/v1/loop-manager/payments/ikhokha/callback`
- Public return page: `https://loop-secure-pay.loopsecure-bacanyclinda.workers.dev/return`
- Private administrator: `https://loop-secure-admin.loopsecure-bacanyclinda.workers.dev`

The API sends these callback/return addresses when creating each link. The return Worker is separate from the administrator's Cloudflare Access application, has no secrets, does not call the database and does not grant account credit. Keep it publicly accessible for hosted checkout returns.

New installations apply `cloud-api/ikpay-confirmation.sql`, deploy the existing Edge Function including `ikpay.ts` and `ikpay-gateway.ts`, and deploy `cloudflare-pay/worker.mjs` using its Wrangler configuration. Preserve the private administrator secret when updating `cloudflare-admin`. Configure merchant credentials through the authenticated owner-only endpoint/UI. No ChatGPT/Codex subscription is checked by these services. Existing hosting/provider limits, merchant approval and payment processing charges still apply.

Documentation: [iKhokha overview](https://developer.ikhokha.com/overview), [official request signing example](https://github.com/ikhokha/ik-pay-api-examples/blob/main/nodejs/index.js).

## Validation and remaining acceptance check

Passed: official request-signing fixtures, callback tampering and wrong-intent rejection, exact amount/currency/link checks, redirect rejection, database confirmation and duplicate-credit checks inside an explicit rolled-back transaction, restricted SQL execution privileges, Android checkout/banking unit tests, signed release lint/build, administrator browser and Cloudflare relay tests, live unsigned-callback rejection and public return-page availability.

**A real card payment has not been made.** Before relying on this for customers, use an enrolled test phone to complete one legitimate R300 payment and verify one approved claim, one 30-day extension and the phone's acknowledged unlock. Merchant account activation, actual bank/3-D Secure redirects, provider settlement and handset behavior can only be confirmed by that live test. Do not repeat payment if it appears debited but still pending; reconcile/check with the provider first. Administrator SMS/email delivery is not configured by this integration.

See [payment recovery and protected automatic updates](PAYMENT_RECOVERY_AND_UPDATES.md) for version 0.16.0 fixes, the current UNPAID observation, saved-card limitations and handset acceptance checks.
