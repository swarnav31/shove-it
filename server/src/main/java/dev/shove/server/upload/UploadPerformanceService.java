package dev.shove.server.upload;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.shove.server.upload.UploadPerformanceSnapshot.DestinationPerformance;
import dev.shove.server.upload.UploadPerformanceSnapshot.PhasePerformance;
import dev.shove.server.upload.UploadPerformanceSnapshot.RecentPerformance;

@Service
public final class UploadPerformanceService {

    private static final int RECENT_LIMIT = 8;

    private final UploadStore uploads;
    private final Clock clock;

    @Autowired
    public UploadPerformanceService(UploadStore uploads) {
        this(uploads, Clock.systemUTC());
    }

    UploadPerformanceService(UploadStore uploads, Clock clock) {
        this.uploads = uploads;
        this.clock = clock;
    }

    public UploadPerformanceSnapshot current() {
        return snapshot(uploads.listAll(), clock.instant());
    }

    static UploadPerformanceSnapshot snapshot(List<UploadReceipt> receipts, Instant generatedAt) {
        List<UploadReceipt> samples = receipts.stream()
                .filter(receipt -> receipt.timings() != null && receipt.timings().totalMs() != null)
                .toList();
        int verified = countState(samples, "verified");
        int failed = countState(samples, "failed");
        List<UploadReceipt> verifiedSamples = samples.stream().filter(UploadReceipt::verified).toList();

        Map<String, List<UploadReceipt>> byDestination = new LinkedHashMap<>();
        samples.stream()
                .sorted(Comparator.comparing(UploadReceipt::destinationId))
                .forEach(receipt -> byDestination
                        .computeIfAbsent(receipt.destinationId(), ignored -> new ArrayList<>())
                        .add(receipt));

        List<DestinationPerformance> destinations = byDestination.entrySet().stream()
                .map(entry -> destination(entry.getKey(), entry.getValue()))
                .toList();
        List<PhasePerformance> phases = List.of(
                phase("receive_hash_force", verifiedSamples, receipt -> receipt.timings().receiveHashMs(), false),
                phase("external_copy_force", verifiedSamples, receipt -> receipt.timings().externalCopyMs(), true),
                phase("atomic_promote", verifiedSamples, receipt -> receipt.timings().promoteMs(), false),
                phase("audit_commit", verifiedSamples, receipt -> receipt.timings().auditMs(), false));
        List<RecentPerformance> recent = samples.stream()
                .limit(RECENT_LIMIT)
                .map(receipt -> new RecentPerformance(
                        receipt.uploadId(),
                        receipt.destinationId(),
                        receipt.originalFilename(),
                        receipt.bytes(),
                        receipt.state(),
                        receipt.updatedAt(),
                        receipt.timings()))
                .toList();

        return new UploadPerformanceSnapshot(
                generatedAt,
                samples.size(),
                verified,
                failed,
                samples.stream().filter(UploadReceipt::verified).mapToLong(UploadReceipt::bytes).sum(),
                ratio(verified, samples.size()),
                percentile(verifiedSamples, receipt -> receipt.timings().totalMs(), 0.50, false),
                percentile(verifiedSamples, receipt -> receipt.timings().totalMs(), 0.95, false),
                destinations,
                phases,
                recent);
    }

    private static DestinationPerformance destination(String destinationId, List<UploadReceipt> samples) {
        int verified = countState(samples, "verified");
        int failed = countState(samples, "failed");
        List<UploadReceipt> verifiedSamples = samples.stream().filter(UploadReceipt::verified).toList();
        return new DestinationPerformance(
                destinationId,
                samples.size(),
                verified,
                failed,
                samples.stream().filter(UploadReceipt::verified).mapToLong(UploadReceipt::bytes).sum(),
                ratio(verified, samples.size()),
                percentile(verifiedSamples, receipt -> receipt.timings().totalMs(), 0.50, false),
                percentile(verifiedSamples, receipt -> receipt.timings().totalMs(), 0.95, false),
                throughput(verifiedSamples, receipt -> receipt.timings().receiveHashMs(), false),
                throughput(verifiedSamples, receipt -> receipt.timings().externalCopyMs(), true));
    }

    private static PhasePerformance phase(
            String name,
            List<UploadReceipt> samples,
            Function<UploadReceipt, Long> duration,
            boolean excludeZero) {
        List<Long> durations = durations(samples, duration, excludeZero);
        return new PhasePerformance(
                name,
                durations.size(),
                percentile(durations, 0.50),
                percentile(durations, 0.95));
    }

    private static Long throughput(
            List<UploadReceipt> samples,
            Function<UploadReceipt, Long> duration,
            boolean excludeZero) {
        long bytes = 0;
        long milliseconds = 0;
        for (UploadReceipt receipt : samples) {
            Long value = duration.apply(receipt);
            if (value == null || (excludeZero && value == 0) || !receipt.verified()) {
                continue;
            }
            bytes += receipt.bytes();
            milliseconds += value;
        }
        if (bytes <= 0 || milliseconds <= 0) {
            return null;
        }
        return Math.round(bytes / (milliseconds / 1000.0));
    }

    private static Long percentile(
            List<UploadReceipt> samples,
            Function<UploadReceipt, Long> duration,
            double percentile,
            boolean excludeZero) {
        return percentile(durations(samples, duration, excludeZero), percentile);
    }

    private static List<Long> durations(
            List<UploadReceipt> samples,
            Function<UploadReceipt, Long> duration,
            boolean excludeZero) {
        return samples.stream()
                .map(duration)
                .filter(value -> value != null && (!excludeZero || value > 0))
                .sorted()
                .toList();
    }

    private static Long percentile(List<Long> sortedValues, double percentile) {
        if (sortedValues.isEmpty()) {
            return null;
        }
        int index = Math.max(0, (int) Math.ceil(percentile * sortedValues.size()) - 1);
        return sortedValues.get(index);
    }

    private static int countState(List<UploadReceipt> samples, String state) {
        return (int) samples.stream().filter(receipt -> state.equals(receipt.state())).count();
    }

    private static double ratio(int numerator, int denominator) {
        return denominator == 0 ? 0 : numerator / (double) denominator;
    }
}
