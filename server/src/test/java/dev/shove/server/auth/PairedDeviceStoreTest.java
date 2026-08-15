package dev.shove.server.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class PairedDeviceStoreTest {

    @Test
    void persistsAndReturnsTheLatestValidatedDeviceStatus() {
        var dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        try {
            var store = new PairedDeviceStore(new JdbcTemplate(dataSource));
            Instant pairedAt = Instant.parse("2026-08-15T11:00:00Z");
            Instant reportedAt = Instant.parse("2026-08-15T12:00:00Z");
            store.add("device-1", "Test phone", "token-hash", pairedAt);

            assertThat(store.updateStatus("device-1", "Pixel 2", "android", 40, 100, reportedAt)).isTrue();

            var device = store.list().getFirst();
            assertThat(device.displayName()).isEqualTo("Pixel 2");
            assertThat(device.platform()).isEqualTo("android");
            assertThat(device.storageAvailableBytes()).isEqualTo(40);
            assertThat(device.storageTotalBytes()).isEqualTo(100);
            assertThat(device.storageReportedAt()).isEqualTo(reportedAt);
            assertThat(device.lastSeenAt()).isEqualTo(reportedAt);
        } finally {
            dataSource.destroy();
        }
    }
}
