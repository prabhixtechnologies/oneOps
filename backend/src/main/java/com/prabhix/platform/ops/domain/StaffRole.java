package com.prabhix.platform.ops.domain;

import java.util.Set;

/**
 * What kind of Prabhix staff someone is.
 *
 * <p>These are not tenant roles. A tenant role says what you may do inside one organization; a staff
 * role says what you may do across all of them, which is a different and much larger question. The two
 * deliberately do not share a vocabulary, so nobody can grant {@code OWNER} meaning "owner of this
 * organization" and get "owner of the platform".
 *
 * <p>Named after jobs rather than capabilities because that is how the grant decision is actually
 * made: somebody is hired into support, and the question is which role support gets — not which
 * eleven permissions to tick.
 */
public enum StaffRole {

    /**
     * Read a tenant's data to answer a ticket. No writes, no billing, no revocation.
     *
     * <p>The most-granted role and therefore the one whose limits matter most. A support hire who can
     * revoke sessions is a support hire who can lock a customer out of their own business by mistake.
     */
    SUPPORT,

    /** Subscriptions, invoices and plan changes. No tenant content. */
    BILLING,

    /**
     * Platform health, queues, retries and replays. No tenant content.
     *
     * <p>Separate from SUPPORT because the jobs do not overlap: whoever is replaying a stuck outbound
     * mail queue has no reason to read the mail in it.
     */
    OPERATOR,

    /**
     * Break-glass token revocation, and the audit trail.
     *
     * <p>Narrow on purpose. Revoking every token for an account has to work while an attacker holds a
     * valid session, which makes it the most powerful thing here — and the thing most likely to be
     * reached for in a panic by whoever happens to be logged in.
     */
    SECURITY,

    /** Everything, including granting these roles. */
    OWNER;

    /** Whether holding this role implies the other. Only OWNER implies anything. */
    public boolean implies(StaffRole other) {
        return this == OWNER || this == other;
    }

    /**
     * Who may revoke tokens for an arbitrary account.
     *
     * <p>Not SUPPORT and not OPERATOR. The whole reason for splitting the boolean was that a support
     * hire should be able to read a ticket without also being able to sign every customer out.
     */
    public static final Set<StaffRole> BREAK_GLASS = Set.of(SECURITY, OWNER);

    /** Who may grant and revoke staff roles. */
    public static final Set<StaffRole> ROLE_ADMIN = Set.of(OWNER);

    /**
     * Who may act inside a tenant they are not a member of.
     *
     * <p>Not BILLING and not OPERATOR. Both jobs are about the platform, not its customers' content —
     * whoever is replaying a stuck mail queue has no reason to read the mail in it.
     */
    public static final Set<StaffRole> TENANT_ACCESS = Set.of(SUPPORT, OWNER);
}
