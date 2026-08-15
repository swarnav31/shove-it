package dev.shove.server.upload;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import dev.shove.core.observability.UploadObserver;
import dev.shove.core.observability.UploadObservers;

class UploadObserverIsolationTest {

    @Test
    void observerFailuresCannotEscapeIntoTheTransferPath() {
        AtomicInteger reportedFailures = new AtomicInteger();
        UploadObserver brokenObserver = (destinationId, expectedBytes) -> new UploadObserver.UploadObservation() {
            @Override
            public UploadObserver.PhaseObservation phase(String name) {
                return () -> { throw new IllegalStateException("phase close failed"); };
            }

            @Override
            public void completed(long bytes, long totalMs) {
                throw new IllegalStateException("completion failed");
            }

            @Override
            public void failed(Throwable failure, String failedPhase, long totalMs) {
                throw new IllegalStateException("failure reporting failed");
            }

            @Override
            public void close() {
                throw new IllegalStateException("observer close failed");
            }
        };
        UploadObserver safeObserver = UploadObservers.failSafe(
                brokenObserver,
                ignored -> reportedFailures.incrementAndGet());

        assertDoesNotThrow(() -> {
            try (var upload = safeObserver.start("local", 42L)) {
                try (var ignored = upload.phase("receive_hash_force")) {
                }
                upload.completed(42L, 3L);
                upload.failed(new IllegalStateException("transfer"), "audit_commit", 4L);
            }
        });
        assertEquals(4, reportedFailures.get());
    }
}
