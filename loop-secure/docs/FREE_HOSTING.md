# Loop Secure free hosting

The standalone administrator website is https://loop-secure-admin.loopsecure-bacanyclinda.workers.dev . Sign in with bacanyclinda@gmail.com using Cloudflare Access's email code. Both the Cloudflare edge policy and the application verify the authorized owner. The Cloudflare account, Worker and existing Supabase backend are separate from Codex usage credits.

The existing Sites dashboard stays available as a fallback. The new website uses the same backend, customer records, signed client installers and payment history. Android customer apps need no update to reach that backend. New administrator-app connection links can switch to the new site through the owner-only `/api/admin/site` configuration endpoint after authenticated owner access to the real register is verified.

The connection is a server-side authenticated relay. The backend administrator credential is a Cloudflare runtime secret, never browser JavaScript, HTML, or source archives. Access JWT verification requires the configured Cloudflare issuer and application audience, signature, expiry and exact owner email. No public visitor can issue phone controls or read customer records.

Free hosting has limits: Workers Free currently provides 100,000 dynamic requests/day and 10 ms CPU per invocation; Supabase Free has separate storage, bandwidth and function-call limits and may pause low-activity projects after seven days. A large fleet or uninterrupted production service may require a paid plan. No paid plan was selected in this migration.

Rebuild instructions and security tests are in `cloudflare-admin/README.md`. Live publication and anonymous sign-in redirects were checked. The user's first email-code sign-in and a physical handset test are still required; a published resource is not proof of handset enrollment, payment settlement or lock execution.
