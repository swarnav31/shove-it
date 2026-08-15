# ADR 0016: Automate narrow Windows firewall rules with consent

- **Status:** Accepted for development
- **Date:** 2026-08-15

## Context

A healthy Java process on the laptop can still be unreachable from an iPhone because Windows blocks unsolicited inbound traffic. Requiring a new user to calculate a subnet, locate Java and Node executables, open an administrator terminal, and type `New-NetFirewallRule` commands makes first-run setup fragile. Silently adding broad rules would be surprising and unsafe.

## Decision

- Interactive `shove.cmd setup` asks whether to configure Windows Firewall and defaults to yes.
- The launcher explains the change before Windows shows its UAC approval prompt.
- The elevated helper creates a Java TCP rule for the configured server port and, only for the Expo development client, a Node TCP rule for `8081`.
- Both rules are limited to the exact private IPv4 subnet detected from the active adapter with a default gateway. They never use `RemoteAddress Any`.
- The rules apply on Public and Private Windows profiles because the exact source subnet is the primary boundary and many home Wi-Fi networks are initially classified Public.
- `shove.cmd firewall` safely recreates the rules after a network or configuration change.
- `shove.cmd firewall -Remove` removes only the two Shove-managed named rules.
- Non-interactive setup never elevates unless `-ConfigureFirewall` is supplied.

## Rationale

This makes the normal first run reproducible while keeping privileged system changes visible, narrow, and reversible. Program path, protocol, port, and source subnet constraints are stronger than accepting every inbound connection to a port.

## Consequences

- Windows displays a UAC prompt during the normal interactive setup.
- Cancelling the prompt leaves setup incomplete with an actionable error.
- Moving to a different home subnet requires rerunning `shove.cmd firewall`.
- Port `8081` and its Node rule are development-only and will not exist in the packaged customer architecture.
- The helper does not change the Windows network category, disable the firewall, configure the router, or create internet port forwarding.

## Alternatives considered

- Keep manual administrator commands as the primary flow.
- Use a broad program rule or `RemoteAddress Any`.
- Change the active Wi-Fi profile automatically.
- Disable Windows Firewall during setup.

All were rejected because they create avoidable onboarding failure or weaken the local-network boundary.

## Verification note

The first-run test began with both Shove-managed rules absent and older broad Java allow rules disabled. Interactive setup detected `192.168.1.0/24`, requested consent and Windows UAC approval, then created exactly the expected Java TCP `8787` and Expo TCP `8081` rules for the corresponding executable paths. Elevated Windows read-back matched `.shove-dev/logs/firewall.log`. The iPhone subsequently connected, paired against a fresh SQLite database, and completed a verified T7 upload through those rules.
