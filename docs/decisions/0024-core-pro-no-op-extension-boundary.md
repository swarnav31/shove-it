# ADR 0024: Core/Pro no-op extension boundary

- **Status:** Accepted
- **Date:** 2026-08-16

## Context

Shove needs a public Community product and a private Pro distribution without maintaining two transfer engines. Pro capabilities such as deep observability must be able to consume upload lifecycle facts, but Core reliability must not depend on a private repository, telemetry SDK, Docker, licensing service, or dashboard.

## Decision

Every Pro capability begins with a minimal, stable contract owned by `shove-core`. Core supplies a safe no-op implementation and the Community server remains complete when no replacement is installed. Pro depends on published Core/server artifacts and replaces the default implementation at application-composition time.

Extension execution is fail-safe. Core catches ordinary runtime failures from installed observers, reports them locally, and continues the transfer path. Contracts expose only the facts required by the capability and do not expose package-private implementation objects.

The first implementation is `UploadObserver`:

```text
Community server -> fail-safe wrapper -> no-op observer
Pro server       -> fail-safe wrapper -> OpenTelemetry observer
```

## Consequences

- Dependency direction is always `Pro -> Community server -> Core`.
- Community has no OpenTelemetry, Grafana, Docker, licensing, or private-code dependency.
- Pro composes the public server JAR; it does not fork or copy upload logic.
- A Pro extension can lose telemetry, but it cannot turn a valid upload into a failed upload.
- New Pro work must first justify and define its narrow Core contract and no-op behavior.
