package dev.shove.server.upload;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class UploadStore {

    private static final RowMapper<UploadReceipt> RECEIPT_MAPPER = UploadStore::mapReceipt;

    private final JdbcTemplate jdbc;

    public UploadStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS uploads (
                    upload_id TEXT PRIMARY KEY,
                    device_id TEXT NOT NULL,
                    destination_id TEXT NOT NULL DEFAULT 'legacy',
                    storage_root TEXT,
                    original_filename TEXT NOT NULL,
                    stored_relative_path TEXT,
                    expected_bytes INTEGER,
                    bytes_received INTEGER NOT NULL DEFAULT 0,
                    sha256 TEXT,
                    state TEXT NOT NULL,
                    started_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    verified_at TEXT,
                    failure_message TEXT
                )
                """);
        ensureColumn("destination_id", "TEXT NOT NULL DEFAULT 'legacy'");
        ensureColumn("storage_root", "TEXT");
        jdbc.execute("CREATE INDEX IF NOT EXISTS uploads_device_started_idx ON uploads(device_id, started_at DESC)");
    }

    public void start(
            String uploadId,
            String deviceId,
            String destinationId,
            String storageRoot,
            String filename,
            long expectedBytes,
            Instant now) {
        jdbc.update("""
                INSERT INTO uploads(
                    upload_id, device_id, destination_id, storage_root,
                    original_filename, expected_bytes, bytes_received,
                    state, started_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, 0, 'receiving', ?, ?)
                """,
                uploadId,
                deviceId,
                destinationId,
                storageRoot,
                filename,
                expectedBytes >= 0 ? expectedBytes : null,
                now.toString(),
                now.toString());
    }

    public void markVerified(
            String uploadId,
            String storedRelativePath,
            long bytes,
            String sha256,
            Instant verifiedAt) {
        jdbc.update("""
                UPDATE uploads
                SET stored_relative_path = ?, bytes_received = ?, sha256 = ?,
                    state = 'verified', verified_at = ?, updated_at = ?, failure_message = NULL
                WHERE upload_id = ?
                """,
                storedRelativePath,
                bytes,
                sha256,
                verifiedAt.toString(),
                verifiedAt.toString(),
                uploadId);
    }

    public void markFailed(String uploadId, String failureMessage, Instant failedAt) {
        jdbc.update("""
                UPDATE uploads
                SET state = 'failed', updated_at = ?, failure_message = ?
                WHERE upload_id = ?
                """,
                failedAt.toString(),
                truncate(failureMessage, 500),
                uploadId);
    }

    public Optional<UploadReceipt> findById(String uploadId) {
        return jdbc.query(
                        "SELECT * FROM uploads WHERE upload_id = ?",
                        RECEIPT_MAPPER,
                        uploadId)
                .stream()
                .findFirst();
    }

    public List<UploadReceipt> listForDevice(String deviceId) {
        return jdbc.query(
                "SELECT * FROM uploads WHERE device_id = ? ORDER BY started_at DESC",
                RECEIPT_MAPPER,
                deviceId);
    }

    public List<UploadReceipt> listAll() {
        return jdbc.query(
                "SELECT * FROM uploads ORDER BY started_at DESC",
                RECEIPT_MAPPER);
    }

    private static UploadReceipt mapReceipt(ResultSet resultSet, int rowNumber) throws SQLException {
        long expectedBytesValue = resultSet.getLong("expected_bytes");
        Long expectedBytes = resultSet.wasNull() ? null : expectedBytesValue;
        Instant verifiedAt = nullableInstant(resultSet.getString("verified_at"));
        String state = resultSet.getString("state");
        return new UploadReceipt(
                resultSet.getString("upload_id"),
                resultSet.getString("device_id"),
                resultSet.getString("destination_id"),
                resultSet.getString("storage_root"),
                resultSet.getString("original_filename"),
                resultSet.getString("stored_relative_path"),
                expectedBytes,
                resultSet.getLong("bytes_received"),
                resultSet.getString("sha256"),
                state,
                "verified".equals(state),
                Instant.parse(resultSet.getString("started_at")),
                Instant.parse(resultSet.getString("updated_at")),
                verifiedAt,
                resultSet.getString("failure_message"));
    }

    private void ensureColumn(String columnName, String definition) {
        boolean exists = jdbc.queryForList("PRAGMA table_info(uploads)")
                .stream()
                .anyMatch(row -> columnName.equals(row.get("name")));
        if (!exists) {
            jdbc.execute("ALTER TABLE uploads ADD COLUMN " + columnName + " " + definition);
        }
    }

    private static Instant nullableInstant(String value) {
        return value == null ? null : Instant.parse(value);
    }

    private static String truncate(String value, int maximumLength) {
        String safeValue = value == null || value.isBlank() ? "Upload failed" : value;
        return safeValue.substring(0, Math.min(safeValue.length(), maximumLength));
    }
}
