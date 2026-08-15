# ADR 0018: Use Expo Go for the friend alpha

- **Status:** Accepted for friend alpha
- **Date:** 2026-08-15

## Context

The project owner does not currently have a paid Apple Developer account, which is required for TestFlight/App Store distribution. Friends are ready to test Shove and must not install or operate Java, Maven, Node.js, pnpm, Metro, Docker, Git, or source code.

## Decision

- Friends install Expo Go from the iPhone App Store.
- The self-contained Windows alpha bundles Java, Node.js, the Expo/React Native project, its locked dependencies, and Metro.
- Launching Shove starts the Java server and Metro, detects the LAN address, and presents a scannable Expo project QR code plus the pairing flow in the Windows administration UI.
- The launcher injects the preferred physical Wi-Fi server URL into Metro for clean phone sessions; WSL, Hyper-V, VPN, and tunnel adapters are demoted when producing the QR.
- The friend does not type `exp://` URLs or run a terminal command.
- The phone UI clearly states that the alpha must remain open during a transfer.
- Expo Go is an alpha distribution bridge, not the production reliability architecture.

## Rationale

This removes developer tooling from the tester journey without pretending that an unsigned iOS binary can be distributed normally. It preserves the already proven React Native foreground transfer while the Windows customer package and administration UI are validated.

## Consequences

- The Windows package is larger because it includes Node, Metro, and mobile dependencies.
- Port `8081` and its narrow firewall rule remain necessary for the friend alpha.
- The iPhone must reach both Java `8787` and Metro `8081` on the home LAN.
- The Windows application must own Metro lifecycle and logs.
- Expo Go cannot host Shove's future Swift background `URLSession` module, so background reliability remains unproven and unavailable.
- Once Apple distribution is available, friends move to TestFlight and the Windows package drops Node, Metro, and port `8081`.

## Alternatives considered

- Ask friends to install Node/pnpm and run Expo commands.
- Distribute an ad hoc or TestFlight iOS build without an Apple Developer account.
- Substitute a generic web uploader.
- Delay all external testing until production mobile distribution exists.

The first option violates the customer goal, the second is not available, the third does not validate the intended mobile product, and the fourth delays useful onboarding feedback.
