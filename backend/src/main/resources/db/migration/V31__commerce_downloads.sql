-- =============================================================================
-- V31  Digital download entitlements
-- =============================================================================

CREATE TABLE commerce_order_downloads (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version                 bigint      NOT NULL DEFAULT 0,
    organization_id         uuid        NOT NULL,
    order_id                uuid        NOT NULL,
    order_item_id           uuid        NOT NULL,
    file_id                 uuid        NOT NULL,
    download_token          varchar(64) NOT NULL,
    download_count          integer     NOT NULL DEFAULT 0,
    max_download_count      integer     NOT NULL DEFAULT 5,
    link_expires_at         timestamptz NOT NULL,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    created_by              uuid,
    updated_by              uuid,

    CONSTRAINT uq_commerce_downloads_token UNIQUE (download_token),
    CONSTRAINT uq_commerce_downloads_order_item UNIQUE (order_item_id),
    CONSTRAINT ck_commerce_downloads_count CHECK (download_count >= 0 AND max_download_count > 0),
    CONSTRAINT fk_commerce_downloads_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_commerce_downloads_order
        FOREIGN KEY (order_id) REFERENCES commerce_orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_commerce_downloads_order_item
        FOREIGN KEY (order_item_id) REFERENCES commerce_order_items (id) ON DELETE CASCADE,
    CONSTRAINT fk_commerce_downloads_file
        FOREIGN KEY (file_id) REFERENCES stored_files (id) ON DELETE RESTRICT
);

CREATE INDEX ix_commerce_downloads_order ON commerce_order_downloads (order_id);
