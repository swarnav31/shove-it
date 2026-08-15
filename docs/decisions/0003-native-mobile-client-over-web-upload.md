# ADR 0003: Use a mobile client as the primary experience

- **Status:** Accepted
- **Date:** 2026-08-15

## Context

A generic Safari upload form would quickly prove that HTTP can carry a file. It would not prove the intended phone product: persistent pairing, original-library selection, visible task state, future background ownership, reconciliation, and deliberate post-verification cleanup.

The iPhone 13 mini is the primary client and Windows is the development host.

## Decision

Use a React Native mobile application as the primary client. Use Expo Go for the first foreground protocol proof. Treat a web uploader only as a possible fallback or recovery surface, not the core product.

## Rationale

- The product depends on native photo-library and transfer behavior.
- A mobile UI can represent connection, pairing, progress, verification, and later retry/cancel state.
- React Native allows the product UI to move quickly while preserving a path to targeted Swift modules.
- Expo Go lets the actual iPhone participate before App Store/TestFlight packaging exists.

## Consequences

- Metro and port `8081` are development-only prerequisites.
- Expo Go cannot validate custom Swift background-transfer code.
- Distribution eventually requires an iOS development/production build pipeline.
- The current foreground engine is evidence for the server protocol, not the final reliability implementation.

## Alternatives considered

- Safari-only multipart or raw file upload.
- A fully native Swift application from the first hour.
- Automatic photo synchronization without explicit selection.

The web option was too shallow, full Swift would slow the first Windows-only prototype, and automatic sync was outside the deliberate-selection product scope.

