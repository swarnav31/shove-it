# Current status and session handoff

**Checkpoint:** 2026-08-15  
**Hardware:** Windows laptop, iPhone 13 mini, Samsung T7 Shield (`E:`, exFAT)

This is the quickest starting point for the next Shove-It session. Detailed evidence is in [Prototype verification](prototype-verification.md), while architectural reasoning is indexed under [Architecture decision records](decisions/README.md).

## Outcome so far

The first real vertical slice is working:

```text
iPhone original -> private Wi-Fi -> paired Java API -> selected Windows storage
                                      -> .part -> SHA-256 -> atomic promotion
                                      -> SQLite audit -> Verified on Windows
```

This is a functional prototype, not a mock. Real originals reached both the laptop filesystem and the external T7, and their recorded byte counts and SHA-256 values matched the Windows files.

## Implemented

- Java 21 and Spring Boot server on TCP `8787`.
- Expo/React Native iPhone prototype with a narrow `TransferEngine` boundary.
- Two-minute, single-use pairing codes created only from Windows loopback.
- Random per-device bearer tokens; only token hashes are persisted.
- Pair, self-unpair, revocation, re-pair, and per-device ownership.
- Raw streaming upload, bounded memory use, `.part` staging, SHA-256, length check, flush, and atomic promotion.
- Local staging followed by a large sequential copy for external destinations, so removable-drive latency is decoupled from the phone's HTTP receive loop.
- SQLite device and upload audit across Java restarts.
- Server-approved storage IDs: `local` and optional `external`.
- Authenticated live destination status and foreground two-second UI refresh.
- Upload audit fields for destination ID and resolved storage root.
- Pairing popup/CLI through `pair.cmd`.
- Embedded laptop-only control panel at `/admin` with an Expo Go QR, pairing countdown, live storage, device revocation, and verified upload history.
- Physical Wi-Fi adapter preference for the QR and phone server address, avoiding WSL/Hyper-V/VPN addresses.
- Same-loopback-origin checks for browser-initiated admin mutations, while preserving native CLI access.
- Double-click Windows bootstrap for first-run storage choice, T7 detection, firewall consent, preparation progress, and handoff to the embedded control panel.
- One-command Windows development setup, managed start/status/pair/firewall/stop, persistent non-secret configuration, and per-process logs through `shove.cmd`.
- Consent-driven, UAC-elevated firewall rules constrained to Java/Node, their required TCP ports, and the detected home subnet.

The two-second JavaScript refresh exists only to update the visible destination picker. Transfer reliability must ultimately be owned by iOS background `URLSession`, never by a React Native background loop.

## Proven on real hardware

- iPhone and Windows connectivity across the private home LAN.
- Narrow Windows firewall access for Java `8787` and development Metro `8081`.
- Pair, upload, self-unpair, re-pair, and correct historical device attribution.
- Verified transfers to the laptop root and `E:\Shove`.
- SQLite persistence and independent Windows size/hash checks.
- Successful atomic promotion on the T7's exFAT filesystem.
- Both destinations visible and selectable from the iPhone.
- Clean T7 disconnection detected within the refresh window.
- Automatic safe fallback from a selected T7 to local storage after disconnection.
- Physical T7 reconnection detected without restarting Java or Expo, with automatic reappearance on the iPhone.
- External staging reduced the same 2,308,501-byte T7 transfer from about 30 seconds to 1.74 seconds while preserving byte/hash verification and zero leftover partials.
- Complete clean-state setup with consent/UAC-created firewall rules, fresh SQLite, new pairing, and a 1,823,392-byte verified T7 upload in 2.051 seconds.
- Elevated firewall read-back matched the persistent firewall log and confirmed that only Java `8787` and development Node `8081` were opened to the detected `192.168.1.0/24` subnet.

## Not yet proven

- Removing the SSD during an active write or promotion.
- Medium and 2-5 GB video behavior.
- Wi-Fi loss, Java termination, or Windows restart during a request.
- Retry, idempotency, resumable chunks, deduplication, and a durable multi-item queue.
- Native iOS background execution and source-side digest comparison.
- TLS/server identity, self-contained packaged installation, Android, or installed shortcut/uninstall integration.

## Current paths and data

| Purpose | Path |
| --- | --- |
| Repository | `D:\Shove-it` |
| SQLite audit | `D:\Shove-it\server\shove.db` |
| Local destination | `D:\Shove-it\server\.shove-data` |
| External destination | `E:\Shove` |
| Pairing helper | `D:\Shove-it\pair.cmd` |
| Control panel | `http://127.0.0.1:8787/admin/` |

There are no database credentials: `shove.db` is a local SQLite file. DBeaver should use its SQLite driver and open that file directly. Raw phone tokens are not in the database; only SHA-256 token hashes are stored. Pairing codes exist in Java memory for at most two minutes.

Each destination contains:

```text
<root>/.shove/incoming/<upload-id>.part
<root>/Shove Library/<year>/<month>/<upload-id>_<safe-original-name>
```

## Restart the prototype

For normal use, double-click `Open Shove.cmd`. It starts the saved configuration and opens the control panel. Use `Shove Settings.cmd` to change storage.

Diagnostic PowerShell fallback:

```powershell
cd D:\Shove-it
.\shove.cmd start
```

Run `.\shove.cmd setup` first when configuration or dependencies have not been prepared. The server starts even when the T7 is absent and reports `external` unavailable until the configured `E:\Shove` path returns.

Useful lifecycle commands:

```powershell
.\shove.cmd status
.\shove.cmd pair
.\shove.cmd stop
```

Use the control panel opened by `start` to scan the Expo Go QR and create a pairing code. A clean phone receives the current server address from Metro; a saved, non-revoked device token reconnects automatically. `stop` preserves configuration, audit history, pairings, and originals.

## Safety and design invariants

- The phone chooses an opaque destination ID; it never supplies an arbitrary Windows path.
- Java resolves the ID through its configured allow-list and rechecks availability when an upload starts.
- Unknown IDs receive a client error; disconnected destinations receive a conflict response.
- A successful UI label means Java flushed, hashed, atomically promoted, and persisted the verified audit row.
- External uploads temporarily require local free space approximately equal to the original size; multi-gigabyte validation must include a free-space preflight before this becomes customer-ready.
- Current HTTP is acceptable only on the trusted private LAN with no router port forwarding.
- Development authentication bypass exists only at `/api/v1/dev/uploads` under the `dev` profile. The real `/api/v1/upload` remains paired and authenticated.
- Never delete phone originals automatically in this prototype.

## Recommended next validation order

1. Package a self-contained Windows alpha with an included Java runtime; keep Docker optional for technical deployments.
2. Bundle Node.js, Metro, and locked mobile dependencies around the implemented Expo QR flow; friends install only Expo Go.
3. Replace `.cmd` entry points with installed shortcuts and add safe uninstall/firewall cleanup.
4. Run the complete customer-alpha acceptance test on a Windows environment without developer tools.
5. Invite a small friend cohort, then validate a medium and 2-5 GB video plus controlled interruption behavior.
6. Use observed failures to specify resumable semantics and native iOS background transfer; Android follows after the iPhone customer path is usable.

## Repository hygiene

The initial prototype checkpoint is committed on `main` and pushed to the private repository [swarnav31/shove-it](https://github.com/swarnav31/shove-it). Local `main` tracks `origin/main`. Preserve this working milestone before reliability experiments by making focused commits for subsequent changes.
