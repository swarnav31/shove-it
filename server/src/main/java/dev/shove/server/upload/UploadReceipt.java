package dev.shove.server.upload;

import java.time.Instant;

public record UploadReceipt(
        String uploadId,
        String deviceId,
        String destinationId,
        String storageRoot,
        String originalFilename,
        String storedRelativePath,
        Long expectedBytes,
        long bytes,
        String sha256,
        String state,
        boolean verified,
        Instant startedAt,
        Instant updatedAt,
        Instant verifiedAt,
        String failureMessage
) {
}
