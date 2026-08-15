# ADR 0005: Start LAN-first with an explicit prototype trust boundary

- **Status:** Accepted
- **Date:** 2026-08-15

## Context

Shove's core promise is local transfer without a commercial cloud. The initial devices share a home Wi-Fi network. Locally trusted TLS provisioning for an iPhone is non-trivial, while exposing an unauthenticated service broadly would be unsafe.

Windows also blocks unsolicited inbound connections, and virtual adapters such as WSL or VPN interfaces can make the correct address unclear.

## Decision

- Bind Java to the LAN intentionally on port `8787`.
- Use the physical Wi-Fi IPv4 address, not loopback, WSL, Docker, or VPN addresses.
- Permit inbound traffic only for the exact Java program/port and home subnet.
- Permit Node `8081` similarly only during Expo development.
- Use authenticated HTTP only on a trusted private LAN for the prototype.
- Do not configure router port forwarding or remote/public access.
- Keep sensitive management endpoints loopback-only.

## Rationale

This proves the local pipeline with a narrow, understandable exposure. Program, port, and subnet scoping reduce accidental reachability while preserving the phone-to-laptop path.

## Consequences

- Users must be on the same non-isolated Wi-Fi network.
- Guest/AP isolation can prevent connectivity.
- Home DHCP address changes require manual address updates in the prototype.
- Bearer tokens travel without transport encryption and must not be used on hostile networks.
- Production remote or untrusted-network support requires TLS and authenticated server identity.

## Alternatives considered

- Bind only to `127.0.0.1`, which makes phone access impossible.
- Open the port to any remote address.
- Use a public relay/cloud service.
- Block the prototype on local certificate provisioning.

All either prevent the core test, widen exposure unnecessarily, contradict local-first behavior, or delay the vertical slice.

