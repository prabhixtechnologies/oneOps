package com.prabhix.platform.files.service;

import com.prabhix.platform.common.web.Cursor;
import com.prabhix.platform.common.web.CursorPage;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.files.domain.StoredFile;
import com.prabhix.platform.files.dto.FileDtos;
import com.prabhix.platform.files.repository.StoredFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileQueryService {

    private final StoredFileRepository repository;
    private final PrabhixProperties properties;

    @Transactional(readOnly = true)
    public CursorPage<FileDtos.FileSummary> list(UUID organizationId,
                                                 StoredFile.FilePurpose purpose,
                                                 StoredFile.ScanStatus scanStatus,
                                                 String search,
                                                 String cursor,
                                                 Integer limit) {
        Cursor decoded = cursor != null ? Cursor.decode(cursor) : Cursor.beginning();
        int pageSize = properties.limits().clampPageSize(limit) + 1;
        List<StoredFile> fetched = repository.listWithCursor(
                organizationId,
                purpose != null ? purpose.name() : null,
                scanStatus != null ? scanStatus.name() : null,
                blankToNull(search),
                decoded.timestamp(),
                decoded.id(),
                pageSize);
        return CursorPage.of(fetched, pageSize - 1, this::toSummary,
                f -> Cursor.of(f.getCreatedAt(), f.getId()).encode());
    }

    private FileDtos.FileSummary toSummary(StoredFile file) {
        return new FileDtos.FileSummary(
                file.getId(),
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSizeBytes(),
                file.getPurpose(),
                file.getScanStatus(),
                file.getCreatedAt(),
                file.getUploadedBy());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
