# ADR 0015: Stage external uploads locally before durable SSD promotion

- **Status:** Accepted
- **Date:** 2026-08-15

## Context

The first direct iPhone-to-Samsung-T7 tests were correct but unexpectedly slow. The same 2,308,501-byte JPEG reached the internal Windows destination in 0.456 seconds but consistently took about 30 seconds when the Java request thread wrote directly to the T7's exFAT filesystem.

Independent size and SHA-256 checks passed. Six live Java thread samples showed the request blocked in Windows' native `FileDispatcherImpl.write0` call. A controlled Java write of the same file from the internal disk to the same T7, including durable flush and atomic rename, completed in about two seconds. This isolated the poor behavior to direct network-stream-to-external-drive writes on this hardware path.

## Decision

For a non-local destination, Java will:

1. stream the HTTP request into a local `.part` file while computing SHA-256;
2. reject a content-length mismatch before touching the final library;
3. copy the completed local part to the external destination's incoming area in large sequential blocks;
4. durably flush the external part;
5. atomically rename it into the external library;
6. persist the verified receipt; and
7. remove the local staging part.

Uploads targeting the local destination retain the direct `.part`-then-promote path. The phone still receives success only after the selected final destination has been flushed, promoted, and recorded.

## Rationale

This separates unpredictable removable-drive latency from the network receive loop while preserving bounded memory use and the existing verification boundary. It uses the fast path demonstrated on the real hardware rather than weakening the durable flush or reporting success before the T7 contains the final file.

## Consequences

- An external upload temporarily needs local free space approximately equal to the source size.
- External uploads perform an additional local write and read.
- A normal failure cleans both local and external partial files.
- A process or Windows crash can still leave a partial file; startup recovery and resumable sessions remain future reliability work.
- Before accepting multi-gigabyte uploads, the server should add a local free-space preflight and a reserved-space policy.
- The audit continues to identify only the user-selected final destination, not the internal staging location.

## Alternatives considered

- Remove `FileChannel.force(true)`. Rejected because the controlled durable flush took less than half a second and was not the 30-second stall; weakening verification would not address the measured cause.
- Blame Wi-Fi or Expo. Rejected because the same phone, file, server, and network completed the local destination upload at 40.5 Mbps.
- Ask customers to reformat external drives as NTFS. Rejected as a product requirement because exFAT is common on portable SSDs and reformatting is destructive.
- Buffer the whole upload in memory. Rejected because the target workload includes multi-gigabyte videos.

## Verification note

On the real iPhone 13 mini and Samsung T7 Shield, the same 2,308,501-byte JPEG completed in 1.74 seconds after this change, compared with 29.65-30.72 seconds before it. The final T7 size and SHA-256 matched the receipt, and neither the local nor external incoming area retained a `.part` file.
