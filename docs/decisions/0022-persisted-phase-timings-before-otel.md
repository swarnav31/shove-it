# ADR 0022: Persist phase timings before adding OpenTelemetry

- **Status:** Accepted
- **Date:** 2026-08-15

## Context

Shove needs evidence for the delay between the phone finishing its HTTP send and Windows reporting a verified original. The current product is one Java process on one Windows computer; shipping an OpenTelemetry SDK, exporter, and collector would add configuration and another customer runtime before distributed tracing provides meaningful value.

## Decision

Instrument the upload path directly with monotonic `System.nanoTime` measurements and stable, OpenTelemetry-friendly phase names:

- `receive_hash_force`
- `external_copy_force`
- `atomic_promote`
- `audit_commit`

Persist millisecond durations with each SQLite upload record, including the active failure phase. Expose completed breakdowns in the loopback-only Windows control panel and emit one structured `upload_performance` log for every verified or failed upload.

The measurements are product/audit data and remain useful without a telemetry backend. If Shove later needs cross-process traces or remote fleet diagnostics, these same phases can become spans and attributes through Micrometer Observation or OpenTelemetry without changing transfer semantics.

## Consequences

- Tonight's benchmarks work offline and survive Java restarts.
- No collector, exporter endpoint, extra port, or customer configuration is required.
- Live server states distinguish receiving, external-drive copying, and final promotion.
- The measurements add small SQLite updates at phase boundaries; their own final audit cost is measured separately.
- Full OTel trace context and remote export are deliberately excluded from the customer runtime. ADR 0023 adds them as an explicit, disposable developer mode while keeping these SQLite measurements authoritative.
