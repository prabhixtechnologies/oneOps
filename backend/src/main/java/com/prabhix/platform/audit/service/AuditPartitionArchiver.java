package com.prabhix.platform.audit.service;

import tools.jackson.databind.ObjectMapper;
import com.prabhix.platform.files.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditPartitionArchiver {

    private static final Pattern MONTHLY_PARTITION =
            Pattern.compile("^audit_logs_(\\d{4})_(\\d{2})$");
    private static final String PLATFORM_SCOPE = "platform";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Optional<FileStorageService.ObjectStore> objectStore;

    @Value("${prabhix.storage.bucket:prabhix-local}")
    private String bucket;

    public List<String> findEligiblePartitions(Instant retentionCutoff) {
        List<String> partitions = jdbc.queryForList("""
                SELECT c.relname AS partition_name
                FROM pg_inherits i
                JOIN pg_class c ON c.oid = i.inhrelid
                JOIN pg_class p ON p.oid = i.inhparent
                WHERE p.relname = 'audit_logs'
                  AND c.relname ~ '^audit_logs_[0-9]{4}_[0-9]{2}$'
                ORDER BY c.relname
                """, String.class);

        List<String> eligible = new ArrayList<>();
        for (String partition : partitions) {
            YearMonth month = parsePartitionMonth(partition);
            if (month == null) {
                continue;
            }
            Instant partitionEnd = month.atEndOfMonth().plusDays(1)
                    .atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
            if (partitionEnd.isBefore(retentionCutoff)
                    && !isAlreadyArchived(partition)) {
                eligible.add(partition);
            }
        }
        return eligible;
    }

    @Transactional
    public void archiveAndDrop(String partitionName, boolean dryRun) {
        validatePartitionName(partitionName);
        if (isAlreadyArchived(partitionName)) {
            log.info("Partition {} is already archived; skipping", partitionName);
            return;
        }

        List<UUID> organizationIds = jdbc.queryForList(
                "SELECT DISTINCT organization_id FROM %s WHERE organization_id IS NOT NULL"
                        .formatted(partitionName),
                UUID.class);
        boolean hasPlatformRows = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM %s WHERE organization_id IS NULL LIMIT 1)"
                        .formatted(partitionName),
                Boolean.class));

        long rowCount = countRows(partitionName);
        if (rowCount == 0) {
            if (dryRun) {
                log.info("[dry-run] Would detach and drop empty partition {}", partitionName);
                return;
            }
            detachAndDrop(partitionName);
            return;
        }

        FileStorageService.ObjectStore store = objectStore.orElseThrow(() ->
                new IllegalStateException("Object storage is required to archive audit partitions"));

        Map<String, String> storageKeys = new LinkedHashMap<>();
        List<byte[]> uploadedPayloads = new ArrayList<>();

        if (hasPlatformRows) {
            ArchivePayload payload = exportScope(partitionName, null);
            String key = storageKey(partitionName, PLATFORM_SCOPE);
            storageKeys.put(PLATFORM_SCOPE, key);
            uploadedPayloads.add(payload.bytes());
            if (!dryRun) {
                uploadVerified(store, key, payload);
            } else {
                log.info("[dry-run] Would archive {} platform rows from {} to {}",
                        payload.rowCount(), partitionName, key);
            }
        }

        for (UUID organizationId : organizationIds) {
            ArchivePayload payload = exportScope(partitionName, organizationId);
            String scope = organizationId.toString();
            String key = storageKey(partitionName, scope);
            storageKeys.put(scope, key);
            uploadedPayloads.add(payload.bytes());
            if (!dryRun) {
                uploadVerified(store, key, payload);
            } else {
                log.info("[dry-run] Would archive {} rows for org {} from {} to {}",
                        payload.rowCount(), organizationId, partitionName, key);
            }
        }

        if (dryRun) {
            log.info("[dry-run] Would record archive ledger for {} ({} rows)", partitionName, rowCount);
            return;
        }

        String manifestDigest = sha256(concat(uploadedPayloads));
        recordArchive(partitionName, storageKeys, rowCount, manifestDigest);
        detachAndDrop(partitionName);
        jdbc.update("""
                UPDATE audit_archive_records
                SET detached_at = now(), dropped_at = now()
                WHERE partition_name = ?
                """, partitionName);
        log.info("Archived and dropped audit partition {} ({} rows)", partitionName, rowCount);
    }

    private ArchivePayload exportScope(String partitionName, UUID organizationId) {
        String sql = organizationId == null
                ? """
                SELECT id, organization_id, actor_user_id, actor_email, actor_type, action,
                       resource_type, resource_id, resource_label, changes, metadata,
                       ip_address, user_agent, outcome, created_at
                FROM %s WHERE organization_id IS NULL ORDER BY created_at, id
                """.formatted(partitionName)
                : """
                SELECT id, organization_id, actor_user_id, actor_email, actor_type, action,
                       resource_type, resource_id, resource_label, changes, metadata,
                       ip_address, user_agent, outcome, created_at
                FROM %s WHERE organization_id = ? ORDER BY created_at, id
                """.formatted(partitionName);

        List<Map<String, Object>> rows = organizationId == null
                ? jdbc.query(sql, this::mapAuditRow)
                : jdbc.query(sql, this::mapAuditRow, organizationId);

        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(buffer)) {
                for (Map<String, Object> row : rows) {
                    gzip.write(objectMapper.writeValueAsBytes(row));
                    gzip.write('\n');
                }
            }
            byte[] bytes = buffer.toByteArray();
            return new ArchivePayload(bytes, rows.size(), sha256(bytes));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not export audit partition " + partitionName, ex);
        }
    }

    private Map<String, Object> mapAuditRow(ResultSet rs, int rowNum) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getObject("id", UUID.class));
        row.put("organization_id", rs.getObject("organization_id", UUID.class));
        row.put("actor_user_id", rs.getObject("actor_user_id", UUID.class));
        row.put("actor_email", rs.getString("actor_email"));
        row.put("actor_type", rs.getString("actor_type"));
        row.put("action", rs.getString("action"));
        row.put("resource_type", rs.getString("resource_type"));
        row.put("resource_id", rs.getObject("resource_id", UUID.class));
        row.put("resource_label", rs.getString("resource_label"));
        row.put("changes", readJson(rs, "changes"));
        row.put("metadata", readJson(rs, "metadata"));
        row.put("ip_address", rs.getString("ip_address"));
        row.put("user_agent", rs.getString("user_agent"));
        row.put("outcome", rs.getString("outcome"));
        row.put("created_at", rs.getTimestamp("created_at").toInstant());
        return row;
    }

    private Object readJson(ResultSet rs, String column) throws SQLException {
        String raw = rs.getString(column);
        if (raw == null) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, Object.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not parse JSON column " + column, ex);
        }
    }

    private void uploadVerified(FileStorageService.ObjectStore store, String key, ArchivePayload payload) {
        store.put(bucket, key, payload.bytes(), "application/x-ndjson");
        byte[] stored = store.get(bucket, key);
        if (!payload.digest().equals(sha256(stored))) {
            throw new IllegalStateException(
                    "Archive upload for " + key + " failed durability check — partition will not be dropped");
        }
    }

    private void recordArchive(String partitionName,
                               Map<String, String> storageKeys,
                               long rowCount,
                               String manifestDigest) {
        try {
            jdbc.update("""
                    INSERT INTO audit_archive_records
                        (partition_name, storage_bucket, storage_keys, row_count, content_sha256)
                    VALUES (?, ?, ?::jsonb, ?, ?)
                    """,
                    partitionName,
                    bucket,
                    objectMapper.writeValueAsString(storageKeys),
                    rowCount,
                    manifestDigest);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not record archive ledger for " + partitionName, ex);
        }
    }

    private void detachAndDrop(String partitionName) {
        validatePartitionName(partitionName);
        jdbc.execute("ALTER TABLE audit_logs DETACH PARTITION %s".formatted(partitionName));
        jdbc.execute("DROP TABLE %s".formatted(partitionName));
    }

    private long countRows(String partitionName) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM %s".formatted(partitionName), Long.class);
        return count == null ? 0L : count;
    }

    private boolean isAlreadyArchived(String partitionName) {
        Boolean exists = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM audit_archive_records WHERE partition_name = ?)",
                Boolean.class,
                partitionName);
        return Boolean.TRUE.equals(exists);
    }

    private static String storageKey(String partitionName, String scope) {
        return "audit-archive/" + partitionName + "/" + scope + ".ndjson.gz";
    }

    private static YearMonth parsePartitionMonth(String partitionName) {
        var matcher = MONTHLY_PARTITION.matcher(partitionName);
        if (!matcher.matches()) {
            return null;
        }
        return YearMonth.of(
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)));
    }

    static void validatePartitionName(String partitionName) {
        if (!MONTHLY_PARTITION.matcher(partitionName).matches()) {
            throw new IllegalArgumentException("Invalid audit partition name: " + partitionName);
        }
    }

    private static byte[] concat(List<byte[]> payloads) {
        int length = payloads.stream().mapToInt(bytes -> bytes.length).sum();
        byte[] combined = new byte[length];
        int offset = 0;
        for (byte[] payload : payloads) {
            System.arraycopy(payload, 0, combined, offset, payload.length);
            offset += payload.length;
        }
        return combined;
    }

    private static String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required but unavailable", ex);
        }
    }

    record ArchivePayload(byte[] bytes, int rowCount, String digest) {
    }
}
