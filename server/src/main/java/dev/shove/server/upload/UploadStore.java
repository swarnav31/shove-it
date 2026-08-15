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
                    receive_hash_ms INTEGER,
                    external_copy_ms INTEGER,
                    promote_ms INTEGER,
                    audit_ms INTEGER,
                    total_ms INTEGER,
                    failure_phase TEXT,
                    failure_message TEXT
                )
                """);
        ensureColumn("destination_id", "TEXT NOT NULL DEFAULT 'legacy'");
        ensureColumn("storage_root", "TEXT");
        ensureColumn("receive_hash_ms", "INTEGER");
        ensureColumn("external_copy_ms", "INTEGER");
        ensureColumn("promote_ms", "INTEGER");
        ensureColumn("audit_ms", "INTEGER");
        ensureColumn("total_ms", "INTEGER");
        ensureColumn("failure_phase", "TEXT");
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
            UploadPhaseTimings timings,
            Instant verifiedAt) {
        jdbc.update("""
                UPDATE uploads
                SET stored_relative_path = ?, bytes_received = ?, sha256 = ?,
                    state = 'verified', verified_at = ?, updated_at = ?,
                    receive_hash_ms = ?, external_copy_ms = ?, promote_ms = ?,
                    failure_phase = NULL, failure_message = NULL
                WHERE upload_id = ?
                """,
                storedRelativePath,
                bytes,
                sha256,
                verifiedAt.toString(),
                verifiedAt.toString(),
                timings.receiveHashMs(),
                timings.externalCopyMs(),
                timings.promoteMs(),
                uploadId);
    }

    public void markPhase(
            String uploadId,
            String state,
            long bytes,
            String sha256,
            UploadPhaseTimings timings,
            Instant updatedAt) {
        jdbc.update("""
                UPDATE uploads
                SET state = ?, bytes_received = ?, sha256 = ?, updated_at = ?,
                    receive_hash_ms = ?, external_copy_ms = ?, promote_ms = ?
                WHERE upload_id = ?
                """,
                state,
                bytes,
                sha256,
                updatedAt.toString(),
                timings.receiveHashMs(),
                timings.externalCopyMs(),
                timings.promoteMs(),
                uploadId);
    }

    public void updateFinalTimings(String uploadId, UploadPhaseTimings timings) {
        jdbc.update("""
                UPDATE uploads
                SET receive_hash_ms = ?, external_copy_ms = ?, promote_ms = ?,
                    audit_ms = ?, total_ms = ?, failure_phase = ?
                WHERE upload_id = ?
                """,
                timings.receiveHashMs(),
                timings.externalCopyMs(),
                timings.promoteMs(),
                timings.auditMs(),
                timings.totalMs(),
                timings.failurePhase(),
                uploadId);
    }

    public void markFailed(
            String uploadId,
            String failureMessage,
            UploadPhaseTimings timings,
            Instant failedAt) {
        jdbc.update("""
                UPDATE uploads
                SET state = 'failed', updated_at = ?, failure_message = ?,
                    receive_hash_ms = ?, external_copy_ms = ?, promote_ms = ?,
                    audit_ms = ?, total_ms = ?, failure_phase = ?
                WHERE upload_id = ?
                """,
                failedAt.toString(),
                truncate(failureMessage, 500),
                timings.receiveHashMs(),
                timings.externalCopyMs(),
                timings.promoteMs(),
                timings.auditMs(),
                timings.totalMs(),
                timings.failurePhase(),
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
                new UploadPhaseTimings(
                        nullableLong(resultSet, "receive_hash_ms"),
                        nullableLong(resultSet, "external_copy_ms"),
                        nullableLong(resultSet, "promote_ms"),
                        nullableLong(resultSet, "audit_ms"),
                        nullableLong(resultSet, "total_ms"),
                        resultSet.getString("failure_phase")),
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

    private static Long nullableLong(ResultSet resultSet, String columnName) throws SQLException {
        long value = resultSet.getLong(columnName);
        return resultSet.wasNull() ? null : value;
    }

    private static String truncate(String value, int maximumLength) {
        String safeValue = value == null || value.isBlank() ? "Upload failed" : value;
        return safeValue.substring(0, Math.min(safeValue.length(), maximumLength));
    }
}
