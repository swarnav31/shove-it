# Shove on Windows: easy usage guide

Back to the [main README](../README.md).

## First: know which version this is

This repository currently provides a **source preview**, not a finished installer. Its Windows setup is graphical, but it still uses development software already installed on the laptop.

| Laptop requirement | Source preview today | Friend installer target |
| --- | --- | --- |
| Java | Java 21 JDK installed separately | Bundled |
| Maven | Installed separately | Not needed |
| Node.js | Node.js 20.19+ installed separately | Bundled for the Expo alpha |
| pnpm | pnpm 10 installed separately | Not needed |
| Docker or WSL | Not needed | Not needed |
| Git | Only when cloning; downloading a ZIP is fine | Not needed |
| Phone | Expo Go on iOS or Android | Expo Go for the friend alpha |

Do not send the source preview to a nontechnical friend yet. The friend-ready milestone is one Windows package plus Expo Go—nothing else.

That installer will check and repair Shove's own bundled runtime. It will not depend on, upgrade, or reconfigure Java, Node, Maven, or pnpm already installed for development.

## Set up the source preview

### Before opening Shove

Confirm the following:

- Java 21, Maven, Node.js 20.19+, and pnpm 10 are installed on Windows.
- Expo Go is installed on the phone.
- The Windows laptop and phone are connected to the same trusted home Wi-Fi.
- The repository is on the Windows filesystem, such as `D:\Shove-it`, not inside WSL.
- The Windows system drive has at least 5 GB free; 10 GB is more comfortable for the development toolchain.

If the setup window reports missing software, install it in this order and then reopen Shove:

