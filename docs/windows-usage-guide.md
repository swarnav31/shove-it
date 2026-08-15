# Shove on Windows: local-network usage guide

Back to the [documentation index](README.md).

This guide explains how Shove connects an iPhone to a Windows computer, why Windows may ask for network permission, and how to configure that access safely.

> This document describes the prototype. A customer release should automate most of these steps in the Windows installer and desktop application.

## What Shove connects

Shove transfers files directly across the home Wi-Fi network:

```text
iPhone
  |
  | home Wi-Fi
  v
Windows computer
  |
  | Shove server
  v
Internal drive or external SSD
```

The photo or video does not need to pass through a Shove cloud service. The iPhone opens a connection to the Windows computer's private Wi-Fi address, such as `192.168.1.8`.

Both devices must normally be connected to the same home network. A guest Wi-Fi network may prevent devices from seeing one another.

## Why Windows asks for network access

Windows Firewall blocks unsolicited inbound connections by default, especially when a Wi-Fi network is classified as **Public**.

Opening a website from the computer still works because the computer initiates that outbound connection. Shove works in the other direction: the iPhone initiates a new connection to the Shove server on the computer.

For the prototype, two local development services are involved:

| Port | Program | Purpose |
| --- | --- | --- |
| TCP `8787` | Java / Spring Boot | Pairing and photo/video uploads |
| TCP `8081` | Node.js / Expo | Loads the development app in Expo Go |

Port `8081` is only required during development. A packaged customer app will not need Expo or the Node.js development server.

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

## Prototype setup

### 1. Connect both devices

Connect the Windows computer and iPhone to the same normal Wi-Fi network. Avoid a network name containing “Guest” unless the router explicitly permits communication between guests.

### 2. Find the computer's Wi-Fi address

Open PowerShell and run:

```powershell
ipconfig
```

Under **Wireless LAN adapter Wi-Fi**, find **IPv4 Address**. It will commonly look like `192.168.1.8` or `192.168.0.20`.

Ignore addresses belonging to WSL, Docker, VPNs, or virtual Ethernet adapters. The address required by the iPhone is the physical Wi-Fi adapter's address.

### 3. Start the Shove server

From the repository:

```powershell
cd D:\Shove-it\server
$env:SHOVE_STORAGE_ROOT = "D:\Shove Photos"
$env:SHOVE_EXTERNAL_STORAGE_ROOT = "E:\Shove"
$env:SHOVE_EXTERNAL_STORAGE_NAME = "T7 Shield (E:)"
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

`SHOVE_STORAGE_ROOT` is the always-configured local choice. The two external variables register an optional second choice; omit them when no external destination is wanted. Java approves these paths at startup, but checks their connection and writability for every status request and upload.

### 4. Permit only the required local connection

The prototype needs an inbound rule for the Java server on TCP port `8787`. Expo development also needs Node.js on TCP port `8081`.

The rules should be limited to the home subnet. The following example assumes `192.168.1.0/24`; change it to match the computer's Wi-Fi address.

Run PowerShell as Administrator:

```powershell
$homeSubnet = "192.168.1.0/24"
$javaProgram = (Get-Command java.exe).Source
$nodeProgram = (Get-Command node.exe).Source

New-NetFirewallRule `
  -Name "ShoveIt-Java-8787" `
  -DisplayName "Shove-It Java server (home LAN only)" `
  -Direction Inbound `
  -Action Allow `
  -Protocol TCP `
  -LocalPort 8787 `
  -RemoteAddress $homeSubnet `
  -Program $javaProgram `
  -Profile Public,Private

New-NetFirewallRule `
  -Name "ShoveIt-Expo-8081" `
  -DisplayName "Shove-It Expo development server (home LAN only)" `
  -Direction Inbound `
  -Action Allow `
  -Protocol TCP `
  -LocalPort 8081 `
  -RemoteAddress $homeSubnet `
  -Program $nodeProgram `
  -Profile Public,Private
