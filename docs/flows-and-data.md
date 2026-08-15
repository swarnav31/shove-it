# Flows and data

**Status:** The flows and tables below are implemented and were exercised on the prototype unless marked otherwise.

## Pairing

```mermaid
sequenceDiagram
    actor Owner as Windows owner
    participant Helper as pair.cmd / loopback client
    participant Server as Java server
    participant Phone as iPhone app
    participant Secure as iOS SecureStore
    participant DB as SQLite

    Owner->>Helper: Create pairing code
    Helper->>Server: POST /api/v1/pairing/sessions (loopback)
    Server-->>Helper: Six-digit code + expiresAt
    Note over Server: In memory, single use, two-minute TTL
    Owner->>Phone: Enter code
    Phone->>Server: POST /api/v1/pair
    Server->>DB: Insert device ID, name, token hash, pairedAt
    Server-->>Phone: Device ID + raw random token
    Phone->>Secure: Persist raw token
```

Controls:

- Pairing sessions can be minted only through loopback.
- Codes are removed when claimed and rejected after expiration.
- Device tokens contain 32 random bytes and are returned only once.
- SQLite stores `SHA-256(token)`, not the raw token.
- Upload authentication hashes the presented token and accepts only a matching, non-revoked device.

## Verified upload

```mermaid
sequenceDiagram
    actor Owner
    participant Phone as React Native app
    participant Engine as ExpoGoTransferEngine
    participant Destinations as Java destination registry
    participant API as Java UploadController
    participant Audit as SQLite uploads
    participant Incoming as .shove/incoming
    participant Library as Shove Library

    Phone->>Destinations: GET /api/v1/destinations + bearer token
    Destinations-->>Phone: Approved IDs, paths, availability, free space
    Note over Phone,Destinations: Refresh every 2 seconds while the UI is active
    Owner->>Phone: Choose destination and select original
    Phone->>Engine: enqueue(file URI, destination ID, expected bytes)
    Engine->>API: POST /api/v1/upload + bearer token + X-Shove-Destination
    API->>API: Authenticate and resolve device ID
    API->>Destinations: Resolve ID and require writable root
    API->>Audit: Insert state=receiving, device ID, destination ID, root, start time
    API->>Incoming: Stream bytes to <upload-id>.part
    API->>API: SHA-256 while streaming
    API->>Incoming: force(true) and close
    API->>API: Compare received and HTTP content length
    API->>Library: Atomic move to dated final path
    API->>Audit: state=verified, bytes, hash, path, verifiedAt
    API-->>Engine: 201 + persistent receipt
    Engine-->>Phone: completed
    Phone-->>Owner: Verified on Windows
```

The success label is shown only after the server has flushed, hashed, promoted, and persisted the verified audit record.

On a handled write, length, promotion, or persistence failure, Java attempts to delete the `.part` file and marks the audit row `failed` with an update time and bounded failure message.

One crash-consistency gap remains explicit: if the atomic move succeeds and the following SQLite verified-state update fails, the final file may exist while the audit row becomes failed. Likewise, an abrupt process termination can leave an audit row in `receiving`. Reconciliation and orphan recovery are deferred until interruption testing defines the required behavior.

## Unpair and re-pair

```mermaid
sequenceDiagram
    participant Phone
    participant Server
    participant DB
    participant Secure as iOS SecureStore

    Phone->>Server: DELETE /api/v1/device + current token
    Server->>DB: Update last_seen_at
    Server->>DB: Set revoked_at on device A
    Server-->>Phone: 204 No Content
    Phone->>Secure: Delete raw token

    Note over Phone,Server: Later, run normal pairing again
    Phone->>Server: Claim fresh code
    Server->>DB: Insert new active device B
    Server-->>Phone: New token
```

Revocation is non-destructive. Device A remains in the database so existing upload rows retain their correct historical owner. Re-pairing creates device B even when its display name is the same.

## Data model

```mermaid
erDiagram
    PAIRED_DEVICES ||--o{ UPLOADS : "device_id"

    PAIRED_DEVICES {
        text id PK
        text display_name
        text token_hash UK
        text paired_at
        text last_seen_at "nullable"
        text revoked_at "nullable"
    }

    UPLOADS {
        text upload_id PK
        text device_id
        text destination_id
        text storage_root
        text original_filename
        text stored_relative_path "nullable until verified"
        integer expected_bytes "nullable"
        integer bytes_received
        text sha256 "nullable until verified"
        text state
        text started_at
        text updated_at
        text verified_at "nullable"
        text failure_message "nullable"
    }
```

SQLite timestamps are stored as ISO-8601 UTC strings. Media files remain ordinary filesystem files; SQLite stores only identity, ownership, destination, lifecycle, integrity, and path metadata. Older upload rows are migrated with destination ID `legacy` rather than being falsely attributed to a new configured root.

The current schema does not declare a database foreign-key constraint from `uploads.device_id` because the development bypass records a synthetic development device ID. Application code still records and enforces real paired-device ownership on the authenticated route.

## Ownership rules

- `POST /api/v1/upload` attributes the new record to the authenticated device ID.
- The upload header contains an opaque destination ID; Java resolves it through its allow-list and rejects unknown or disconnected targets.
- `GET /api/v1/uploads` filters by the authenticated device ID.
- `GET /api/v1/uploads/{id}` returns a receipt only when its device ID matches the caller.
- Loopback administrators may view all device and upload rows.
- Revocation blocks future authentication but preserves previous audit history and media.

## Integrity meaning

`verified=true` currently means:

1. Java consumed the request body.
2. Received bytes matched the declared HTTP content length when known.
3. Java calculated SHA-256 over exactly the bytes written.
4. The file channel was forced to storage and closed.
5. The storage provider completed an atomic move into the final library path.
6. SQLite persisted the verified state, path, size, hash, and timestamps.

It does not yet mean that the phone independently calculated and compared a source digest. Client-side digest comparison is a later hardening option.
