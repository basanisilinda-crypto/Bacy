# Server package

The API, administrator UI, message connection, enrollment endpoints and signed APK are served by one backend. The production Compose definition adds Caddy HTTPS and persistent volumes. This is the optional self-hosting package. The separate cloud deployment is live; see `START-HERE.md`.

No new cloud account or paid plan was created. A separate compatible API and isolated tables have now been deployed alongside the existing finance backend; the existing MBMF tables and API remain unchanged. This Docker definition is not the runtime of that cloud deployment.

## Local administrator evaluation

```sh
docker compose -f compose.local.yml up --build -d
docker compose -f compose.local.yml exec api python -m backend.manage create-admin --email your-admin@example.com
```

Open `http://127.0.0.1:8000`. Enter the password and authenticator code from local setup. This HTTP endpoint is for administrator evaluation only; Android enrollment requires trusted HTTPS.

## Public HTTPS host

On a persistent Linux server with Docker Compose, a DNS hostname pointing to that server, and ports 80 and 443 available:

```sh
export LOOP_DOMAIN=your-real-management-hostname
# LOOP_DOMAIN must be an actual hostname you control, with working DNS.
docker compose up --build -d
docker compose exec api python -m backend.manage create-admin --email your-admin@example.com
```

Caddy obtains and renews a publicly trusted certificate. The administration page is the HTTPS origin. Client setup codes contain that same origin. The hosted signed APK is `/downloads/client.apk`; the QR form automatically fills its URL and checksum after enrollment creation. No manual APK installation is needed on customer phones when the handset supports setup-wizard provisioning.

The dashboard owner configures verified EFT destination details, approved bank package IDs, grace periods and device policy. Account numbers and package names are never guessed. Keep the production service in a single process while using SQLite. Hardware-side enrollment and restrictions still require the acceptance procedure.

A free cloud tier must support a long-running Python process, persistent writable storage, inbound HTTPS and long-lived client connections. This code does not make an ephemeral filesystem durable or turn a static-site host into a Python runtime. Cloudflare Sites runs Workers; the existing Python/SQLite backend cannot be uploaded there unchanged.

## Backups and signing

Back up the complete `loop_data` volume, including its database and cryptographic keys, using a stopped service or a consistent SQLite backup. Encrypt backups and test restoring them. The Android release signing key is supplied separately in the private signing backup; do not put it or its password in GitHub, this deployment image, or the hosted downloads directory. The source ZIP does not include that private key.

The packaged release is signed for this project, but it has not been certified by the phone manufacturer or tested on a physical Rain Loop handset. It is an evaluation build until that validation is complete.

## Rebuilding the signed release

Restore the private signing backup outside your source checkout. Export `LOOP_SIGNING_STORE_FILE` pointing to the restored keystore, `LOOP_SIGNING_STORE_PASSWORD`, `LOOP_SIGNING_KEY_PASSWORD`, and `LOOP_SIGNING_KEY_ALIAS` from the private backup. Do not put passwords in command-line arguments, Git, or build logs. Run `./gradlew assembleRelease lintRelease` from `android/`, then replace `backend/downloads/LoopPhoneManager.apk` with the new signed release and rebuild the server image. Increase the Android version code for every update.