```

Do not replace `$homeSubnet` with `Any`. Restricting the source network is an important part of the prototype's safety model.

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

### 6. Check it from the iPhone

In Safari on the iPhone, open:

```text
http://<windows-ip>:8787/healthz
```

For example:

```text
http://192.168.1.8:8787/healthz
```

If Safari displays a small JSON response containing `"status":"ok"`, the Wi-Fi route and Windows firewall are ready.

### 7. Start the Expo development client

This step exists only for the prototype. A customer release will ship as an installed iPhone app and will not require Node.js, pnpm, Metro, or port `8081`.

In a second PowerShell window:

```powershell
cd D:\Shove-it\apps\mobile
pnpm.cmd install
pnpm.cmd start --lan
```

Metro prints an address such as `exp://192.168.1.8:8081`. Install Expo Go on the iPhone, then scan Metro's QR code with the iPhone Camera app or open that address in Expo Go. Keep the PowerShell window running during the test.

The Expo toolchain uses temporary space on the Windows system drive even when the repository is on another drive. Keep at least 5 GB free on `C:` before installing packages or starting Metro; 10 GB is a healthier margin for Windows generally.

### 8. Create a pairing code

From the repository root, double-click `pair.cmd`. Shove opens a small Windows window containing a fresh single-use code, a copy button, and a live countdown.

For a terminal-only display:

```powershell
cd D:\Shove-it
.\pair.cmd -Cli
```

Each launch creates a new six-digit code. The code expires after two minutes and can be used only once. Code creation is accepted only from the Windows computer itself; another device on the network cannot mint its own pairing code.

### 9. Pair and transfer

In the Shove development app:

1. Enter `http://<windows-ip>:8787`.
2. Select **Find my server**.
3. Enter the six-digit pairing code.
4. Select **Pair iPhone**.
5. In **Destination**, choose the Windows library or external SSD.
6. Choose one photo or video.

The destination list refreshes about every two seconds while the app is open. If the configured SSD is unplugged, it is shown as disconnected and a selected SSD falls back to the local destination. Reconnecting the same configured drive/path makes it available again. The phone never sends an arbitrary Windows path.

The phone reports **Verified on Windows** only after the server has:

1. streamed the request into a `.part` file;
2. flushed it to storage;
3. calculated SHA-256;
4. atomically promoted it into the dated Shove Library folder;
5. returned the verification receipt.

The prototype does not delete the source item from the iPhone.

## Inspecting paired devices and upload history

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

The iPhone app also provides **Unpair this iPhone**. It calls the authenticated self-revocation endpoint and clears the token from iOS secure storage. Use that action when repeating the pairing journey from scratch; it does not remove transferred originals.

## Removing the prototype firewall rules

Run PowerShell as Administrator:

```powershell
Remove-NetFirewallRule -Name "ShoveIt-Java-8787"
Remove-NetFirewallRule -Name "ShoveIt-Expo-8081"
```

Removing the rules prevents the iPhone from initiating new connections to those development services. It does not delete any transferred photos, paired-device records, or application files.

## Troubleshooting

### The laptop works locally, but Safari on the iPhone cannot connect

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

This means the Windows system drive is full, not that the iPhone lacks memory. Free space on `C:` and retry. Package download caches under `C:\Users\<you>\AppData\Local\npm-cache` and the pnpm store are disposable and can be downloaded again; do not remove the repository's `node_modules` directory while Metro is expected to run.

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

## Requirements for a customer release

The customer experience should not require PowerShell, Java, Node.js, Expo, or manual firewall commands. The Windows installer/application should:

- install a packaged Shove server runtime;
- ask the customer before creating a narrowly scoped firewall rule;
- explain that the rule permits local iPhone connections;
- omit the Expo/port `8081` rule entirely;
- detect and display reachable private-network addresses;
- provide local discovery instead of manual IP entry;
- allow the customer to remove network access and unpair devices in the UI;
- preserve transferred files when the application is uninstalled unless the customer explicitly chooses otherwise.
