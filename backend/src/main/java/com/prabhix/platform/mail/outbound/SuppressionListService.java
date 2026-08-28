package com.prabhix.platform.mail.outbound;

import com.prabhix.platform.mail.domain.MailSuppression;
import com.prabhix.platform.mail.domain.MailEnums;
import com.prabhix.platform.mail.dto.SuppressionDtos;
import com.prabhix.platform.mail.repository.MailSuppressionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SuppressionListService {

    private final MailSuppressionRepository suppressionRepository;
    private final SuppressionService suppressionService;

    @Transactional(readOnly = true)
    public List<SuppressionDtos.SuppressionResponse> list(UUID organizationId) {
        return suppressionRepository.findByOrganizationIdOrOrganizationIdIsNullOrderByAddress(organizationId)
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public SuppressionDtos.SuppressionResponse add(UUID organizationId,
                                                   SuppressionDtos.CreateRequest request) {
        suppressionService.recordHardBounce(request.address(), organizationId, request.detail());
        return suppressionRepository.findByOrganizationIdOrOrganizationIdIsNullOrderByAddress(organizationId)
                .stream().filter(s -> s.getAddress().equalsIgnoreCase(request.address()))
                .findFirst().map(this::toDto).orElseThrow();
    }

    private SuppressionDtos.SuppressionResponse toDto(MailSuppression s) {
        return new SuppressionDtos.SuppressionResponse(
                s.getId(), s.getAddress(), s.getReason(), s.getDetail(), s.getExpiresAt());
    }
}
