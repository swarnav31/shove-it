# ADR 0007: Stream, hash, flush, and atomically promote uploads

- **Status:** Accepted
- **Date:** 2026-08-15

## Context

The central safety claim is not merely that bytes reached an HTTP handler. A user must not be told an original is safe while it is buffered, partially written, incorrectly sized, or visible in the final library before completion.

Large videos also make whole-request memory buffering unacceptable.

## Decision

For the current whole-file endpoint:

1. Stream the raw request body into `<storage>/.shove/incoming/<upload-id>.part` using a bounded buffer.
2. Calculate SHA-256 during that single pass.
3. Compare received bytes with HTTP content length when it is known.
4. Force the file channel to storage and close it.
5. Atomically move the file into `Shove Library/<year>/<month>`.
6. Persist the verified path, bytes, hash, and timestamps.
7. Only then return a successful receipt.

Delete the partial file on handled failure and record a failed audit state.

## Rationale

Streaming bounds memory use, hashing during the write avoids a second read, and atomic promotion prevents incomplete files from masquerading as library originals. The receipt has a concrete, testable meaning.

## Consequences

- The storage provider must support atomic moves within the configured root.
- Incoming and final paths must remain on the same filesystem.
- The current digest is server-authoritative; the phone does not independently compare a source hash.
- Abrupt process/power termination cleanup still requires hardening.
- Whole-file retry may resend all bytes until resumability is designed.

## Alternatives considered

- Multipart buffering managed by the web framework.
- Write directly to the final filename.
- Calculate the digest in a second full-file pass.
- Report success when the HTTP request completes without storage verification.

Each weakens memory behavior, integrity semantics, or library correctness.

