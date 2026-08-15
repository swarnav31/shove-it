# Shove documentation

This directory is the source of truth for the prototype's architecture, operational guidance, verified behavior, and design decisions.

## Start here

| Document | Purpose |
| --- | --- |
| [Current status and handoff](current-status.md) | Session checkpoint, proven behavior, restart commands, open risks, and next tests |
| [Architecture](architecture.md) | Current components, boundaries, deployment, APIs, and future native-transfer seam |
| [Flows and data](flows-and-data.md) | Pairing, upload, unpair/re-pair, integrity, and SQLite data model |
| [Prototype verification](prototype-verification.md) | What was tested on real hardware, automated checks, and what remains unproven |
| [Windows usage guide](windows-usage-guide.md) | Setup, firewall rationale, pairing, audit inspection, and troubleshooting |
| [Customer alpha](customer-alpha.md) | Friend-facing installation experience and acceptance criteria |
| [Architecture decision records](decisions/README.md) | One durable record for each major design decision |

## Status language

The documents use these labels deliberately:

- **Implemented:** present in this repository and exercised by tests or the real prototype.
- **Proven:** observed end to end on the Windows laptop and iPhone 13 mini.
- **Proposed:** agreed direction or likely next design, but not implemented.
- **Deferred:** intentionally outside the current vertical slice.

The current product proof is a foreground Expo Go transfer. The target reliability model is a platform-native iOS background `URLSession`; no document should imply that native background execution has already been implemented.

## Documentation principles

- Keep the current implementation separate from target architecture.
- Record why a decision was made, not only what code exists.
- Treat security limitations as explicit prototype constraints.
- Update verification claims only after observing the behavior.
- Preserve the narrow goal: prove and harden the transfer pipeline before expanding the product surface.
