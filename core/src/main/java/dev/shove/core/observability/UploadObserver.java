package dev.shove.core.observability;

/**
 * Observes upload lifecycle events without coupling the transfer engine to a
 * telemetry framework.
 *
 * <p>Implementations must not change transfer correctness. In particular, an
 * observer should avoid throwing from lifecycle methods.</p>
 */
public interface UploadObserver {

    UploadObservation start(String destinationId, long expectedBytes);

    interface UploadObservation extends AutoCloseable {

        PhaseObservation phase(String name);

        void completed(long bytes, long totalMs);

        void failed(Throwable failure, String failedPhase, long totalMs);

        @Override
        void close();
    }

    @FunctionalInterface
    interface PhaseObservation extends AutoCloseable {

        @Override
        void close();
    }
}
