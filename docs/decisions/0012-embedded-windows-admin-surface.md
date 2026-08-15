# ADR 0012: Prefer an embedded Windows administration surface

- **Status:** Proposed
- **Date:** 2026-08-15

## Context

The prototype now has multiple Windows owner operations: health, storage inspection, address discovery, pairing-code creation, device listing/revocation, and upload-audit inspection. Scripts and DBeaver are useful development probes but are not a customer experience.

Adding Electron, another React application, or a separate web server would create a new build/runtime/distribution problem before transfer reliability is proven.

## Proposed decision

When an administration UI is implemented, serve a small loopback-only page from Spring Boot itself, likely at `/admin`.

It should consolidate existing capabilities rather than introduce new transfer features:

- health and configured storage;
- reachable LAN address;
- pairing-code creation and countdown;
- paired-device timestamps and revocation;
- upload audit and verification details;
- access to the Shove Library location.

Use server-rendered HTML/CSS and minimal JavaScript. A later packaged Windows tray application may open this page and manage server lifecycle.

## Rationale

The Java server already owns the data and loopback APIs. An embedded surface avoids a second frontend toolchain and can evolve into the local operator experience without affecting the iPhone transfer engine.

## Consequences

- This is not implemented in the current repository.
- `pair.cmd`, PowerShell, and DBeaver remain the prototype tools.
- Browser-origin, CSRF, local-user, and file-opening behavior must be designed before implementation.
- The page must not weaken loopback restrictions or expose bearer-token hashes.
- UI work should follow, not displace, large-file and interruption testing.

## Alternatives considered

- Electron desktop application now.
- A separate React/Vite administration server.
- Keep PowerShell as the customer interface.
- Make the iPhone the administrator for all household devices.

The embedded direction best matches the single-process Java architecture while remaining intentionally deferred.

