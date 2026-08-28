-- =============================================================================
-- V42  Commerce catalog server-side search and filtering indexes
--
-- pg_trgm on name/tagline supports storefront ILIKE-style search with a GIN
-- index that stays useful at catalog scale. B-tree composites cover the exact
-- filter/sort paths the public list endpoint uses (type, category, featured,
-- price) without forcing a seq scan over active products.
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX ix_commerce_products_name_trgm
    ON commerce_products USING gin (name gin_trgm_ops)
    WHERE deleted_at IS NULL AND status = 'ACTIVE';

CREATE INDEX ix_commerce_products_tagline_trgm
    ON commerce_products USING gin (tagline gin_trgm_ops)
    WHERE deleted_at IS NULL AND status = 'ACTIVE' AND tagline IS NOT NULL;

CREATE INDEX ix_commerce_products_public_list
    ON commerce_products (organization_id, status, featured DESC, sort_order ASC, created_at DESC, id DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_commerce_products_public_type
    ON commerce_products (organization_id, product_type, status, featured DESC, sort_order ASC)
    WHERE deleted_at IS NULL AND status = 'ACTIVE';

CREATE INDEX ix_commerce_category_links_product
    ON commerce_product_category_links (category_id, product_id);
