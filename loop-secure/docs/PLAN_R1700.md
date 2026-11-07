# R1,700 total repayment plan

The business owner explicitly set the full phone price to R1,700 for every customer. Approved payment records are preserved; pending, rejected and browser-return claims do not count as paid. Existing manually released accounts remain released.

The signed customer account and administrator device records expose total R1,700, verified paid total, balance, percentage and instalments remaining. Normal payments are up to R300. A R1,500 approved total leaves R200, so the final hosted checkout and new EFT claim are R200. After verified approved payments reach R1,700 the account is released permanently; the managed phone receives that signed result on its next successful sync. This is the same permanent device-owner release used for payoff, not a temporary unlock. No further plan payments are offered.

Only iKhokha PAID verification with an exact intent/link/amount/currency match can auto-credit online payments. The database locks the account for confirmation, rejects duplicates, and sends a verified stale checkout exceeding the remaining balance to settlement/refund review. Manual EFT remains subject to bank settlement review; a claim exceeding the current balance cannot be approved without resolving its settlement/refund discrepancy. Existing pending provider intents remain available for late callbacks.

Deployment changes only public.loop_* objects. No mbmf_* data is modified. New SQL helpers are service-role only, use an empty search path and run with caller privileges. The migration preserves customer/payment records. Rollback acceptance fixtures cover summaries, final manual EFT, exact final online payment, duplicate receipt protection, full payoff and stale-link overpayment review; no synthetic records are committed.

The release also replaces the logo with a realistic phone-padlock: its closed gold loop represents security and continuity, and the green phone body represents the protected device. Customer 0.18.2 and admin 1.7.1 retain signing identity and account data. The customer update download is over 10 MB, using the normal signed update channel.
