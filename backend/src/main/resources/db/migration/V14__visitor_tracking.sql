-- =============================================================================
-- V14  Visitor tracking: anonymous visitors, sessions, page views, events
-- =============================================================================

CREATE TABLE visitors (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version                 bigint      NOT NULL DEFAULT 0,
    organization_id         uuid        NOT NULL,
    -- Client-issued durable id from cookie/localStorage.
    external_key            varchar(64) NOT NULL,
    -- FULL | MINIMAL | DELETED
    consent_status          varchar(16) NOT NULL DEFAULT 'FULL',
    first_seen_at           timestamptz NOT NULL DEFAULT now(),
    last_seen_at            timestamptz NOT NULL DEFAULT now(),
    -- Set when the visitor identifies via form, chat, or login.
    identified_user_id      uuid,
    email                   citext,
    display_name            varchar(160),
    -- First-touch UTM/campaign attribution, captured once.
    first_touch_utm         jsonb       NOT NULL DEFAULT '{}'::jsonb,
    first_touch_referrer    varchar(500),
    merged_into_id          uuid,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    created_by              uuid,
    updated_by              uuid,
    deleted_at              timestamptz,

    CONSTRAINT uq_visitors_org_external UNIQUE (organization_id, external_key),
    CONSTRAINT ck_visitors_consent CHECK (consent_status IN ('FULL', 'MINIMAL', 'DELETED')),
    CONSTRAINT fk_visitors_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_visitors_identified_user
        FOREIGN KEY (identified_user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_visitors_merged_into
        FOREIGN KEY (merged_into_id) REFERENCES visitors (id) ON DELETE SET NULL
);

CREATE INDEX ix_visitors_org_last_seen
    ON visitors (organization_id, last_seen_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX ix_visitors_org_email
    ON visitors (organization_id, email) WHERE email IS NOT NULL AND deleted_at IS NULL;
CREATE INDEX ix_visitors_org_user
    ON visitors (organization_id, identified_user_id) WHERE identified_user_id IS NOT NULL;


CREATE TABLE visitor_sessions (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version                 bigint      NOT NULL DEFAULT 0,
    organization_id         uuid        NOT NULL,
    visitor_id              uuid        NOT NULL,
    started_at              timestamptz NOT NULL DEFAULT now(),
    ended_at                timestamptz,
    duration_seconds        integer,
    entry_url               varchar(2000),
    exit_url                varchar(2000),
    referrer                varchar(500),
    device_type             varchar(32),
    browser                 varchar(80),
    os                      varchar(80),
    screen_width            integer,
    screen_height           integer,
    language                varchar(16),
    timezone                varchar(64),
    ip_address              varchar(45),
    geo_country             varchar(2),
    geo_region              varchar(80),
    geo_city                varchar(120),
    utm                     jsonb       NOT NULL DEFAULT '{}'::jsonb,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    created_by              uuid,
    updated_by              uuid,

    CONSTRAINT fk_visitor_sessions_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_visitor_sessions_visitor
        FOREIGN KEY (visitor_id) REFERENCES visitors (id) ON DELETE CASCADE
);

CREATE INDEX ix_visitor_sessions_org_started
    ON visitor_sessions (organization_id, started_at DESC);
CREATE INDEX ix_visitor_sessions_visitor
    ON visitor_sessions (visitor_id, started_at DESC);


CREATE TABLE visitor_page_views (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version                 bigint      NOT NULL DEFAULT 0,
    organization_id         uuid        NOT NULL,
    visitor_id              uuid        NOT NULL,
    session_id              uuid        NOT NULL,
    url                     varchar(2000) NOT NULL,
    path                    varchar(500) NOT NULL,
    title                   varchar(500),
    referrer                varchar(500),
    viewed_at               timestamptz NOT NULL DEFAULT now(),
    duration_ms             integer,
    is_entry                boolean     NOT NULL DEFAULT false,
    is_exit                 boolean     NOT NULL DEFAULT false,
    created_at              timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT fk_visitor_page_views_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_visitor_page_views_visitor
        FOREIGN KEY (visitor_id) REFERENCES visitors (id) ON DELETE CASCADE,
    CONSTRAINT fk_visitor_page_views_session
        FOREIGN KEY (session_id) REFERENCES visitor_sessions (id) ON DELETE CASCADE
);

CREATE INDEX ix_visitor_page_views_org_viewed
    ON visitor_page_views (organization_id, viewed_at DESC);
CREATE INDEX ix_visitor_page_views_session
    ON visitor_page_views (session_id, viewed_at ASC);
CREATE INDEX ix_visitor_page_views_org_path
    ON visitor_page_views (organization_id, path, viewed_at DESC);


CREATE TABLE visitor_events (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version                 bigint      NOT NULL DEFAULT 0,
    organization_id         uuid        NOT NULL,
    visitor_id              uuid        NOT NULL,
    session_id              uuid,
    event_name              varchar(120) NOT NULL,
    properties              jsonb       NOT NULL DEFAULT '{}'::jsonb,
    occurred_at             timestamptz NOT NULL DEFAULT now(),
    created_at              timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT fk_visitor_events_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_visitor_events_visitor
        FOREIGN KEY (visitor_id) REFERENCES visitors (id) ON DELETE CASCADE,
    CONSTRAINT fk_visitor_events_session
        FOREIGN KEY (session_id) REFERENCES visitor_sessions (id) ON DELETE SET NULL
);

CREATE INDEX ix_visitor_events_org_occurred
    ON visitor_events (organization_id, occurred_at DESC);
CREATE INDEX ix_visitor_events_org_name
    ON visitor_events (organization_id, event_name, occurred_at DESC);
CREATE INDEX ix_visitor_events_visitor
    ON visitor_events (visitor_id, occurred_at DESC);


CREATE TABLE visitor_daily_aggregates (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id         uuid        NOT NULL,
    aggregate_date          date        NOT NULL,
    -- PAGE_VIEWS | SESSIONS | UNIQUE_VISITORS | EVENT
    metric_type             varchar(24) NOT NULL,
    dimension               varchar(500) NOT NULL DEFAULT '',
    count_value             bigint      NOT NULL DEFAULT 0,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT uq_visitor_daily_aggregates UNIQUE (organization_id, aggregate_date, metric_type, dimension),
    CONSTRAINT ck_visitor_daily_aggregates_metric CHECK (metric_type IN
        ('PAGE_VIEWS', 'SESSIONS', 'UNIQUE_VISITORS', 'EVENT')),
    CONSTRAINT fk_visitor_daily_aggregates_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE
);

CREATE INDEX ix_visitor_daily_aggregates_org_date
    ON visitor_daily_aggregates (organization_id, aggregate_date DESC);
