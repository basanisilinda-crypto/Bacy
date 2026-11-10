# Loop Secure Pay@ customer app / signed update changes

Candidate release: client 0.27.0 (versionCode 40). This branch does **not** change the deployed 0.26.0 APK.

- Make a payment includes Pay@ number/link, alongside existing iKhokha and EFT.
- Pay@ status is read from the authenticated Loop Secure backend, not from a page redirect.
- Background update checks work for enrolled clients on validated Wi-Fi or mobile internet.
- Only Android Device Owner phones attempt silent PackageInstaller updates. On other phones, the customer must tap Install and grant any Android-required approval.
- The downloaded APK must have the same package name and signing certificate as the installed release; it must match the signed update manifest and SHA-256. Never publish a debug-signed APK as an in-place update.
- The backend publishes a signed manifest only after a correctly signed APK is uploaded and the installer_current record is deliberately advanced. A website/backend text edit does not automatically replace an installed app.
- Verify Pay@ OAuth, one real low-value transaction, idempotent credit, and device acknowledgment before activating payment-triggered releases.
