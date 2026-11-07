# Loop Secure admin on Cloudflare

The deployment target is https://loop-secure-admin.loopsecure-bacanyclinda.workers.dev. Cloudflare Access protects administrator sign-in. The existing ChatGPT Sites admin remains available, and the Supabase backend is shared. Existing Android customer apps continue to use the same Supabase API and customer records.

The website uses Cloudflare Access for administrator login. The Worker independently verifies the Access JWT signature, RS256 algorithm, exact team issuer, application audience, expiry and subject, then matches the configured owner email. Plain identity headers, ChatGPT Sites headers and browser bearer tokens cannot grant access. The privileged backend credential remains a runtime secret and is never embedded in HTML or browser JavaScript.

## One-time hosting setup

1. Sign into your own free Cloudflare account at https://dash.cloudflare.com/sign-up. A paid domain is not required; Workers supplies a workers.dev address.
2. Install Node.js 22 or later. In this folder, run `npm ci`.
3. Run `npm run test` and `npm run build`. Build uses Wrangler's dry run and does not publish.
4. Authenticate Wrangler with `npx wrangler login`, following Cloudflare's browser sign-in. Do not paste account passwords or API tokens into chat.
5. In Cloudflare Zero Trust, create your team and choose the Free plan. Enable Cloudflare Access for this Worker's production workers.dev hostname. Add an **Allow** policy limited to your exact administrator email. Do not use an Everyone or Bypass rule. Preview URLs are disabled in this package.
6. Add the following runtime secrets using Cloudflare's dashboard or `npx wrangler secret put NAME` (the command prompts privately for each value):
   - `CF_ACCESS_TEAM_DOMAIN`: team hostname such as `my-team.cloudflareaccess.com`, without protocol or slash.
   - `CF_ACCESS_AUD`: this Access application's audience tag.
   - `LOOP_ADMIN_EMAIL`: the exact authorized business-owner email, matching the Access policy.
   - `LOOP_ADMIN_TOKEN`: the existing Loop Secure backend administrator credential, transferred through a secure secret channel. Never put it in source, a URL, browser storage, or a public ZIP.
7. Deploy with `npm run deploy`. Configure Access before handing out the URL; incomplete configuration denies all access. `LOOP_API_URL` is pinned to the existing backend in wrangler.jsonc and validated by the Worker.
8. Verify: authorized owner opens the real customer register; a different account is denied; missing/forged authentication is denied; Lock/Unlock show real handset acknowledgements; the signed USB ZIP downloads; administrator-app pairing works through a `?connect_admin=` link.
9. On the first valid owner login that successfully loads the real customer overview, the Worker automatically checks the owner-only `/api/admin/site` setting and activates this exact production workers.dev origin if necessary. The check and update share a four-second timeout and use server-only credentials. Successful activation is cached per isolate; failures preserve the working register and retry on a later overview load. Cloudflare's request context runs activation in the background, so it does not block the customer register. Other hostnames, unverified visitors and unsuccessful overview requests cannot trigger activation. Existing ChatGPT Sites access stays available. Newly generated native-admin approval links then use the Cloudflare website.

The exact Access creation flow may first require creating a named Worker to obtain its workers.dev hostname. An initial deployment without the secrets returns only a configuration error; it cannot expose customer data. Supply the Access configuration and redeploy before testing.

## Local work

`npm run generate` rebuilds assets from the existing application source, preserving Fraunces/DM Sans, the isometric logo and customer workflows. `npm test` runs real cryptographic JWT and transport tests. `npm run build` creates the dry-run bundle under dist/. The generator reads public source assets only. `.dev.vars.example` contains placeholders; actual `.dev.vars` is ignored and must remain private. In the full source checkout, generation uses `../tools/build_cloudflare_admin.py`. A standalone export uses its included generated interface and asset manifest; no unrelated project files or credentials are required.

## Free-plan limits

Cloudflare Workers Free currently allows 100,000 dynamic requests/day with 10 ms CPU per invocation. Cloudflare Access has a Free option for small teams. Supabase has its own separate limits, including inactivity pausing on Free. This is a free pilot path, not an unlimited availability guarantee. Customer devices' sync/long-poll traffic consumes Supabase limits independently of website traffic. Check usage before increasing the fleet. Codex credits are not needed to serve a deployed Worker.

Official references:
- https://developers.cloudflare.com/workers/configuration/cloudflare-access/
- https://developers.cloudflare.com/cloudflare-one/access-controls/applications/http-apps/authorization-cookie/validating-json/
- https://developers.cloudflare.com/workers/platform/pricing/
- https://supabase.com/pricing
