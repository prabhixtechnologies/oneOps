-- Sign-in lives in the identity database. These copies were left behind when that split happened.
--
-- The application does not map them. auth_challenges, auth_identities, device_sessions and
-- refresh_tokens are Identity's tables, under Identity's role. notifications has no writer.
-- The password and lockout columns on public.users are the same leftover: the mirror insert
-- does not write them, and the User entity does not read them.
--
-- mail.mail_mailboxes.password_hash is not one of these. That column is the IMAP credential
-- Dovecot checks, and it stays.
--
-- A table that still holds rows is refused rather than emptied. A live database that never
-- finished moving its sessions should fail this migration, not lose them.

DO $$
DECLARE
    leftover text;
BEGIN
    SELECT string_agg(format('%s (%s rows)', rel, n), ', ' ORDER BY rel)
    INTO leftover
    FROM (
        SELECT 'auth_challenges'::text AS rel, count(*) AS n FROM public.auth_challenges
        UNION ALL SELECT 'auth_identities', count(*) FROM public.auth_identities
        UNION ALL SELECT 'device_sessions', count(*) FROM public.device_sessions
        UNION ALL SELECT 'refresh_tokens', count(*) FROM public.refresh_tokens
        UNION ALL SELECT 'notifications', count(*) FROM public.notifications
    ) counts
    WHERE n > 0;

    IF leftover IS NOT NULL THEN
        RAISE EXCEPTION 'Refusing to drop credential leftovers that still hold rows: %', leftover;
    END IF;
END $$;

-- refresh_tokens points at device_sessions, so it goes first.
DROP TABLE public.refresh_tokens;
DROP TABLE public.device_sessions;
DROP TABLE public.auth_challenges;
DROP TABLE public.auth_identities;
DROP TABLE public.notifications;

ALTER TABLE public.users
    DROP COLUMN password_hash,
    DROP COLUMN password_changed_at,
    DROP COLUMN phone,
    DROP COLUMN phone_verified_at,
    DROP COLUMN failed_login_attempts,
    DROP COLUMN locked_until,
    DROP COLUMN last_login_at;
