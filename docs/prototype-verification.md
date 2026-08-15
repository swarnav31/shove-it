# Prototype verification

**Last consolidated:** 2026-08-15

## Real-hardware environment

- Client: iPhone 13 mini
- Client runtime: Expo Go, Expo SDK 54
- Network: private home Wi-Fi, phone and laptop on `192.168.1.0/24`
- Server: Java 21, Spring Boot 4.1.0 on Windows
- Metadata: SQLite
- Media destination: Windows filesystem beneath the configured storage root
- External destination tested: Samsung T7 Shield, exFAT, mounted as `E:`

## Proven journeys

### Connectivity

- iPhone Safari reached `http://<laptop-ip>:8787/healthz`.
- The response reported `status=ok` and `storageWritable=true`.
- The physical Wi-Fi address was used; WSL and VPN adapter addresses were excluded.
- Narrow Java `8787` and Expo `8081` firewall rules allowed only the home subnet.

### Pairing and authentication

- A Windows-created six-digit code paired the iPhone.
- Successful short-code claim was exercised; two-minute expiry and single-use removal are implemented in the pairing service.
- The iPhone retained its token across a Java restart.
- An authenticated self-unpair updated last-seen and revocation timestamps.
- Re-pairing created a distinct active device ID.
- The revoked device remained available for historical attribution.

### Upload and audit

- Multiple HEIC and PNG originals were uploaded from the actual iPhone.
- The phone displayed byte progress and `Verified on Windows`.
- Audit rows referenced the correct pre- and post-re-pair device IDs.
- Audit rows survived Java restarts.
- Every audited file existed at its recorded path.
- Every audited byte count matched the Windows file length.
- Every audited SHA-256 matched an independent Windows `Get-FileHash` result.
- No `.part` files remained after the successful transfers.

### External SSD destination

- Java exposed the laptop root and `E:\Shove` as separate server-approved destination IDs.
- The iPhone displayed both choices and completed verified uploads to each one.
- Health reported the external root writable and existing audit history remained available.
- A real iPhone JPEG was written to `E:\Shove\Shove Library\2026\08`.
- The 1,426,135-byte Windows file matched the SQLite byte count.
- Independent Windows SHA-256 matched the persisted audit hash.
- exFAT completed the required atomic move for this transfer.
- The old default storage root did not receive a second copy.
- The SSD incoming directory contained zero `.part` files afterward.
- With no transfer active, physically disconnecting the T7 was reflected in the iPhone UI within the two-second refresh window.
- The disconnected T7 became unavailable and the selection safely fell back to the Windows destination without restarting Java or Expo.

### External-drive performance investigation

A fresh-pairing retest exposed a repeatable performance problem with direct request streaming to the T7's exFAT filesystem. The same 2,308,501-byte JPEG took 0.456 seconds to the local destination but 29.65-30.72 seconds across repeated external uploads. All copies remained byte- and SHA-256-correct.

Six live Java thread samples caught the request blocked in Windows' native file-write call. A controlled Java sequence that copied the same local file to the T7, durably flushed it, and atomically renamed it completed in about two seconds. External uploads were therefore changed to receive into a bounded-memory local staging part before a sequential copy, durable external flush, and atomic promotion.

The same iPhone and JPEG completed the post-fix T7 upload in 1.74 seconds (10.61 Mbps). The 2,308,501-byte final file matched the receipt and SHA-256, and both the local staging and external incoming directories contained zero `.part` files afterward. Automated success and length-mismatch cleanup tests also pass.

One verified post-re-pair example:

```text
original filename: IMG_1180.png
bytes:             873295
state:             verified
sha256:            c1cddb4349f19b39c564432f0140e2debda26d15662ef0b41a5c6b1a5f5e0eb0
```

## Automated checks

Java tests cover:

- Spring application context and database schema initialization;
- configured storage layout and writability;
- streaming, hashing, and atomic promotion;
- exact SHA-256 and stored bytes;
- partial cleanup and persistent failed audit state on a length mismatch.
- local staging, external promotion, and cleanup of both incoming areas.

The mobile strict TypeScript check passes. The foreground engine and UI were additionally exercised through the real Expo Go client.

The Windows development launcher was exercised end to end: persistent setup, Java packaging, locked pnpm installation, managed Java and Metro startup, health/status and LAN URL discovery, real pairing-code creation, verified PID-scoped shutdown, and confirmation that ports `8787` and `8081` were closed afterward.

The complete first-run path was then repeated with launcher state and SQLite moved to a recoverable backup, the phone explicitly unpaired, the Shove-managed firewall rules absent, and two older broad Java inbound rules disabled. Interactive `setup` detected `192.168.1.0/24`, requested consent and UAC approval, and created only the Java `8787` and Expo `8081` TCP rules for that subnet. Elevated read-back matched the persistent `.shove-dev/logs/firewall.log` entries for rule name, executable, direction, action, profile, protocol, port, and remote scope.

Through those newly automated rules, the iPhone connected, created the only device in the fresh database, paired, and created the only upload row. The 1,823,392-byte HEIC reached the T7 in 2.051 seconds, matched the final file size and SHA-256, and left zero local or external `.part` files.

The shared Expo application also successfully exports a production Android Hermes bundle (596 modules, approximately 1.8 MB). This verifies build compatibility only. No Android device has yet exercised discovery, pairing, secure token storage, media selection, upload, or reconnect behavior, so the end-to-end pipeline remains verified on iPhone only.

## Known prototype limitations

Not yet proven:

- 2–5 GB video transfer;
- behavior when Wi-Fi disappears mid-request;
- Java termination or Windows restart mid-request;
- external SSD removal during a write or promotion (normal external-SSD completion is proven);
- retry, idempotency, duplicate suppression, or resumable offsets;
- a multi-item durable queue;
- native iOS background execution;
- physical Android end-to-end behavior;
- independent source-side hash comparison;
- TLS and authenticated server identity;
- packaged Windows/iOS installation.
- recovery of audit rows left in `receiving` after abrupt termination;
- the edge case where file promotion succeeds but the subsequent SQLite verified-state update fails.

The development profile also exposes `/api/v1/dev/uploads`, which intentionally bypasses pairing. It must not exist in a production deployment.

## Next validation order

This is a test order, not a feature commitment:

1. Medium video with the current whole-file protocol.
2. 2–5 GB video while observing memory, disk, time, and iOS behavior.
3. Interrupt Wi-Fi and confirm failed state/partial cleanup.
4. Terminate Java and confirm restart behavior.
5. Remove or fill the destination and observe failure semantics.
6. Use the observed failures to specify retry, idempotency, and resume behavior.
7. Replace the foreground adapter with native iOS background transfer behind `TransferEngine`.
