# Loop Secure brand refresh

The customer app, administrator app and website use a new forest-green and copper infinity/shield mark. The website displays the mark in its original colors and uses it as the browser icon. Existing page structure, fonts, navigation, account information and management controls are preserved.

The customer home header includes an illustration of a handset's front and rear, derived from the owner's supplied Loop phone photographs. It is a product illustration, not a claim about the currently enrolled handset model. Android artwork uses `drawable-nodpi` to avoid density-based bitmap enlargement. Both generated originals remain available separately in the workspace.

Release versions: managed client 0.12.2 (19), administrator 1.5.1 (8), optional disconnected preview 1.1.1 (9). These are signed app updates using the existing application IDs and signing identity; existing enrolled phones should update in place. This branding change does not reassign device ownership or customer accounts.

Verification: release builds and release lint for all three applications, signature verification against the existing signing identity, existing native screenshot checks, desktop/mobile browser checks, and Cloudflare authentication/relay/asset tests. Published installer bytes are verified against the local signed builds. Physical handset lock and payment tests are outside this visual-only change.
