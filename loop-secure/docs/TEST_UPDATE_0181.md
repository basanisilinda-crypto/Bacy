# Test the customer update from 0.18.0 to 0.18.1

Release 0.18.1/code 28 is a small, real update on the existing signed customer channel. Administrator 1.7.0 is unchanged.

Three visible improvements:
1. Home shows the installed app version beneath Loop Secure.
2. A gold **New in 0.18.1 · What’s new** strip opens the release notes.
3. Tap **Copy** beside the EFT reference on the account card to copy it.

To test the automatic download, keep the existing customer 0.18.0 installed and enrolled. Connect to ordinary unmetered Wi-Fi with working internet and open Loop Secure. Do not manually install the new APK first: that would skip the download test. If a check just ran, allow at least one minute before reopening. A required-update screen/notification should show download progress and then **Install update**. Tap Install and approve any Android confirmation. Open Loop Secure again: **v0.18.1** and the gold release strip confirm the installed version. Verify that the same customer account is still shown.

An older client needs updater-capable 0.18.0 once; released/unmanaged phones do not use managed customer updates. Android can delay checks when the app is stopped or background-restricted. Downloads are automatic; installation approval is still required. The existing bounded maintenance gate keeps banking, Wi-Fi and emergency access available and does not change financial entitlements.

This release was built, linted and checked for same signing identity and hosted download integrity. Automated SDK 30/35 cases check version visibility, both shortcut routes and update manifest rejection rules. The user's handset must confirm the real download and installation; publishing alone is not proof that it updated.
