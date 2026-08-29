package com.prabhix.operator.data.session

/**
 * What each app does when a session begins, ends, or is already present at launch.
 *
 * <p>Sign-in is shared — one screen, one endpoint, both apps — but what follows it is not. OneOps
 * opens the chat and mail streams, registers for push and schedules the outbound queue flush. The
 * admin app does none of that: it reads two cross-tenant endpoints on demand and has no
 * conversations to stream or send.
 *
 * <p>This interface exists so that difference does not put the product's realtime, push and
 * database layers into the admin APK. `AuthRepository` needs *something* to call after a successful
 * login, and if that something were `RealtimeHub` directly then Hilt would hold a provider for it in
 * both apps, R8 could not prove it unreachable, and the staff build would ship the streaming client
 * and the Room database it feeds.
 *
 * <p>Implemented once per flavor. There is deliberately no default here: a new app has to say what
 * it does with a session, and forgetting is a compile error rather than a silently inert app.
 */
interface SessionLifecycle {

    /** Called at process start. [hasSession] is false when nobody is signed in. */
    fun onAppStart(hasSession: Boolean)

    /** Called after a sign-in completes and tokens are stored. */
    fun onSignedIn()

    /** Called after tokens are cleared, whether by signing out or by a refresh failing. */
    fun onSignedOut()

    /** Called when the active organization changes, so anything tenant-scoped can be restarted. */
    fun onOrganizationChanged()
}
