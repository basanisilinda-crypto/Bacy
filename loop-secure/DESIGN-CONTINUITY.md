# Preserve my design and compatible update behaviour

These are the owner's standing instructions for future ChatGPT work on the ORIGINAL production app.

- Start by inspecting the current deployed screens/source and supplied screenshots. Preserve the established visual identity and font/assets unless a specific design change is requested. Reliability fixes must not silently restyle the app.
- When asked to update design, apply that request across relevant screens/components consistently, including dialogs, navigation, empty/error/loading states and accessibility. Do not apply a generic template.
- Avoid generic AI styling: no Inter/Roboto/Arial/Space Grotesk, glassmorphism, glow orbs, metallic rings, neon purple/cyan overlays, repeated heading labels, or default three-card page patterns. The owner dislikes purple, gold, red and charcoal; do not reintroduce them as default accents. Use a deliberate, legible white/black base and purposeful colour only where requested.
- Keep real task navigation, useful spacing, understandable labels and truthful payment/device status. Never invent balances, order/payment approvals, delivery acknowledgement or contact data.
- Preserve original production application IDs, signing certificate, trusted update hosts, signed manifests and customer data. Future updates need greater version codes and the same signer; do not generate a replacement keystore or tell the owner to uninstall linked apps to solve conflicts.
- Automatic updates must come from published compatible signed builds. Existing updater checks run under Android scheduling; internet is not a guarantee of immediate installation. Admin installation may require Android approval. Do not falsely claim GitHub CI or auto-publication is active when it is only a template.
- Treat the original GitHub repository as read-only. Do real work in a separate production copy; Rain evaluation is independent. Runtime does not require ChatGPT credits/subscription. Document decisions for the next session.
