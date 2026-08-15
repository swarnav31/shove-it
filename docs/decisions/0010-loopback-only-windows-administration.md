# ADR 0010: Keep sensitive Windows administration loopback-only

- **Status:** Accepted
- **Date:** 2026-08-15

## Context

The Windows owner needs to create pairing sessions, list/revoke devices, and inspect the complete upload audit. Exposing those operations to every device on the LAN would allow device enumeration, pairing-code creation, or revocation without an established administrator identity model.

## Decision

Allow these management operations only when the HTTP peer is a loopback address:

- create pairing sessions;
- list all paired devices;
- revoke a device by ID;
- list the complete cross-device upload audit.

The phone receives only self-service revocation and its own authenticated upload history.

For the prototype, expose Windows operations through `pair.cmd`, PowerShell, DBeaver read-only inspection, and local APIs.

## Rationale

Loopback is a simple, enforceable boundary for a single-user Windows prototype. It keeps sensitive data and mutation off the LAN while a proper local administrator surface and identity model are absent.

## Consequences

- Administrative calls must originate on the laptop.
- The phone cannot enumerate other devices or uploads.
- DBeaver needs no credentials because it opens the SQLite file under Windows permissions.
- Loopback alone does not solve malicious local software, browser-origin attacks, or multi-user Windows administration.
- A future local admin UI still needs careful same-origin/CSRF and local-user considerations.

## Alternatives considered

- Make management APIs public on the LAN.
- Reuse any paired phone as a household administrator.
- Add a separate administrator password immediately.

Loopback provides the smallest safe boundary for the current single-owner prototype.

