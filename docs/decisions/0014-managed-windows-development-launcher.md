# ADR 0014: Use one managed Windows development launcher

- **Status:** Accepted for development
- **Date:** 2026-08-15

## Context

The proven prototype required users to coordinate Maven, Java environment variables, pnpm, Metro, IP discovery, pairing helpers, logs, and shutdown across several PowerShell windows. That is difficult to reproduce and obscures whether a failure belongs to Shove or to its development tooling.

## Decision

- Provide `shove.cmd` and `shove.ps1` as the single development command surface.
- Support `setup`, `start`, `status`, `pair`, `firewall`, and `stop`.
- Persist non-secret configuration, runtime PID records, and logs under ignored `.shove-dev`.
- Make `setup` validate prerequisites, package the executable Spring Boot JAR, and install locked mobile dependencies.
- Make `start` rebuild Java only when inputs are newer, launch Java and Metro in the background, wait for stable health, and print LAN URLs.
- Make `stop` verify recorded process identity and stop only the recorded Java/Metro trees.
- Keep prerequisite installation and Windows Firewall changes explicit; firewall automation requires an affirmative choice and Windows UAC approval.
- Preserve SQLite, device records, configuration, and media across normal stop/start cycles.

## Rationale

One command surface makes the current prototype reproducible without pretending it is a customer installer. PID-scoped lifecycle and per-process logs are safer and more diagnosable than broad process-name termination. Persistent configuration eliminates repeated environment-variable entry while remaining easy to inspect and discard.

## Consequences

- A developer can prepare once and subsequently start or stop Shove from one terminal.
- A disconnected configured SSD is reported but does not block the server.
- The first setup may take several minutes while Maven and pnpm prepare dependencies.
- `.shove-dev` is machine-local and must remain untracked.
- This launcher still requires development prerequisites and Expo Go; it is a bridge to, not a substitute for, the Windows installer and embedded admin surface.

## Alternatives considered

- Continue documenting independent manual commands.
- Silently install all prerequisites and broad firewall rules.
- Stop every Java or Node process by executable name.
- Build the final installer before stabilizing the development lifecycle.

These alternatives were rejected because they retain needless friction, make surprising system changes, risk unrelated processes, or skip a useful intermediate validation boundary.
