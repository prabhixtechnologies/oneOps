package com.prabhix.operator.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.prabhix.operator.BuildConfig
import com.prabhix.operator.MainActivity
import com.prabhix.operator.R
import com.prabhix.operator.data.push.PushTokenManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PrabhixFirebaseMessagingService : FirebaseMessagingService() {

    @Inject lateinit var pushTokenManager: PushTokenManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        scope.launch { pushTokenManager.registerToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val type = message.data["type"] ?: return
        val title = message.data["title"] ?: "Prabhix"
        val body = message.data["body"] ?: ""
        val conversationId = message.data["conversationId"]
        val threadId = message.data["threadId"]

        // Mailroom is a separate app; a thread-only payload has nowhere to land here.
        if (conversationId == null && threadId != null) return

        ensureChannel()
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (conversationId != null) {
                val scheme = BuildConfig.DEEP_LINK_SCHEME
                data = android.net.Uri.parse("$scheme://chat/$conversationId")
            }
        }
        val pending = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        val manager = getSystemService(NotificationManager::class.java)
        // Keyed by the thing the notification is about, so two conversations produce two
        // notifications. Keying by type alone would silently replace the first message with the
        // second, which is the failure mode where push looks like it works and still loses work.
        val id = (conversationId ?: type).hashCode()
        manager.notify(id, notification)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Named per flavor: with both apps installed the user otherwise sees two identical
            // "Operator alerts" entries in system settings and cannot tell which app to mute.
            val channel = NotificationChannel(
                CHANNEL_ID,
                "${BuildConfig.APP_LABEL} alerts",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "prabhix_operator"
    }
}
