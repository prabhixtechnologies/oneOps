-- =============================================================================
-- V7  Transactional engine: templates, outbox, suppressions, tracking, bounces
-- =============================================================================

-- -----------------------------------------------------------------------------
-- mail_templates: versioned, localisable transactional templates.
--
-- organization_id NULL is a platform default that every tenant inherits until
-- they save their own copy, so branding is customisable without seeding dozens
-- of rows per signup.
-- -----------------------------------------------------------------------------
CREATE TABLE mail_templates (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version         bigint      NOT NULL DEFAULT 0,
    organization_id uuid,
    -- Dotted key: auth.magic-link, billing.invoice, org.invite
    template_key    varchar(80) NOT NULL,
    locale          varchar(16) NOT NULL DEFAULT 'en',
    name            varchar(160) NOT NULL,
    description     varchar(500),
    subject         varchar(500) NOT NULL,
    body_html       text        NOT NULL,
    -- Plain-text alternative. Auto-derived from HTML when left null.
    body_text       text,
    -- Declared variables: name, required, example. Render fails fast when a
    -- required variable is missing, rather than emailing "Hello ${name}".
    variables       jsonb       NOT NULL DEFAULT '[]'::jsonb,
    -- TRANSACTIONAL mail is never tracked and never suppressible by preference;
    -- MARKETING mail honours both.
    category        varchar(24) NOT NULL DEFAULT 'TRANSACTIONAL',
    tracking_enabled boolean    NOT NULL DEFAULT false,
    enabled         boolean     NOT NULL DEFAULT true,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_by      uuid,

    CONSTRAINT ck_mail_templates_category
        CHECK (category IN ('TRANSACTIONAL', 'NOTIFICATION', 'MARKETING')),
    CONSTRAINT ck_mail_templates_variables_array CHECK (jsonb_typeof(variables) = 'array'),
    CONSTRAINT fk_mail_templates_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uq_mail_templates_platform
    ON mail_templates (template_key, locale) WHERE organization_id IS NULL;
CREATE UNIQUE INDEX uq_mail_templates_org
    ON mail_templates (organization_id, template_key, locale)
    WHERE organization_id IS NOT NULL;
CREATE INDEX ix_mail_templates_org ON mail_templates (organization_id);


-- -----------------------------------------------------------------------------
-- mail_outbox: the only way mail leaves this system.
--
-- Services insert a row inside their own transaction and return. A worker drains
-- it. That decoupling is what makes a provider outage a delay rather than a
-- failed user action, and it makes retries safe.
-- -----------------------------------------------------------------------------
CREATE TABLE mail_outbox (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version             bigint      NOT NULL DEFAULT 0,
    -- Null for platform mail that predates organization selection, e.g. a magic
    -- link to someone who has not joined anything yet.
    organization_id     uuid,
    mailbox_id          uuid,
    -- Set when this send is an agent reply, linking outbox to the thread.
    thread_id           uuid,
    message_id          uuid,
    -- Rendered from a template, or supplied directly for agent replies.
    template_key        varchar(80),
    locale              varchar(16) NOT NULL DEFAULT 'en',
    template_variables  jsonb       NOT NULL DEFAULT '{}'::jsonb,
    from_address        citext      NOT NULL,
    from_name           varchar(200),
    reply_to            citext,
    to_addresses        jsonb       NOT NULL DEFAULT '[]'::jsonb,
    cc_addresses        jsonb       NOT NULL DEFAULT '[]'::jsonb,
    bcc_addresses       jsonb       NOT NULL DEFAULT '[]'::jsonb,
    subject             varchar(500),
    body_html           text,
    body_text           text,
    -- Extra headers, including In-Reply-To and References for threaded replies.
    headers             jsonb       NOT NULL DEFAULT '{}'::jsonb,
    attachment_ids      jsonb       NOT NULL DEFAULT '[]'::jsonb,
    -- Idempotency key. Makes "send the invoice for order X" exactly-once even
    -- across a redeploy mid-transaction.
    dedupe_key          varchar(200),
    -- Lower runs first: 0 for auth mail a user is waiting on, 100 for bulk.
    priority            integer     NOT NULL DEFAULT 50,
    -- PENDING | CLAIMED | SENDING | SENT | FAILED | DEAD | CANCELLED | SUPPRESSED
    status              varchar(16) NOT NULL DEFAULT 'PENDING',
    attempts            integer     NOT NULL DEFAULT 0,
    max_attempts        integer     NOT NULL DEFAULT 6,
    scheduled_at        timestamptz NOT NULL DEFAULT now(),
    next_attempt_at     timestamptz,
    claimed_at          timestamptz,
    claimed_by          varchar(80),
    sent_at             timestamptz,
    last_error          varchar(2000),
    -- Which MailTransport handled it, for provider-level failure analysis.
    transport_used      varchar(32),
    provider_message_id varchar(255),
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_by          uuid,

    CONSTRAINT ck_mail_outbox_status CHECK (status IN
        ('PENDING', 'CLAIMED', 'SENDING', 'SENT', 'FAILED', 'DEAD', 'CANCELLED', 'SUPPRESSED')),
    CONSTRAINT ck_mail_outbox_attempts CHECK (attempts >= 0 AND max_attempts > 0),
    CONSTRAINT ck_mail_outbox_priority CHECK (priority BETWEEN 0 AND 100),
    CONSTRAINT ck_mail_outbox_recipients CHECK (jsonb_typeof(to_addresses) = 'array'),
    -- Either a template to render or a body to send; never neither.
    CONSTRAINT ck_mail_outbox_payload
        CHECK (template_key IS NOT NULL OR body_html IS NOT NULL OR body_text IS NOT NULL),
    CONSTRAINT fk_mail_outbox_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_mail_outbox_mailbox
        FOREIGN KEY (mailbox_id) REFERENCES mail_mailboxes (id) ON DELETE SET NULL,
    CONSTRAINT fk_mail_outbox_thread
        FOREIGN KEY (thread_id) REFERENCES mail_threads (id) ON DELETE CASCADE,
    CONSTRAINT fk_mail_outbox_message
        FOREIGN KEY (message_id) REFERENCES mail_messages (id) ON DELETE SET NULL
);

CREATE UNIQUE INDEX uq_mail_outbox_dedupe
    ON mail_outbox (dedupe_key) WHERE dedupe_key IS NOT NULL;

-- The drain query. Narrow and partial so it stays small no matter how much
-- history accumulates in SENT rows.
CREATE INDEX ix_mail_outbox_claimable
    ON mail_outbox (priority, scheduled_at, id)
    WHERE status = 'PENDING';
CREATE INDEX ix_mail_outbox_retryable
    ON mail_outbox (next_attempt_at)
    WHERE status = 'FAILED';
-- Finds rows a crashed worker left claimed, so they can be released.
CREATE INDEX ix_mail_outbox_stuck
    ON mail_outbox (claimed_at)
    WHERE status IN ('CLAIMED', 'SENDING');
CREATE INDEX ix_mail_outbox_org    ON mail_outbox (organization_id, created_at DESC);
CREATE INDEX ix_mail_outbox_thread ON mail_outbox (thread_id);
CREATE INDEX ix_mail_outbox_message ON mail_outbox (message_id);
CREATE INDEX ix_mail_outbox_mailbox ON mail_outbox (mailbox_id);
CREATE INDEX ix_mail_outbox_dead    ON mail_outbox (created_at DESC) WHERE status = 'DEAD';

COMMENT ON COLUMN mail_outbox.dedupe_key IS
    'Unique when present. Insert with ON CONFLICT DO NOTHING to make a send idempotent.';
COMMENT ON COLUMN mail_outbox.claimed_by IS
    'Worker instance id. With claimed_at, lets a sweeper reclaim work from a dead worker.';


-- -----------------------------------------------------------------------------
-- mail_suppressions: addresses we must stop mailing.
--
-- Checked at drain time, not at enqueue time, because an address can become
-- suppressed while a message sits in the queue.
-- -----------------------------------------------------------------------------
CREATE TABLE mail_suppressions (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version         bigint      NOT NULL DEFAULT 0,
    -- Null suppresses platform-wide, e.g. a confirmed spam complaint.
    organization_id uuid,
    address         citext      NOT NULL,
    -- HARD_BOUNCE | SOFT_BOUNCE | COMPLAINT | UNSUBSCRIBE | MANUAL | INVALID
    reason          varchar(24) NOT NULL,
    detail          varchar(1000),
    -- Soft bounces expire; hard bounces and complaints do not.
    expires_at      timestamptz,
    bounce_count    integer     NOT NULL DEFAULT 1,
    last_bounce_at  timestamptz NOT NULL DEFAULT now(),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_by      uuid,

    CONSTRAINT ck_mail_suppressions_reason CHECK (reason IN
        ('HARD_BOUNCE', 'SOFT_BOUNCE', 'COMPLAINT', 'UNSUBSCRIBE', 'MANUAL', 'INVALID')),
    CONSTRAINT ck_mail_suppressions_count CHECK (bounce_count > 0),
    CONSTRAINT fk_mail_suppressions_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uq_mail_suppressions_platform
    ON mail_suppressions (address) WHERE organization_id IS NULL;
CREATE UNIQUE INDEX uq_mail_suppressions_org
    ON mail_suppressions (organization_id, address) WHERE organization_id IS NOT NULL;
CREATE INDEX ix_mail_suppressions_address ON mail_suppressions (address);
CREATE INDEX ix_mail_suppressions_expiry  ON mail_suppressions (expires_at)
    WHERE expires_at IS NOT NULL;


-- -----------------------------------------------------------------------------
-- mail_delivery_events: provider feedback and engagement, append-only.
-- -----------------------------------------------------------------------------
CREATE TABLE mail_delivery_events (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     uuid,
    outbox_id           uuid,
    message_id          uuid,
    address             citext      NOT NULL,
    -- QUEUED | SENT | DELIVERED | DEFERRED | BOUNCED | COMPLAINED | OPENED |
    -- CLICKED | UNSUBSCRIBED | FAILED
    event_type          varchar(24) NOT NULL,
    -- Provider's own status code, e.g. an SMTP 5xx enhanced status.
    provider_code       varchar(32),
    detail              varchar(1000),
    -- Populated for CLICKED.
    clicked_url         varchar(2000),
    user_agent          varchar(500),
    ip_address          varchar(45),
    occurred_at         timestamptz NOT NULL DEFAULT now(),
    created_at          timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT ck_delivery_events_type CHECK (event_type IN
        ('QUEUED', 'SENT', 'DELIVERED', 'DEFERRED', 'BOUNCED', 'COMPLAINED',
         'OPENED', 'CLICKED', 'UNSUBSCRIBED', 'FAILED')),
    CONSTRAINT fk_delivery_events_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_delivery_events_outbox
        FOREIGN KEY (outbox_id) REFERENCES mail_outbox (id) ON DELETE CASCADE,
    CONSTRAINT fk_delivery_events_message
        FOREIGN KEY (message_id) REFERENCES mail_messages (id) ON DELETE CASCADE
);

CREATE INDEX ix_delivery_events_outbox  ON mail_delivery_events (outbox_id, occurred_at);
CREATE INDEX ix_delivery_events_address ON mail_delivery_events (address, occurred_at DESC);
CREATE INDEX ix_delivery_events_org
    ON mail_delivery_events (organization_id, event_type, occurred_at DESC);
CREATE INDEX ix_delivery_events_message ON mail_delivery_events (message_id);


-- -----------------------------------------------------------------------------
-- Platform default templates. organization_id NULL, inherited by every tenant.
-- Kept plain and inline-styled: email clients ignore stylesheets.
-- -----------------------------------------------------------------------------
INSERT INTO mail_templates
    (organization_id, template_key, locale, name, description, subject, body_html, variables, category)
VALUES
(NULL, 'auth.magic-link', 'en', 'Sign-in link',
 'Passwordless sign-in link',
 'Your Prabhix sign-in link',
 '<div style="font-family:''Plus Jakarta Sans'',Segoe UI,Arial,sans-serif;max-width:520px;margin:0 auto;padding:32px 24px;color:#0B0B12">'
 '<h1 style="font-size:20px;margin:0 0 16px">Sign in to Prabhix</h1>'
 '<p style="font-size:15px;line-height:1.6;margin:0 0 24px">Hello <span th:text="${name}">there</span>, use the button below to sign in. This link works once and expires in <span th:text="${expiryMinutes}">15</span> minutes.</p>'
 '<p style="margin:0 0 28px"><a th:href="${link}" href="#" style="background:#7C3AED;color:#fff;text-decoration:none;padding:12px 22px;border-radius:8px;font-size:15px;display:inline-block">Sign in</a></p>'
 '<p style="font-size:13px;color:#6b6b7b;line-height:1.6;margin:0">If you did not request this, you can safely ignore this email. Nobody can sign in without opening the link.</p>'
 '</div>',
 '[{"name":"name","required":true,"example":"Priya"},'
 '{"name":"link","required":true,"example":"https://app.prabhixtechnologies.com/auth/magic?token=..."},'
 '{"name":"expiryMinutes","required":true,"example":"15"}]'::jsonb,
 'TRANSACTIONAL'),

(NULL, 'auth.otp', 'en', 'One-time code',
 'Numeric code for email or SMS verification',
 'Your Prabhix verification code',
 '<div style="font-family:''Plus Jakarta Sans'',Segoe UI,Arial,sans-serif;max-width:520px;margin:0 auto;padding:32px 24px;color:#0B0B12">'
 '<h1 style="font-size:20px;margin:0 0 16px">Your verification code</h1>'
 '<p style="font-size:32px;letter-spacing:8px;font-weight:700;margin:0 0 20px;color:#7C3AED" th:text="${code}">000000</p>'
 '<p style="font-size:14px;color:#6b6b7b;line-height:1.6;margin:0">Expires in <span th:text="${expiryMinutes}">10</span> minutes. Never share this code with anyone, including someone claiming to be from Prabhix.</p>'
 '</div>',
 '[{"name":"code","required":true,"example":"482913"},'
 '{"name":"expiryMinutes","required":true,"example":"10"}]'::jsonb,
 'TRANSACTIONAL'),

(NULL, 'org.invite', 'en', 'Organization invitation',
 'Invite someone to join an organization',
 '[(${inviterName})] invited you to [(${organizationName})] on Prabhix',
 '<div style="font-family:''Plus Jakarta Sans'',Segoe UI,Arial,sans-serif;max-width:520px;margin:0 auto;padding:32px 24px;color:#0B0B12">'
 '<h1 style="font-size:20px;margin:0 0 16px">You have been invited</h1>'
 '<p style="font-size:15px;line-height:1.6;margin:0 0 20px"><strong th:text="${inviterName}">A colleague</strong> invited you to join <strong th:text="${organizationName}">their organization</strong> on Prabhix as <span th:text="${roleName}">a member</span>.</p>'
 '<blockquote th:if="${message}" style="border-left:3px solid #7C3AED;margin:0 0 20px;padding:4px 0 4px 16px;font-size:14px;color:#3f3f52" th:text="${message}"></blockquote>'
 '<p style="margin:0 0 28px"><a th:href="${link}" href="#" style="background:#7C3AED;color:#fff;text-decoration:none;padding:12px 22px;border-radius:8px;font-size:15px;display:inline-block">Accept invitation</a></p>'
 '<p style="font-size:13px;color:#6b6b7b;margin:0">This invitation expires on <span th:text="${expiresOn}">a week from now</span>.</p>'
 '</div>',
 '[{"name":"inviterName","required":true,"example":"Priya Sharma"},'
 '{"name":"organizationName","required":true,"example":"Acme Corp"},'
 '{"name":"roleName","required":true,"example":"Agent"},'
 '{"name":"link","required":true,"example":"https://app.prabhixtechnologies.com/invite/..."},'
 '{"name":"expiresOn","required":true,"example":"5 September 2026"},'
 '{"name":"message","required":false,"example":"Looking forward to having you on the team."}]'::jsonb,
 'TRANSACTIONAL'),

(NULL, 'billing.payment-succeeded', 'en', 'Payment receipt',
 'Confirmation after a successful payment',
 'Payment received — invoice [(${invoiceNumber})]',
 '<div style="font-family:''Plus Jakarta Sans'',Segoe UI,Arial,sans-serif;max-width:520px;margin:0 auto;padding:32px 24px;color:#0B0B12">'
 '<h1 style="font-size:20px;margin:0 0 16px">Payment received</h1>'
 '<p style="font-size:15px;line-height:1.6;margin:0 0 20px">Thank you. We have received <strong th:text="${amount}">₹0</strong> for <span th:text="${planName}">your plan</span>.</p>'
 '<table style="font-size:14px;border-collapse:collapse;margin:0 0 24px">'
 '<tr><td style="padding:4px 16px 4px 0;color:#6b6b7b">Invoice</td><td th:text="${invoiceNumber}">PBX-0001</td></tr>'
 '<tr><td style="padding:4px 16px 4px 0;color:#6b6b7b">Paid on</td><td th:text="${paidOn}">today</td></tr>'
 '<tr><td style="padding:4px 16px 4px 0;color:#6b6b7b">Next renewal</td><td th:text="${nextRenewal}">in a month</td></tr>'
 '</table>'
 '<p style="margin:0"><a th:href="${invoiceUrl}" href="#" style="color:#7C3AED;font-size:14px">Download invoice</a></p>'
 '</div>',
 '[{"name":"amount","required":true,"example":"₹2,499.00"},'
 '{"name":"planName","required":true,"example":"Growth (monthly)"},'
 '{"name":"invoiceNumber","required":true,"example":"PBX-2026-0042"},'
 '{"name":"paidOn","required":true,"example":"27 August 2026"},'
 '{"name":"nextRenewal","required":true,"example":"27 September 2026"},'
 '{"name":"invoiceUrl","required":true,"example":"https://app.prabhixtechnologies.com/billing/invoices/..."}]'::jsonb,
 'TRANSACTIONAL'),

(NULL, 'billing.payment-failed', 'en', 'Payment failed',
 'Dunning notice after a failed renewal',
 'Action needed: your Prabhix payment did not go through',
 '<div style="font-family:''Plus Jakarta Sans'',Segoe UI,Arial,sans-serif;max-width:520px;margin:0 auto;padding:32px 24px;color:#0B0B12">'
 '<h1 style="font-size:20px;margin:0 0 16px">We could not process your payment</h1>'
 '<p style="font-size:15px;line-height:1.6;margin:0 0 20px">The payment of <strong th:text="${amount}">₹0</strong> for <span th:text="${planName}">your plan</span> failed. Your workspace stays active until <strong th:text="${graceEndsOn}">the grace period ends</strong>.</p>'
 '<p style="margin:0 0 24px"><a th:href="${retryUrl}" href="#" style="background:#7C3AED;color:#fff;text-decoration:none;padding:12px 22px;border-radius:8px;font-size:15px;display:inline-block">Update payment method</a></p>'
 '<p style="font-size:13px;color:#6b6b7b;margin:0">Attempt <span th:text="${attempt}">1</span> of <span th:text="${maxAttempts}">3</span>. We will try again automatically.</p>'
 '</div>',
 '[{"name":"amount","required":true,"example":"₹2,499.00"},'
 '{"name":"planName","required":true,"example":"Growth (monthly)"},'
 '{"name":"graceEndsOn","required":true,"example":"3 September 2026"},'
 '{"name":"retryUrl","required":true,"example":"https://app.prabhixtechnologies.com/billing"},'
 '{"name":"attempt","required":true,"example":"1"},'
 '{"name":"maxAttempts","required":true,"example":"3"}]'::jsonb,
 'TRANSACTIONAL'),

(NULL, 'mail.sla-breach', 'en', 'SLA breach alert',
 'Sent to mailbox leads when a thread misses its target',
 'SLA breached: [(${threadSubject})]',
 '<div style="font-family:''Plus Jakarta Sans'',Segoe UI,Arial,sans-serif;max-width:520px;margin:0 auto;padding:32px 24px;color:#0B0B12">'
 '<h1 style="font-size:20px;margin:0 0 12px;color:#b91c1c">SLA breached</h1>'
 '<p style="font-size:15px;line-height:1.6;margin:0 0 16px"><strong th:text="${threadSubject}">A conversation</strong> in <span th:text="${mailboxName}">an inbox</span> passed its <span th:text="${targetMinutes}">60</span>-minute first-response target.</p>'
 '<table style="font-size:14px;border-collapse:collapse;margin:0 0 24px">'
 '<tr><td style="padding:4px 16px 4px 0;color:#6b6b7b">Customer</td><td th:text="${customerEmail}">someone@example.com</td></tr>'
 '<tr><td style="padding:4px 16px 4px 0;color:#6b6b7b">Assignee</td><td th:text="${assigneeName}">Unassigned</td></tr>'
 '<tr><td style="padding:4px 16px 4px 0;color:#6b6b7b">Waiting</td><td th:text="${waitingFor}">2 hours</td></tr>'
 '</table>'
 '<p style="margin:0"><a th:href="${threadUrl}" href="#" style="background:#7C3AED;color:#fff;text-decoration:none;padding:12px 22px;border-radius:8px;font-size:15px;display:inline-block">Open conversation</a></p>'
 '</div>',
 '[{"name":"threadSubject","required":true,"example":"Login not working"},'
 '{"name":"mailboxName","required":true,"example":"Support"},'
 '{"name":"targetMinutes","required":true,"example":"60"},'
 '{"name":"customerEmail","required":true,"example":"customer@acme.com"},'
 '{"name":"assigneeName","required":true,"example":"Unassigned"},'
 '{"name":"waitingFor","required":true,"example":"2 hours 14 minutes"},'
 '{"name":"threadUrl","required":true,"example":"https://app.prabhixtechnologies.com/inbox/threads/..."}]'::jsonb,
 'NOTIFICATION'),

(NULL, 'site.lead-acknowledgement', 'en', 'Enquiry acknowledgement',
 'Auto-reply to a marketing site enquiry',
 'Thanks for contacting Prabhix Technologies',
 '<div style="font-family:''Plus Jakarta Sans'',Segoe UI,Arial,sans-serif;max-width:520px;margin:0 auto;padding:32px 24px;color:#0B0B12">'
 '<h1 style="font-size:20px;margin:0 0 16px">Thanks for getting in touch</h1>'
 '<p style="font-size:15px;line-height:1.6;margin:0 0 16px">Hello <span th:text="${name}">there</span>, we have your enquiry and someone from our team will reply within one business day.</p>'
 '<p style="font-size:14px;color:#6b6b7b;line-height:1.6;margin:0 0 8px">What you sent us:</p>'
 '<blockquote style="border-left:3px solid #7C3AED;margin:0 0 24px;padding:4px 0 4px 16px;font-size:14px;color:#3f3f52" th:text="${message}"></blockquote>'
 '<p style="font-size:13px;color:#6b6b7b;margin:0">Prabhix Technologies — building software that simplifies business.</p>'
 '</div>',
 '[{"name":"name","required":true,"example":"Priya"},'
 '{"name":"message","required":true,"example":"We would like a demo for a 200-agent support team."}]'::jsonb,
 'TRANSACTIONAL')
ON CONFLICT (template_key, locale) WHERE organization_id IS NULL DO NOTHING;
