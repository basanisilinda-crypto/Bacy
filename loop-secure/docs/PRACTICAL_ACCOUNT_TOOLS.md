# Practical account tools

Customer 0.24.0/code 37 and native administrator 1.12.0/code 18.

## Customer additions

**Home → Details / Your plan** now shows the phone price, approved total, remaining balance, percentage, remaining payment count, next amount, payment date, restriction date and reference together. Figures come from the signed account summary; missing data stays unavailable. A submitted claim is not treated as a received payment. Existing copy-summary, copy-reference and payment actions remain.

**Support → Payment & phone help** contains offline answers about grace/countdowns, delayed payment confirmation, direct EFT versus hosted checkout, five-minute codes, banking/OTPs, debugging restrictions and restart time verification. These are bundled explanations, not a claim that the network or bank was checked. Help links back to support without sending a message automatically.

Rejected payment claims are labelled **Not approved**. A claim-review decision is not represented as a card decline from the bank.

## Native administrator additions

**Payments** has local search by customer name, account reference, bank reference, receipt reference or recorded state. The query filters only the authorized records already loaded and is not sent to a search service or persisted to storage. Search does not approve/reject anything; the existing settled-funds review remains required.

Customer records include **Call customer**, which opens a validated number in the dialer for the administrator to review and call. It does not place a call automatically or request call permissions. Invalid/missing numbers and devices without a dialer are handled explicitly. **Copy account reference** copies the selected record's exact reference to the clipboard.

## Preserved behavior and update

Developer-options/ADB restrictions, early startup recovery, offline monotonic timers, five-minute courtesy codes, the R1,700 plan, signed updates and bank/Wi-Fi/settings/support/emergency access are unchanged. The admin phone is never restricted. Native apps retain their package names and release signer; update in place, without uninstalling or clearing data. After debugging protection is active, use the signed in-app update channel rather than USB tools.

No new database schema, payment-provider capability, external messaging integration or website feature is introduced in this native release. Payment screenshots, bank-app interoperability and installation still require physical-phone acceptance checks.
