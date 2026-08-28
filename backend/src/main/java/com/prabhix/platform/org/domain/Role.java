package com.prabhix.platform.org.domain;

import com.prabhix.platform.common.entity.AuditableEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "roles")
public class Role extends AuditableEntity {

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "role_key", nullable = false, length = 64)
    private String roleKey;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "is_system", nullable = false)
    private boolean system;

    @Column(name = "rank", nullable = false)
    private int rank = 100;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "permission_code", length = 64)
    private Set<String> permissionCodes = new HashSet<>();

    public boolean isSystemRole() {
        return system && organizationId == null;
    }
}
