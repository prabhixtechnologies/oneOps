-- =============================================================================
-- V9  Marketing site: leads, newsletter subscribers, job applications
-- =============================================================================
-- These tables back the public site and are deliberately NOT tenant-scoped:
-- they belong to Prabhix itself, not to a customer organization.
-- =============================================================================

CREATE TABLE site_leads (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version             bigint      NOT NULL DEFAULT 0,
    name                varchar(160) NOT NULL,
    email               citext      NOT NULL,
    company             varchar(200),
    phone               varchar(32),
    employee_count      varchar(32),
    -- PLATFORM | MOBISTACK | CUSTOM_SOFTWARE | CLOUD_DEVOPS | AI_ML |
    -- MOBILE_APPS | CONSULTING | PARTNERSHIP | OTHER
    interest            varchar(32) NOT NULL DEFAULT 'OTHER',
    message             varchar(4000) NOT NULL,
    -- Which page or campaign produced the lead.
    source              varchar(80) NOT NULL DEFAULT 'contact-form',
    utm                 jsonb       NOT NULL DEFAULT '{}'::jsonb,
    referrer            varchar(500),
    ip_address          varchar(45),
    user_agent          varchar(500),
    -- NEW | CONTACTED | QUALIFIED | DEMO_BOOKED | WON | LOST | SPAM
    status              varchar(16) NOT NULL DEFAULT 'NEW',
    -- Set once someone on our side picks it up.
    assigned_to         uuid,
    internal_notes      varchar(4000),
    -- Thread opened in the sales inbox, linking a lead to its conversation.
    thread_id           uuid,
    contacted_at        timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_by          uuid,

    CONSTRAINT ck_site_leads_interest CHECK (interest IN
        ('PLATFORM', 'MOBISTACK', 'CUSTOM_SOFTWARE', 'CLOUD_DEVOPS', 'AI_ML',
         'MOBILE_APPS', 'CONSULTING', 'PARTNERSHIP', 'OTHER')),
    CONSTRAINT ck_site_leads_status CHECK (status IN
        ('NEW', 'CONTACTED', 'QUALIFIED', 'DEMO_BOOKED', 'WON', 'LOST', 'SPAM')),
    CONSTRAINT fk_site_leads_assignee
        FOREIGN KEY (assigned_to) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_site_leads_thread
        FOREIGN KEY (thread_id) REFERENCES mail_threads (id) ON DELETE SET NULL
);

CREATE INDEX ix_site_leads_status   ON site_leads (status, created_at DESC);
CREATE INDEX ix_site_leads_email    ON site_leads (email, created_at DESC);
CREATE INDEX ix_site_leads_assignee ON site_leads (assigned_to);
CREATE INDEX ix_site_leads_thread   ON site_leads (thread_id);
-- Cheap flood check: how many submissions from this address recently.
CREATE INDEX ix_site_leads_recent   ON site_leads (created_at DESC);


CREATE TABLE site_subscribers (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version             bigint      NOT NULL DEFAULT 0,
    email               citext      NOT NULL,
    name                varchar(160),
    source              varchar(80) NOT NULL DEFAULT 'footer',
    -- PENDING | CONFIRMED | UNSUBSCRIBED | BOUNCED
    -- Double opt-in: PENDING until the confirmation link is opened, which is
    -- what keeps this list compliant and deliverable.
    status              varchar(16) NOT NULL DEFAULT 'PENDING',
    confirm_token_hash  varchar(64),
    confirmed_at        timestamptz,
    unsubscribed_at     timestamptz,
    unsubscribe_token   varchar(80) NOT NULL,
    ip_address          varchar(45),
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_by          uuid,

    CONSTRAINT uq_site_subscribers_email UNIQUE (email),
    CONSTRAINT uq_site_subscribers_unsub_token UNIQUE (unsubscribe_token),
    CONSTRAINT ck_site_subscribers_status
        CHECK (status IN ('PENDING', 'CONFIRMED', 'UNSUBSCRIBED', 'BOUNCED'))
);

CREATE INDEX ix_site_subscribers_status ON site_subscribers (status, created_at DESC);
CREATE INDEX ix_site_subscribers_confirm
    ON site_subscribers (confirm_token_hash) WHERE status = 'PENDING';


CREATE TABLE site_job_roles (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version             bigint      NOT NULL DEFAULT 0,
    slug                varchar(120) NOT NULL,
    title               varchar(200) NOT NULL,
    department          varchar(120) NOT NULL,
    location            varchar(160) NOT NULL,
    -- FULL_TIME | PART_TIME | CONTRACT | INTERNSHIP
    employment_type     varchar(24) NOT NULL DEFAULT 'FULL_TIME',
    -- ONSITE | HYBRID | REMOTE
    work_mode           varchar(16) NOT NULL DEFAULT 'HYBRID',
    experience_range    varchar(60),
    salary_range        varchar(80),
    summary             varchar(1000) NOT NULL,
    description_md      text        NOT NULL,
    -- OPEN | PAUSED | CLOSED
    status              varchar(16) NOT NULL DEFAULT 'OPEN',
    published_at        timestamptz,
    closed_at          timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_by          uuid,

    CONSTRAINT uq_site_job_roles_slug UNIQUE (slug),
    CONSTRAINT ck_site_job_roles_employment CHECK (employment_type IN
        ('FULL_TIME', 'PART_TIME', 'CONTRACT', 'INTERNSHIP')),
    CONSTRAINT ck_site_job_roles_mode CHECK (work_mode IN ('ONSITE', 'HYBRID', 'REMOTE')),
    CONSTRAINT ck_site_job_roles_status CHECK (status IN ('OPEN', 'PAUSED', 'CLOSED'))
);

CREATE INDEX ix_site_job_roles_open ON site_job_roles (published_at DESC)
    WHERE status = 'OPEN';


CREATE TABLE site_job_applications (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version             bigint      NOT NULL DEFAULT 0,
    role_id             uuid,
    -- Kept even when the role row is removed, so applications are never orphaned
    -- without context.
    role_slug           varchar(120) NOT NULL,
    name                varchar(160) NOT NULL,
    email               citext      NOT NULL,
    phone               varchar(32),
    portfolio_url       varchar(500),
    linkedin_url        varchar(500),
    cover_letter        varchar(8000),
    resume_file_id      uuid,
    -- RECEIVED | SCREENING | INTERVIEWING | OFFERED | HIRED | REJECTED | WITHDRAWN
    status              varchar(16) NOT NULL DEFAULT 'RECEIVED',
    internal_notes      varchar(4000),
    ip_address          varchar(45),
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_by          uuid,

    CONSTRAINT ck_site_applications_status CHECK (status IN
        ('RECEIVED', 'SCREENING', 'INTERVIEWING', 'OFFERED', 'HIRED', 'REJECTED', 'WITHDRAWN')),
    CONSTRAINT fk_site_applications_role
        FOREIGN KEY (role_id) REFERENCES site_job_roles (id) ON DELETE SET NULL,
    CONSTRAINT fk_site_applications_resume
        FOREIGN KEY (resume_file_id) REFERENCES stored_files (id) ON DELETE SET NULL
);

-- One application per address per role; re-applying updates the existing row.
CREATE UNIQUE INDEX uq_site_applications_role_email
    ON site_job_applications (role_slug, email);
CREATE INDEX ix_site_applications_role   ON site_job_applications (role_id, created_at DESC);
CREATE INDEX ix_site_applications_status ON site_job_applications (status, created_at DESC);
CREATE INDEX ix_site_applications_resume ON site_job_applications (resume_file_id);
