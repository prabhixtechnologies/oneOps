package com.prabhix.platform.files.service;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.files.domain.StoredFile;
import com.prabhix.platform.files.repository.StoredFileRepository;
import com.prabhix.platform.files.scan.MalwareScanner;
import com.prabhix.platform.security.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class FileStorageService {

    /**
     * User-global assets (avatars) live under the platform org so upload works before a
     * customer organization is selected. See V44 migration.
     */
    public static final UUID PLATFORM_FILES_ORGANIZATION_ID =
            UUID.fromString("00000000-0000-4000-8000-000000000001");

    private static final DateTimeFormatter KEY_DATE = DateTimeFormatter.ofPattern("yyyy/MM");

    private final StoredFileRepository repository;
    private final PrabhixProperties properties;
    private final ObjectStore store;
    private final MalwareScanner malwareScanner;

    public FileStorageService(StoredFileRepository repository,
                             PrabhixProperties properties,
                             Optional<ObjectStore> configuredStore,
                             MalwareScanner malwareScanner) {
        this.repository = repository;
        this.properties = properties;
        this.malwareScanner = malwareScanner;
        this.store = configuredStore.orElseGet(() -> {
            Path root = Paths.get(System.getProperty("java.io.tmpdir"), "prabhix-files");
            log.warn("Object storage is not configured. Falling back to local disk at {}. "
                    + "Never run production this way: files are lost when the container restarts.", root);
            return new LocalDiskObjectStore(root);
        });
    }

    @Transactional
    public StoredFile store(byte[] content,
                            String filename,
                            String contentType,
                            StoredFile.FilePurpose purpose,
                            UUID uploadedBy) {
        return storeForOrganization(
                TenantContext.require(), content, filename, contentType, purpose, uploadedBy);
    }

    @Transactional
    public StoredFile storeAvatar(byte[] content,
                                  String filename,
                                  String contentType,
                                  UUID uploadedBy) {
        return storeForOrganization(
                PLATFORM_FILES_ORGANIZATION_ID, content, filename, contentType,
                StoredFile.FilePurpose.AVATAR, uploadedBy);
    }

    @Transactional
    public StoredFile storeForOrganization(UUID organizationId,
                                           byte[] content,
                                           String filename,
                                           String contentType,
                                           StoredFile.FilePurpose purpose,
                                           UUID uploadedBy) {
        long maxBytes = properties.limits().maxAttachmentSizeBytes();

        if (content.length > maxBytes) {
            throw ApiException.of(ErrorCode.LIMIT_EXCEEDED,
                    "That file is larger than the " + (maxBytes / (1024 * 1024)) + " MB limit");
        }

        String checksum = sha256(content);
        Optional<StoredFile> existing =
                repository.findDuplicate(organizationId, checksum, content.length);
        if (existing.isPresent()) {
            return existing.get();
        }

        String bucket = properties.storage().bucket();
        String key = buildKey(organizationId, purpose, filename);

        try {
            store.put(bucket, key, content, contentType);
        } catch (RuntimeException ex) {
            throw ApiException.of(ErrorCode.STORAGE_ERROR, "Could not store that file", ex);
        }

        StoredFile file = new StoredFile();
        file.setOrganizationId(organizationId);
        file.setBucket(bucket);
        file.setStorageKey(key);
        file.setOriginalFilename(sanitiseFilename(filename));
        file.setContentType(contentType == null ? "application/octet-stream" : contentType);
        file.setSizeBytes(content.length);
        file.setChecksumSha256(checksum);
        file.setPurpose(purpose);
        file.setUploadedBy(uploadedBy);
        file.setScanStatus(switch (purpose) {
            case INVOICE, EXPORT, MAIL_RAW_MIME -> StoredFile.ScanStatus.SKIPPED;
            default -> StoredFile.ScanStatus.PENDING;
        });

        file = repository.save(file);
        if (file.getScanStatus() == StoredFile.ScanStatus.PENDING) {
            applyScanResult(file, malwareScanner.scan(file, content));
        }
        return file;
    }

    @Transactional(readOnly = true)
    public byte[] read(UUID fileId) {
        return read(TenantContext.require(), fileId);
    }

    @Transactional(readOnly = true)
    public byte[] read(UUID organizationId, UUID fileId) {
        StoredFile file = requireForOrganization(organizationId, fileId);

        if (!file.isDownloadable()) {
            throw ApiException.of(ErrorCode.FORBIDDEN,
                    file.getScanStatus() == StoredFile.ScanStatus.INFECTED
                            ? "That file was quarantined by our malware scanner"
                            : "That file is still being scanned. Try again shortly.");
        }

        try {
            return store.get(file.getBucket(), file.getStorageKey());
        } catch (RuntimeException ex) {
            throw ApiException.of(ErrorCode.STORAGE_ERROR, "Could not read that file", ex);
        }
    }

    @Transactional(readOnly = true)
    public StoredFile requireMetadata(UUID organizationId, UUID fileId) {
        return requireForOrganization(organizationId, fileId);
    }

    @Transactional(readOnly = true)
    public Optional<String> signedUrl(UUID fileId) {
        return signedUrl(TenantContext.require(), fileId);
    }

    @Transactional(readOnly = true)
    public Optional<String> signedUrl(UUID organizationId, UUID fileId) {
        StoredFile file = requireForOrganization(organizationId, fileId);
        if (!file.isDownloadable()) {
            return Optional.empty();
        }
        return store.signedUrl(file.getBucket(), file.getStorageKey(),
                properties.storage().signedUrlTtl());
    }

    @Transactional
    public void softDelete(UUID fileId) {
        softDelete(TenantContext.require(), fileId);
    }

    @Transactional
    public void softDelete(UUID organizationId, UUID fileId) {
        StoredFile file = requireForOrganization(organizationId, fileId);
        file.setDeletedAt(Instant.now());
        repository.save(file);
    }

    private StoredFile requireForOrganization(UUID organizationId, UUID fileId) {
        return repository.findByIdAndOrganizationIdAndDeletedAtIsNull(fileId, organizationId)
                .orElseThrow(() -> ApiException.notFound("File"));
    }

    private void applyScanResult(StoredFile file, MalwareScanner.ScanResult result) {
        file.setScanStatus(result.status());
        file.setScannedAt(Instant.now());
        repository.save(file);
    }

    private String buildKey(UUID organizationId, StoredFile.FilePurpose purpose, String filename) {
        return organizationId
                + "/" + purpose.name().toLowerCase()
                + "/" + LocalDate.now().format(KEY_DATE)
                + "/" + UUID.randomUUID() + "-" + sanitiseFilename(filename);
    }

    private String sanitiseFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "file";
        }
        String cleaned = filename.replaceAll("[\\\\/\\p{Cntrl}]", "_").trim();
        cleaned = cleaned.replaceAll("^\\.+", "");
        if (cleaned.isBlank()) {
            return "file";
        }
        return cleaned.length() > 200 ? cleaned.substring(cleaned.length() - 200) : cleaned;
    }

    private String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required but unavailable", ex);
        }
    }

    public interface ObjectStore {
        void put(String bucket, String key, byte[] content, String contentType);

        byte[] get(String bucket, String key);

        Optional<String> signedUrl(String bucket, String key, java.time.Duration ttl);
    }

    static final class LocalDiskObjectStore implements ObjectStore {

        private final Path root;

        LocalDiskObjectStore(Path root) {
            this.root = root;
        }

        @Override
        public void put(String bucket, String key, byte[] content, String contentType) {
            try {
                Path target = root.resolve(bucket).resolve(key).normalize();
                if (!target.startsWith(root)) {
                    throw new IllegalArgumentException("Resolved path escapes the storage root");
                }
                Files.createDirectories(target.getParent());
                Files.write(target, content);
            } catch (IOException ex) {
                throw new IllegalStateException("Local write failed for " + key, ex);
            }
        }

        @Override
        public byte[] get(String bucket, String key) {
            try (InputStream in = Files.newInputStream(root.resolve(bucket).resolve(key))) {
                return in.readAllBytes();
            } catch (IOException ex) {
                throw new IllegalStateException("Local read failed for " + key, ex);
            }
        }

        @Override
        public Optional<String> signedUrl(String bucket, String key, java.time.Duration ttl) {
            return Optional.empty();
        }
    }
}
