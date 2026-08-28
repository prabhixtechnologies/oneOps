package com.prabhix.platform.commerce.domain;

import com.prabhix.platform.common.entity.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "commerce_customers")
public class CommerceCustomer extends TenantScopedEntity {

    @Column(name = "email", nullable = false, columnDefinition = "citext")
    private String email;

    @Column(name = "name", length = 160)
    private String name;

    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "marketing_consent", nullable = false)
    private boolean marketingConsent;

    @Column(name = "visitor_id")
    private UUID visitorId;
}
