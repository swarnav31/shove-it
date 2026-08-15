# ADR 0008: Persist device and upload audit data in SQLite

- **Status:** Accepted
- **Date:** 2026-08-15

## Context

The first upload receipt existed only in process memory. That could not answer which paired device uploaded a file, when it happened, whether it succeeded, or what hash/path was verified after Java restarted.

The home server needs durable metadata without requiring a separate database service.

## Decision

Use embedded SQLite at `server/shove.db` by default.

Persist:

- paired device ID, display name, token hash, pairing time, last-seen time, and revocation time;
- upload ID, owning device ID, original filename, expected/received bytes, state, path, SHA-256, lifecycle timestamps, and failure message.

Insert the upload row before reading the body, transition it to verified only after atomic promotion, and retain failed/revoked history. Scope phone history and receipt lookup to the authenticated device. Provide complete audit views only through loopback.

## Rationale

SQLite is a single local file, needs no credentials or database service, works with Spring JDBC, and is easy to inspect in DBeaver. It is proportionate to a single-household server.

## Consequences

- Metadata survives Java restarts.
- Re-pairing creates a new device ID while old uploads retain attribution.
- The database is currently unencrypted and relies on Windows file permissions.
- Schema evolution is currently lightweight application-managed initialization/migration.
- Files transferred before upload auditing cannot be attributed retroactively without inventing data.
- Media bytes stay outside the database.

## Alternatives considered

- In-memory receipts only.
- JSON sidecar files beside every original.
- PostgreSQL or another separate database service.
- Embedding media blobs in the database.

SQLite provides durable relational auditability with the smallest operational footprint.

