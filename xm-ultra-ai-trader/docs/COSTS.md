> Update: account-bound live-mode source support was added after the original demo baseline. See [live-mode notes](LIVE_MODE.md). Defaults remain demo; no live broker validation has been performed. Older demo-specific procedures remain the recommended validation path.

# Cost and licensing

The delivered design needs **no additional paid development API, hosting subscription, database subscription, premium indicator, app-store account, or news subscription**. No credit card is requested by the project. Existing computer, internet, electricity, and access to the development environment are assumptions; this software cannot make those costs zero.

| Item | Initial cost assumption | Important qualification |
|---|---|---|
| Project source | Free under the project licence | Review the delivered licence; no profit warranty. |
| Python, FastAPI, Uvicorn, SQLite, NumPy | Free/open-source distributions | Their own licences apply. |
| Optional scikit-learn | Free/open-source | Offline research extra; memory/CPU use may be significant on 4 GB. |
| MT5 and MetaEditor | No initial desktop software fee | Proprietary, **not open source**. Required by the selected platform. |
| XM demo account | Eligible free broker demo | Eligibility, regional availability, expiry, instruments, and broker terms vary. No real deposit is required by this project. |
| Windows/browser | Existing compatible installation | No new Windows licence is supplied. Browser/OS licence terms apply. |
| PWA installation | No Play Store publishing account | Android browser installation requires a supported secure origin. |
| Dashboard speech | No API paid by this application | Browser recognition may use the browser vendor's remote service, requires consent, and may be unavailable. Typed chat always remains available. |
| Browser speech output | Existing browser/OS voices | Availability of offline voices varies; no paid voice provider is configured. |
| Economic calendar | Native MT5 facility when available | Not a paid feed or guaranteed comprehensive real-time news service. Missing required data blocks entries. |
| Local HTTPS | Free locally managed certificates | Requires correct trust installation on each device; not automatic. |
| Optional private VPN | Only if a suitable eligible free plan exists | External account and terms may apply; disabled by default and not provisioned by setup. Verify current eligibility before choosing it. |
| Hosting/VPS | None | The existing Windows computer must stay powered on and connected. |

A strict “every component must be open source” requirement conflicts with the explicit choice of Windows, XM, and MT5. The practical free alternative delivered here is open-source application code and dependencies around the existing proprietary MT5/Windows environment. Replacing MT5 would also replace the requested execution platform.

No OpenAI/ChatGPT API key is needed. The conversational component defaults to local rule-based explanations. External LLM providers, paid notification services, public hosting, and paid data feeds are not configured or required.

Real trading, if separately developed and authorized in the future, involves spreads, possible commissions and swaps, slippage, losses, and other broker terms. Those costs are not made free by software and are outside this demo-only release.
