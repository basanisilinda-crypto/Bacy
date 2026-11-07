# Complete customer controls and new logo — 0.17.1

This release extends the colour-coded interface through all customer-app controls. The previous MaterialAlertDialog menus and stock MaterialButton controls are removed from the client activities. Banking selection, manual EFT details, manual claim confirmation, review status, device information, app updates, messages, support, USB enrollment confirmation and checkout notices now use the same native bottom-sheet system. Android-owned permission prompts, the operating system Settings application and external bank/provider screens retain their own UI.

A new folded-loop L/check logo in petrol teal, orange and gold replaces the old infinity mark in customer/admin app resources and website branding. The existing DM Sans/Fraunces font pairing is retained. The customer signing identity, device owner and enrolled account stay in place.

Six practical improvements accompany the design:

1. Last-opened approved bank is listed first on the next banking-app selection.
2. Payment history can be filtered locally by all, approved, pending or rejected, with counts from the signed account records.
3. Each displayed EFT destination value can be copied, in addition to the existing customer payment reference.
4. A connection panel distinguishes Android-validated Wi-Fi/mobile access from the last signed account contact, with account retry and Wi-Fi actions.
5. Branded, dismissible in-app notices replace customer-activity Toast feedback. The OS may still show its own clipboard/security notifications.
6. Support sending blocks repeated taps while pending, retains text after a network failure and shows an inline retry message. Text length matches the server's existing limit.

Financial checks are unchanged: a submitted manual EFT claim is still reviewed; only independently verified iKhokha PAID receipts add time; failed-link recovery never fabricates credit. Inbox read receipts, enrollment consent/ownership checks, same-signer updates and bank/OTP access retain their original authorization paths.

Customer 0.17.1/code 26 is released through the protected automatic-update channel used by enrolled 0.16+ phones. Wi-Fi, idle state, Android scheduling and device-owner installation eligibility still apply. Administrator 1.6.2 includes the updated shared logo and has a separate signed download. A ChatGPT subscription is not checked by either application.

Validation covers signed release build/lint for customer/admin, native screenshot rendering, payment callback/dismissal behavior, pending-state presentation, banking shortcut ordering and existing access, checkout and update-manifest regressions. Handset behavior and third-party provider availability remain separate acceptance checks; this design update does not enable a provider-disabled payment method or override Ozow bank support.
