package dev.shove.server.upload;

import java.time.Instant;
import java.util.List;

public record UploadPerformanceSnapshot(
        Instant generatedAt,
        int sampleCount,
        int verifiedCount,
        int failedCount,
        long totalBytes,
        double successRate,
        Long p50TotalMs,
        Long p95TotalMs,
        List<DestinationPerformance> destinations,
        List<PhasePerformance> phases,
        List<RecentPerformance> recent) {

    public record DestinationPerformance(
            String destinationId,
            int sampleCount,
            int verifiedCount,
            int failedCount,
            long totalBytes,
            double successRate,
            Long p50TotalMs,
            Long p95TotalMs,
            Long receiveBytesPerSecond,
            Long driveSaveBytesPerSecond) {
    }

    public record PhasePerformance(
            String phase,
            int sampleCount,
            Long p50Ms,
            Long p95Ms) {
    }

    public record RecentPerformance(
            String uploadId,
            String destinationId,
            String originalFilename,
            long bytes,
            String state,
            Instant updatedAt,
            UploadPhaseTimings timings) {
    }
}
