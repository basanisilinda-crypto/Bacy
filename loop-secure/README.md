# Loop Secure — original production project

Production source: https://github.com/basanisilinda-crypto/Bacy , folder loop-secure. Customer package za.co.loopmanager; native admin za.co.bacy.admin. The independent Rain evaluation is a different app/service and is not included here.

- [Owner/customer setup](START-HERE.md)
- [Engineering continuation](PRODUCTION-CONTINUATION.md), [agent instructions](AGENTS.md), [design continuity](DESIGN-CONTINUITY.md), [verified status](STATUS.md)
- [Compatible customer0.27.0 APK](downloads/Loop-Secure-Client.apk), [Windows client update ZIP](downloads/Loop-Secure-Client-Update.zip), [admin1.14.1 APK](downloads/Loop-Secure-Admin.apk)

Customer0.27.0/code40 adds recovery for an owned but unlinked phone, USB connectivity retry, cached restriction recovery before sync-job networking and clear current-version wording. Package identity, original signer, saved linked accounts and payment verification safeguards are preserved. 142customer and24admin tests, both release builds/lint and certificate/package checks passed in the prepared production source.

The owner authorised Codex to sync these reviewed changes to original Bacy. Future ChatGPT sessions should treat it as read-only unless separately authorised. Use a separate production working copy and preserve the current design and trusted signed update channels.

Live production backend was loop-manager v39 and the November12 website differs from the earlier source baseline. A read-only function snapshot is included under deployment/runtime-reference for comparison. Do not redeploy old backend/website/database code blindly. The included update workflow is a template, not active CI. Runtime does not depend on ChatGPT subscription credits; provider quotas and credentials remain independent.

No physical phone was connected to this engineering workspace. The owner reports that all tests they ran on their own phone passed; device-specific QR/USB/restart/payment acceptance remains a hands-on task. Do not confuse an installation or a queued command with an actual phone acknowledgement.
