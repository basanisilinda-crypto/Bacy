# Developer options and startup protection

Customer 0.23.0/code 36 and native admin 1.11.0/code 17.

## Priority changes

The client requests Android's Device Owner `DISALLOW_DEBUGGING_FEATURES` restriction throughout active financing. This restricts Developer options and debugging features, including ADB. It is not conditioned on the financial screen lock, so ordinary paid access and a five-minute code do not remove debugging protection. Permanent finance release clears our restriction before removing Device Owner. It does not revoke a policy applied by another administrator.

New USB enrollment remains possible before customer activation. Debugging is blocked after the account is established. Existing financed devices receive the policy through the signed customer update. ADB can disconnect when the new policy applies. Later USB installation, shell diagnostics and USB helper ownership queries will be unavailable while the restriction is active. Use the signed managed updater and in-app protection/report screens. Do not uninstall or clear data to restore ADB.

This uses standard Android policy. Actual Developer-options visibility, wired/wireless ADB shutdown and OS-specific behavior need a handset test. It cannot relock an already-unlocked bootloader, disable MediaTek BROM, alter recovery firmware or make an app survive factory reset.

A direct-boot-aware receiver handles LOCKED_BOOT_COMPLETED using only a device-protected finance-active boolean and local startup markers. It does not read customer credentials, payment data or the credential-encrypted account before Android unlock. Existing Android policies persist through reboot; this early path reasserts debugging protection. USER_UNLOCKED and BOOT_COMPLETED immediately apply the verified cached local policy before network work, then restart management/update recovery. MainActivity also reapplies policy on resume, without waiting for cloud refresh.

Android controls the initial credential/first-unlock screen. The app cannot replace that trusted screen or guarantee zero-delay execution on all firmware. A restricted or time-unverified managed account returns to the payment/recovery flow as soon as Android allows application policy to run. Released phones remain excluded. Existing offline monotonic timers, five-minute access and banking/Wi-Fi/support/emergency allowances are preserved.

## Additional improvements

- Customer **Your phone → Protection checklist** shows actual Device Owner/debugging policy and the current USB debugging setting, plus the latest local startup phase. Requested policy is not represented as proof that a PC connection was physically tested.
- **Copy support diagnostics** copies the platform report and app version without customer identity, enrollment codes, access tokens or merchant credentials. It can include installed banking-app names; share only with the support contact.
- Native admin **Clients → Needs attention** filters policy errors, unsupported management, time checks, missing banking access and unacknowledged commands.
- **Not recent** filters last contact older than fifteen minutes or missing contact. It does not assert that a phone is currently offline.
- The native admin now shares the customer's black-and-white palette and bundled typefaces. These native changes do not change the website theme or payment rules.

Install both apps in place. Existing signed update channels serve the releases where Android permits installation. Admin update approval can still be required and the admin phone is never restricted. No database or Edge Function migration is needed for these native additions.
