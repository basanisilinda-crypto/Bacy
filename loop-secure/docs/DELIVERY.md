# Delivery status

This document distinguishes source implementation, verified software behavior, and deployment/hardware dependencies.

## Implemented in this repository

- Native Android app targeting Android 16 (SDK 36), without a WebView.
- Device-owner provisioning handlers and admin-extra import; administrator-generated one-time setup QR.
- Android setup consent, automatic authenticated customer details, paid-time display, EFT reference copy, banking app launch, payment-claim dialog on return, payment history, support messages and emergency-dialer shortcut.
- Actual DevicePolicyManager reset-in-Settings, management-app uninstall, Wi-Fi-configuration restrictions, and system-update installation windows when enabled by the owner.
- Permission-gated IMEI collection and SIM carrier/country metadata, encrypted on the backend and visible only through an audited owner endpoint. Denied or unavailable identifiers are reported explicitly. SIM serial numbers and hardware attestation are not collected.
- Managed lock-task mode for overdue devices, with an explicitly configured bank-app allowlist and default dialer. This requires device ownership; ordinary screen pinning is not counted as successful restriction.
- Policy changes and fully paid release, including clearing app-imposed restrictions and relinquishing device ownership.
- Encrypted device-local storage using Android Keystore; HTTPS-only API calls; pinned Ed25519 entitlement verification.
- Server-authorized deadlines projected using elapsed time within a boot, signed revision checks, and offline uncertainty handling after restart.
- Persistent customer/payment/support records in SQLite; encrypted identity numbers; hashed enrollment/session/device tokens; password hashing; role checks; authenticator codes with replay protection; secure cookie sessions and same-origin write protection.
- Administrator dashboard for identity-check records, enrollment, QR creation, customer search, policy configuration, payment review, support inbox, audit events and payoff release.
- Atomic payment approval, unique bank transaction references, duplicate/concurrent review protection, separate requested-versus-acknowledged device state.
- Targeted and all-active customer messages with durable delivery/read receipts, signed inbox synchronization, native inbox and privacy-preserving notifications.
- Owner-only fleet policy changes that exclude released phones, plus dashboard configuration of verified EFT destination details.
- Visible Android device-owner foreground connection for live updates, authenticated long-polling with reconnect backoff, and JobScheduler fallback.
- Signed release APK, private signing backup, hosted APK endpoint with automatic checksum/URL in the QR form, and a non-root Docker/Caddy deployment package.

Enrollment asks only for name and phone. Policy, price, period and grace defaults are automatic. The operator records customer agreement by generating enrollment; Android still presents system management consent. No identity-verification result is invented. The client activates and loads its account automatically from QR provisioning.

## Requires user configuration

- Hosting is configured. A business backup/restore and account-recovery plan is still needed before customer rollout.
- Owner/staff account setup and authenticator enrollment.
- Verified EFT destination details.
- Verified installed banking package IDs, package publisher identity, banking dependencies and emergency access.
- Securely retain the supplied release signing identity and private backup. The APK download URL is generated once this backend is hosted on trusted HTTPS.
- Written finance agreement, disclosure, customer consent, identity-check process, retention rules and the business's final legal identity.
- Confirmed support phone/WhatsApp number if phone support is desired. Only the support email has been confirmed.

## Not implemented or not established

- No privileged DeviceLockManager/financed-device role integration, manufacturer partnership or entitlement has been obtained. Do not infer this from generic device-owner code.
- No physical Note enrollment, bank compatibility, emergency calling, factory reset or lock/unlock test has been performed.
- No guarantee against recovery resets, reflashing, root access, locked bootloader attacks, hardware changes or offline restarts.
- No guaranteed on-the-second expiry or delivery. The foreground connection long-polls for changes, with Android-scheduled fallback work. A disconnected or OS-suspended handset cannot immediately receive approval.
- No external identity-verification service; manual checks are recorded. The system cannot guarantee that an operator has rejected every fictitious name.
- No bank feed integration. Payment settlement is manually verified.
- No proof-of-payment upload in this version; reference claims and review notes are supported. Proof screenshots alone would not establish settlement.
- No SIM serial-number collection or identifier attestation. Actual IMEI availability still depends on the handset, device-owner route and Android permissions.
- No indefinite operating-system update blocking. The administrator can choose a daily update installation window; system behavior and exceptions need handset validation.
- No automatic South Africa location enforcement. The product uses ZAR, EFT and an administrator-approved banking allowlist; it does not track location or block travel.
- No verified WhatsApp integration, automated email/SMS reminders, staff-account recovery UI, customer export/deletion UI or external production monitoring. Administrators can reply to support requests using targeted customer messages. Android local payment reminders, server health checks and an administrator support inbox are included.
- The cloud API, signed installer and private administrator Site are deployed. No real handset enrollment or customer rollout has been verified, and there is no app-store distribution.

