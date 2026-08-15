# ADR 0012: Prefer an embedded Windows administration surface

- **Status:** Accepted and implemented
- **Date:** 2026-08-15

## Context

The prototype now has multiple Windows owner operations: health, storage inspection, address discovery, pairing-code creation, device listing/revocation, and upload-audit inspection. Scripts and DBeaver are useful development probes but are not a customer experience.

Adding Electron, another React application, or a separate web server would create a new build/runtime/distribution problem before transfer reliability is proven.

## Decision

Serve a small loopback-only page from Spring Boot itself at `/admin`.

It should consolidate existing capabilities rather than introduce new transfer features:

- health and configured storage;
- reachable LAN address;
- pairing-code creation and countdown;
- paired-device timestamps and revocation;
- upload audit and verification details;
- access to the Shove Library location.

Use bundled HTML/CSS and minimal framework-free JavaScript. The development launcher opens this page after Java and Metro are healthy. A later packaged Windows shell will own process lifecycle and open the same surface.

The implemented page provides:

- live Java, Expo, LAN, and storage readiness;
- an Expo Go QR generated locally for the preferred physical Wi-Fi adapter;
- two-minute pairing-code creation and countdown;
- paired-device history and revocation;
- verified upload history and destination attribution;
- two-second removable-storage refresh.

The page, its data APIs, and QR are rejected when the HTTP peer is not loopback. Mutating owner operations also reject browser requests whose `Origin` is not the same loopback origin; command-line tools without an `Origin` remain supported.

## Rationale

The Java server already owns the data and loopback APIs. An embedded surface avoids a second frontend toolchain and can evolve into the local operator experience without affecting the iPhone transfer engine.

## Consequences

- `pair.cmd`, PowerShell, and DBeaver remain diagnostic fallbacks rather than the main operating surface.
- Storage configuration, firewall elevation, process lifecycle, and opening a library folder still belong to the Windows packaging layer; the web server must not launch arbitrary local processes in response to a browser request.
- The page does not expose bearer-token hashes.
- Browser-origin risk is reduced through loopback enforcement and same-origin checks on mutations. A future multi-user Windows release may require a stronger local-user session boundary.

## Alternatives considered

- Electron desktop application now.
- A separate React/Vite administration server.
- Keep PowerShell as the customer interface.
- Make the iPhone the administrator for all household devices.

The embedded direction best matches the single-process Java architecture while remaining intentionally deferred.
