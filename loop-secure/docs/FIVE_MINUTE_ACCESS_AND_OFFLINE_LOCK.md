# Five-minute access and offline locking

Customer **0.22.0/code 35**, administrator **1.10.0/code 16**, and the administrator website add a separate courtesy code and visible local restriction countdown. Existing white/black customer design and R1,700 payment rules remain.

## Give a client five minutes

1. Update the enrolled customer app in place. Do not uninstall or clear its account data. Use the current website or update the native admin app.
2. Open the intended customer. A temporary code is available for an enrolled, unreleased phone with finance enforcement enabled and an actual manual or overdue restriction. For a controlled test, press **Lock** and wait for the phone to acknowledge the command. Do not change paid dates for testing.
3. Choose **Generate five-minute code**. Copy the code or open the WhatsApp composer; the administrator decides whether to send it. No message is sent automatically.
4. On that phone, open **Support → Five-minute access code** and enter the ten digits while online.
5. The signed five-minute window begins at server verification, with conservative request-start anchoring to avoid granting extra time through network delays. Home shows the remaining local countdown. You can disconnect internet after verification. At expiry the original finance restriction resumes. A genuinely paid/unlocked state received afterward remains valid.

This code is separate from **Generate unlock code**, which restores already-approved paid access. Courtesy codes never approve a payment, add money, extend the payment deadline, clear the original manual lock or release device management. They are phone/credential/revision bound, one-use, redeemable for up to 30 minutes, limited to five issued per hour and five wrong attempts. New issuance replaces the previous unused courtesy code. A new explicit admin command revokes an active courtesy grant and invalidates unused codes. A network retry never restarts the five minutes.

Verification needs internet once. The numeric code is not an offline master PIN. After validation, expiry is enforced locally; loss of internet does not prolong access. Android's screen lock, bank PIN and account credentials are unaffected.

## Local finance clock

The app saves a verified signed payment deadline and an elapsed-realtime anchor. Android's elapsed realtime includes deep sleep and is independent of the displayed date, time zone and editable clock. Local enforcement does not wait for a cloud request or network timeout. Home now shows **Locks in …**, including the configured grace deadline, or the temporary-access countdown. A future paid deadline is not an overdue account; this release does not shorten any actual customer's paid time.

An elapsed-realtime wake-up alarm targets the local restriction deadline. The visible management service is a second enforcement path, and screen-on reapplies cached policy. Exact idle alarms are used where Android permits them; otherwise the fallback is inexact. A temporary grant also has a bounded partial wake lock and one-second local checks to keep the five-minute expiry responsive. Normal financed operation retains five-second checks. No wake lock is held for the full finance period.

Receiving an identical signed revision/time cannot move the saved same-boot anchor forward. Concurrent acceptance is serialized. Fresh online responses are anchored at request start, not their later receipt.

## Restart and platform limits

Elapsed realtime resets on reboot. A normal Android app cannot guarantee a tamper-resistant clock across power loss without trusted platform support. If the saved anchor belongs to another boot or becomes invalid, an enforcement-enabled, unreleased phone requires time verification before normal use. This is a clock-verification restriction, not a claim that an unpaid debt exists. A fresh signed check restores genuinely valid paid access. Disabled policies and permanently released phones are excluded.

Banking, Wi-Fi/settings, support and emergency access remain available during restriction. A five-minute grant is not silently renewed after reboot; online verification may restore only its actual remaining server-authorized duration. Normal Android credential/first-unlock requirements remain.

Android/OEM background policies, denied alarm access, force-stop and firmware manipulation can delay or defeat app-level execution; instantaneous enforcement on every handset is not guaranteed. The helper does not modify firmware or block recovery-button erasure. A new remote admin command still needs delivery to the phone; an already-saved expiry does not need internet.

## Administrator updates

Admin **1.10.0** checks/downloads on unmetered Wi-Fi with a persisted 15-minute scheduling backstop rather than the older six-hour interval. Android controls actual scheduling; an active signed-in session is required. Installation can still require approval. Update notifications avoid repeated alert sounds. The admin app never applies customer phone restrictions.

## Validation

Service-only RPC fixtures use explicit BEGIN/ROLLBACK and leave no fake customer, code, payment or policy change behind. They cover 300-second grants, unchanged ledger/due/manual lock, token binding, expiry, rate/attempt limits, replacement, idempotency and newer-command revocation. Android tests cover monotonic time, deep sleep, wall-clock changes, reboot verification, grace, local alarms/fallback, released/disabled exceptions and re-lock with banking/Wi-Fi available. Browser fixtures cover both separate code types and sensitive-code cleanup. Real-device sleep/wake, installation and banking handoff remain acceptance checks.

For a source deployment, apply `cloud-api/temporary-unlock.sql` after the existing finance schema/dispatcher, deploy the existing authenticated `loop-manager` Edge Function with the updated `index.ts`/`unlock-codes.ts`, regenerate/build the admin Worker assets, and publish both same-signer APKs through their separate installer endpoints. Never replace the client installer with the admin APK. Signing keys, device secrets and merchant credentials remain private. This publication used Supabase migration `loop_five_minute_courtesy_access`, Edge Function version29 and the existing protected Cloudflare site.

Final native validation: 114 customer cases, 14 administrator cases and 2 preview cases passed; both release lint tasks reported zero errors. Nineteen server signature/payment/update cases, eight Worker tests, browser fixtures, route authentication probes and rollback SQL checks passed. No physical handset was connected to the build workspace.