## Enforcement defaults

The owner must opt into enforcement after validating the device. Banking packages default to empty. The app will not newly enter kiosk mode if it cannot find any configured banking application, and reports an actionable configuration problem instead. That check establishes installation, not that a bank app works inside kiosk mode.

After an offline reboot, the monotonic time anchor is unavailable. This implementation asks for an online check instead of trusting the editable wall clock. Offline reboot is therefore a known enforcement gap. Stronger finance protection requires the appropriate OEM-supported route.

Approval grants 30 days from the later of the current paid-through deadline or approval time. Default grace is 24 hours. These are documented business assumptions and can be changed before rollout.

The database records authenticated client acknowledgements. A compromised handset can lie; these reports are not hardware-backed attestation. Audit rows are application records, not externally immutable logs.

## Automatic enrollment update — 0.4.0

Native Material UI, automatic customer binding, supported permission grants and retry-safe activation are implemented. New enrollments use R300/30 days/24-hour grace, automatic reset/uninstall restrictions, and overdue navigation/notification-shade/outgoing-call restrictions with emergency access preserved. Pending QR refresh keeps the same customer/reference. Signed installer downloads are immutable and checksum-verified. Physical handset acceptance remains outstanding.

## Independent local enforcement — 0.4.1

Local signed-deadline checks no longer share the network request/reconnect loop. Android service lifecycle still applies; five-second checks while the service runs do not guarantee enforcement while Android suspends or terminates it. PayJoy resources were reference material only; this does not add manufacturer financing privileges or establish PayJoy-equivalent resistance to reset/reflash.

## Account recovery and management visibility — 0.4.2

Added distinct active, grace, overdue, unknown-clock and released payment phases using the signed account and elapsed-time projection. The customer sees the exact grace end and remaining grace time. The screen refreshes when a phase changes instead of waiting for a network update. Released accounts do not show overdue restrictions.

Pending claims now show a review action instead of another submission action. The client guards duplicate confirmation dialogs and simultaneous submissions; the existing backend remains authoritative and already deduplicates pending/idempotent claims. Failed attempts retain the request key for a safe retry.

A native management-status dialog reads actual device ownership, managed-lock versus ordinary screen pinning, policy enablement, trusted-clock status, last signed account check, Android connectivity validation, declared permission grants, applied reset/uninstall/call restrictions and approved banking launchers. It does not infer bank compatibility or manufacturer protection from these observations.

Pure Java boundary checks cover due-time transition, final grace second, overdue threshold, zero grace, restart uncertainty, release and an approved extended deadline. Native release build/lint and signed download checks are recorded by the delivery workflow. Physical handset policy behavior and interactive UI acceptance remain unverified.

## Restart recovery, reminders and administrator alerts — 0.4.3

A one-off recovery job is requested after Android boot-completed and same-package replacement, alongside the existing foreground connection and periodic fallback. Android chooses actual scheduling; the requested short deadline is not a guarantee. This does not claim execution before the first user unlock or a trustworthy offline reboot clock. The existing policy refuses a new deadline-based lock when its elapsed-time anchor cannot be verified after restart.

Payment notifications now use signed-account phases: approaching payment (three-, two- and one-day buckets), grace, overdue, and post-restart verification. A durable key prevents repeating the same reminder on every check. Paid-off or newly extended accounts cancel obsolete reminders. Denied permissions, disabled channels and notification/storage failures cannot interrupt enforcement. Notifications contain no customer name, reference or account number. Local checks, signed sync and fallback work can update reminders; Android suspension and notification settings still affect delivery.

