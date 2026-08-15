package dev.shove.server.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StorageLayoutTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void createsIncomingAndLibraryDirectories() {
        Path root = temporaryDirectory.resolve("phone-photos");

        StorageLayout layout = new StorageLayout(root);

        assertThat(layout.root()).isEqualTo(root.toAbsolutePath());
        assertThat(layout.incoming()).isDirectory();
        assertThat(layout.library()).isDirectory();
        assertThat(layout.isWritable()).isTrue();
    }
}
