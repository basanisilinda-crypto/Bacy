# Private credentials: inventory, not values

Never upload credentials or signing backups into ChatGPT or GitHub. Transfer secrets directly into the hosting provider's secret manager or an owner-controlled password manager.

| Item | Purpose | Owner recovery location |
|---|---|---|
| Android release keystore and passwords | Updates accepted over existing installations | Private `.signing/signing.json`, `.signing/loop-release.jks`; encrypted owner backup |
| Owner deployment token | Protected installer publication | Private `.signing/cloud-admin.json`; Cloudflare secret LOOP_ADMIN_TOKEN |
| Supabase service role | Server-side database/API operations | Supabase project secrets; never APK/browser |
| Signed entitlement key | Device authorization | Encrypted server configuration; preserve rather than regenerate |
| iKhokha application ID/secret | Payment links and signature validation | Merchant dashboard and private provider configuration |
| Cloudflare/GitHub authorization | Deployment and source management | Owner-connected account; not exported |

Workspace paths are session-specific and are not a durable backup. The owner must keep an encrypted signing backup outside this workspace. Loss of the original signer can prevent normal in-place Android upgrades. Rotate previously exposed merchant secrets through the provider and reconfigure privately; never put replacements in this document.