1. [Eclipse Temurin JDK 21](https://adoptium.net/temurin/releases/?version=21) for Windows. Use the JDK, not only the JRE, and allow the installer to add Java to `PATH`.
2. [Apache Maven for Windows](https://maven.apache.org/guides/getting-started/windows-prerequisites.html).
3. A Windows LTS installer from the [official Node.js download page](https://nodejs.org/en/download).
4. pnpm 10. After Node is installed, run `npm install --global pnpm@10.14.0` once in PowerShell. The [official pnpm installation guide](https://pnpm.io/installation) documents the available Windows installation methods.

These are temporary source-preview requirements. Do not ask a friend or customer to perform this toolchain setup.

### First launch

1. Open the repository folder in File Explorer.
2. Double-click **`Open Shove.cmd`**. A brief command-window flash is expected in this source preview; all interaction happens in the Shove setup window.
3. Leave **Windows folder** at its suggested value or select **Browse**.
4. If an external SSD is connected, Shove suggests `<drive>:\Shove`. Keep it, browse elsewhere, or leave external storage blank.
5. Give the SSD the friendly name that should appear on the phone.
6. Leave **Allow my phone to reach Shove on this home Wi-Fi** selected.
7. Select **Prepare Shove** and personally approve the Windows permission prompt.
8. Wait for **Shove is ready**. The first preparation can take several minutes.
9. Select **Open Shove**. The Windows control panel opens in the browser.

Setup creates narrow firewall access only for the Java upload port `8787` and Expo port `8081`, restricted to the current home subnet. It does not disable Windows Firewall or configure internet/router access.

### Connect and transfer

1. In the Windows control panel, confirm the status says **Ready** and the intended storage says **Available**.
2. Open Expo Go on the phone and scan the QR shown by Shove.
3. In Windows, select **Create pairing code**.
4. Enter the six-digit code on the phone and pair.
5. Choose **This Windows PC** or the named external SSD.
6. Choose one photo or video and keep Expo Go open during this prototype transfer.
7. Wait for **Verified on Windows**.
8. Confirm the Windows control panel shows the transfer as **Verified**.

Shove never deletes the source item from the phone in this prototype.

### Use it again later

- Double-click **`Open Shove.cmd`** to start the saved setup and open the control panel.
- Double-click **`Shove Settings.cmd`** to change storage choices.
- If the external SSD is absent, Shove marks it disconnected and keeps Windows storage available.
- If Windows remembers a phone that the mobile app no longer recognizes—or vice versa—Shove clears the orphaned credential and returns to pairing.

## If something does not work

- **The setup window lists missing software:** install the named Java/Maven/Node/pnpm prerequisite, then reopen Shove.
- **Open Shove appears to do nothing:** wait up to 30 seconds, then open `http://127.0.0.1:8787/admin/` on the laptop.
- **The phone cannot load the QR project:** confirm both devices use the same non-guest Wi-Fi and approve the Windows firewall prompt.
- **Pairing fails:** create a new code; codes are single-use and expire after two minutes.
- **The SSD is disconnected:** reconnect the same drive and folder. It should reappear automatically within a few seconds.
- **More detail is needed:** logs are stored under `.shove-dev\logs` in the repository.

The remainder of this guide explains the networking and diagnostic command-line workflow. Normal onboarding should not require it.

## What Shove connects

Shove transfers files directly across the home Wi-Fi network:

```text
Phone
  |
  | home Wi-Fi
  v
Windows computer
  |
  | Shove server
  v
Internal drive or external SSD
```

The photo or video does not need to pass through a Shove cloud service. The phone opens a connection to the Windows computer's private Wi-Fi address, such as `192.168.1.8`.

Both devices must normally be connected to the same home network. A guest Wi-Fi network may prevent devices from seeing one another.

## Why Windows asks for network access

Windows Firewall blocks unsolicited inbound connections by default, especially when a Wi-Fi network is classified as **Public**.

Opening a website from the computer still works because the computer initiates that outbound connection. Shove works in the other direction: the phone initiates a new connection to the Shove server on the computer.

For the prototype, two local development services are involved:

| Port | Program | Purpose |
| --- | --- | --- |
| TCP `8787` | Java / Spring Boot | Pairing and photo/video uploads |
| TCP `8081` | Node.js / Expo | Loads the development app in Expo Go |

Port `8081` is required by the source preview and the planned Expo Go friend alpha. It disappears only after Shove can distribute a standalone mobile build instead of loading through Expo Go.

Allowing these ports does not make the computer a public internet server. A safe rule should be restricted to:

- the Shove program;
- the specific TCP port;
- the local home subnet;
- inbound connections only.

For example, a computer with address `192.168.1.8/24` is usually on subnet `192.168.1.0/24`.

## Security guidance

- Use Shove on a trusted home network, not public café, hotel, or airport Wi-Fi.
- Keep the firewall rule limited to the local subnet.
- Do not configure port forwarding on the router.
- Do not expose port `8787` directly to the internet.
- Pairing protects the upload endpoint, but the current prototype still uses unencrypted HTTP on the trusted LAN.
- A production release must authenticate the server identity and encrypt transport before supporting untrusted or remote networks.

## Diagnostic command-line workflow

The graphical path above is the supported onboarding flow. Use the following only to troubleshoot or develop Shove.

### 1. Connect both devices

Connect the Windows computer and phone to the same normal Wi-Fi network. Avoid a network name containing “Guest” unless the router explicitly permits communication between guests.

### 2. Find the computer's Wi-Fi address

Open PowerShell and run:

```powershell
ipconfig
```

Under **Wireless LAN adapter Wi-Fi**, find **IPv4 Address**. It will commonly look like `192.168.1.8` or `192.168.0.20`.

Ignore addresses belonging to WSL, Docker, VPNs, or virtual Ethernet adapters. The address required by the phone is the physical Wi-Fi adapter's address.

### 3. Configure and start Shove

From the repository:

```powershell
cd D:\Shove-it
.\shove.cmd setup
.\shove.cmd start
```

`setup` asks for the local library and optional external SSD. It checks the existing Java/Maven/Node/pnpm installation, packages Java, installs the locked mobile dependencies, and stores non-secret settings in ignored `.shove-dev\config.json`. It also offers to configure the firewall. Choosing yes still requires approval in a Windows UAC prompt.

`start` runs the packaged Java server and Expo/Metro in the background, records only the processes it created, waits for health, and prints the phone server and Expo project URLs. Use these commands afterward:

```powershell
.\shove.cmd status
.\shove.cmd pair
.\shove.cmd firewall
.\shove.cmd stop
```

`stop` terminates only the recorded development processes and preserves configuration, SQLite history, pairing records, and originals. Logs are under `.shove-dev\logs`.

### 4. Permit only the required local connection

The prototype needs an inbound rule for the Java server on TCP port `8787`. Expo development also needs Node.js on TCP port `8081`.

The normal `setup` flow offers to create these rules. If setup was skipped, cancelled, or the laptop moved to a different subnet, run:

```powershell
.\shove.cmd firewall
```

The launcher detects the active private IPv4 network, shows the subnet it will use, and opens a Windows UAC prompt. The elevated helper replaces only the named Shove rules. It constrains each rule by program, TCP port, and the detected subnet; it never opens the port to `Any` remote address.

Every install or removal appends a non-secret audit to `.shove-dev\logs\firewall.log`. It records the requested action and the resulting rule name, executable path, enabled state, direction, action, profiles, protocol, local port, and remote-address scope.

To inspect the result from an administrator PowerShell:

```powershell
Get-NetFirewallRule -Name "ShoveIt-Java-8787", "ShoveIt-Expo-8081" |
  Get-NetFirewallPortFilter
```

To remove only the Shove-managed rules:

```powershell
.\shove.cmd firewall -Remove
```

This does not disable Windows Firewall, change the Wi-Fi network category, modify the router, or create internet port forwarding.

### 5. Check the server locally

On the Windows computer:

```powershell
Invoke-RestMethod http://127.0.0.1:8787/healthz
```

Expected result:

```text
status storageWritable
------ ---------------
ok                 True
```

### 6. Check it from the phone

In a browser on the phone, open:

```text
http://<windows-ip>:8787/healthz
```

For example:

```text
http://192.168.1.8:8787/healthz
```

If Safari displays a small JSON response containing `"status":"ok"`, the Wi-Fi route and Windows firewall are ready.

### 7. Open the Expo development client

This step exists for the source preview and the planned Expo Go friend alpha. The friend Windows package will bundle Node and Metro, so friends will not install or operate them. A later standalone mobile release can remove Metro and port `8081` entirely.

Expo/Metro was already started by `shove.cmd start`. To print its current URL again:

```powershell
cd D:\Shove-it
.\shove.cmd status
```

`start` opens the Shove control panel automatically. Scan the Expo Go QR shown there. Shove prefers the physical Wi-Fi adapter over WSL, Hyper-V, VPN, and tunnel adapters and supplies that same server address to a clean phone bundle. The managed processes continue running after the start command returns.

The Expo toolchain uses temporary space on the Windows system drive even when the repository is on another drive. Keep at least 5 GB free on `C:` before installing packages or starting Metro; 10 GB is a healthier margin for Windows generally.

### 8. Create a pairing code

In the control panel, select **Create pairing code**. The code, expiry countdown, storage state, paired devices, and recent transfers remain together in the same Windows view.

As a diagnostic fallback, run `.\shove.cmd pair` or double-click `pair.cmd`. Shove opens a small Windows window containing a fresh single-use code, a copy button, and a live countdown.

For a terminal-only display:

```powershell
cd D:\Shove-it
.\shove.cmd pair -Cli
```

Each launch creates a new six-digit code. The code expires after two minutes and can be used only once. Code creation is accepted only from the Windows computer itself; another device on the network cannot mint its own pairing code.

### 9. Pair and transfer

In the Shove development app:

1. A clean Expo session uses the Windows server address supplied by Metro. If the phone has a saved address from an earlier test network, replace it with the **Phone server URL** shown by `.\shove.cmd status`.
2. Select **Find my server**.
3. Enter the six-digit pairing code.
4. Select **Pair phone**.
5. In **Destination**, choose the Windows library or external SSD.
6. Choose one photo or video.

The destination list refreshes about every two seconds while the app is open. If the configured SSD is unplugged, it is shown as disconnected and a selected SSD falls back to the local destination. Reconnecting the same configured drive/path makes it available again. The phone never sends an arbitrary Windows path.

The phone reports **Verified on Windows** only after the server has:

1. streamed the request into a `.part` file;
2. flushed it to storage;
3. calculated SHA-256;
4. atomically promoted it into the dated Shove Library folder;
5. returned the verification receipt.

The prototype does not delete the source item from the phone.

## Inspecting paired devices and upload history

Use `http://127.0.0.1:8787/admin/` on Windows for normal inspection and unpairing. Shove rejects this page, its QR, and its audit data over the LAN; they are visible only on the laptop. Browser mutations also require the same local origin, preventing an unrelated website from silently creating codes or revoking a phone.

The commands below remain useful as diagnostic probes.

Shove stores its audit database at `server\shove.db` by default. The raw device token is never stored there; the server stores only its SHA-256 hash.

The following management endpoints accept requests only from the Windows computer itself:

```powershell
# Paired device ID, name, pairing time, last-seen time, and revocation time
Invoke-RestMethod http://127.0.0.1:8787/api/v1/devices

# Every new upload: owning device, filename, path, byte counts, state,
# SHA-256, start/update/verification times, and any failure message
Invoke-RestMethod http://127.0.0.1:8787/api/v1/admin/uploads
```

Upload records survive Java restarts. A paired phone can retrieve only its own history through the authenticated `GET /api/v1/uploads` route and cannot retrieve another device's receipt by guessing an upload ID.

Files transferred before audit persistence was introduced remain intact, but Shove does not invent a device identity or timestamp for those historical files.

### Revoke a paired device

First list devices and copy the intended `deviceId`. Then run:

```powershell
Invoke-RestMethod `
  -Method Delete `
  -Uri "http://127.0.0.1:8787/api/v1/devices/<device-id>"
```

Revocation immediately prevents that token from authenticating another upload. The phone must pair again to receive a new token. Revocation does not delete previously transferred files or their audit records.

The mobile app also provides **Unpair this phone**. It calls the authenticated self-revocation endpoint and clears the token from platform secure storage. Use that action when repeating the pairing journey from scratch; it does not remove transferred originals.

## Removing the prototype firewall rules

```powershell
.\shove.cmd firewall -Remove
```

Approve the Windows UAC prompt. The helper removes only the named Shove rules. Removing them prevents the phone from initiating new connections to those development services; it does not delete transferred photos, paired-device records, or application files.

## Troubleshooting

### The laptop works locally, but the phone browser cannot connect

Check these in order:

1. Confirm both devices show the same Wi-Fi network name.
2. Confirm the URL uses the Wi-Fi IPv4 address—not `127.0.0.1`, a WSL address, a Docker address, or a VPN address.
3. Confirm the Java server is still running.
4. Confirm the `8787` firewall rule exists and its remote subnet matches the Wi-Fi address.
5. Temporarily pause a VPN such as Cloudflare WARP if it intercepts local routes, then retry.
6. Check the router for **AP isolation**, **client isolation**, or **guest isolation**. These settings prevent devices on the same Wi-Fi from communicating.

Inspect the rule with:

```powershell
Get-NetFirewallRule -Name "ShoveIt-Java-8787" |
  Get-NetFirewallPortFilter
```

### The address worked yesterday but not today

Home routers commonly assign addresses dynamically. Run `ipconfig` again and update the address in the phone app. Production Shove should use local discovery so customers do not need to manage addresses manually.

### Pairing says the code is invalid or expired

Run `pair.cmd` again and enter the new code within two minutes. Each code can be claimed only once.

### A transfer fails immediately

- Confirm the phone remains on Wi-Fi.
- Confirm the destination drive is connected and writable.
- Confirm the destination has more free space than the selected file.
- Retry with a small photo before testing a multi-gigabyte video.

### Expo reports `ENOSPC` or "no space left on device"

This means the Windows system drive is full, not that the phone lacks storage. Free space on `C:` and retry. Package download caches under `C:\Users\<you>\AppData\Local\npm-cache` and the pnpm store are disposable and can be downloaded again; do not remove the repository's `node_modules` directory while Metro is expected to run.

### Expo reports that PowerShell scripts are disabled

Use the Windows command wrapper explicitly:

```powershell
pnpm.cmd start --lan
```

This avoids changing the computer-wide PowerShell execution policy.

### The app reports `AbortSignal.timeout is not a function`

Update to the current repository version. The prototype uses an `AbortController` timer compatible with Expo Go's Hermes runtime rather than relying on the newer `AbortSignal.timeout` API.

### A `.part` file remains

A `.part` file is an incomplete transfer, not a verified library item. Do not treat it as a successful backup. The prototype removes partial files after handled failures; abrupt process termination cleanup will be hardened later.

## Requirements for the friend installer

The friend experience must not require PowerShell, Java, Maven, Node.js, pnpm, Git, Docker, or manual firewall commands.

The Expo Go friend-alpha package should:

- bundle the Java runtime and compiled Shove server;
- bundle Node, Metro, locked mobile dependencies, and the prepared project;
- ask before creating narrowly scoped rules for Java `8787` and Expo `8081`;
- detect and display the reachable private-network address and QR;
- allow network access removal and device unpairing in the UI;
- preserve transferred files during uninstall unless the customer explicitly chooses otherwise.

Once Shove has standalone mobile builds, the Windows package can drop Node, Metro, Expo port `8081`, and the Expo Go prerequisite.
