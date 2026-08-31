-- A durable queue of "please send this email", written in the caller's own transaction.
--
-- Until now a module asked for mail by publishing a Spring event, and the mail module listened
-- AFTER_COMMIT. That leaves a window: the business transaction commits, the process dies before the
-- listener runs, and the mail is never queued. Nothing records that it was asked for, so there is
-- nothing to retry and nothing to find afterwards — for a magic link, that is a person who cannot
-- sign in and no trace of why.
--
-- A row here is written by the same transaction as the work that asked for it, so the request and
-- the reason for it commit together or not at all. A relay moves rows into mail_outbox afterwards.
--
-- Deliberately not merged into mail_outbox, which it superficially resembles. mail_outbox belongs to
-- the mail module and holds a rendered, addressed, retried message; this holds an intent, owned by
-- the platform, and is the seam mail leaves through. When mail becomes its own service with its own
-- database, the relay's destination changes from a method call to an HTTP request and nothing on
-- this side of the seam moves.

CREATE TABLE public.mail_requests (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    organization_id uuid,
    template_key character varying(80) NOT NULL,
    locale character varying(16) DEFAULT 'en'::character varying NOT NULL,
    to_addresses jsonb DEFAULT '[]'::jsonb NOT NULL,
    variables jsonb DEFAULT '{}'::jsonb NOT NULL,
    dedupe_key character varying(200),
    priority integer DEFAULT 50 NOT NULL,
    status character varying(16) DEFAULT 'PENDING'::character varying NOT NULL,
    attempts integer DEFAULT 0 NOT NULL,
    max_attempts integer DEFAULT 6 NOT NULL,
    next_attempt_at timestamp with time zone DEFAULT now() NOT NULL,
    claimed_at timestamp with time zone,
    claimed_by character varying(80),
    delivered_at timestamp with time zone,
    -- The mail_outbox row this became. No foreign key on purpose: mail is leaving, and a constraint
    -- pointing into its tables is one of the things that would have to be dropped to let it go.
    outbox_id uuid,
    last_error character varying(2000),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    CONSTRAINT ck_mail_requests_attempts CHECK (((attempts >= 0) AND (max_attempts > 0))),
    CONSTRAINT ck_mail_requests_priority CHECK (((priority >= 0) AND (priority <= 100))),
    CONSTRAINT ck_mail_requests_recipients CHECK ((jsonb_typeof(to_addresses) = 'array'::text)),
    CONSTRAINT ck_mail_requests_status CHECK (((status)::text = ANY ((ARRAY[
        'PENDING'::character varying,
        'CLAIMED'::character varying,
        'DELIVERED'::character varying,
        'FAILED'::character varying,
        'DEAD'::character varying])::text[])))
);

ALTER TABLE ONLY public.mail_requests
    ADD CONSTRAINT mail_requests_pkey PRIMARY KEY (id);

-- ON DELETE CASCADE, matching mail_outbox: a deleted organization takes its unsent mail with it.
ALTER TABLE ONLY public.mail_requests
    ADD CONSTRAINT fk_mail_requests_organization FOREIGN KEY (organization_id)
        REFERENCES public.organizations(id) ON DELETE CASCADE;

-- Partial, on the columns the relay orders by, so claiming stays an index scan over the pending
-- rows alone rather than over every request ever made.
CREATE INDEX ix_mail_requests_claimable ON public.mail_requests
    USING btree (priority, next_attempt_at, id) WHERE ((status)::text = 'PENDING'::text);

CREATE INDEX ix_mail_requests_retryable ON public.mail_requests
    USING btree (next_attempt_at) WHERE ((status)::text = 'FAILED'::text);

-- A claim that never completed. The relay releases these rather than leaving them stranded by the
-- process that died holding them.
CREATE INDEX ix_mail_requests_stuck ON public.mail_requests
    USING btree (claimed_at) WHERE ((status)::text = 'CLAIMED'::text);

CREATE INDEX ix_mail_requests_dead ON public.mail_requests
    USING btree (created_at DESC) WHERE ((status)::text = 'DEAD'::text);

-- Idempotency, enforced here rather than in the relay. The caller's retry, a redeploy replaying the
-- same work, and two instances racing all collapse to one row, and the duplicate insert fails at
-- the point it happens instead of producing a second email.
CREATE UNIQUE INDEX uq_mail_requests_dedupe ON public.mail_requests
    USING btree (dedupe_key) WHERE (dedupe_key IS NOT NULL);
