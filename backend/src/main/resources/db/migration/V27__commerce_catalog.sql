-- =============================================================================
-- V27  D2C commerce catalog (mixed product types)
-- =============================================================================
-- Modelling: shared `products` + `product_variants` for queryable price/inventory.
-- Type-specific SKU fields live on variants (billing_interval, download_file_id, etc.)
-- because checkout always resolves a variant. Flexible marketing metadata uses JSONB
-- `attributes` on products so we do not sprawl nullable columns for rare fields.
-- =============================================================================

CREATE TABLE commerce_products (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version                 bigint      NOT NULL DEFAULT 0,
    organization_id         uuid        NOT NULL,
    slug                    varchar(120) NOT NULL,
    name                    varchar(200) NOT NULL,
    tagline                 varchar(300),
    description             text,
    -- SUBSCRIPTION | DIGITAL | SERVICE | PHYSICAL
    product_type            varchar(16) NOT NULL,
    -- DRAFT | ACTIVE | ARCHIVED
    status                  varchar(16) NOT NULL DEFAULT 'DRAFT',
    featured                boolean     NOT NULL DEFAULT false,
    sort_order              integer     NOT NULL DEFAULT 100,
    hero_image_file_id      uuid,
    gallery_file_ids        jsonb       NOT NULL DEFAULT '[]'::jsonb,
    seo_title               varchar(200),
    seo_description         varchar(500),
    tax_code                varchar(20),
    hsn_code                varchar(20),
    attributes              jsonb       NOT NULL DEFAULT '{}'::jsonb,
    published_at            timestamptz,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    created_by              uuid,
    updated_by              uuid,
    deleted_at              timestamptz,

    CONSTRAINT uq_commerce_products_org_slug UNIQUE (organization_id, slug),
    CONSTRAINT ck_commerce_products_type CHECK (product_type IN ('SUBSCRIPTION', 'DIGITAL', 'SERVICE', 'PHYSICAL')),
    CONSTRAINT ck_commerce_products_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    CONSTRAINT fk_commerce_products_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_commerce_products_hero_image
        FOREIGN KEY (hero_image_file_id) REFERENCES stored_files (id) ON DELETE SET NULL
);

CREATE INDEX ix_commerce_products_org_status_featured
    ON commerce_products (organization_id, status, featured DESC, sort_order ASC)
    WHERE deleted_at IS NULL;
CREATE INDEX ix_commerce_products_org_type
    ON commerce_products (organization_id, product_type, status)
    WHERE deleted_at IS NULL;


CREATE TABLE commerce_product_variants (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version                 bigint      NOT NULL DEFAULT 0,
    organization_id         uuid        NOT NULL,
    product_id              uuid        NOT NULL,
    name                    varchar(200) NOT NULL,
    sku                     varchar(80) NOT NULL,
    price_minor             bigint      NOT NULL,
    compare_at_price_minor  bigint,
    currency                varchar(3)  NOT NULL DEFAULT 'INR',
    track_inventory         boolean     NOT NULL DEFAULT false,
    stock_on_hand           integer     NOT NULL DEFAULT 0,
    stock_reserved          integer     NOT NULL DEFAULT 0,
    weight_grams            integer,
    length_mm               integer,
    width_mm                integer,
    height_mm               integer,
    -- SUBSCRIPTION: MONTHLY | ANNUAL | ONE_TIME
    billing_interval        varchar(16),
    download_file_id        uuid,
    license_terms           text,
    service_duration_days   integer,
    delivery_sla_days       integer,
    sort_order              integer     NOT NULL DEFAULT 100,
    active                  boolean     NOT NULL DEFAULT true,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    created_by              uuid,
    updated_by              uuid,
    deleted_at              timestamptz,

    CONSTRAINT uq_commerce_variants_org_sku UNIQUE (organization_id, sku),
    CONSTRAINT ck_commerce_variants_price CHECK (price_minor >= 0),
    CONSTRAINT ck_commerce_variants_stock CHECK (stock_on_hand >= 0 AND stock_reserved >= 0),
    CONSTRAINT fk_commerce_variants_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_commerce_variants_product
        FOREIGN KEY (product_id) REFERENCES commerce_products (id) ON DELETE CASCADE,
    CONSTRAINT fk_commerce_variants_download
        FOREIGN KEY (download_file_id) REFERENCES stored_files (id) ON DELETE SET NULL
);

CREATE INDEX ix_commerce_variants_product
    ON commerce_product_variants (product_id, sort_order ASC)
    WHERE deleted_at IS NULL;
CREATE INDEX ix_commerce_variants_org_product
    ON commerce_product_variants (organization_id, product_id)
    WHERE deleted_at IS NULL AND active = true;


CREATE TABLE commerce_product_media (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version                 bigint      NOT NULL DEFAULT 0,
    organization_id         uuid        NOT NULL,
    product_id              uuid        NOT NULL,
    file_id                 uuid        NOT NULL,
    alt_text                varchar(300),
    sort_order              integer     NOT NULL DEFAULT 100,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    created_by              uuid,
    updated_by              uuid,

    CONSTRAINT fk_commerce_media_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_commerce_media_product
        FOREIGN KEY (product_id) REFERENCES commerce_products (id) ON DELETE CASCADE,
    CONSTRAINT fk_commerce_media_file
        FOREIGN KEY (file_id) REFERENCES stored_files (id) ON DELETE CASCADE
);

CREATE INDEX ix_commerce_media_product ON commerce_product_media (product_id, sort_order);


CREATE TABLE commerce_product_categories (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version                 bigint      NOT NULL DEFAULT 0,
    organization_id         uuid        NOT NULL,
    slug                    varchar(120) NOT NULL,
    name                    varchar(160) NOT NULL,
    description             varchar(500),
    sort_order              integer     NOT NULL DEFAULT 100,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    created_by              uuid,
    updated_by              uuid,
    deleted_at              timestamptz,

    CONSTRAINT uq_commerce_categories_org_slug UNIQUE (organization_id, slug),
    CONSTRAINT fk_commerce_categories_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE
);


CREATE TABLE commerce_product_category_links (
    product_id              uuid NOT NULL,
    category_id             uuid NOT NULL,
    PRIMARY KEY (product_id, category_id),
    CONSTRAINT fk_commerce_category_links_product
        FOREIGN KEY (product_id) REFERENCES commerce_products (id) ON DELETE CASCADE,
    CONSTRAINT fk_commerce_category_links_category
        FOREIGN KEY (category_id) REFERENCES commerce_product_categories (id) ON DELETE CASCADE
);

CREATE INDEX ix_commerce_category_links_category ON commerce_product_category_links (category_id);
