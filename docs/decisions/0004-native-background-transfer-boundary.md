# ADR 0004: Put reliable transfer behind a native-capable boundary

- **Status:** Accepted
- **Date:** 2026-08-15

## Context

iOS does not guarantee that a JavaScript loop, timer, React component, or ordinary foreground request continues after suspension or termination. Designing reliability around JS liveness would fail the eventual large-file/background requirement.

At the same time, building a custom Expo native module before proving the HTTP/storage path would turn V1 into a React Native integration project.

## Decision

Define a small `TransferEngine` interface for enqueue, cancel, lookup, list/reconcile, and subscription.

- The implemented Expo Go adapter performs foreground uploads for protocol validation.
- The target iOS adapter will be a Swift Expo module backed by background `URLSession` file-upload tasks.
- Native code and the operating system will own durable task execution and state.
- React Native will observe native events while active and reconcile full state on launch/resume.
- Selected PhotoKit assets will be staged to stable app-controlled file URLs before native background enqueue.

## Rationale

This preserves fast UI development without pretending JavaScript is the reliability layer. The interface lets the mechanism change without rewriting pairing, selection, or presentation.

## Consequences

- `ExpoGoTransferEngine` is explicitly disposable.
- The server protocol must eventually support reconciliation and idempotency.
- Native task IDs and Shove upload IDs must be durably mapped before scheduling.
- Staging files cannot be deleted until verified completion is reconciled.
- Native background behavior remains unproven until a custom iOS build exists.

## Alternatives considered

- Keep retry/background scheduling entirely in JavaScript.
- Build the Swift module before the first foreground upload.
- Abandon React Native and rewrite the whole client in Swift immediately.

The chosen boundary avoids both unreliable JS ownership and premature native-project complexity.

