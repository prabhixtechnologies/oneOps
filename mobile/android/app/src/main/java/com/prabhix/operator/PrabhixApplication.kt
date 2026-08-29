package com.prabhix.operator

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.prabhix.operator.data.auth.TokenStore
import com.prabhix.operator.data.session.SessionLifecycle
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PrabhixApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var tokenStore: TokenStore
    @Inject lateinit var sessionLifecycle: SessionLifecycle

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // What starting up means is the flavor's business: OneOps opens its streams and schedules
        // the send queue, admin does nothing. Naming either here would put both apps' startup work
        // into both apps.
        sessionLifecycle.onAppStart(hasSession = tokenStore.session() != null)
    }
}
