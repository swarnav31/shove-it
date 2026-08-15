package dev.shove.server.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

public final class StorageLayout {

    private final Path root;
    private final Path incoming;
    private final Path library;

    public StorageLayout(Path configuredRoot) {
        root = configuredRoot.toAbsolutePath().normalize();
        incoming = root.resolve(".shove").resolve("incoming");
        library = root.resolve("Shove Library");
        createDirectories();
    }

    private void createDirectories() {
        try {
            Files.createDirectories(incoming);
            Files.createDirectories(library);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to initialize Shove storage at " + root, exception);
        }
    }

    public Path root() {
        return root;
    }

    public Path incoming() {
        return incoming;
    }

    public Path library() {
        return library;
    }

    public Path incomingPart(String uploadId) {
        return incoming.resolve(uploadId + ".part");
    }

    public Path libraryDirectory(LocalDate date) throws IOException {
        Path directory = library
                .resolve(Integer.toString(date.getYear()))
                .resolve("%02d".formatted(date.getMonthValue()));
        return Files.createDirectories(directory);
    }

    public boolean isWritable() {
        return Files.isDirectory(root) && Files.isWritable(root);
    }
}
