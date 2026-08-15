# ADR 0017: Prefer a self-contained Windows package over Docker for customers

- **Status:** Accepted
- **Date:** 2026-08-15

## Context

The development prototype currently asks the Windows laptop to provide Java 21, Maven, Node.js, and pnpm. Docker could place the server and development toolchain in images, but it would replace those prerequisites with Docker Desktop, WSL 2 or Hyper-V, container networking, image pulls, and bind mounts for the Windows and removable-SSD libraries.

The customer goal is an appliance-like Windows experience, not a developer deployment workflow. The main storage path is a removable Samsung T7 whose live disconnect/reconnect behavior and native exFAT write performance are product-critical.

## Decision

- Build and test with Java/Maven/Node in CI or a developer environment; do not require them on customer laptops.
- Package the Spring Boot server as a self-contained Windows application with an included Java runtime, using Java 21 `jpackage`/`jlink` or an equivalent installer tool.
- Serve the future administration UI from the Java process so the Windows package does not need a second frontend runtime.
- Ship an installed iOS/Android client so customers do not need Expo or Metro.
- Keep Docker Compose as an optional advanced/developer deployment only after Windows bind-mount performance, SQLite behavior, firewall scope, and T7 disconnect/reconnect are proven.

## Rationale

Java 21's `jpackage` produces self-contained platform packages and generates or accepts a bundled runtime image. On Windows it can produce an `exe` or `msi`, so Java and Maven remain build-machine concerns rather than customer prerequisites.

Docker Desktop can simplify dependency versions for developers, but it is itself a substantial Windows prerequisite. Its supported WSL 2 path requires modern WSL, virtualization, and at least 8 GB RAM. Linux containers run in a Docker-managed VM; Windows host paths reach them through Docker Desktop's file-sharing/bind-mount layer. Published ports also add a container-to-host networking layer, and omitting a host IP binds a published port to all host interfaces by default.

Those abstractions are reasonable for servers but cut across Shove's most sensitive proven path: direct, durable writes to a hot-pluggable Windows SSD and narrowly managed host firewall rules. They must be measured before becoming an alternative, not assumed to simplify the product.

## Consequences

- The next customer-distribution milestone should be a self-contained Windows application/installer, not a public Docker image.
- Developers still need the existing prerequisites until CI artifacts and the installed mobile client exist.
- An intermediate portable `jpackage --type app-image` can validate the bundled-runtime approach before installer/service integration.
- Windows packages must be built on Windows; other desktop platforms require their own packaging jobs.
- Docker remains useful for CI, reproducible builds, technical users, or a future NAS/Linux deployment.
- Docker Desktop licensing must be considered for professional use in larger organizations.

## Evidence and references

- [Java 21 jpackage guide](https://docs.oracle.com/en/java/javase/21/jpackage/index.html)
- [Java 21 basic packaging and bundled runtime](https://docs.oracle.com/en/java/javase/21/jpackage/basic-packaging.html)
- [Docker Desktop Windows requirements](https://docs.docker.com/desktop/setup/install/windows-install/)
- [Docker bind-mount behavior](https://docs.docker.com/engine/storage/bind-mounts/)
- [Docker port publishing](https://docs.docker.com/reference/compose-file/services/#ports)
- [Docker Desktop licensing](https://docs.docker.com/subscription/desktop-license/)
