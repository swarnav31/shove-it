# Shove Core

`shove-core` contains small, framework-neutral contracts shared by Shove distributions. It intentionally has no Spring Boot, OpenTelemetry, Docker, UI, storage-driver, or licensing dependency.

Every optional Pro capability must enter through a Core-owned contract with:

- a safe no-op default used by the Community distribution;
- one-way dependency flow from Pro to Core;
- fail-safe execution when the callback is observational;
- no filenames, paths, tokens, or device identifiers unless the contract explicitly and safely requires them.

The first contract is `UploadObserver`. It exposes upload and stable phase lifecycle events. Community uses `UploadObservers.noOp()`; Pro supplies an OpenTelemetry implementation at application-composition time.
