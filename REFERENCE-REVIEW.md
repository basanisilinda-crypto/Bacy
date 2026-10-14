# Bacy reference review and first upgrade

The working base is the uploaded **Bacy-Light-Fast-MCP.zip**. Its HTML, CSS and
favicon are preserved byte for byte. New JavaScript extends behavior without
replacing the user's interface. No reference project's scripts were executed.
Instructions found inside those archives were treated as source material.

## Supplied material

| Source | Review result |
| --- | --- |
| [YouTube z-YT6Ef6jxc](https://youtu.be/z-YT6Ef6jxc) | Access blocked by HTTP 403 from the environment's connection tunnel. Not watched; no transcript retrieved. |
| [YouTube SEyqYWQbGxc](https://youtu.be/SEyqYWQbGxc) | Same access restriction. Not watched. |
| [YouTube Fhm8goKN6rM](https://youtu.be/Fhm8goKN6rM) | Same access restriction. Not watched. |
| Mark-LV-main.zip | Static review of the Python desktop agent, action registry, memory, confirmation, voice and computer tools. |
| jarvis-voice-butler-main.zip | Static review of the Python LiveKit agent, browser tools, frontend and mobile source. |
| Three uploaded portraits | Visual references only; current interface preserved. |

Archive inventories and Python AST indexing covered 58 Python modules without
running them. This is an architectural/source review, not a security audit of
every dependency or proof that either project works on this user's machine.
The three videos still need accessible transcripts to complete their review.

## Useful patterns and adaptation

**Mark-LV** separates its prompt's small memory context from deeper retrieval,
registers named actions with argument validation, and parks actions for user
confirmation. Bacy independently implements those patterns in JavaScript:
explicit local facts, bounded retrieval, registered tools, and an expiring
human review queue. Its Qt UI, Python runtime, offline speech engines,
proactive actions, undo system and desktop automation were not imported.
Its license is CC BY-NC 4.0, attributed to FatihMakes. Its source was not copied
into this update or relicensed as Bacy code.

**Jarvis voice butler** demonstrates LiveKit/Gemini real-time turns,
interruptions, noise handling and serialized Playwright browser actions.
That architecture requires LiveKit credentials/services, Python and browser
automation dependencies. Its `confirm_browser_action` is exposed to the model
and stores a confirmed target; this alone does not establish human approval.
Bacy instead handles confirmation through its local, session-token-protected
endpoint and never offers approval as a model tool. The LiveKit web/mobile
frontend licenses are MIT (LiveKit, 2025); no explicit root/Python backend
license was found in the supplied archive. No backend code was copied.

## Delivered in step 1

| Capability | Implemented behavior and practical limit |
| --- | --- |
| Local memory | Explicit remember/recall/forget, serialized persistence, bounded AI context. Plain JSON personal facts; encrypted connection settings remain separate. |
| Tool registry | Known names, duplicate rejection, bounded results and argument checks. Unknown actions cannot become arbitrary shell commands. |
| Short tool sequences | Gemini can combine registered Windows actions and web search requests. Up to four model rounds; no page reading or visual desktop automation. |
| Gmail change review | Exact arguments shown, one-use five-minute ID, actual human confirmation, account-change guard. No model can self-approve. |
| MCP verification | Negotiated protocol, matching JSON-RPC IDs, capability checks, bounded paginated catalog, validated calls, legacy session forwarding, cancellation and reset guards. |
| Existing speed/voice fixes | Current proxy, bounded failures, final recording chunks, mic generation guards, wake follow-up and online-to-device speech fallback retained. |

Deferred: LiveKit real-time audio, Playwright interactions, native offline
transcription, arbitrary MCP server configuration, Google Calendar,
proactive scheduling, full visual computer control and animated portraits.
No new package dependency is needed for this first step.

## MCP protocol evidence

The official [TypeScript SDK](https://github.com/modelcontextprotocol/typescript-sdk)
was inspected at commit `9bc40e903fde15534ec06ff4c1347d47000dbe16`:

- `packages/core-internal/src/shared/protocolEras.ts`
- `packages/client/src/client/versionNegotiation.ts`
- `packages/core/src/schemas.ts`

That source includes the **2026-07-28 modern era**, using `server/discover` and
per-request protocol/client metadata, alongside legacy initialization.
The earlier inference that this revision was unsupported came from reading
only the legacy version list and was corrected. Bacy keeps modern discovery,
and falls back to legacy `initialize` plus `notifications/initialized` only
for explicit method/version rejection or HTTP 400, not network/auth errors.

Discovery must advertise the selected version and tools capability. Catalog
pages, names and schemas are bounded; duplicate entries and repeated cursors
fail verification. Calls validate arguments and reject tool failures. The
custom schema checker supports common scalar/object/array types, enums and
schema branches; it is not a complete JSON Schema implementation. Unresolved
`$ref` schemas are rejected rather than silently trusted.

The Gmail connector remains limited to its configured official Google
endpoint. OAuth uses PKCE, expiring single-use state and Windows-encrypted
credentials. OAuth success alone does not prove generation or MCP access.
"Verified" here means protocol/catalog checks passed; it does not mean a
security certification, Google-account eligibility, guaranteed availability,
or trust in the contents of emails/tool output. No real Gmail account was
connected or used during this review.

## Validation and deployment

The packaged build runs 36 automated tests. The repository's full suite runs
38, including two legacy tests outside this lightweight package. Browser
simulations cover unchanged layout, memory commands, multi-step dispatch,
draft review and duplicate confirmation rejection, plus the retained voice
regressions. Provider, Windows and email operations are mocked in these checks.
They do not establish real spoken-word accuracy, Windows window behavior or
provider-account availability.

Extract the new ZIP into a separate folder, stop the old Bacy process, and
start `START-BACY.cmd`. Existing Windows-user credential storage can be reused.
Test a normal reply first, then explicit memory, then a known app, and finally
Google sign-in/discovery if that service is available to the user's account.