The private dashboard has a device-health attention panel based on actual existing overview records. It flags contact older than 15 minutes, reported policy failures, unsupported management, restart verification, missing approved banking apps, unacknowledged revisions and unconfirmed final release. An overdue/unrestricted alert requires an enabled policy, elapsed grace, a fresh report after the restriction deadline, and acknowledgement of the latest revision. A stale report alone is not proof of a current lock failure. These are dashboard alerts refreshed while open, not email, SMS or system push to the administrator.

Validation: pure Java payment/reminder boundary and deduplication checks; administrator alert classification checks for grace boundaries, stale versus fresh evidence, pending revisions, release and unsupported management; Android release build/lint and signing verification. Physical restart, notification delivery, banking and lock/release acceptance remain outstanding.

## One-click access controls and separate preview — 0.5.0

Customer management opens with Lock, Unlock and Give 24-hour grace buttons, with no typed control fields. Advanced policy and permanent-release forms are collapsed. Commands are owner-only, audited, revisioned, idempotent and server-authorized. Released or unenrolled phones reject controls. Lock requests set a signed manual lock. Unlock clears that lock only while paid time or explicit grace remains; an overdue account instead requires settled-payment approval or an explicit grace action. Granting grace does not create a payment or extend the contractual paid-until date. Approved payment clears manual locks and temporary grace, then grants the existing 30-day renewal. Policy/fleet edits preserve access-control fields.

On the latest managed client, signed manual locks and explicit grace deadlines are enforced by the policy controller. A validated network transition wakes reconnection backoff and requests fresh signed account data before returning to long polling. Failed restrictions continue to be retried locally. Android scheduling, network request timeouts and reboot clock uncertainty remain relevant; this is not an exact-time or manufacturer anti-reflash guarantee. Older clients must be updated to understand the new signed fields.

A separate native Phone Finance Preview application has its own package and contains layout examples only. It has no device-admin components, network access, enrollment receiver or payment submission. Preview account states are labelled examples rather than live records. Its download and metadata are separate from the real QR installer. AndroidX contributes a private signature receiver permission; there are no device-management or runtime-dangerous permissions.

The owner-supplied Capitec savings destination was saved through the authenticated banking API and read back. Universal branch code was checked against Capitec's official FAQ. Account ownership is not independently verified. Actual banking values stay in protected cloud settings and are delivered in signed customer entitlements, rather than embedded in the preview or source.

Validation: 29 backend workflow tests pass; rolled-back cloud transactions cover lock/grace/unlock, idempotence, overdue rejection and approval clearing locks; pure Java timing checks cover grace expiry; both native release builds and lint pass; both APK signatures and downloaded bytes verify; preview publication leaves managed QR installer metadata unchanged; dashboard artifact tests verify only three control buttons, no typed command fields, generated idempotency keys and pending acknowledgement messaging. Physical lock/reconnect, emergency/bank access and interactive hosted UI acceptance remain unverified.

## Bacy Enterprise light design — 0.6.0

The owner approved Bacy Enterprise branding and a white/blue visual direction based on the supplied references. The managed client now has a light blue/white account layout, bottom navigation and a two-column native action grid linked to real plan, history, device status, support and messages. Its existing signed account, QR provisioning, payment and enforcement behavior is retained. No reference-image names, prices, payment totals or device models are imported as customer data.

The separate view-only preview now has navigable native Home, Payments, Device and Support pages. It displays actual fixed financing terms but explicitly shows no enrolled account, payment history or management connection. Preview actions explain the real flow and do not send claims/messages or manage the phone. Both application labels and the private administrator dashboard use Bacy branding. Admin appearance is light rather than dark.

Android build/lint, package/signature and hosted byte checks apply to the releases. Dashboard owner/origin, login regression, minimal enrollment and one-click control checks pass. No physical visual acceptance or browser screenshot test of this redesign is claimed.

## Rebuilt reference composition and live native admin — 0.7.0 / Admin 1.0.0

The customer landing screen was replaced with a shared native composition: branded blue header and logo, handset imagery, an overlapping white device/account sheet, a payment card with a circular remaining-time indicator, six drawn icon tiles in a three-column grid, and bottom navigation. The earlier customer quick-action grid was removed. Actual contract payment totals and counts are not fabricated; the ring represents remaining time. The preview is explicitly disconnected and uses the same composition. Phone artwork is based on the owner's supplied handset images.

The private website stylesheet was replaced, with a rebuilt operations hero, metrics, contact ring, recent activity and customer attention panels in a light palette. The percentage is recent contact, not an unsupported security-compliance claim.

