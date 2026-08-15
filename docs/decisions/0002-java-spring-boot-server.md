# ADR 0002: Keep the server in Java with Spring Boot

- **Status:** Accepted
- **Date:** 2026-08-15

## Context

The home server must accept large request bodies, enforce authentication, persist audit metadata, manage ordinary files, and eventually support resumable protocols and a local administration surface. The project owner explicitly chose Java for the server.

## Decision

Use Java 21 and Spring Boot for the Windows server. Keep capabilities grouped by product area—pairing, authentication, upload, storage, and server information—rather than organizing the entire application into global controller/service/repository folders.

Use Spring MVC with multipart handling disabled for the raw streaming endpoint.

## Rationale

- Java provides predictable stream and filesystem primitives for large files.
- Spring Boot supplies HTTP, configuration, lifecycle, JDBC, and testing infrastructure without requiring a second server stack.
- Java can later package an embedded local administration page with the server.
- The code can remain cross-platform even though Windows is the first host.

## Consequences

- Java 21 and Maven are development prerequisites.
- The prototype runs as a foreground process rather than a Windows service.
- Customer distribution must later package a runtime and startup lifecycle.
- Server storage behavior is testable independently of the mobile client.

## Alternatives considered

- Node.js for both client tooling and server.
- A Python upload server.
- A Windows-only .NET service.

They were not selected because Java was a product constraint and is suitable for the core transfer/storage work.

