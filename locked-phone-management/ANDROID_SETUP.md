# MBMF Android Setup Wizard (native Kotlin/Compose)

This is a genuine Android implementation, not an HTML wrapper.

The Client app contains four screens: welcome, affirmative management agreement, actual Android permission status with an optional system Device Administrator request, and final enrolment verification. Customers do not sign up or log in. The custom onboarding does not acquire Device Owner.

**Normal installation:** Android offers ordinary Device Administrator access via its own ACTION_ADD_DEVICE_ADMIN prompt, only after a user taps the permission button. This is **not** Device Owner.

**Full finance enrolment:** A compatible new/reset phone reads the administrator's one-use QR during Android initial setup; Android downloads the exact MBMF Client APK, handles system management disclosure, invokes PolicyComplianceActivity for affirmative consent and attempts Device Owner provisioning. MBMF Client checks DevicePolicyManager.isDeviceOwnerApp() before activating the assigned finance contract.

Declining consent returns RESULT_CANCELED. Provisioning without a valid one-use extra also cancels. APK debug installation is insufficient.

Build with Java 17 / Android SDK 35 / Gradle 8.9 using:
gradle :core:testDebugUnitTest :customer:assembleDebug :admin:assembleDebug

Not yet tested on LOOP hardware. No covert persistence, payment-triggered locking or factory-reset bypass is implemented by these screens.
