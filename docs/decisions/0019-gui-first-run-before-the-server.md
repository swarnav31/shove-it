# ADR 0019: Put a Windows setup UI before the embedded server UI

- **Status:** Accepted for source preview
- **Date:** 2026-08-15

## Context

The embedded `/admin` control panel is a good operating surface once Java is running, but it cannot configure the folders and firewall permission required to start that Java process. A clean-room walkthrough exposed that telling a first-time user to run `shove.cmd setup` still made the command line the real onboarding experience.

## Decision

Provide a double-click Windows bootstrap named `Open Shove.cmd`:

- with no saved configuration, it opens a WinForms first-run screen;
- it detects likely external drives, gathers local/external folder choices, and explains firewall consent;
- preparation runs in a managed child process with progress and errors presented in the window;
- after successful preparation, **Open Shove** starts Java and Metro and hands off to `/admin`;
- with saved configuration, the same entry starts Shove directly;
- `Shove Settings.cmd` reopens the bootstrap screen deliberately.

The existing PowerShell commands remain diagnostic and automation interfaces. The customer installer will replace the `.cmd` entry with a normal shortcut and bundled runtime while retaining the same bootstrap-to-control-panel flow.

## Rationale

First-run choices exist before the server can serve HTML. A narrow native Windows bootstrap solves that ordering problem without Electron or a second web server and lets the already-implemented embedded page remain the long-running administration surface.

## Consequences

- Repository testers no longer need to interact with terminal prompts for ordinary setup.
- The source preview still detects and reports missing developer prerequisites; the customer package must bundle them.
- Windows UAC remains an operating-system prompt that the user must approve personally.
- The bootstrap does not weaken the loopback boundary or expose local process-launch operations as browser endpoints.

## Alternatives considered

- Keep command-line setup as the documented first step.
- Let the running web server rewrite its own launcher configuration and invoke elevated processes.
- Add Electron solely for first-run setup.

The first option is not a customer experience, the second creates an unsafe browser-to-process boundary, and the third is disproportionate for this bootstrap.
