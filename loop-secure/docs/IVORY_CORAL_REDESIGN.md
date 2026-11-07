# Loop Secure 0.19.0 / Admin 1.8.0

## Visual direction

The customer's explicit rejection of charcoal is reflected throughout: bright ivory surfaces, coral actions, burgundy typography and pale rose/peach structural panels. There are no charcoal panels, glass layers or neon overlays. Outfit headings and Manrope body text are bundled for native/offline use, with locally served WOFF2 versions on the administrator website; their OFL licenses are included.

The customer Home layout is rebuilt around the next payment, verified paid/balance/total readings, a segmented repayment meter and compact tools. Navigation is a light fixed bar with a clearly marked destination. All existing action routes, bank/Settings/emergency access and account fields remain connected. Customer sheets, setup, notices and payment controls follow the same typography/palette.

Native admin has a new receipt-first overview, compact operational counts, action queue and account records. The website has a vertical desktop navigation sidebar, new register/toolbars, responsive mobile records and a consistent action hierarchy. The existing meaningful phone-padlock logo remains.

## Startup restriction and normal screen locking

Only restricted customer phones use the always-on payment screen, as the owner explicitly selected. Boot/package-replacement handlers and screen-wake events apply the cached signed policy before waiting for the network. A private latch remembers an already-confirmed restriction bound to the same device, revision and signed due date, allowing restoration after a reboot without inventing a new deadline from editable wall-clock time. A newer paid/unlocked policy clears it.

Restricted customer/payment activities set KEEP_SCREEN_ON. Device-owner keyguard suppression is requested only while restricted; normal keyguard and screen-timeout behaviour return after unlock. Android can retain an existing secure PIN or require the user's first unlock after reboot; hardware power/recovery controls remain OS/OEM controlled. No password, account or device data is removed. Active checkout is preserved on wake. Paid/unrestricted phones do not receive a new finance lock.

## Payment access and verification

The customer Home Make a payment button now opens secure online checkout directly, without a customer login/reference form. My plan retains alternative card/Instant EFT/manual bank-transfer choices. Bank/provider card details, consent and 3-D Secure verification cannot be removed by restyling Loop Secure.

Online payments still require authenticated provider confirmation and an exact verified amount/link before credit/unlock. Browser returns and customer claims do not mark a payment paid. Direct bank EFT still requires settlement review. The R1,700 total, exact final payment, saved account and update signing rules remain in place.

## Automatic updates and runtime

The existing server-driven updater remains active. Existing 0.18.3+ enrolled device-owner clients can download and attempt this update in the background on working Wi-Fi/mobile data. Android scheduling and installer policy control timing/approval. Native admin remains unrestricted and retains its approval-based installation flow.

The apps and hosted server do not depend on ChatGPT/Codex credits or an active chat. Hosting, database and payment-provider availability remain their own services. No OpenAI API is used for app operation.

Validation covers SDK 30/35 policy/awake/restoration regressions, existing payment/owner/update checks, native screenshots and navigation bounds, website browser fixtures and authentication/relay tests, same-signer builds/lint and public hosted-byte verification. Real boot/wake timing, hardware keyguard behaviour and actual provider payment still require handset confirmation.
