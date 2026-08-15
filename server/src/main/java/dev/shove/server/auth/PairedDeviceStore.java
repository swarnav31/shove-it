package dev.shove.server.auth;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PairedDeviceStore {

    private final JdbcTemplate jdbc;

    public PairedDeviceStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS paired_devices (
                    id TEXT PRIMARY KEY,
                    display_name TEXT NOT NULL,
                    token_hash TEXT NOT NULL UNIQUE,
                    paired_at TEXT NOT NULL,
                    last_seen_at TEXT,
                    revoked_at TEXT
                )
                """);
        ensureColumn("last_seen_at", "TEXT");
        ensureColumn("revoked_at", "TEXT");
        ensureColumn("platform", "TEXT");
        ensureColumn("storage_available_bytes", "INTEGER");
        ensureColumn("storage_total_bytes", "INTEGER");
        ensureColumn("storage_reported_at", "TEXT");
    }

    public void add(String id, String displayName, String tokenHash, Instant pairedAt) {
        jdbc.update(
                "INSERT INTO paired_devices(id, display_name, token_hash, paired_at) VALUES (?, ?, ?, ?)",
                id, displayName, tokenHash, pairedAt.toString());
    }

    public Optional<String> findDeviceId(String tokenHash) {
        List<String> matches = jdbc.query(
                "SELECT id FROM paired_devices WHERE token_hash = ? AND revoked_at IS NULL",
                (resultSet, rowNumber) -> resultSet.getString("id"),
                tokenHash);
        return matches.stream().findFirst();
    }

    public void markSeen(String deviceId, Instant seenAt) {
        jdbc.update(
                """
                UPDATE paired_devices
                SET last_seen_at = ?
                WHERE id = ? AND revoked_at IS NULL
                  AND (last_seen_at IS NULL OR last_seen_at < ?)
                """,
                seenAt.toString(),
                deviceId,
                seenAt.minus(Duration.ofMinutes(1)).toString());
    }

    public boolean updateStatus(
            String deviceId,
            String displayName,
            String platform,
            long storageAvailableBytes,
            long storageTotalBytes,
            Instant reportedAt) {
        return jdbc.update(
                """
                UPDATE paired_devices
                SET display_name = COALESCE(?, display_name),
                    platform = ?,
                    storage_available_bytes = ?,
                    storage_total_bytes = ?,
                    storage_reported_at = ?,
                    last_seen_at = ?
                WHERE id = ? AND revoked_at IS NULL
                """,
                displayName,
                platform,
                storageAvailableBytes,
                storageTotalBytes,
                reportedAt.toString(),
                reportedAt.toString(),
                deviceId) == 1;
    }

    public List<PairedDeviceRecord> list() {
        return jdbc.query(
                """
                SELECT id, display_name, paired_at, last_seen_at, revoked_at,
                       platform, storage_available_bytes, storage_total_bytes, storage_reported_at
                FROM paired_devices
                ORDER BY paired_at DESC
                """,
                (resultSet, rowNumber) -> new PairedDeviceRecord(
                        resultSet.getString("id"),
                        resultSet.getString("display_name"),
                        Instant.parse(resultSet.getString("paired_at")),
                        nullableInstant(resultSet.getString("last_seen_at")),
                        nullableInstant(resultSet.getString("revoked_at")),
                        resultSet.getString("platform"),
                        nullableLong(resultSet.getObject("storage_available_bytes")),
                        nullableLong(resultSet.getObject("storage_total_bytes")),
                        nullableInstant(resultSet.getString("storage_reported_at"))));
    }

    public boolean revoke(String deviceId, Instant revokedAt) {
        return jdbc.update(
                "UPDATE paired_devices SET revoked_at = ? WHERE id = ? AND revoked_at IS NULL",
                revokedAt.toString(),
                deviceId) == 1;
    }

    private void ensureColumn(String columnName, String type) {
        boolean exists = jdbc.queryForList("PRAGMA table_info(paired_devices)")
                .stream()
                .anyMatch(row -> columnName.equals(row.get("name")));
        if (!exists) {
            jdbc.execute("ALTER TABLE paired_devices ADD COLUMN " + columnName + " " + type);
        }
    }

    private static Instant nullableInstant(String value) {
        return value == null ? null : Instant.parse(value);
    }

    private static Long nullableLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    public record PairedDeviceRecord(
            String deviceId,
            String displayName,
            Instant pairedAt,
            Instant lastSeenAt,
            Instant revokedAt,
            String platform,
            Long storageAvailableBytes,
            Long storageTotalBytes,
            Instant storageReportedAt) {
    }
}
