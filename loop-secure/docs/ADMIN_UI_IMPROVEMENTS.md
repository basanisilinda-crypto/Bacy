# Administrator workspace update — 7 November 2026

Ten additions to the existing white Loop Secure administrator website:

1. Mobile customer cards with labeled fields and no clipped table content.
2. Comfortable/compact row density, remembered on this browser.
3. Larger operational text, readable contrast and accessible control targets.
4. Visible refresh progress with busy-state announcements.
5. Dismissible status messages and clear keyboard focus.
6. Customer sorting by name, paid deadline or latest contact.
7. Quick customer filters with current counts.
8. Payment lookup by customer or bank reference.
9. Payment-status counts on the register tabs.
10. A setup-help dialog with enrollment, updating and EFT guidance.

The existing six conveniences remain available. Visual preferences save no customer information. Live records and existing backend workflows are retained.

Verification: Playwright desktop/mobile acceptance checks use intercepted fixture responses without production writes. They cover sorting, quick filters, saved preferences, searching, tab counts, dialog dismissal, status dismissal, offline state, repeated refresh and mobile size/overflow. The eight existing JWT/relay groups and Worker build also pass. Screenshots are local QA artifacts, not production data.
