package com.prabhix.operator

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.prabhix.operator.data.auth.TokenStore
import com.prabhix.operator.data.sse.RealtimeHub
import com.prabhix.operator.worker.OutboundMessageWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class PrabhixApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var tokenStore: TokenStore
    @Inject lateinit var realtimeHub: RealtimeHub

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        if (tokenStore.session() != null) {
            realtimeHub.start()
        }
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "outbound_flush",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<OutboundMessageWorker>(15, TimeUnit.MINUTES).build(),
        )
    }
}
