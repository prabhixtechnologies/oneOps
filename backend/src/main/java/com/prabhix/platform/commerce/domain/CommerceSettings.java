package com.prabhix.platform.commerce.domain;

import com.prabhix.platform.common.entity.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "commerce_settings")
public class CommerceSettings extends TenantScopedEntity {

    @Column(name = "seller_state", nullable = false, length = 80)
    private String sellerState = "Karnataka";

    @Column(name = "seller_name", length = 200)
    private String sellerName;

    @Column(name = "seller_gstin", length = 20)
    private String sellerGstin;

    @Column(name = "seller_address", columnDefinition = "text")
    private String sellerAddress;

    @Column(name = "order_number_prefix", nullable = false, length = 10)
    private String orderNumberPrefix = "ORD";

    @Column(name = "gst_percent", nullable = false)
    private int gstPercent = 18;

    @Column(name = "flat_shipping_paise", nullable = false)
    private long flatShippingPaise;

    @Column(name = "free_shipping_above_paise")
    private Long freeShippingAbovePaise;
}
