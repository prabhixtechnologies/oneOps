package com.prabhix.platform.mail.repository;

import com.prabhix.platform.mail.domain.MailThreadTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MailThreadTagRepository extends JpaRepository<MailThreadTag, MailThreadTag.Id> {

    List<MailThreadTag> findByIdThreadId(UUID threadId);

    boolean existsByIdThreadIdAndIdTagId(UUID threadId, UUID tagId);

    void deleteByIdTagId(UUID tagId);

    void deleteByIdThreadIdAndIdTagId(UUID threadId, UUID tagId);

    int countByIdTagId(UUID tagId);
}
