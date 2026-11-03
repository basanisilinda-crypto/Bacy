# MBMF Client — real Android setup and management permissions

This native client now starts with a four-page onboarding wizard.

1. **Welcome.** Explains that customers do not create an account.
2. **Payments.** Explains how finance records and manual EFT verification work.
3. **Android access.** Shows the actual Device Owner / Device Administrator status, a consent checkbox, and a button that launches Android's official `ACTION_ADD_DEVICE_ADMIN` permission request.
4. **Finish.** Shows the true device-management status and opens MBMF Client.

The onboarding is the first-run launcher, implemented by `WelcomeSetupActivity.java`. Users can reopen it from **Phone → Review Android permissions**.

## Important: the two permissions are not interchangeable

| Provisioning mode | How it is obtained | What this build reports |
| --- | --- | --- |
| Device Administrator | The user explicitly enables it from Android's system permission screen | `Device Administrator active`; **not** Device Owner |
| Device Owner | Android provisions the device during compatible **initial setup** using a trusted QR and management disclosures | `Device Owner active` only when `DevicePolicyManager.isDeviceOwnerApp()` returns true |
| No management | The customer declines or setup was not performed | `No device management activated` |

An APK cannot become Device Owner simply by requesting Device Administrator or by opening an Activity. Android's initial setup flow must actually assign Device Owner.

This onboarding does **not** automatically restrict a device, prevent resets, or claim payment-based access-control functionality. The optional Device Administrator grant is for an explicit device-management test and cannot replace the contract-specific QR setup.

## Paths
- `src/main/java/za/co/bacyflex/customer/WelcomeSetupActivity.java` — welcome, disclosures, status, native graphics, Android permission request
- `src/main/java/za/co/bacyflex/customer/MainActivity.java` — finance UI and link back to setup
- `src/main/java/za/co/bacyflex/customer/FinanceAdminReceiver.java` — Android Device Admin component
- `src/main/java/za/co/bacyflex/customer/PolicyComplianceActivity.java` — Android managed-provisioning disclosure
- `src/main/java/za/co/bacyflex/customer/ProvisioningModeActivity.java` — Android 12+ provisioning mode
- `src/main/AndroidManifest.xml` — manifest and launcher configuration

## Testing

1. Install the debug APK on a spare Android device. Launch the app for the first time.
2. Walk through all four screens. On the third screen, tick consent and select **Review Android permission**.
3. Confirm Android displays the **system-controlled** Device Administrator disclosure. Decline once and confirm the app does not pretend management is active. Grant voluntarily and confirm `isAdminActive()` is true.
4. Verify the app accurately says **Device Administrator**, not Device Owner.
5. For real Device Owner testing, enrol a supported, appropriately reset test phone using its **initial Android setup QR** and confirm `isDeviceOwnerApp()` returns true.
6. Confirm emergency calling and normal device safety features remain available.

A successful Gradle build proves compilation and APK packaging, not successful provisioning on a particular LOOP device. Record physical-device results before production deployment.
