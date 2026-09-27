# Invite-era duplicate user reconciliation

Some invite accepts before identity enforcement created more than one platform `users` row for the same email. Reconciliation folds duplicates into the **Identity UUID** (platform primary key) without guessing when Identity has no authoritative user.

## Internal API

Requires `X-Prabhix-Service-Token` (same secret as other `/internal/oneops` endpoints).

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/internal/oneops/users/reconcile/dry-run` | Report all normalized emails with more than one platform user. |
| `POST` | `/internal/oneops/users/reconcile/dry-run` | `{ "email": "person@example.com" }` — single-group report. |
| `POST` | `/internal/oneops/users/reconcile/apply` | `{ "email": "person@example.com" }` — transactional merge. |

## Dry-run report fields

- **authoritativeIdentityUserId** — from Identity lookup by email; `null` if absent or ambiguous.
- **identityLookupStatus** — `OK`, `IDENTITY_DISABLED`, `IDENTITY_USER_NOT_FOUND`, `IDENTITY_AMBIGUOUS`, or `PLATFORM_MISSING_AUTHORITATIVE_ROW`.
- **platformUsers** — each duplicate id, created time, deleted flag, total FK reference count.
- **referenceCounts** — per table/column counts for every duplicate id.
- **uniqueConflicts** — overlapping membership, team, mailbox, thread flag, staff role, or device session keys that would violate uniqueness if references were repointed.
- **applyAllowed** — `true` only when Identity resolves exactly one user, that UUID exists as a platform row, and there are no unique conflicts.

## Apply behaviour

Runs in a single transaction per email:

1. Rejects when `applyAllowed` is false (no Identity guess).
2. `UPDATE`s FK columns listed in `UserForeignKeyCatalog` from duplicate ids → authoritative id.
3. Soft-deletes duplicate users (email suffixed with `+reconciled-{id}` to satisfy uniqueness) and sets `reconciled_from_user_id`.

Any unique conflict aborts the whole apply for that email; existing business rows are not partially soft-deleted.

## Example (curl from app host)

```bash
curl -sS -H "X-Prabhix-Service-Token: $SERVICE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"email":"dup@example.com"}' \
  http://127.0.0.1:8080/internal/oneops/users/reconcile/dry-run
```
