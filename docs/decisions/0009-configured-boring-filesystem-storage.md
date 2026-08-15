# ADR 0009: Store originals as normal files under a configured root

- **Status:** Accepted
- **Date:** 2026-08-15

## Context

Users should retain control of their originals even if Shove is stopped or removed. The first host may use an internal drive or external SSD. Automatic Windows drive detection would add OS-specific complexity before storage behavior is understood.

## Decision

- Accept a configured storage root through `SHOVE_STORAGE_ROOT`.
- Default development storage to `server/.shove-data`.
- Keep incoming partials under an internal `.shove/incoming` directory.
- Store verified originals as ordinary files under `Shove Library/<year>/<month>`.
- Prefix the original safe filename with the server upload UUID.
- Sanitize client-provided filenames and never accept a destination path from the client.
- Keep the default and optional external roots explicit server configuration. Client selection is refined by [ADR 0013](0013-server-approved-live-storage-destinations.md).

## Rationale

Normal files are inspectable, portable, recoverable, and independent of Shove. A configured root keeps the core storage service cross-platform and makes internal/external drive testing explicit.

## Consequences

- Customers can browse and back up the library with ordinary tools.
- UUID prefixes prevent collisions while preserving recognizable names.
- Registering or changing the approved roots requires configuration and restart; choosing between registered roots does not.
- A missing or unwritable drive is a visible health/failure condition.
- Filesystem capability—especially atomic move support—is a requirement.
- Uninstall must never delete originals by default.

## Alternatives considered

- Store originals inside SQLite blobs.
- Rename files to hashes only.
- Automatically scan and choose external drives in V1.
- Let clients submit absolute destination paths.

The chosen layout is intentionally boring and user-controlled.

## Verification note

The configured-root design was exercised with a Samsung T7 Shield formatted exFAT. A real iPhone upload landed only beneath `E:\Shove`, its persisted size/hash matched the file, atomic promotion completed, and no partial remained. A clean physical disconnect was detected within the UI refresh window and safely fell back to local storage. Physical reconnection remains to be recorded.
