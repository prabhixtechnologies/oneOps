# LMTP HTTP ingest (OneOps)

Mailroom (or Postfix/Dovecot HTTP frontends) POST raw messages to OneOps at:

`POST /api/v1/oneops/mail/inbound/lmtp`

## Required headers

| Header | Required | Description |
|--------|----------|-------------|
| `X-Mail-Token` | Always | Shared secret (`MAIL_LMTP_TOKEN` / `prabhix.mail.inbound.lmtp-token`). |
| `X-Mail-Timestamp` | When replay protection is on | Unix epoch **seconds** (UTC). |
| `X-Mail-Nonce` | When replay protection is on | Unique per request (UUID recommended). |
| `X-Mail-Signature` | When replay protection is on | Lowercase hex HMAC-SHA256 of the canonical body (see below). |

## Body

JSON matching `LmtpRequest`:

- `recipient` — envelope recipient address resolved to a mailbox or alias
- `rawMimeBase64` — base64-encoded raw MIME bytes (size capped by `max-raw-bytes`)

## Signature (production)

Enable with `MAIL_LMTP_REPLAY_REQUIRED=true` and set `MAIL_LMTP_HMAC_SECRET`.

Canonical string (UTF-8, newline-separated):

```
{recipient}
{rawMimeBase64}
{timestamp}
{nonce}
```

`X-Mail-Signature` = `HMAC-SHA256(canonical, lmtp-hmac-secret)` as **lowercase hex**.

Skew window: `prabhix.mail.inbound.lmtp-max-timestamp-skew` (default 5 minutes).  
Nonces are stored in Redis for `lmtp-nonce-ttl` (default 15 minutes); reuse is rejected.

When replay protection is **off** (typical local dev), only `X-Mail-Token` is required.
