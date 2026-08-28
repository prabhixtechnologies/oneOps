package com.prabhix.platform.auth.repository;

import com.prabhix.platform.auth.domain.AuthChallenge;
import com.prabhix.platform.auth.domain.AuthChallenge.ChallengePurpose;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AuthChallengeRepository extends JpaRepository<AuthChallenge, UUID> {

    Optional<AuthChallenge> findBySecretHashAndConsumedAtIsNull(String secretHash);

    Optional<AuthChallenge> findBySecretHashAndPurposeAndConsumedAtIsNull(
            String secretHash, ChallengePurpose purpose);

    Optional<AuthChallenge> findFirstByDestinationAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
            String destination, ChallengePurpose purpose);
}
