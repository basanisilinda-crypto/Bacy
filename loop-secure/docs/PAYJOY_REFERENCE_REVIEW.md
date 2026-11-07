# PayJoy reference review

All three shared archives were downloaded on 6 November 2026. Their ZIP integrity checks pass. They are separated compiled code, native libraries and resources, rather than three raw chunks that should be concatenated. No app or native library was installed or executed. Repackaged archives do not establish the original APK signing identity.

## Established by static inspection

- The compiled-code archive contains four DEX files and a compiled Android manifest. The earlier resources-only archive did not contain this code.
- The manifest declares Android device-administration and managed-provisioning components.
- It also declares Samsung Knox permissions, a Knox license-status receiver, PayJoy bridge permissions and an Oplus-specific permission. These are evidence of declared integration paths, not proof that every supported phone grants them.
- Boot-completed and locked-boot-completed events are declared. This suggests separate startup handling is considered; actual pre-unlock behavior was not established.
- The supplied method-reference index contains standard Android management APIs, listed below. A reference alone does not prove a reachable call, success, the algorithm used, or compatibility on a particular handset.
- Native-library filenames include identity-recognition, machine-learning, database-encryption and crash-reporting components. File size and the number of libraries do not establish locking reliability.

Referenced Android APIs: isDeviceOwnerApp, setLockTaskPackages, setUninstallBlocked, setFactoryResetProtectionPolicy, setApplicationHidden, clearDeviceOwnerApp, setKeyguardDisabled.

## What this means for Phone Finance

Our app already uses Android device-owner provisioning and standard policy APIs. Declaring PayJoy or Knox permission names cannot grant proprietary manufacturer access. Manufacturer integration requires the appropriate documented platform support and authorization; it cannot be recovered from images or copied from permission declarations.

The independently implemented Phone Finance 0.4.1 update separates local signed-deadline enforcement from cloud request timeouts and reconnect backoff. That release has already been built, signed, published and checked against its downloaded bytes. This review does not establish that our implementation matches PayJoy's enforcement.

## Not established by this review

No actual PayJoy lock, unlock, offline countdown, reboot behavior, emergency access or factory-reset resistance was tested. No exact PayJoy lock algorithm was reconstructed. Our Note handset also still needs physical enrollment, expiry, payment approval, bank-app, emergency-access, restart and paid-off release acceptance checks. An APK package alone cannot prove those results.

## Complete APK received

The additional shared file downloaded as a complete APK (38,286,106 bytes). Its archive integrity and Android signature verification passed. The compiled manifest and all four DEX files match the split-code archive byte-for-byte. Signature verification establishes package integrity, not independent proof of the publisher's identity. The APK was not installed or executed.
