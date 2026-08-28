package com.prabhix.platform.user.repository;

import com.prabhix.platform.user.domain.AuthIdentity;
import com.prabhix.platform.user.domain.AuthIdentity.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AuthIdentityRepository extends JpaRepository<AuthIdentity, UUID> {

    Optional<AuthIdentity> findByProviderAndProviderSubject(AuthProvider provider, String providerSubject);
}