A separate native Bacy Admin application connects to real cloud overview, enrollments, QR rendering, one-click controls, messages and payment review. Its only embedded WebView renders a bounded enrollment QR SVG with JavaScript, file access and network loads disabled; the admin interface itself is native. Owner sign-in takes place in the external browser through the existing private website. A ten-minute pairing request requires explicit owner approval before returning a seven-day revocable app token. App tokens are stored encrypted with Android Keystore; master service or administrator credentials are not bundled. Website session revocation and per-app disconnect are supported. Expired/invalid app tokens cannot access the admin API.

Validation includes native release builds/lint for customer, preview and admin; Robolectric native-graphics screenshots from the actual Android view trees for the customer home and an empty admin workspace; live connection protocol checks for pending approval, rejection of anonymous approval and wrong verifiers, successful owner-approved live reads, disconnect and revoked-ticket replay denial. Those protocol tests revoke their test session and create no customers. Native UI render tests do not establish physical handset behavior or interactive browser owner sign-in success. Physical MDM locking and emergency/banking acceptance remain unverified.

## Connection discoverability and quiet visual system — Admin 1.1.0 / Client 0.8.0

The owner rejected the blue/white visual direction. The updated visual system uses graphite, warm paper, restrained forest accents, medium-weight typography, flatter metrics and smaller corner radii. Customer handset imagery remains in the header.

The admin website now has a permanent Connect admin app navigation entry and persistent connection panel with download/open links and waiting-device requests. Approval no longer depends on a query parameter surviving the ChatGPT/browser sign-in redirect. Pending requests expose only ticket, device label, expiry and an eight-character code, not verifier or session secrets. An owner explicitly matches the code in the native app before approving.

The native app has a prominent Sign in to Bacy Admin button and numbered instructions. Pending pairing data is stored encrypted and resumed after process restart. Existing requests can reopen the approval page instead of starting from scratch. Invalid/expired exchanges clear stale local pairing data. The app supports the bacyadmin://signin link and retains its original revocable owner-session model.

Validation includes artifact checks for a persistent entry, query-free pending discovery and explicit approval; Android build/lint and native UI rendering; live owner-only pending-list/approval/session checks. Physical handset sign-in and MDM enforcement remain separate acceptance requirements.


## Windows ADB enrollment — Client 0.9.0
Added a camera-free Windows enrollment bundle with the verified signed managed APK, one-time code, CMD helper and instructions. Google Platform Tools are obtained directly from Google, not redistributed. The helper checks for a single authorized USB device, installs the app, asks Android for device ownership, and launches a shell-protected bootstrap. It never resets devices or removes accounts.

The new activity requires Android's DUMP permission (available to authorized ADB shell, not ordinary third-party apps), independently checks actual device ownership, rejects already-enrolled accounts, validates the one-time code format and asks for explicit on-phone confirmation. It fetches the signing key only from the fixed trusted Bacy HTTPS server. QR enrollment is retained. The live owner-only bundle endpoint validates unused/unexpired enrollment codes and the installer checksum before ZIP creation.

Validation: release build/lint and APK signing pass; the compiled manifest confirms protected bootstrap access; the actual ZIP serializer passes Windows CRLF and code-file checks; dashboard regressions pass. Physical Windows ADB execution and this handset's ownership eligibility have not been tested.


## QR density and scan presentation — Client 0.9.2
Compact setup QRs use the already-pinned cloud URL/signing key in the managed app instead of repeating them in every QR. Legacy full extras remain supported. QR downloads use an immutable short public alias whose downloaded bytes match the full signed installer; customer authentication is not required to download the APK. The admin website requests a crisp PNG with a larger white quiet border and offers a tap-to-enlarge scan view. Legacy callers, including the native admin's SVG viewer, retain SVG output by default.

The previous 802-byte payload and the compact 566-byte payload both decoded in offline ZXing checks at 320px and 640px. The compact QR uses fewer modules. Live PNG rendering and short-URL exact-byte checks pass. This does not reproduce the physical handset's scanning failure or prove that density was its only cause; a newly generated QR needs handset verification. Windows USB enrollment remains available independently.


## Administrator website: Swiss operations ledger

Applied the owner's standing anti-template guardrails to the web workspace. The site now self-hosts licensed DM Sans and Fraunces variable WOFF2 subsets at content-addressed URLs. The font licences are included in the source and served by the Worker. Typography does not request external font services.

