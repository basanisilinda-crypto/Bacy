# Design continuity

The following describes a historical production baseline. Runtime styles have drifted; inspect the live UI before changing them. This reliability review does not redesign the production apps. The separate evaluation uses an understated white/black interface with labelled actions and actual device reports. The owner rejects purple/gold and generic gradients; do not apply the historical palette to new screens by default.

# Loop Secure design direction

## Standing guardrails from the owner

- Use Fraunces and DM Sans; preserve self-hosted WOFF2 assets and their licences.
- Build a useful operations app with a clear next action, semantic navigation and tactile controls.
- No glassmorphism, glowing orbs, abstract metallic rings, neon purple/cyan overlays, or repeated heading labels.
- Keep operational values real. Never invent payments, balances, contract totals, payoff percentages, contact evidence or delivery acknowledgements.
- Keep account connection and customer enrollment easy to locate.
- Keep authorization, authentication, endpoint contracts and owner approval unchanged.

## Administrator website: dark instrument panel

Midnight and matte ink surfaces establish a calm work environment. Ivory type, solid sky-blue primary controls and warm amber attention states provide clear hierarchy. Raised surfaces, inset input wells, deliberate borders and restrained shadows make controls feel purposeful. Fraunces supplies the larger headings and financial figures; DM Sans supplies navigation, labels, forms and records. Locally hosted fonts and licences remain unchanged.

The desktop shell uses an icon-and-label navigation rail, a clear page heading, real refresh feedback and prominent enrollment access. On small screens, a labeled menu button reveals the complete navigation and account tools; Escape closes it. Search, status filters and contained horizontal table scrolling keep customer records usable on phones.

The overview separates approved receipts, device contact, the payment review queue, actual device exceptions and recorded activity. Approved receipts use the all-time payment aggregate when the backend supplies it. Older backends fall back to an explicitly scoped sum of approved claims in the available history. Contract total and payoff progress are unavailable and are never fabricated. Customer records show per-account approved receipts only when supplied by the backend.

A recent device report means contact within 15 minutes, not verified live connectivity or confirmed restrictions. The contact meter excludes unenrolled and released devices. Commands show delivery pending until the handset acknowledges the desired revision. Device exceptions retain the existing evidence and grace-period rules.

Payment review remains EFT-based and requires staff to confirm settled funds in the business bank records. Automatic bank verification is not configured. There are no fake notifications, email automation, voucher flows or cash-send claims. Messages expose queued, delivered and read counts from server receipts.

Enrollment still requires only name and phone. QR, Windows USB setup, manual lock/unlock/24-hour grace, payoff release, banking configuration and explicit mobile-admin pairing retain their request contracts and DOM hooks. Routine advanced policy controls and the fleet-policy button remain hidden. Dialogs, visible focus, keyboard navigation, semantic status text and reduced-motion styling support accessibility.

These changes cover the administrator website. Native customer and admin applications require their own design and release validation.
