package com.prabhix.operator.data.session

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A session means nothing extra in the admin app, and that is the point.
 *
 * <p>Nothing to stream, because there are no conversations here. Nothing to queue, because there is
 * nothing to send. No push registration, because the notifications this platform sends address a
 * chat or a mail thread and this app has nowhere to open one — registering would produce
 * notifications that go nowhere when tapped.
 *
 * <p>Empty by design rather than unfinished. It is what keeps the streaming client, the Room
 * database, the send queue and the Firebase messaging service out of the staff APK: with no
 * implementation here referring to them, nothing in this app's dependency graph does.
 */
@Singleton
class AdminSessionLifecycle @Inject constructor() : SessionLifecycle {
    override fun onAppStart(hasSession: Boolean) = Unit
    override fun onSignedIn() = Unit
    override fun onSignedOut() = Unit
    override fun onOrganizationChanged() = Unit
}

@Module
@InstallIn(SingletonComponent::class)
interface SessionLifecycleModule {
    @Binds fun bind(impl: AdminSessionLifecycle): SessionLifecycle
}
