# Native appearance release

Customer 0.26.0/code 39 and native admin 1.14.0/code 20.

Customer: Support → Appearance. Admin: More → Appearance. Choose Follow phone, Light or Dark. Preferences are stored separately on each app installation; changing them affects presentation only. Native account surfaces, sheets, text, action controls, navigation and Material form/dialog themes use the chosen palette. Secure payment-provider pages retain the provider's own appearance. Restarting an activity after selection preserves enrollment and finance records. The administrator app never restricts its phone.

Customer Home displays the manufacturer/model reported by Android. This can be a model code; no marketing name is guessed. The existing phone image is illustrative and no unverified internet photo is downloaded.

Website appearance selection and automatic installer cleanup are already live. Custom-plan enrollment and expanded online-order views remain on work/custom-plan-orders and are not part of this release. Automatic review rejected the broad database migration again after explicit approval; do not deploy those inputs/API changes before the migration is approved.

Update in place with the same signing key. Customer managed updates can install unattended where Android allows; admin installation may require approval. Handset installation, bank handoffs and OEM appearance still need physical acceptance checks.

Validation: 138 customer and 24 admin tests passed with no failures, including preference persistence, system night selection, rejected invalid choices and readable dark-action contrast on API 30 and 35. Both signed release builds and lint passed; the final admin-dialog resource adjustment receives an additional build/lint/test check before publication. Physical handset installation and bank handoffs remain unverified in this workspace.
