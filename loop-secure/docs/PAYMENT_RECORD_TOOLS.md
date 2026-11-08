# Payment records and follow-up tools

Customer 0.25.0/code 38 and native administrator 1.13.0/code 19.

## Customer

- **Payments → Order** switches oldest/newest first while preserving the status filter. Missing dates appear last. Sorting never modifies stored records or payment approval.
- **Payments → Copy account statement** copies the account reference, last signed check time, verified summary and available payment records. It explicitly identifies itself as a loaded snapshot, not a bank receipt or a complete bank statement. Pending and not-approved claims remain separate; missing figures say Not available. No account token, unlock code or merchant credentials are copied. The user chooses whether and where to paste the statement.
- **Support** offers Payment review, Connection help and Update help starters. Tapping adds text to the existing encrypted draft without replacing it or sending anything. Repeated taps do not duplicate a starter; a full draft is kept intact. The customer edits details and explicitly presses Send.

## Native administrator

- **Payments** adds All, In review, Received and Not approved filters with counts for loaded records. Search and the selected status apply together.
- Oldest/newest ordering helps work through the review queue. **Overview → Review payments / Payment claims** opens pending records oldest first. Approval still requires settled-funds review.
- Refresh preserves the payment search text, input focus and selected text, as it does for customer search. Empty views no longer claim that the whole queue is clear.

The existing monochrome layout, signed updates, device protection, offline deadlines, five-minute access and payment verification are preserved. No database or website changes are needed. Install in place with the same package and signer. Customer managed updates can install unattended where Android permits; the admin update may require approval and never restricts its phone. Physical handset installation and banking handoffs still require a device check.

## Release checks

130 customer tests and 22 administrator tests passed with no failures or skips, including Android 11/API 30 and API 35 coverage for record sorting, pending-versus-paid statement accuracy, missing data, draft preservation and combined admin search/status filtering with refresh focus. Both release builds and lint checks passed. Both APKs retain the existing release certificate. These automated checks do not replace physical-device installation and banking checks.
