# Operation without ChatGPT or Codex

Loop Secure does not check an OpenAI subscription, use an OpenAI API key or call an AI model to operate. Ending a ChatGPT/Codex subscription does not itself disable the installed customer app, administrator app, payment records, management commands, grace periods, account synchronization, enrollment codes, QR generation or downloads.

The administrator website is https://loop-secure-admin.loopsecure-bacanyclinda.workers.dev and uses Cloudflare Access with the configured owner email. The backend is the existing Supabase `loop-manager` Edge Function and its database/storage. App approval URLs use Cloudflare; the backend default also uses Cloudflare and converts a saved legacy ChatGPT-hosted address to that independent default. Native apps communicate directly with Supabase. Public releases and source are in the owner's GitHub repository.

These provider accounts, their service availability, credentials and free-plan limits remain separate requirements. No paid hosting plan was activated by this change. This is not a promise of unlimited free capacity or permanent provider uptime. Administrator mobile sessions expire normally and can be approved again through Cloudflare. Enrollment codes still expire after 30 minutes. Customer payment/trial deadlines still govern financed-device restrictions; they are unrelated to the business owner's ChatGPT subscription.

Future source changes or rebuilding APKs require development tools and the existing private signing identity, not a ChatGPT subscription. Private keys and administrator credentials must remain outside public GitHub/source archives. Existing APKs and provider deployments have no Codex-credit expiry.
