package dev.shove.server.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import dev.shove.server.config.ShoveProperties;

@Component
public final class StorageDestinationRegistry {

    public static final String DEFAULT_DESTINATION_ID = "local";
    public static final String EXTERNAL_DESTINATION_ID = "external";

    private final Map<String, ConfiguredDestination> destinations = new LinkedHashMap<>();

    public StorageDestinationRegistry(ShoveProperties properties) {
        Path localRoot = properties.storageRoot().toAbsolutePath().normalize();
        new StorageLayout(localRoot);
        destinations.put(
                DEFAULT_DESTINATION_ID,
                new ConfiguredDestination(DEFAULT_DESTINATION_ID, "This Windows PC", localRoot, true));

        if (properties.externalStorageRoot() != null && !properties.externalStorageRoot().isBlank()) {
            Path externalRoot = Path.of(properties.externalStorageRoot()).toAbsolutePath().normalize();
            if (!externalRoot.equals(localRoot)) {
                String name = properties.externalStorageName() == null || properties.externalStorageName().isBlank()
                        ? "External SSD"
                        : properties.externalStorageName().trim();
                destinations.put(
                        EXTERNAL_DESTINATION_ID,
                        new ConfiguredDestination(EXTERNAL_DESTINATION_ID, name, externalRoot, false));
            }
        }
    }

    public List<StorageDestinationStatus> statuses() {
        List<StorageDestinationStatus> result = new ArrayList<>();
        for (ConfiguredDestination destination : destinations.values()) {
            result.add(status(destination));
        }
        return List.copyOf(result);
    }

    public boolean anyAvailable() {
        return destinations.values().stream().anyMatch(this::isAvailable);
    }

    public ResolvedStorageDestination resolveAvailable(String requestedId) {
        String destinationId = requestedId == null || requestedId.isBlank()
                ? DEFAULT_DESTINATION_ID
                : requestedId.trim();
        ConfiguredDestination destination = destinations.get(destinationId);
        if (destination == null) {
            throw new UnknownStorageDestinationException(destinationId);
        }
        if (!isAvailable(destination)) {
            throw new StorageDestinationUnavailableException(destination.displayName());
        }
        return new ResolvedStorageDestination(
                destination.id(),
                destination.displayName(),
                destination.root(),
                new StorageLayout(destination.root()));
    }

    private StorageDestinationStatus status(ConfiguredDestination destination) {
        boolean available = isAvailable(destination);
        Long freeBytes = null;
        String fileSystem = null;
        if (available) {
            try {
                var fileStore = Files.getFileStore(destination.root());
                freeBytes = fileStore.getUsableSpace();
                fileSystem = fileStore.type();
            } catch (IOException ignored) {
                // This metadata is optional. Do not hide an otherwise writable target.
            }
        }
        return new StorageDestinationStatus(
                destination.id(),
                destination.displayName(),
                destination.root().toString(),
                available,
                destination.defaultDestination(),
                freeBytes,
                fileSystem);
    }

    private boolean isAvailable(ConfiguredDestination destination) {
        return Files.isDirectory(destination.root()) && Files.isWritable(destination.root());
    }

    private record ConfiguredDestination(
            String id,
            String displayName,
            Path root,
            boolean defaultDestination) {
    }

    public record StorageDestinationStatus(
            String id,
            String displayName,
            String path,
            boolean available,
            boolean defaultDestination,
            Long freeBytes,
            String fileSystem) {
    }

    public record ResolvedStorageDestination(
            String id,
            String displayName,
            Path root,
            StorageLayout layout) {
    }
}
