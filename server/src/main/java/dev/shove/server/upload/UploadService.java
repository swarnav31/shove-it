package dev.shove.server.upload;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import dev.shove.server.storage.StorageDestinationRegistry;
import dev.shove.server.storage.StorageLayout;

@Service
public final class UploadService {

    private static final int BUFFER_SIZE = 1024 * 1024;
    private static final int MAX_FILENAME_LENGTH = 180;
    private static final Logger LOGGER = LoggerFactory.getLogger(UploadService.class);

    private final StorageDestinationRegistry destinations;
    private final UploadStore uploadStore;
    private final Clock clock;

    @Autowired
    public UploadService(StorageDestinationRegistry destinations, UploadStore uploadStore) {
        this(destinations, uploadStore, Clock.systemUTC());
    }

    UploadService(StorageDestinationRegistry destinations, UploadStore uploadStore, Clock clock) {
        this.destinations = destinations;
        this.uploadStore = uploadStore;
        this.clock = clock;
    }

    public UploadReceipt receive(
            String deviceId,
            String destinationId,
            String suppliedFilename,
            long expectedLength,
            InputStream input) throws IOException {
        var destinationConfig = destinations.resolveAvailable(destinationId);
        StorageLayout storage = destinationConfig.layout();
        String uploadId = UUID.randomUUID().toString();
        String safeFilename = safeFilename(suppliedFilename);
        boolean stagesLocally = !StorageDestinationRegistry.DEFAULT_DESTINATION_ID.equals(destinationConfig.id());
        Path destinationPart = storage.incomingPart(uploadId);
        Path receivePart = stagesLocally
                ? destinations.resolveAvailable(StorageDestinationRegistry.DEFAULT_DESTINATION_ID)
                        .layout()
                        .incomingPart(uploadId + ".external-staging")
                : destinationPart;
        uploadStore.start(
                uploadId,
                deviceId,
                destinationConfig.id(),
                destinationConfig.root().toString(),
                safeFilename,
                expectedLength,
                clock.instant());

        DigestWriteResult result;
        try {
            result = writeAndHash(input, receivePart);
            if (expectedLength >= 0 && result.bytes() != expectedLength) {
                throw new IOException(
                        "Upload length mismatch: received %d bytes, expected %d"
                                .formatted(result.bytes(), expectedLength));
            }

            if (stagesLocally) {
                copyAndForce(receivePart, destinationPart);
            }

            LocalDate today = LocalDate.now(clock);
            Path destinationDirectory = storage.libraryDirectory(today);
            Path destination = destinationDirectory.resolve(uploadId + "_" + safeFilename);
            promoteAtomically(destinationPart, destination);

            String storedRelativePath = storage.root()
                    .relativize(destination)
                    .toString()
                    .replace('\\', '/');
            uploadStore.markVerified(uploadId, storedRelativePath, result.bytes(), result.sha256(), clock.instant());
            if (stagesLocally) {
                deleteStagingBestEffort(receivePart, uploadId);
            }
            return uploadStore.findById(uploadId).orElseThrow();
        } catch (IOException | RuntimeException exception) {
            deleteAfterFailure(destinationPart, exception);
            if (!receivePart.equals(destinationPart)) {
                deleteAfterFailure(receivePart, exception);
            }
            try {
                uploadStore.markFailed(uploadId, exception.getMessage(), clock.instant());
            } catch (RuntimeException persistenceException) {
                exception.addSuppressed(persistenceException);
            }
            throw exception;
        }
    }

    public Optional<UploadReceipt> findReceipt(String uploadId) {
        return uploadStore.findById(uploadId);
    }

    public List<UploadReceipt> listReceipts(String deviceId) {
        return uploadStore.listForDevice(deviceId);
    }

    private DigestWriteResult writeAndHash(InputStream input, Path part) throws IOException {
        MessageDigest digest = sha256();
        long bytesWritten = 0;
        byte[] bytes = new byte[BUFFER_SIZE];

        try (FileChannel channel = FileChannel.open(
                part,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE)) {
            int read;
            while ((read = input.read(bytes)) != -1) {
                digest.update(bytes, 0, read);
                ByteBuffer buffer = ByteBuffer.wrap(bytes, 0, read);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                bytesWritten += read;
            }
            channel.force(true);
        }

        return new DigestWriteResult(bytesWritten, HexFormat.of().formatHex(digest.digest()));
    }

    private void copyAndForce(Path source, Path destination) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
        try (FileChannel sourceChannel = FileChannel.open(source, StandardOpenOption.READ);
             FileChannel destinationChannel = FileChannel.open(
                     destination,
                     StandardOpenOption.CREATE_NEW,
                     StandardOpenOption.WRITE)) {
            while (sourceChannel.read(buffer) != -1) {
                buffer.flip();
                while (buffer.hasRemaining()) {
                    destinationChannel.write(buffer);
                }
                buffer.clear();
            }
            destinationChannel.force(true);
        }
    }

    private void deleteAfterFailure(Path path, Exception originalException) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException cleanupException) {
            originalException.addSuppressed(cleanupException);
        }
    }

    private void deleteStagingBestEffort(Path staging, String uploadId) {
        try {
            Files.deleteIfExists(staging);
        } catch (IOException exception) {
            LOGGER.warn("Verified upload {} left local staging file {}", uploadId, staging, exception);
        }
    }

    private static void promoteAtomically(Path part, Path destination) throws IOException {
        try {
            Files.move(part, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException("Storage destination does not support atomic promotion", exception);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM does not provide SHA-256", exception);
        }
    }

    static String safeFilename(String suppliedFilename) {
        String candidate = suppliedFilename == null ? "upload.bin" : suppliedFilename.trim();
        candidate = candidate.replace('\\', '/');
        candidate = candidate.substring(candidate.lastIndexOf('/') + 1);
        candidate = candidate.replaceAll("[<>:\"/\\\\|?*\\p{Cntrl}]", "_");
        candidate = candidate.replaceAll("^[. ]+|[. ]+$", "");
        if (candidate.isBlank()) {
            candidate = "upload.bin";
        }
        if (candidate.length() > MAX_FILENAME_LENGTH) {
            candidate = candidate.substring(candidate.length() - MAX_FILENAME_LENGTH);
        }
        return candidate;
    }

    private record DigestWriteResult(long bytes, String sha256) {
    }
}
