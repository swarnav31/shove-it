package dev.shove.server.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.shove.server.config.ShoveProperties;

class StorageDestinationRegistryTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void reflectsExternalDestinationAvailabilityWithoutRestart() throws IOException {
        Path local = temporaryDirectory.resolve("local");
        Path external = temporaryDirectory.resolve("external");
        StorageDestinationRegistry registry = new StorageDestinationRegistry(
                new ShoveProperties("Test", local, external.toString(), "Test SSD"));

        assertThat(registry.statuses())
                .filteredOn(status -> status.id().equals(StorageDestinationRegistry.EXTERNAL_DESTINATION_ID))
                .singleElement()
                .satisfies(status -> assertThat(status.available()).isFalse());

        Files.createDirectory(external);

        assertThat(registry.statuses())
                .filteredOn(status -> status.id().equals(StorageDestinationRegistry.EXTERNAL_DESTINATION_ID))
                .singleElement()
                .satisfies(status -> assertThat(status.available()).isTrue());

        Files.delete(external);

        assertThat(registry.statuses())
                .filteredOn(status -> status.id().equals(StorageDestinationRegistry.EXTERNAL_DESTINATION_ID))
                .singleElement()
                .satisfies(status -> assertThat(status.available()).isFalse());
    }
}
