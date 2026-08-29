-- -----------------------------------------------------------------------------
-- Shared browser session, so one sign-in covers every console hostname.
--
-- The console previously kept a refresh token in localStorage. localStorage is
-- scoped to a single origin, so admin.prabhixtechnologies.com and
-- oneops.prabhixtechnologies.com could not see each other's copy and each
-- demanded its own login. Sharing the refresh token was not an option: refresh
-- tokens rotate, and presenting an already-used one is treated as theft and
-- revokes the session (see refresh_tokens.replaced_by), so two apps racing to
-- refresh the same token would lock the user out rather than sign them in.
--
-- This adds a second, non-rotating credential to the session row instead. It is
-- delivered as an HttpOnly cookie on the parent domain, so both hostnames send
-- it, JavaScript cannot read it, and exchanging it for an access token is safe
-- to do concurrently because nothing about it changes.
--
-- Only a SHA-256 hash is stored, for the same reason refresh_tokens does that:
-- a copy of this table is then not a set of usable session credentials.
-- -----------------------------------------------------------------------------
ALTER TABLE device_sessions
    ADD COLUMN cookie_token_hash varchar(64),
    ADD COLUMN cookie_expires_at timestamptz;

-- Partial and unique: the lookup on every exchange is by hash alone, and most
-- rows (every phone, every API session) never hold one, so they must not
-- collide with each other on NULL.
CREATE UNIQUE INDEX uq_device_sessions_cookie_token
    ON device_sessions (cookie_token_hash) WHERE cookie_token_hash IS NOT NULL;

COMMENT ON COLUMN device_sessions.cookie_token_hash IS
    'SHA-256 of the opaque browser session cookie. Non-rotating, unlike '
    'refresh_tokens, so both console hostnames can exchange it concurrently. '
    'Cleared on logout; irrelevant once revoked_at is set.';

COMMENT ON COLUMN device_sessions.cookie_expires_at IS
    'Server-side expiry for the cookie above. Enforced independently of the '
    'browser Max-Age, which a client is free to ignore.';
