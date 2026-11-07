# Loop Phone Manager — product requirements

> Latest handset correction: the user calls the reset test handset **Note**. The manufacturer/model will be read from the handset during enrollment.
> This is the original product brief. Current implementation and verification status are in docs/DELIVERY.md and docs/BUILD_REPORT.md.

## Purpose and current status

A South African phone-finance business needs a real native Android customer app, a connected backend, and an administrator dashboard. The reference products are PayJoy, Watu, and M-KOPA. The requested outcome is production functionality, including actual device restrictions and unlocking, rather than simulated controls or mock payment data. No implementation or handset validation has been completed in this conversation.

The user describes a stock of new Rain Loop phones running Android 16. The exact model, firmware, manufacturer management capabilities, and Android version need verification on a handset. Rain Loop is the user's description, not an independently verified model identification.

## Commercial and payment workflow

- The customer pays R300 for a 30-day service period using EFT only.
- Each customer has an assigned payment reference displayed in the app.
- The app displays a 30-day countdown and payment deadline.
- After paying, the customer taps “I have paid.” Returning from a banking app should offer this action without assuming a payment actually occurred.
- A payment claim notifies the administrator. Optional proof-of-payment attachments can support review but do not establish that funds settled.
- The administrator checks the actual bank receipt and approves or rejects the claim.
- Only an approved payment authorizes another 30-day period and unlocking. The connected handset must acknowledge receiving and applying the change; an admin command being sent is not proof of unlock.
- Repeated submissions and retries must not grant duplicate credit.
- Whether a renewal starts from the previous deadline or the approval date remains undecided. Calendar-month billing is not interchangeable with the requested 30-day period.

## Native Android enrollment

Requested enrollment sequence:

1. Scan a provisioning QR code during the initial setup of a new or factory-reset handset; download and enroll the management app.
2. An authorized administrator chooses supported device policies.
3. Register customer information, identity details, device association, and informed consent.
4. Show the customer dashboard with the payment amount, reference, countdown, status, and support.

The user requests device-owner management, restrictions on uninstallation and removal of management, factory-reset controls, Wi-Fi controls, and phone-update controls. Exact policy choices and their default values are not yet settled. Preserve connectivity needed for banking, support, and receipt of unlock instructions. Security updates need a deliberate policy rather than an assumed permanent block.

Identity details must be verified through an appropriate process; name requirements and ID-format checks alone cannot establish a real identity. The user also requests IMEI and SIM association where the platform and permissions permit access. Account for dual-SIM handsets and identifier access restrictions.

## Customer dashboard and restricted mode

- Professional native design with intentional typography and layout; avoid generic dashboard styling.
- Display R300 due, days remaining, due date, EFT reference, payment history, payment-review status, and support.
- When a valid deadline expires, apply genuine supported restrictions on other apps.
- Keep approved South African banking apps available, including Capitec and TymeBank if those are the intended banks; the spoken bank name needs confirmation. The precise allowlist remains undecided.
- Keep emergency calling and support available, with any dependencies necessary for banking authentication and network connection.
- Provide reminders before expiry, an appropriate grace/review process for delayed payment confirmation, and a recovery route.
- Release finance restrictions permanently after full payoff.

## Administrator dashboard

The customer app must be native. The administrator interface may be a web app or a separate app.

- Search and manage customer and device records.
- Review claimed payments and supporting evidence.
- Approve or reject claims and record reasons.
- View deadlines, overdue status, connectivity, and confirmed device restriction/unlock status.
- Apply supported policies through authenticated, authorized actions.
- Keep an audit history of enrollment, policy changes, payment approvals, deadline changes, unlocks, and final release.
- Provide staff roles and two-step administrator authentication.
- Provide payment history/receipts that distinguish submitted claims from confirmed receipts.

## Reliability and security

- Connect the customer app, backend, and admin dashboard to real persistent records and authenticated services.
- Do not trust the phone's editable wall clock as the sole source of payment entitlement. Design deadlines around server-authorized entitlement and appropriate monotonic time handling.
- Define safe behavior for reboots, prolonged offline operation, lost connectivity, and resynchronization. Absolute offline clock-tamper resistance across resets cannot be promised from ordinary app code alone.
- Protect customer identity documents and device records with access controls, secure transport/storage, and a defined retention policy appropriate to South African privacy requirements.
- Do not store or collect customers' banking credentials or intercept banking traffic.
- Verify bank app compatibility, emergency access, enrollment, expiration, payment review, and unlock recovery on the actual target handset.
- Customer disclosure, consent, contractual authority, grace periods, and final-release conditions remain to be specified explicitly.

## Platform research established so far

Official Android documentation describes QR provisioning for new/factory-reset managed devices and a financed-device locking API. This establishes available platform concepts, not entitlement for an arbitrary app or compatibility with these particular phones.

- Device-owner provisioning documentation: https://source.android.com/docs/devices/admin
- Financed-device API documentation: https://developer.android.com/reference/android/devicelock/DeviceLockManager
- Provisioning documentation: https://developers.google.com/android/management/provision-device
- Device security and reset protection: https://developer.android.com/work/dpc/security

Determine the legitimate deployment route for consumer-finance devices, required financed-device role or privileged permissions, manufacturer integration, and applicable distribution/API eligibility before selecting an enforcement architecture. Enterprise management APIs are not automatically approved for consumer-finance locking. An ordinary device-admin permission prompt does not provide device-owner or financed-device privileges. A settings factory-reset restriction does not prove resistance to recovery resets or firmware reflashing.

## Business identity and contact details

Confirmed support email, supplied in writing: basanisilinda@gmail.com

The user's legal name, business name, branding, and support/WhatsApp number have not been confirmed in writing. The spoken phone number and names were ambiguous. Do not derive a legal identity from the email address or publish guessed contact details. EFT destination bank details have not been supplied.

## Delivery expectations and unresolved dependencies

The user wants the complete feature set developed together and rejects a simulator or mock-data deliverable. Hardware-dependent functionality must be described honestly and validated before being represented as working. The user has not chosen a backend provider, identity-verification provider, hosting service, app signing/distribution route, or final design identity. No repository, deployed backend, signed APK, or hardware test result exists yet.

The first feasibility milestone is a real QR enrollment and supported restriction/unlock test on a target handset. The remaining product can be developed against genuine authenticated services, with unsupported enforcement clearly identified rather than represented by fake success states.
