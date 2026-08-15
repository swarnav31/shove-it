# ADR 0021: Treat phone status as authenticated foreground telemetry

- **Status:** Accepted
- **Date:** 2026-08-15

## Context

The phone and Windows control panel should show useful device storage and connection state. Expo SDK 54's newer iOS disk-space properties currently return the system free-space value for both available and total capacity, producing a misleading value such as `732 MB free of 732 MB`.

This visibility must not turn into a reliability dependency on a React Native background loop.

## Decision

While Shove is visible, the paired mobile client reads capacity through Expo FileSystem's legacy asynchronous native methods and sends an authenticated status heartbeat every ten seconds. Java validates the platform and capacity values, rejects impossible readings including `available >= total`, applies its own receipt timestamp, and stores the latest report with the paired-device record.

The loopback-only Windows control panel polls the existing device list and treats a report newer than 25 seconds as connected. Older reports remain visible with their update time and are labeled idle.

Status reporting is best-effort presentation data. Uploads, authentication, verification, retry behavior, and future iOS background `URLSession` work must never depend on this JavaScript timer.

## Consequences

- The phone no longer presents known-invalid free/total pairs.
- Windows can show platform, current foreground presence, free capacity, total capacity, and freshness for each paired device.
- A force-closed or backgrounded Expo app becomes idle naturally instead of pretending to be continuously online.
- Android uses the same contract, but its physical-device reading remains unverified until Android hardware is tested.
