# Native application redesign

Loop Secure's native customer and administrator interfaces now share an intentionally technical visual system: light neutral canvas, charcoal surfaces, forest-green controls, sage navigation selection and copper warning accents. DM Sans remains the interface family, with the existing Fraunces wordmark retained. The new infinity/shield logo and front/rear phone illustration remain in place.

The customer home is rebuilt around device identity, a due-date strip, the payment action, a repayment dial and a two-column quick-access grid. The dial displays a repayment percentage only when a verified financed total and percentage are available; otherwise it displays the approved payment count or an unavailable state. No percentages or customer records are invented.

The administrator home replaces the four-metric card grid with a fleet summary, enrollment/review actions, an attention queue and customer controls. Customer cards now group identity, reported restriction status, reference/contact information and Lock/Unlock/24h grace controls. Payment reviews use a ledger-style hierarchy. A refresh action is present in the app header. The sign-in screen explains owner approval without assuming a specific identity provider.

Both apps and the separate disconnected preview use the same named charcoal navigation rail and new geometric icon set. Secondary screens, forms and dialogs use the updated native theme. Fonts, touch targets, status descriptions and scrollable content remain accessible.

Release versions: managed client 0.13.0 (20), administrator 1.6.0 (9), optional preview 1.2.0 (10). Package names and signing identity are preserved for updates. Android support remains API 30 through the current API 36 target (Android 11 through Android 16), subject to the existing device-management and manufacturer limitations.

No enrollment, restriction, banking access, claim approval or account-sync handler was changed. The administrator website link points to the already-deployed Cloudflare workspace. Tests use isolated screenshot fixtures; production apps continue to use real connected records.

Validation includes signed release builds and lint for all three apps, native screenshots for disconnected/verified customer states and administrator sign-in/empty/customer-control states, unchanged business-handler comparison, signing-identity verification and exact published-download checks.
