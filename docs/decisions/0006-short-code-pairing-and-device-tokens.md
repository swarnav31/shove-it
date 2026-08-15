# ADR 0006: Pair with a short code and authenticate with device tokens

- **Status:** Accepted
- **Date:** 2026-08-15

## Context

Pairing is an authentication boundary, not a prerequisite for testing raw upload mechanics. Once the pipe worked, the prototype needed a small security mechanism without prematurely implementing a full public-key challenge-response protocol.

The pairing experience also needed to be repeatable from Windows without hand-written HTTP commands.

## Decision

- Create six-digit pairing codes only through a loopback request.
- Give each code a two-minute TTL and single-use semantics.
- Exchange a valid code for a unique device ID and a random 32-byte bearer token.
- Store the raw token in iOS SecureStore.
- Store only `SHA-256(token)` in SQLite.
- Authenticate uploads and receipt history by resolving the token hash to a non-revoked device.
- Preserve revoked device rows for audit attribution.
- Let a device revoke itself and clear its local token.
- Provide `pair.cmd` as a Windows popup/countdown helper, with a CLI fallback.

## Rationale

The mechanism is understandable, testable, and sufficient to prevent ordinary unpaired LAN uploads on the real endpoint. It also establishes a stable `DeviceAuthenticator` boundary for later credential evolution.

## Consequences

- Pairing codes are held in Java memory and disappear on restart.
- Re-pairing creates a new device identity rather than silently reactivating the old token.
- Revocation is immediate and non-destructive.
- The token remains vulnerable to network interception while prototype HTTP is used.
- Device naming is currently supplied by the client and is not a strong identity claim.

## Alternatives considered

- No authentication after V0.
- Passwords stored on both devices.
- Ed25519 challenge-response and certificate provisioning immediately.

The selected design is deliberately intermediate: stronger than unauthenticated LAN access, smaller than the eventual production identity protocol.

