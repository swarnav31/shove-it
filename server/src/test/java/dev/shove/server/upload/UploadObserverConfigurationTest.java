package dev.shove.server.upload;

import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import dev.shove.core.observability.UploadObservers;

class UploadObserverConfigurationTest {

    @Test
    void communityConfigurationUsesTheSharedNoOpObserver() {
        var configuration = new UploadObserverConfiguration();

        assertSame(UploadObservers.noOp(), configuration.uploadObserver());
    }
}
