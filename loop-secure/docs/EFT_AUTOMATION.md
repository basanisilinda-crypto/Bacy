# Loop Secure: verified EFT automation

The current live payment route is ordinary EFT plus an administrator's bank review. Customer claims and screenshots never unlock a phone by themselves. No voucher, Cash Send, eWallet or eMali integration is included.

## Recommended next connection

Ozow One API supports South African Pay by Bank including Capitec Pay. It requires an approved merchant account, client ID, client secret, site code and webhook secret. Merchant approval must cover financed phones and the proposed settlement account. Existing Capitec account details alone do not provide transaction-feed access.

- Onboarding: https://hub.ozow.com/getting-started/prerequisites-and-onboarding/
- Current One API: https://hub.ozow.com/getting-started/quick-start/
- Pricing: https://ozow.com/pricing
- Transaction verification: https://hub.ozow.com/api-reference/one-api/get-transactions-id/
- Payment states: https://hub.ozow.com/integration-methods/statuses/
- Settlement: https://hub.ozow.com/payment-methods/settlements-and-float/settlements/

Public pricing checked on 6 November 2026 lists 1.5% with a R1 minimum for Pay by Bank, subject to tax treatment and merchant terms. Do not use Ozow's deprecated Payments API for a new integration. No paid account or transaction was created during this work.

A provider-confirmed successful payment and a deposit settled into Capitec are different events. The merchant must choose whether successful provider confirmation is sufficient for instant access. Requiring the actual bank credit needs settlement/bank-feed reconciliation and can delay access. Ordinary transfers directly to existing EFT bank details do not generate Ozow callbacks; customers would use a new merchant EFT checkout for automated handling.

Paystack's South African EFT via Ozow is another documented option, requiring merchant compliance and additional EFT activation: https://support.paystack.com/en/articles/2132482 and https://paystack.com/za/pricing. Its public EFT fee is 2% excluding VAT. It advertises payouts after two working days. Payment success should not be described as already settled bank funds.

## Integration contract before enabling automatic renewal

1. Server creates a unique R300 ZAR obligation for an existing customer/device/instalment and records its provider reference before returning checkout details.
2. Provider webhook is checked against its current signature scheme and raw body. Ozow One API uses Svix signatures. Do not copy a deprecated integration or rely on browser return URLs.
3. Persist verified events durably, then independently query the provider. Require correct production merchant/site, final successful status, exact gross amount, currency and stored reference. Typed references, customer metadata or a claim are not authority.
4. A unique provider transaction and obligation key are credited atomically once. Replays must not add more paid days. Extend the existing signed entitlement, then wait for the phone's acknowledgement separately.
5. Retry transient provider failures, reconcile pending obligations, and flag refunds/reversals for review. Never treat uncertainty as a successful payment.
6. Separate notifications for claimed, verified, and needs-review events. Log no payment credentials.

Provider checkout and automatic crediting are not enabled in this release because no approved merchant account/API credentials are connected. No dummy success route or unverified auto-unlock was added.

## Administrator alerts and email

Native admin alerts use the existing authenticated encrypted session. Foreground refresh detects claims; an Android job performs best-effort network checks approximately every 15 minutes. Android may delay background work. Android 13+ requires notification permission. Alerts contain no customer names, references or amounts on the lockscreen and say a claim awaits review rather than saying funds arrived.

Automatic email additionally needs SMTP or an email-provider account and authorized sender credentials (plus domain verification where required). basanisilinda@gmail.com is the supplied support/recipient address; knowing it does not grant permission to send from Gmail. No automatic emails are being sent in this release. Configure a sender before adding a durable retryable email outbox to the same verified payment events.