Removed the marketing intro, repeated pre-heading labels and donut chart. The operations view uses a ruled metrics strip and an asymmetric review desk / record rail. Payment review, device attention, contact recency and audit records retain their live data. Contact recency is a semantic linear meter and excludes released devices. Native app layouts were not changed by this website release.

Validation covers actual font bytes/type/cache headers and licences, prohibited typeface and effect removal in CSS, unique element IDs, managed-contact calculations and the existing owner/authentication, enrollment, control and connection workflows. Automated browser visual QA was not performed in this coordinator environment.


## Windows helper: device detection and authorization retry

A handset screenshot showed the phone listed as unauthorized, followed by a Windows filename/directory syntax error from the helper. Removed the quoted FOR /F subprocess pipeline. The helper now captures ADB output directly to a quoted temporary file, parses reported states without a nested command shell, cleans up the file, and requires one connected, authorized device. Unauthorized, offline, missing and multiple-device cases offer a recheck after the user acts on the phone instead of proceeding to installation. No accounts are removed and no reset is performed.

Both new enrollment ZIPs and a public helper-only update use the revised script. The helper-only ZIP includes the CMD and readme, with no APK, customer data or enrollment code. Existing enrollment codes retain their original expiry. The API publishes the replacement directly; no native APK rebuild was required.

Validation: executed the actual ZIP route branches with their pinned serializer, verified exact helper/CRLF and signed APK bytes, enrollment-check rejection, untrusted installer rejection and checksum mismatch rejection. Live helper bytes, health and unauthenticated admin denial were checked after publishing. Windows CMD execution and successful handset enrollment remain unverified.


## Native ledger and Android account verification update

Client 0.10.0 replaces Android's Keystore-backed Ed25519 external SPKI import with the pinned Tink Android 1.24.0 verifier and strict RFC8410 SPKI validation. Standard Android15 source rejects the old X509EncodedKeySpec import; the observed handset had successful enrollment/sync HTTP responses but no accepted account revision. The handset's exact original exception was not captured. Signature, device binding, rollback and payoff-release checks remain enforced. Network, TLS, verification and other failures have distinct safe messages, and no credentials, signed payloads or raw server errors are logged. A successful background sync can clear its earlier error; local policy broadcasts cannot.

Native client, owner app 1.3.0 and view-only preview 0.9.0 now share the Swiss operations ledger design, using bundled licensed Fraunces and DM Sans fonts, paper/graphite/forest colours, ruled rows and numbered navigation. Unknown preview data stays unconnected. The paid-off home removes the payment prompt. Admin controls retain real request/acknowledgement distinctions, submitted EFT references and existing authorization.

Settings reset, app-uninstall, safe-mode and additional-user/switching restrictions remain active for an enrolled financed device independently of its paid/overdue state or the app-access enforcement toggle. Confirmed signed payoff release clears these policies before relinquishing device ownership. Wi-Fi connectivity stays available by default, and emergency/banking/support access remains part of overdue lock handling. Recovery-menu reset or firmware protection is not supplied by these ordinary Android APIs.

A public Windows client-update ZIP includes the signed managed APK and an update-only helper. It uses install -r and opens the existing app without setting ownership again, changing the customer account or requiring an enrollment code. Installer checksum validation remains mandatory.

Validation completed so far: RFC8032 and server-format signature vectors; provider-import refusal regression; altered payload/signature/key/algorithm/truncated/trailing key rejection; safe error categories; actual helper ZIP serialization/CRLF; exact signed APK bytes and rejection of untrusted installers/checksum mismatches. Windows command execution and new-release account acceptance/physical locking still require handset checks. Final build, native screen render and publication results are recorded below after verification.

Final verification: all three signed native release builds and release lint tasks passed. Native graphics screenshot tests passed for customer preview, administrator sign-in and workspace; the screenshots were inspected and an oversized admin metric row was corrected and rechecked. The exact licensed font bytes are present in every APK, and signing identities match the previous installed releases. Client0.10.0, native admin1.3.0 and disconnected preview0.9.0 were published with exact downloaded-byte verification. The live client-update ZIP contains the final signed client and update-only helper; it exposes no enrollment code and retains the existing account. API health and unauthenticated-admin rejection passed. Handset account loading, actual restriction behavior, emergency/banking compatibility and manufacturer reset limits still require physical verification.

