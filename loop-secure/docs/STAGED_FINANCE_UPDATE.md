# Staged update — not live

This branch prepares total phone price and monthly instalment fields for new enrollments, variable final payments and payoff, online order IDs and provider/checkout outcomes in both admin views, and the reported manufacturer/model on the customer dashboard. The user requested these changes. Existing plans retain R1,700/R300 defaults; payment intervals remain 30 days.

Native source versions 0.26.0 and 1.14.0 are **not published installers**. Production remains customer 0.25.0 and admin 1.13.0. The existing phone picture is retained because no exact image match was verified. Native dark/light controls have not been implemented.

The migration was exercised with custom R2,400/R700 fixtures (including a R300 final payment), the existing R1,700 fixtures and explicit rollback. No fixture record was retained. Both native release builds, lint and regression tests passed. The persistent production migration was rejected by automatic approval review because it replaces core enrollment, sync, payment-confirmation and recovery functions; explicit approval and further end-to-end validation are required before applying it. The new API/UI must not be deployed ahead of that migration, because older API validation would ignore custom agreement inputs.

Automatic storage cleanup and website appearance selection are already live independently on main. Preserve those changes when completing this branch. Rebuild generated website assets after finance approval. Validate larger provider amounts, callback/reconciliation and device payoff in the final coordinated release; never use a checkout return alone as proof of payment.
