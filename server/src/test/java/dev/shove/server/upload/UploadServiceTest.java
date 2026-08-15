package dev.shove.server.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import dev.shove.server.config.ShoveProperties;
import dev.shove.server.storage.StorageDestinationRegistry;

class UploadServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-15T06:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path temporaryDirectory;

    @Test
    void streamsHashesAndPromotesAnUpload() throws IOException {
        byte[] source = "an original from the iphone".getBytes();
        ServiceFixture fixture = service();

        UploadReceipt receipt = fixture.service().receive(
                "test-device", "local", "../IMG_0001.HEIC", source.length, new ByteArrayInputStream(source));

        assertThat(receipt.verified()).isTrue();
        assertThat(receipt.deviceId()).isEqualTo("test-device");
        assertThat(receipt.destinationId()).isEqualTo("local");
        assertThat(receipt.storageRoot()).isEqualTo(temporaryDirectory.toAbsolutePath().toString());
        assertThat(receipt.state()).isEqualTo("verified");
        assertThat(receipt.originalFilename()).isEqualTo("IMG_0001.HEIC");
        assertThat(receipt.expectedBytes()).isEqualTo(source.length);
        assertThat(receipt.bytes()).isEqualTo(source.length);
        assertThat(receipt.sha256())
                .isEqualTo("cee8d254332dd9c6de6f63d7ac9946d9e016d157e3f29731eb5021a35d2aca0f");
        assertThat(receipt.storedRelativePath()).startsWith("Shove Library/2026/08/");
        assertThat(receipt.timings().receiveHashMs()).isNotNull().isGreaterThanOrEqualTo(0);
        assertThat(receipt.timings().externalCopyMs()).isZero();
        assertThat(receipt.timings().promoteMs()).isNotNull().isGreaterThanOrEqualTo(0);
        assertThat(receipt.timings().auditMs()).isNotNull().isGreaterThanOrEqualTo(0);
        assertThat(receipt.timings().totalMs()).isNotNull().isGreaterThanOrEqualTo(0);
        assertThat(receipt.timings().failurePhase()).isNull();
        assertThat(Files.readAllBytes(temporaryDirectory.resolve(receipt.storedRelativePath())))
                .isEqualTo(source);
        assertThat(temporaryDirectory.resolve(".shove/incoming")).isEmptyDirectory();
        assertThat(fixture.store().findById(receipt.uploadId())).contains(receipt);
        assertThat(fixture.store().listForDevice("another-device")).isEmpty();
    }

    @Test
    void deletesThePartialFileWhenLengthDoesNotMatch() {
        ServiceFixture fixture = service();

        assertThatThrownBy(() -> fixture.service().receive(
                "test-device", "local", "video.mov", 100, new ByteArrayInputStream(new byte[] {1, 2, 3})))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("length mismatch");

        assertThat(temporaryDirectory.resolve(".shove/incoming")).isEmptyDirectory();
        assertThat(fixture.store().listForDevice("test-device"))
                .singleElement()
                .satisfies(receipt -> {
                    assertThat(receipt.state()).isEqualTo("failed");
                    assertThat(receipt.verified()).isFalse();
                    assertThat(receipt.failureMessage()).contains("length mismatch");
                    assertThat(receipt.timings().failurePhase()).isEqualTo("length_validation");
                    assertThat(receipt.timings().totalMs()).isNotNull().isGreaterThanOrEqualTo(0);
                });
    }

    @Test
    void stagesExternalUploadsLocallyBeforeDurablePromotion() throws IOException {
        byte[] source = "an original headed to an external ssd".getBytes();
        Path local = temporaryDirectory.resolve("local");
        Path external = temporaryDirectory.resolve("external");
        Files.createDirectories(external);
        ServiceFixture fixture = service(local, external);

        UploadReceipt receipt = fixture.service().receive(
                "test-device", "external", "IMG_0002.HEIC", source.length, new ByteArrayInputStream(source));

        assertThat(receipt.verified()).isTrue();
        assertThat(receipt.destinationId()).isEqualTo("external");
        assertThat(receipt.storageRoot()).isEqualTo(external.toAbsolutePath().toString());
        assertThat(receipt.timings().externalCopyMs()).isNotNull().isGreaterThanOrEqualTo(0);
        assertThat(Files.readAllBytes(external.resolve(receipt.storedRelativePath())))
                .isEqualTo(source);
        assertThat(local.resolve(".shove/incoming")).isEmptyDirectory();
        assertThat(external.resolve(".shove/incoming")).isEmptyDirectory();
    }

    @Test
    void cleansLocalStagingWhenAnExternalUploadLengthDoesNotMatch() throws IOException {
        Path local = temporaryDirectory.resolve("local");
        Path external = temporaryDirectory.resolve("external");
        Files.createDirectories(external);
        ServiceFixture fixture = service(local, external);

        assertThatThrownBy(() -> fixture.service().receive(
                "test-device", "external", "video.mov", 100, new ByteArrayInputStream(new byte[] {1, 2, 3})))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("length mismatch");

        assertThat(local.resolve(".shove/incoming")).isEmptyDirectory();
        assertThat(external.resolve(".shove/incoming")).isEmptyDirectory();
        assertThat(fixture.store().listForDevice("test-device"))
                .singleElement()
                .satisfies(receipt -> assertThat(receipt.state()).isEqualTo("failed"));
    }

    private ServiceFixture service() {
        return service(temporaryDirectory, null);
    }

    private ServiceFixture service(Path local, Path external) {
        StorageDestinationRegistry destinations = new StorageDestinationRegistry(
                new ShoveProperties(
                        "Test",
                        local,
                        external == null ? "" : external.toString(),
                        "External SSD"));
        var dataSource = new DriverManagerDataSource("jdbc:sqlite:" + temporaryDirectory.resolve("audit.db"));
        UploadStore store = new UploadStore(new JdbcTemplate(dataSource));
        return new ServiceFixture(new UploadService(destinations, store, FIXED_CLOCK), store);
    }

    private record ServiceFixture(UploadService service, UploadStore store) {
    }
}