Routine website policy forms and fleet configuration were hidden in favour of essential defaults and direct controls; payoff release remains available. Existing website authentication, enrollment, controls, app connection and ledger checks passed.


## Loop Secure dark release and banking/launcher corrections

Renamed user-facing apps/site to Loop Secure while preserving package IDs, signing identity, enrollment and owner sessions. Replaced the paper ledger UI with dark raised panels, semantic icon navigation, tactile controls and bundled Fraunces/DM Sans typography. Approved totals/counts now come from all-time server aggregates; finite-contract percentages remain unknown when no contract total exists. All pending claims remain in the review queue in addition to recent history.

The customer app has a direct banking action on home, setup and account-error states. A shared exact-package catalogue for verified South African banks drives launcher discovery, lock-task permissions and diagnostics. Bank launches in managed lock mode request a permitted lock task. No browser-wide or arbitrary app-label exemption was introduced. Missing banks and bank-specific login/network/lock-task compatibility remain explicit.

Removed the managed activity's HOME registration and forced preferred-launcher assignment. Unlock clears persistent/preferred entries belonging to earlier versions; normal device-owner lock task remains. Added Android11(API30) guards for internal broadcasts, typed parcelables, notifications and bounded stream reads. Native admin notifications distinguish pending claims from verified funds, support private/deduplicated notifications and best-effort periodic background checks.

Six delivered improvements: real receipt totals/counts; direct banking access; semantic/tactile navigation; actionable full pending queue with filters; administrator claim alerts; Android11+ compatibility work and original-launcher preservation. No voucher or Cash Send flow was added. Automatic EFT/email depend on external merchant/sender credentials; see EFT_AUTOMATION.md for verified provider options and the exact integration/security contract. They are not falsely advertised as active. Final build and publication results follow after validation.

Final validation/publication: client0.11.0, native administrator1.4.0 and disconnected preview1.0.0 passed signed release builds and release lint, with matching signing identity and exact bundled font bytes. Android bank-access and private admin-alert tests pass on SDK30 and35; native customer/admin render tests pass. The app no longer registers HOME or assigns itself as a persistent preferred launcher. All native installers were published and downloaded-byte verified. Website workflow, dark design/local fonts/real receipt summary tests passed and the matching pushed source was published privately under Loop Secure. Live customer update ZIP was compared with the final signed release. These checks do not substitute for bank login/OTP compatibility or locking/restart tests on a physical Android11 handset.

Automatic email and provider-verified EFT unlocking remain unconfigured, because no merchant/sender account credentials were provided. Manual EFT review and native claim notifications are active capabilities; background delivery is best-effort and requires granted notification permission plus a valid administrator session. See EFT_AUTOMATION.md for provider onboarding, payment-confirmation versus settlement, and credential requirements.


## White interface and simpler Windows enrollment

Published native client0.12.0, owner app1.5.0 and disconnected preview1.1.0 with a white tactile layout, preserved Fraunces/DM Sans fonts, and a generated isometric Loop Secure PNG used by all launcher icons and native/web branding. Shared home, native admin, preview, managed setup and payment navigation were updated. Payments now opens a full account/history screen with verified totals, EFT details, banking and existing claim review behavior. Device ownership, enrollment identity, signatures, notifications, money-state rules and package IDs remain unchanged.

Windows enrollment/update helpers now find Platform Tools locally, reuse a per-user ADB path, or offer a one-time adb.exe file picker. Each customer's extracted ZIP stays in its own folder, and commands execute the signed APK/code from that folder. No copying customer files into the SDK directory is required. The helper never downloads third-party tools, changes execution policy, removes accounts or resets phones. The readme/dashboard distinguish new-customer enrollment from update-only installation and retain the30-minute one-time-code rule.

Validation: all three native signed builds, release lint and test suites passed; signing identities match previous installed releases. Site authentication/startup, name+phone enrollment, actual setup-step transitions, one-click controls, bank/health alerts, mobile pairing, all-time receipt summaries, local fonts and PNG alpha/missing-asset behavior passed artifact checks. Published APK bytes and simplified helper bytes were downloaded and compared. Windows file-picker execution still requires a Windows laptop check; no claim of physical handset testing is made.
