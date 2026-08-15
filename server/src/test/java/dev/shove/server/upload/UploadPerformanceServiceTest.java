package dev.shove.server.upload;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

class UploadPerformanceServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-15T12:00:00Z");

    @Test
    void aggregatesDestinationsPhasesAndNearestRankPercentiles() {
        UploadPerformanceSnapshot snapshot = UploadPerformanceService.snapshot(List.of(
                receipt("1", "local", "verified", 1_000, 100L, 0L, 5L, 5L, 110L),
                receipt("2", "external", "verified", 2_000, 200L, 400L, 5L, 5L, 610L),
                receipt("3", "external", "failed", 0, 300L, null, null, null, 300L),
                uninstrumentedReceipt()), NOW);

        assertThat(snapshot.sampleCount()).isEqualTo(3);
        assertThat(snapshot.verifiedCount()).isEqualTo(2);
        assertThat(snapshot.failedCount()).isEqualTo(1);
        assertThat(snapshot.totalBytes()).isEqualTo(3_000);
        assertThat(snapshot.successRate()).isEqualTo(2.0 / 3.0);
        assertThat(snapshot.p50TotalMs()).isEqualTo(110);
        assertThat(snapshot.p95TotalMs()).isEqualTo(610);
        assertThat(snapshot.destinations()).extracting(destination -> destination.destinationId())
                .containsExactly("external", "local");
        assertThat(snapshot.destinations().getFirst().p50TotalMs()).isEqualTo(610);
        assertThat(snapshot.destinations().getFirst().successRate()).isEqualTo(0.5);
        assertThat(snapshot.destinations().getFirst().driveSaveBytesPerSecond()).isEqualTo(5_000);
        assertThat(snapshot.phases().get(1).phase()).isEqualTo("external_copy_force");
        assertThat(snapshot.phases().get(1).sampleCount()).isEqualTo(1);
        assertThat(snapshot.recent()).hasSize(3);
    }

    private static UploadReceipt receipt(
            String id,
            String destination,
            String state,
            long bytes,
            Long receiveMs,
            Long copyMs,
            Long promoteMs,
            Long auditMs,
            Long totalMs) {
        return new UploadReceipt(
                id, "device", destination, "root", id + ".jpg", "library/" + id,
                bytes, bytes, "sha", state, "verified".equals(state), NOW, NOW,
                "verified".equals(state) ? NOW : null,
                new UploadPhaseTimings(receiveMs, copyMs, promoteMs, auditMs, totalMs,
                        "failed".equals(state) ? "receive_hash_force" : null),
                "failed".equals(state) ? "failed" : null);
    }

    private static UploadReceipt uninstrumentedReceipt() {
        return new UploadReceipt(
                "old", "device", "local", "root", "old.jpg", "library/old",
                100L, 100, "sha", "verified", true, NOW, NOW, NOW,
                new UploadPhaseTimings(null, null, null, null, null, null), null);
    }
}
