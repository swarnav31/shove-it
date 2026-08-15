# Architecture decision records

These records capture the decisions behind the prototype. Accepted records describe the current direction; proposed records are not implemented commitments.

| ADR | Status | Decision |
| --- | --- | --- |
| [0001](0001-prove-the-pipeline-first.md) | Accepted | Prove the end-to-end pipeline before expanding scope |
| [0002](0002-java-spring-boot-server.md) | Accepted | Keep the server in Java with Spring Boot |
| [0003](0003-native-mobile-client-over-web-upload.md) | Accepted | Use a mobile client as the primary experience, not a generic web uploader |
| [0004](0004-native-background-transfer-boundary.md) | Accepted | Put reliable transfer behind a native-capable `TransferEngine` boundary |
| [0005](0005-lan-first-network-and-security.md) | Accepted | Start LAN-first with explicit firewall and HTTP prototype constraints |
| [0006](0006-short-code-pairing-and-device-tokens.md) | Accepted | Pair with a short code and authenticate with hashed device tokens |
| [0007](0007-stream-hash-and-atomic-promotion.md) | Accepted | Stream, hash, flush, and atomically promote before reporting success |
| [0008](0008-sqlite-persistent-audit.md) | Accepted | Persist device ownership and upload lifecycle in SQLite |
| [0009](0009-configured-boring-filesystem-storage.md) | Accepted | Store originals as normal files beneath a configured root |
| [0010](0010-loopback-only-windows-administration.md) | Accepted | Keep sensitive Windows administration loopback-only |
| [0011](0011-development-authentication-bypass.md) | Accepted for development only | Isolate the V0 authentication bypass in the `dev` profile |
| [0012](0012-embedded-windows-admin-surface.md) | Proposed | Prefer a small Spring-served local admin surface over another frontend stack |
| [0013](0013-server-approved-live-storage-destinations.md) | Accepted | Let the phone choose only among live, server-approved storage destinations |

## ADR format

Each record contains context, decision, rationale, consequences, and alternatives. A superseded decision is retained and linked to its replacement rather than rewritten as if it never existed.
