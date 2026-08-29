package com.prabhix.operator.data.session

import android.content.Context
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.prabhix.operator.data.push.PushTokenManager
import com.prabhix.operator.data.sse.RealtimeHub
import com.prabhix.operator.worker.OutboundMessageWorker
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What a session means in the product: live streams, push notifications and a send queue.
 *
 * <p>All three are tenant-scoped, which is why the organization changing restarts them rather than
 * leaving them alone. A stream opened against the previous organization keeps delivering its
 * messages into screens now showing another's, and that is how a reply ends up in the wrong
 * company's conversation.
 */
@Singleton
class OneOpsSessionLifecycle @Inject constructor(
    private val realtimeHub: RealtimeHub,
    private val pushTokenManager: PushTokenManager,
    @ApplicationContext private val context: Context,
) : SessionLifecycle {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onAppStart(hasSession: Boolean) {
        if (hasSession) realtimeHub.start()
        // KEEP rather than REPLACE: the queue survives restarts, and rescheduling on every launch
        // would push the next flush 15 minutes out each time the app is opened.
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            OUTBOUND_FLUSH,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<OutboundMessageWorker>(15, TimeUnit.MINUTES).build(),
        )
    }

    override fun onSignedIn() {
        realtimeHub.start()
        scope.launch { pushTokenManager.registerIfPossible() }
    }

    override fun onSignedOut() {
        scope.launch { pushTokenManager.unregisterIfRegistered() }
        realtimeHub.stop()
    }

    override fun onOrganizationChanged() {
        realtimeHub.restart()
    }

    private companion object {
        const val OUTBOUND_FLUSH = "outbound_flush"
    }
}

@Module
@InstallIn(SingletonComponent::class)
interface SessionLifecycleModule {
    @Binds fun bind(impl: OneOpsSessionLifecycle): SessionLifecycle
}
