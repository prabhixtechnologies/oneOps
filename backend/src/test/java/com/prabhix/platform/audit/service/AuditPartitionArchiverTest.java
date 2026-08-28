package com.prabhix.platform.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.prabhix.platform.files.service.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditPartitionArchiverTest {

    @Mock
    JdbcTemplate jdbc;

    InMemoryObjectStore store;
    AuditPartitionArchiver archiver;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        store = new InMemoryObjectStore();
        archiver = new AuditPartitionArchiver(jdbc, objectMapper, Optional.of(store));
        ReflectionTestUtils.setField(archiver, "bucket", "test-bucket");
    }

    @Test
    void doesNotDropPartitionWhenUploadFailsDurabilityCheck() throws Exception {
        String partition = "audit_logs_2020_01";
        UUID orgId = UUID.randomUUID();
        stubPartitionExport(partition, orgId, 1);
        when(jdbc.queryForObject(contains("COUNT(*)"), eq(Long.class))).thenReturn(1L);
        when(jdbc.queryForObject(contains("audit_archive_records"), eq(Boolean.class), eq(partition)))
                .thenReturn(false);
        store.failVerification = true;

        assertThatThrownBy(() -> archiver.archiveAndDrop(partition, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("durability check");

        verify(jdbc, never()).execute(contains("DETACH PARTITION"));
        verify(jdbc, never()).execute(contains("DROP TABLE"));
        verify(jdbc, never()).update(contains("INSERT INTO audit_archive_records"), any(), any(), any(), any(), any());
    }

    @Test
    void archivesVerifiedPayloadBeforeDetachAndDrop() throws Exception {
        String partition = "audit_logs_2020_01";
        UUID orgId = UUID.randomUUID();
        stubPartitionExport(partition, orgId, 2);
        when(jdbc.queryForObject(contains("COUNT(*)"), eq(Long.class))).thenReturn(2L);
        when(jdbc.queryForObject(contains("audit_archive_records"), eq(Boolean.class), eq(partition)))
                .thenReturn(false);

        archiver.archiveAndDrop(partition, false);

        ArgumentCaptor<String> insertSql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(insertSql.capture(), eq(partition), eq("test-bucket"), anyString(), eq(2L), anyString());
        assertThat(insertSql.getValue()).contains("INSERT INTO audit_archive_records");
        verify(jdbc).execute("ALTER TABLE audit_logs DETACH PARTITION " + partition);
        verify(jdbc).execute("DROP TABLE " + partition);
        verify(jdbc).update(contains("UPDATE audit_archive_records"), eq(partition));
    }

    @Test
    void dryRunDoesNotUploadOrDrop() throws Exception {
        String partition = "audit_logs_2020_01";
        UUID orgId = UUID.randomUUID();
        stubPartitionExport(partition, orgId, 1);
        when(jdbc.queryForObject(contains("COUNT(*)"), eq(Long.class))).thenReturn(1L);
        when(jdbc.queryForObject(contains("audit_archive_records"), eq(Boolean.class), eq(partition)))
                .thenReturn(false);

        archiver.archiveAndDrop(partition, true);

        assertThat(store.objects).isEmpty();
        verify(jdbc, never()).execute(contains("DETACH PARTITION"));
        verify(jdbc, never()).update(contains("INSERT INTO audit_archive_records"), any(), any(), any(), any(), any());
    }

    @Test
    void findEligiblePartitionsSkipsRecentMonthsAndAlreadyArchivedOnes() {
        YearMonth oldMonth = YearMonth.now().minusMonths(30);
        String oldPartition = "audit_logs_%d_%02d".formatted(oldMonth.getYear(), oldMonth.getMonthValue());
        String recentPartition = "audit_logs_%d_%02d".formatted(
                YearMonth.now().getYear(), YearMonth.now().getMonthValue());

        when(jdbc.queryForList(anyString(), eq(String.class)))
                .thenReturn(List.of(oldPartition, recentPartition));
        when(jdbc.queryForObject(contains("audit_archive_records"), eq(Boolean.class), eq(oldPartition)))
                .thenReturn(false);

        List<String> eligible = archiver.findEligiblePartitions(Instant.now());

        assertThat(eligible).containsExactly(oldPartition);
    }

    private void stubPartitionExport(String partition, UUID orgId, int rowCount) throws Exception {
        when(jdbc.queryForList(contains("DISTINCT organization_id"), eq(UUID.class)))
                .thenReturn(List.of(orgId));
        when(jdbc.queryForObject(contains("organization_id IS NULL"), eq(Boolean.class)))
                .thenReturn(false);

        when(jdbc.query(contains("WHERE organization_id = ?"), any(RowMapper.class), eq(orgId)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(1);
                    return java.util.stream.IntStream.range(0, rowCount)
                            .mapToObj(i -> {
                                try {
                                    return mapper.mapRow(sampleRow(orgId, i), 0);
                                } catch (Exception ex) {
                                    throw new RuntimeException(ex);
                                }
                            })
                            .toList();
                });
    }

    private static ResultSet sampleRow(UUID orgId, int index) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject("id", UUID.class)).thenReturn(UUID.randomUUID());
        when(rs.getObject("organization_id", UUID.class)).thenReturn(orgId);
        when(rs.getObject("actor_user_id", UUID.class)).thenReturn(null);
        when(rs.getString("actor_email")).thenReturn("actor@example.com");
        when(rs.getString("actor_type")).thenReturn("USER");
        when(rs.getString("action")).thenReturn("test.action." + index);
        when(rs.getString("resource_type")).thenReturn("resource");
        when(rs.getObject("resource_id", UUID.class)).thenReturn(UUID.randomUUID());
        when(rs.getString("resource_label")).thenReturn("label");
        when(rs.getString("changes")).thenReturn(null);
        when(rs.getString("metadata")).thenReturn("{}");
        when(rs.getString("ip_address")).thenReturn("127.0.0.1");
        when(rs.getString("user_agent")).thenReturn("test");
        when(rs.getString("outcome")).thenReturn("SUCCESS");
        when(rs.getTimestamp("created_at")).thenReturn(Timestamp.from(Instant.parse("2020-01-15T12:00:00Z")));
        return rs;
    }

    static final class InMemoryObjectStore implements FileStorageService.ObjectStore {

        final Map<String, byte[]> objects = new ConcurrentHashMap<>();
        boolean failVerification;

        @Override
        public void put(String bucket, String key, byte[] content, String contentType) {
            objects.put(bucket + "/" + key, content);
        }

        @Override
        public byte[] get(String bucket, String key) {
            if (failVerification) {
                return "corrupt".getBytes(StandardCharsets.UTF_8);
            }
            return objects.get(bucket + "/" + key);
        }

        @Override
        public Optional<String> signedUrl(String bucket, String key, java.time.Duration ttl) {
            return Optional.empty();
        }
    }
}
