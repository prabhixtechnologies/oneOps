package com.prabhix.platform.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.util.Objects;
import java.util.UUID;

/**
 * Identity and optimistic locking for every persistent entity.
 *
 * <p>Equality is by primary key only, and an entity that has not been persisted is equal to
 * nothing but itself. Using Lombok's generated equals/hashCode here would break Hibernate
 * collections, because the field values change as the entity is loaded and mutated.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BaseEntity that)) {
            return false;
        }
        // Hibernate proxies subclass the entity, so compare on the persistent class.
        if (!getClass().isInstance(other) && !other.getClass().isInstance(this)) {
            return false;
        }
        return id != null && Objects.equals(id, that.getId());
    }

    @Override
    public final int hashCode() {
        return id != null ? id.hashCode() : System.identityHashCode(this);
    }
}
