# ADR 0020: Use a private customer runtime

- **Status:** Accepted for customer packaging
- **Date:** 2026-08-15

## Decision

The friend installer ships pinned Java and Node/Metro runtimes beside Shove. It checks its own small manifest at launch: reuse the private runtime when valid, and install or repair it when missing or damaged.

Shove does not use or modify system Java or Node, regardless of their version. It does not change `PATH` or `JAVA_HOME`. Maven and pnpm remain build-only tools and are never installed for customers.

The first alpha favors one complete installer over a downloader. This is larger, but keeps first use simple and works without a second download. A lightweight downloader can come later if installer size becomes a real problem.

User settings, pairing/audit data, and photo libraries stay outside the replaceable application folder, so repair, upgrade, and uninstall do not remove originals.

## Consequences

- Friends need only Shove on Windows and Expo Go on the iPhone.
- Existing developer environments remain untouched.
- The installer performs a few deterministic file/version checks, not general package management.
