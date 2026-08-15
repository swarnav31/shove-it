# ADR 0023: Two-tier performance observability

- **Status:** Accepted
- **Date:** 2026-08-15

## Context

Customers need a simple explanation for time spent after a phone reaches 100%, while developers need deeper evidence across HTTP handling, SQLite, and upload phases. Requiring a collector and dashboard service for every customer would conflict with Shove's lightweight, local-first product boundary.

## Decision

Provide two complementary views:

1. A built-in loopback-only performance dashboard backed by phase timings in Shove's SQLite audit. It is always available with the normal server and aggregates only instrumented rows.
2. A separate private Pro distribution that implements Core's framework-neutral `UploadObserver` contract with OpenTelemetry and owns its pinned agent, Grafana lab, trace dashboards, and engineering runbook. The Community distribution installs a no-op observer.

Custom telemetry is limited to low-cardinality destination, phase, outcome, duration, and byte-count fields. It excludes filenames, storage paths, tokens, and device identifiers. Grafana and OTLP ports bind only to Windows loopback.

## Consequences

- Customer installations remain one Java server plus the Expo development client during this prototype phase and have no OpenTelemetry or Docker dependency.
- Performance history survives restarts and remains useful without Docker.
- Pro developers get live percentiles, automatic HTTP/SQLite spans, and exact upload-phase child spans without copying the transfer engine.
- The Pro observer replaces Core's no-op at composition time and is isolated so an observer failure cannot fail a transfer.
- Telemetry label cardinality stays bounded as upload volume grows.
