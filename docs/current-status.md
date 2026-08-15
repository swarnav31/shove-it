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
- SQLite device and upload audit across Java restarts.
- Server-approved storage IDs: `local` and optional `external`.
- Authenticated live destination status and foreground two-second UI refresh.
- Upload audit fields for destination ID and resolved storage root.
- Pairing popup/CLI through `pair.cmd`.

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

## Not yet proven

- Reconnecting the physical T7 and observing automatic reappearance. The code and automated availability-transition test cover this path, but the user did not record the physical observation before this checkpoint.
- Removing the SSD during an active write or promotion.
- Medium and 2-5 GB video behavior.
- Wi-Fi loss, Java termination, or Windows restart during a request.
- Retry, idempotency, resumable chunks, deduplication, and a durable multi-item queue.
- Native iOS background execution and source-side digest comparison.
- TLS/server identity, packaged installation, Android, or a customer-ready Windows admin UI.

## Current paths and data

| Purpose | Path |
| --- | --- |
| Repository | `D:\Shove-it` |
| SQLite audit | `D:\Shove-it\server\shove.db` |
| Local destination | `D:\Shove-it\server\.shove-data` |
| External destination | `E:\Shove` |
| Pairing helper | `D:\Shove-it\pair.cmd` |

There are no database credentials: `shove.db` is a local SQLite file. DBeaver should use its SQLite driver and open that file directly. Raw phone tokens are not in the database; only SHA-256 token hashes are stored. Pairing codes exist in Java memory for at most two minutes.

Each destination contains:

```text
<root>/.shove/incoming/<upload-id>.part
<root>/Shove Library/<year>/<month>/<upload-id>_<safe-original-name>
```

## Restart the prototype

In one Windows PowerShell window:

```powershell
cd D:\Shove-it\server
$env:SHOVE_STORAGE_ROOT = "D:\Shove-it\server\.shove-data"
$env:SHOVE_EXTERNAL_STORAGE_ROOT = "E:\Shove"
$env:SHOVE_EXTERNAL_STORAGE_NAME = "T7 Shield (E:)"
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

The server starts even when the T7 is absent. It will report `external` unavailable until the configured `E:\Shove` path returns.

In a second PowerShell window:

```powershell
cd D:\Shove-it\apps\mobile
pnpm.cmd start --lan
```

Open the printed project in Expo Go. A saved, non-revoked device token reconnects automatically. For a fresh pairing code, double-click `pair.cmd` or run `pair.cmd -Cli` from the repository root.

## Safety and design invariants

- The phone chooses an opaque destination ID; it never supplies an arbitrary Windows path.
- Java resolves the ID through its configured allow-list and rechecks availability when an upload starts.
- Unknown IDs receive a client error; disconnected destinations receive a conflict response.
- A successful UI label means Java flushed, hashed, atomically promoted, and persisted the verified audit row.
- Current HTTP is acceptable only on the trusted private LAN with no router port forwarding.
- Development authentication bypass exists only at `/api/v1/dev/uploads` under the `dev` profile. The real `/api/v1/upload` remains paired and authenticated.
- Never delete phone originals automatically in this prototype.

## Recommended next validation order

1. Reconnect the T7 and confirm it reappears within about two seconds without restarting Java or Expo.
2. Transfer a medium video to each destination.
3. Test a 2-5 GB video while observing phone behavior, Java memory, time, and disk space.
4. Interrupt Wi-Fi, then Java, during controlled transfers and inspect `.part` files plus SQLite state.
5. Use those observed failures to specify idempotency, retry, and resumable-offset semantics.
6. Implement the Swift background `URLSession` adapter behind the existing `TransferEngine` boundary.

Do not expand into Android, cloud accounts, automatic photo deletion, deduplication, or a polished admin dashboard until the failure tests have made the reliability requirements concrete.

## Repository hygiene

The initial prototype checkpoint is committed on `main` and pushed to the private repository [swarnav31/shove-it](https://github.com/swarnav31/shove-it). Local `main` tracks `origin/main`. Preserve this working milestone before reliability experiments by making focused commits for subsequent changes.
