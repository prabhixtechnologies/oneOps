package com.prabhix.operator.data.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class ViewingOrg(val id: String, val name: String)

/**
 * The customer organization a platform admin is currently looking at, when it is not their own.
 *
 * <p>Mirrors `web/src/lib/impersonation.ts`. The server lets staff name any organization in the
 * `X-Prabhix-Org` header and records the access; this decides which id that header carries.
 *
 * <h2>Why it is not persisted</h2>
 *
 * <p>Deliberately in memory only. Encrypted preferences would survive a restart, and the worst
 * outcome of this feature is opening the app tomorrow, seeing an inbox, and not registering that it
 * belongs to a customer. Losing the state when the process dies costs one tap; the alternative
 * costs a reply sent from the wrong company.
 *
 * <p>It also stays out of [TokenStore.clear] concerns for the same reason: nothing to clear.
 */
@Singleton
class ViewingOrgStore @Inject constructor() {

    private val _current = MutableStateFlow<ViewingOrg?>(null)

    /** Observed by the banner, so entering or leaving a tenant updates the UI immediately. */
    val current: StateFlow<ViewingOrg?> = _current.asStateFlow()

    /** Read by the request interceptor on every call, so a change applies to the next request. */
    fun organizationIdOverride(): String? = _current.value?.id

    fun start(org: ViewingOrg) {
        _current.value = org
    }

    fun stop() {
        _current.value = null
    }
}
