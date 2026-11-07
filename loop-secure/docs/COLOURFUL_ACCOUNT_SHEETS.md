# Colourful account sheets — 0.17.0

The customer app now replaces its plain payment menu, account summary, online-status and error alerts with native bottom sheets. DM Sans and the existing Fraunces heading font are retained.

- Card checkout uses a coral action card, Instant EFT uses gold, and manual bank details use mint. Status and fresh-link controls sit side by side below the payment choices, with independent icons and descriptions.
- Account details use a mint plan summary and individual labelled rows for the paid deadline, grace deadline and EFT reference, followed by payment/copy actions.
- Status is coloured from the real response: verified is mint, pending is gold and exhausted links use coral. The layout never invents a successful payment. Existing provider-verification and bank-debit warnings remain.
- Sheets scroll, respect a maximum screen height, have a visible dismiss control and close before invoking the next financial action. This avoids stacking the old menu behind a new status dialog.
- Quick-access tiles use complementary pastel surfaces and a stronger teal banking tile. Existing payment and management actions remain connected to the same controllers.

Version 0.17.0/code 25 is published through the existing signed managed-updater channel. Enrolled phones on 0.16 or later can receive it during a scheduled unmetered Wi-Fi check while idle. This is not an immediate push guarantee; older clients require the one-time updater installation. No new enrollment, factory reset or account replacement is part of this update.

Validation includes native screenshot rendering of the new payment and account sheets, a callback test checking the payment menu closes before launching the selected action, pending-status tests excluding a verified-payment message, existing client-access/checkout/update regression cases, signed release build/lint and hosted-byte verification. Visual QA fixtures are local only and contain no customer records. Provider availability, Ozow bank selection and actual card/OTP completion remain governed by the payment provider and handset.
