# Customer 0.18.3: updates without opening Loop Secure

## One-time transition

Install 0.18.3 once using the currently installed app's update notification/Install update control, or the account-preserving Windows update ZIP. Versions 0.18.0–0.18.2 were built to wait for installation approval: publishing new server metadata cannot retroactively replace that installed behaviour. Keep the app data, signing identity and device ownership in place.

## Background behaviour after that installation

For an enrolled customer phone where Loop Secure remains device owner:

- Its existing visible device-management foreground service checks when a validated internet connection becomes available, using Wi-Fi **or mobile data**. Downloads use the current connection and may be approximately 18.6 MB per full release.
- Successful account syncs in the running connection service trigger another update check, normally about once a minute, so a newly published release can be discovered while the connection stays online and the account screen is closed. Checks are debounced and overlapping work is prevented.
- A persisted 15-minute JobScheduler job with NETWORK_TYPE_ANY is a recovery backstop. Boot and package-replacement receivers re-establish the jobs/service. Android controls exact background scheduling; Doze, OEM battery settings, stopped apps and force-stop can delay work. A force-stopped app cannot promise unattended restart until Android permits it to run again.
- The signed manifest must explicitly authorize `install_automatically: true` and `requires_approval: false`. The native caller must still be the actual device owner. Missing flags do not grant automatic installation.
- A verified update is downloaded into private storage and committed through PackageInstaller without requesting user action on supported managed phones. Signature, version, exact package, pinned origin/path, size, checksum and installed APK signer checks all remain in place; verification is repeated before commit.
- An open payment checkout defers installation until it is closed, including an open checkout whose temporary SMS-access window has expired. Downloading can finish in the background. The installation decision is checked again immediately before commit.
- Automatic background download does not force the account screen to open. The existing bounded maintenance/recovery screen remains available on the explicit manual-install path. Financial restrictions and repayment verification are independent of app-update state.
- If Android requires installation approval, a notification opens only its trusted system installer confirmation through a non-exported, session-bound entry point. The pending session is retained; it is not abandoned merely because approval is required. Automatic failures retain the old app/account and apply a 30-minute installation retry cooldown. Explicit manual retry remains possible.

There is no guarantee that every Android/OEM combination will silently install or run jobs at an exact second. Check the installed version reported by the phone to confirm success; publishing and requesting installation are not installation proof.

## Administrator app

Administrator remains 1.7.1. Its existing automatic Wi-Fi download and user-approved installation flow is unchanged. It has no device-owner policy, lock task or phone restriction.

## Validation

Native SDK 30/35 fixtures cover persisted background scheduling, any-network eligibility, device-owner/manifest permission gates, checkout deferral even after its SMS lease expires, ignored foreign session callbacks, and rejection of non-system approval intents. Existing manifest freshness/package/size/origin checks, payment and account access fixtures also run. Server fixtures verify unattended-install flags only for customer manifests, with admin approval retained. Handset scheduling, actual mobile download and PackageInstaller completion still require device confirmation.
