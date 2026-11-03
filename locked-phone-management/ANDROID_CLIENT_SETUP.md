# MBMF Client setup and management consent

This is a native Android first-launch setup wizard, not an HTML page.

## Normal installation
1. Open MBMF Client. The friendly four-step first-launch wizard appears automatically.
2. View the account and payment explanations. There is no customer registration/login.
3. The wizard checks Android's actual DevicePolicyManager and displays **none**, **device administrator** or **device owner**. There is no simulated success state.
4. On a normal install, selecting **Review optional device admin access** invokes Android's official `ACTION_ADD_DEVICE_ADMIN` system permission screen. The customer may decline. This **does not** turn the phone into Device Owner.
5. Finish the wizard and open the existing account UI. With no QR enrolment the app truthfully displays that no finance contract is assigned.

## Managed QR setup
1. The finance administrator registers the customer, obtains a one-time code and shows the startup QR for a compatible, new or appropriately reset Android device.
2. Android's Setup Wizard manages Device Owner provisioning. Android 12+ invokes the supported provisioning-mode and compliance activities.
3. MBMF's new policy compliance screen shows clear management powers and safety information. It needs an affirmative checkbox; declining returns `RESULT_CANCELED`. It never auto-enables management.
4. Only Android can grant Device Owner. MBMF checks the real DevicePolicyManager state.
5. The existing backend activation remains bound to the original one-time enrolment credential, not a user-entered password.

## UX
Soft ivory, mint and peach, rounded surfaces, custom illustration drawn in Compose; friendly and light without neon or game-style graphics.

## Not verified
Device Owner provisioning, admin prompts, consent activity behaviour and full phone release need real compatible LOOP hardware tests. Debug APKs are not production-ready financing software.
