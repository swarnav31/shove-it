# ADR 0011: Isolate the plumbing bypass in the development profile

- **Status:** Accepted for development only
- **Date:** 2026-08-15

## Context

The first milestone needed to test raw iPhone/HTTP-to-filesystem mechanics without blocking on pairing. Removing authentication from the real upload endpoint would make it difficult to add the security boundary cleanly and easy to ship an unsafe assumption accidentally.

## Decision

- Keep `DeviceAuthenticator` as an explicit request-pipeline boundary.
- Protect the real `/api/v1/upload` endpoint with `PairedDeviceAuthenticator` even when the `dev` profile is active.
- Expose a separate `/api/v1/dev/uploads` endpoint only under Spring's `dev` profile.
- Give bypass uploads a synthetic development device ID so audit records remain structurally complete.
- Document that a production build must not expose the bypass route or activate the development profile.

## Rationale

This allowed V0 plumbing tests while preventing the primary protocol from assuming authentication does not exist. The bypass is obvious, separately routed, and profile-gated.

## Consequences

- Anyone on the permitted LAN can use the development endpoint while the `dev` profile is active.
- Firewall scope remains important during development.
- Test and production startup commands must be unambiguous.
- Packaging must exclude or disable the bypass by default.
- The `authentication-mode` development setting is not a production option.

## Alternatives considered

- Leave `/api/v1/upload` unauthenticated temporarily.
- Block all upload work until cryptographic pairing was complete.
- Hide a bypass behind a special header on the production route.

The separate profile-specific endpoint makes the temporary risk explicit and removable.

