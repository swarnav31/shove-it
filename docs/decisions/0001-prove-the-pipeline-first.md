# ADR 0001: Prove the transfer pipeline first

- **Status:** Accepted
- **Date:** 2026-08-15

## Context

The product vision includes pairing, queues, retries, resumability, background transfer, dashboards, discovery, packaging, duplicate detection, and deletion guidance. Attempting all of them before moving a real original would turn the first weekend into an architecture exercise and leave the central risk unanswered.

The first question was narrower: can an actual iPhone 13 mini send a real photo or multi-gigabyte video over home Wi-Fi to a Java process on Windows and receive a trustworthy verification result?

## Decision

Build a thin vertical slice first:

1. Connect an iPhone to a manually entered Windows address.
2. Pair with a basic short code.
3. Select one original.
4. Upload it as a whole file in the foreground.
5. Stream, hash, flush, and atomically store it.
6. Show verified completion.

Defer features that do not answer that pipeline question. Never automatically delete from the iPhone.

## Rationale

Real hardware and real files expose the constraints that should shape reliability work. A small working protocol provides better evidence for resumability and background scheduling than a speculative comprehensive design.

## Consequences

- The current UI is intentionally one-screen and one-file-at-a-time.
- Manual address entry and development tooling are acceptable temporarily.
- Whole-file upload is acceptable only as a learning protocol.
- Reliability work follows observed interruption and large-file tests.
- The result is a genuine prototype, not a production-ready backup system.

## Alternatives considered

- Build a polished dashboard and device-management product first.
- Implement chunking, deduplication, and background execution before the first transfer.
- Use a web form only to prove HTTP upload.

All were rejected as the initial milestone because they either expand scope or fail to test the intended mobile product boundary.

