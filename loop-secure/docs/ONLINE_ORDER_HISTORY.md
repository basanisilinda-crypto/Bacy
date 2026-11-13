# Online order IDs and verified unlocking

Customer remains 0.26.0/code 39. Native admin 1.14.1/code 21 adds online order history; the protected website has the same history. This release adds reporting without changing the live payment-confirmation, device-control or enrollment functions.

Each iKhokha payment link has a unique UUID order ID (`externalTransactionID`). Retries within one hosted link share that order ID. The admin website Payments view shows the latest 200 stored orders; native admin shows the latest 50 of those, with a copy-order-ID action. Orders show customer/reference, amount, created time and current outcome. This is order history, not a log of every bank-internal card attempt or a bank decline reason: iKhokha's supplied callback contains a success/failure outcome but no detailed bank reason.

- **Payment verified**: the stored order has passed server-side provider confirmation and payment posting.
- **Checkout reported failed / attempt limit / cancelled**: the reported checkout outcome is displayed; it never grants paid access.
- **Awaiting confirmation**: the payment is not verified. A success return page alone never changes this to paid.
- **Needs review**: mismatched fields or a payment needing manual settlement/refund review; no automatic credit is claimed.
- **Link creation failed**: the provider could not create a usable checkout, distinct from a card decline.

A late verified payment can supersede an earlier reported checkout failure. The reporting service only reads existing records and cannot credit payments or unlock phones. Existing iKhokha verification requires the exact stored link, integer amount and currency; authenticated callbacks trigger a provider status check. The existing atomic confirmation posts one approved receipt, extends paid time and clears the finance lock once. The phone applies the signed account state on its next successful sync; an offline phone cannot receive a remote payment update immediately. Direct bank EFT still requires settled-funds review. Only iKhokha is configured; no PayPal integration is added.

## Isolated deployment

`loop-order-history` is a separate read-only Supabase Edge Function, using `orders-index.ts`, `orders-handler.ts`, `order-history.ts` and `deno.json`. It uses Supabase's server environment credentials and authorizes the existing owner token or an unexpired non-revoked native-admin session. Anonymous/customer tokens are refused, write methods return 405, secrets/checkout URLs are not projected, and responses use no-store. Queries are bounded to the latest 200 orders and the corresponding customer/device IDs, avoiding a full fleet scan.

The website relay maps only `/api/admin/orders` to this function after existing Cloudflare Access owner verification. The native admin makes the same authenticated request. Failed retrieval is shown as unavailable, never as an empty successful history. The shared `loop-manager` production function remains version 30, and the blocked custom-price migration is not applied.

## Validation and limits

Four isolated-handler authorization/read-only tests, three projection tests, 14 gateway-signature/status checks, eight Worker authentication/relay tests, browser checks of order IDs and failed/verified labels, 24 native admin tests, release build and lint passed. The existing payment SQL fixture passed in an explicitly rolled-back transaction, verifying that a confirmed payment clears manual_lock, extends exactly one period and cannot be credited twice. No fixture or real charge was created by these tests.

The live order endpoint was checked for authenticated access, rejection of anonymous access and write requests, and exclusion of private fields. A live inventory check found no order marked provider-verified PAID at the time of checking. Therefore the full real-card → callback → physical handset unlock journey still needs a genuine successful payment and handset acceptance check. A bank authorization/return page or a pending claim is not accepted as paid.

Custom total/monthly plan configuration remains staged on work/custom-plan-orders because automatic review rejected replacement of core financial functions, even after explicit approval. The independent reporting service was accepted and deployed without modifying those functions.
