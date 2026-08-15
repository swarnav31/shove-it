# Customer alpha acceptance criteria

## Audience

The first external testers are friends with an iPhone and a Windows laptop. They are product users, not developers. Instructions must not assume knowledge of APIs, Java, Maven, Node.js, pnpm, Expo, Docker, Git, PowerShell, SQLite, ports, or environment variables.

## What a tester receives

1. A signed or clearly identified Shove Windows installer.
2. A link to install Expo Go from the iPhone App Store.
3. A short visual guide covering install, storage choice, QR scan, pairing, first upload, and where the file appears.

The source repository is not part of the customer journey.

## Windows experience

```text
Download installer
    -> Install Shove
    -> Launch Shove
    -> Choose Windows folder and optional external SSD
    -> Approve one explained firewall prompt
    -> See "Ready for iPhone" and pairing code/QR
```

The package includes private Java and Node/Metro runtimes, the compiled server, and the prepared mobile project. It checks and repairs only its own files. Existing developer tools and environment settings remain untouched; Maven and pnpm are not installed for customers. The local administration surface owns storage configuration, the Expo QR code, pairing, device revocation, upload history, logs, and safe shutdown.

## iPhone experience

```text
Install Expo Go
    -> Scan the Shove QR shown on Windows
    -> Load Shove in Expo Go
    -> Pair
    -> Choose destination
    -> Select photo/video
    -> See verified result
```

The alpha may initially require the app to remain foregrounded during a transfer, but it must say so clearly. Durable iOS background `URLSession` remains the reliability target; no customer release may pretend that a JavaScript background loop provides that guarantee.

## First external-test acceptance test

On a Windows laptop without Java, Maven, Node.js, pnpm, Git, Docker, or the source repository:

- install and launch Shove;
- configure an ordinary local folder and an external SSD;
- approve the narrow home-LAN firewall rule;
- install Expo Go from the App Store and scan the QR shown by the Windows application;
- pair without entering an IP address if discovery/QR is ready, or with one clearly presented address for the earliest alpha;
- transfer a real photo to the external SSD;
- verify the Windows file, byte count, digest, device attribution, and zero leftover partial files;
- restart Windows and reconnect without repeating setup;
- uninstall Shove and confirm its managed firewall rule is removed without deleting the photo library.

## Delivery work

1. **Implemented for the source preview:** the Windows bootstrap owns first-run storage and firewall consent; the embedded UI owns readiness, QR, pairing, live storage, devices, and upload history. Packaging must replace the `.cmd` entry with an ordinary installed shortcut.
2. Produce a portable `jpackage` application image with bundled Java and validate it on a clean Windows account or VM.
3. Bundle Node.js, Metro, the locked mobile project, and QR presentation into the Windows application.
4. Produce the Windows installer, shortcuts, lifecycle integration, firewall install/uninstall actions, and upgrade-safe data locations.
5. Run the acceptance test with the project owner before inviting friends.

## External prerequisites for publishing

- Expo Go is the only iPhone installation prerequisite for this friend alpha.
- A paid Apple Developer account will later be required to replace Expo Go with TestFlight/App Store distribution.
- Installer signing is desirable before wider distribution; an unsigned private alpha must explain the Windows warning honestly.
