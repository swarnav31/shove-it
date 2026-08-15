package dev.shove.core.observability;

import java.util.Objects;
import java.util.function.Consumer;

/** Factory methods for built-in upload observers. */
public final class UploadObservers {

    private static final UploadObserver NO_OP = (destinationId, expectedBytes) -> NoOpObservation.INSTANCE;

    private UploadObservers() {
    }

    public static UploadObserver noOp() {
        return NO_OP;
    }

    /**
     * Isolates the transfer path from runtime failures in an installed observer.
     */
    public static UploadObserver failSafe(
            UploadObserver delegate,
            Consumer<RuntimeException> failureHandler) {
        Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(failureHandler, "failureHandler");
        return (destinationId, expectedBytes) -> {
            try {
                return new FailSafeObservation(
                        Objects.requireNonNull(delegate.start(destinationId, expectedBytes), "observer result"),
                        failureHandler);
            } catch (RuntimeException failure) {
                report(failureHandler, failure);
                return NoOpObservation.INSTANCE;
            }
        };
    }

    private static void report(Consumer<RuntimeException> failureHandler, RuntimeException failure) {
        try {
            failureHandler.accept(failure);
        } catch (RuntimeException ignored) {
            // An error reporter is observational too; it cannot break a transfer.
        }
    }

    private static final class FailSafeObservation implements UploadObserver.UploadObservation {

        private final UploadObserver.UploadObservation delegate;
        private final Consumer<RuntimeException> failureHandler;

        private FailSafeObservation(
                UploadObserver.UploadObservation delegate,
                Consumer<RuntimeException> failureHandler) {
            this.delegate = delegate;
            this.failureHandler = failureHandler;
        }

        @Override
        public UploadObserver.PhaseObservation phase(String name) {
            try {
                var phase = Objects.requireNonNull(delegate.phase(name), "phase observer result");
                return () -> {
                    try {
                        phase.close();
                    } catch (RuntimeException failure) {
                        report(failureHandler, failure);
                    }
                };
            } catch (RuntimeException failure) {
                report(failureHandler, failure);
                return () -> { };
            }
        }

        @Override
        public void completed(long bytes, long totalMs) {
            try {
                delegate.completed(bytes, totalMs);
            } catch (RuntimeException failure) {
                report(failureHandler, failure);
            }
        }

        @Override
        public void failed(Throwable failure, String failedPhase, long totalMs) {
            try {
                delegate.failed(failure, failedPhase, totalMs);
            } catch (RuntimeException observerFailure) {
                report(failureHandler, observerFailure);
            }
        }

        @Override
        public void close() {
            try {
                delegate.close();
            } catch (RuntimeException failure) {
                report(failureHandler, failure);
            }
        }
    }

    private enum NoOpObservation implements UploadObserver.UploadObservation {
        INSTANCE;

        @Override
        public UploadObserver.PhaseObservation phase(String name) {
            return () -> { };
        }

        @Override
        public void completed(long bytes, long totalMs) {
        }

        @Override
        public void failed(Throwable failure, String failedPhase, long totalMs) {
        }

        @Override
        public void close() {
        }
    }
}
