package com.prabhix.platform.commerce.domain;

import com.prabhix.platform.common.entity.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "commerce_order_addresses")
public class OrderAddress extends TenantScopedEntity {

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "address_type", nullable = false, length = 16)
    private CommerceEnums.AddressType addressType;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "line1", nullable = false, length = 200)
    private String line1;

    @Column(name = "line2", length = 200)
    private String line2;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "state", nullable = false, length = 80)
    private String state;

    @Column(name = "pincode", nullable = false, length = 12)
    private String pincode;

    @Column(name = "country", nullable = false, length = 2)
    private String country = "IN";

    @Column(name = "phone", length = 32)
    private String phone;
}
