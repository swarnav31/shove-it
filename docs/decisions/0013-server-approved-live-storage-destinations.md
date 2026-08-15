# ADR 0013: Use server-approved live storage destinations

- **Status:** Accepted
- **Date:** 2026-08-15

## Context

The laptop may have both an internal library and a removable SSD. The user should be able to choose the target on the iPhone and see when the SSD is disconnected. Letting a phone submit an arbitrary Windows path would cross the trust boundary, expose host details as authority, and create path traversal and accidental-write risks.

## Decision

- Java owns an allow-list of configured storage roots.
- The built-in destination has opaque ID `local`; an optional configured SSD has opaque ID `external`.
- An authenticated `GET /api/v1/destinations` returns display name, path for user recognition, availability, default status, and optional filesystem/free-space metadata.
- The active React Native screen refreshes this UI status every two seconds. This timer is only for presentation and is not part of transfer reliability.
- The upload sends `X-Shove-Destination`; Java resolves the opaque ID and rejects unknown IDs or currently unavailable roots.
- If the selected destination disappears before an upload starts, the UI falls back to the available default. Java performs the authoritative availability check again when the upload begins.
- Record `destination_id` and `storage_root` in the upload audit.

## Rationale

This gives the phone a simple appliance-like choice without granting it authority over the Windows filesystem. Polling is sufficient for foreground drive-presence feedback, while the request-time Java check handles races. The future iOS background transfer engine remains operating-system-owned and does not depend on this JavaScript refresh loop.

## Consequences

- A disconnected SSD becomes unavailable without restarting Java.
- Reconnecting the configured path makes it selectable again.
- Optional free-space metadata failure does not hide an otherwise writable destination.
- Adding a new approved root still requires Windows-side configuration and a server restart.
- This is a small fixed registry, not general Windows volume discovery or a phone file browser.
- A drive can still disappear during a transfer; interruption and resume semantics remain future reliability work.

## Alternatives considered

- Accept an absolute path from the phone.
- Automatically expose every Windows volume.
- Keep one root and require a restart for every choice.
- Design upload correctness around a React Native background polling loop.

All were rejected because they either weaken the host boundary, increase Windows-specific scope, harm the UX, or conflict with the native background-transfer requirement.

## Verification note

On the real iPhone 13 mini and Samsung T7 Shield, both approved destinations appeared and accepted verified uploads. Disconnecting the idle T7 made it unavailable within the two-second UI refresh window and automatically moved selection to the local destination without a Java or Expo restart. Reconnecting it made the destination reappear without restarting either process.
