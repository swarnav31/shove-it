# Transfer performance and observability

Shove keeps the durable customer performance view in the Community server and exposes a no-op-by-default extension contract for deeper Pro telemetry.

## Built-in customer view

Open the Windows control panel at [http://127.0.0.1:8787/admin/](http://127.0.0.1:8787/admin/) and find **Transfer performance**. It works offline, has no extra prerequisites, and reads measurements persisted with the upload audit in SQLite.

The view shows:

- verified and failed sample counts;
- median (typical) and 95th-percentile (slow-end) total server time for verified transfers;
- Windows-storage versus external-drive throughput and latency;
- median and slow-end time for receive + hash, external drive save, atomic promotion, and audit commit;
- a phase waterfall for recent transfers.

Only uploads made after phase instrumentation was introduced have measurements. Historical rows stay intact and are omitted from aggregates when `total_ms` is null. Failed transfers count toward the reliability rate and remain visible in the recent list, but they do not skew verified-transfer latency percentiles. Percentiles use the deterministic nearest-rank method. A one-file result is useful evidence for that file, not a stable benchmark; use several similar large files before comparing destinations.

The built-in API is loopback-only:

```text
GET /api/v1/admin/performance
```

## Deep-observability extension

`shove-core` defines upload lifecycle and phase callbacks through `UploadObserver`. Community installs a safe no-op implementation, so ordinary setup and `shove.cmd start` never download an agent, Docker image, or dashboard service.

The private Pro distribution supplies the OpenTelemetry implementation and engineering stack. It uses the same stable phase names, while Core wraps the installed observer so ordinary observer failures are reported but cannot escape into transfer execution. Filenames, paths, pairing tokens, and device IDs are not part of the shared observability contract.
