package com.prabhix.operator.data.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.prabhix.operator.data.api.TokenResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class SessionState(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochMs: Long,
    val organizationId: String?,
    val permissions: Set<String>,
    val userId: String? = null,
    val displayName: String? = null,
    val email: String? = null,
)

@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val deviceId: String = prefs.getString(KEY_DEVICE_ID, null)
        ?: UUID.randomUUID().toString().also { prefs.edit().putString(KEY_DEVICE_ID, it).apply() }

    fun deviceId(): String = deviceId

    fun saveTokens(response: TokenResponse) {
        val expiresAt = System.currentTimeMillis() + response.expiresInSeconds * 1000L
        prefs.edit()
            .putString(KEY_ACCESS, response.accessToken)
            .putString(KEY_REFRESH, response.refreshToken)
            .putLong(KEY_EXPIRES, expiresAt)
            .putString(KEY_ORG, response.organizationId)
            .putStringSet(KEY_PERMISSIONS, response.permissions)
            .apply()
    }

    fun saveProfile(userId: String, email: String, displayName: String) {
        prefs.edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_EMAIL, email)
            .putString(KEY_DISPLAY, displayName)
            .apply()
    }

    fun session(): SessionState? {
        val access = prefs.getString(KEY_ACCESS, null) ?: return null
        val refresh = prefs.getString(KEY_REFRESH, null) ?: return null
        return SessionState(
            accessToken = access,
            refreshToken = refresh,
            expiresAtEpochMs = prefs.getLong(KEY_EXPIRES, 0L),
            organizationId = prefs.getString(KEY_ORG, null),
            permissions = prefs.getStringSet(KEY_PERMISSIONS, emptySet()) ?: emptySet(),
            userId = prefs.getString(KEY_USER_ID, null),
            displayName = prefs.getString(KEY_DISPLAY, null),
            email = prefs.getString(KEY_EMAIL, null),
        )
    }

    fun organizationId(): String? = prefs.getString(KEY_ORG, null)

    fun setOrganizationId(orgId: String?) {
        prefs.edit().putString(KEY_ORG, orgId).apply()
    }

    fun permissions(): Set<String> = prefs.getStringSet(KEY_PERMISSIONS, emptySet()) ?: emptySet()

    fun hasPermission(permission: String): Boolean = permissions().contains(permission)

    fun clear() {
        val id = deviceId
        prefs.edit().clear().putString(KEY_DEVICE_ID, id).apply()
    }

    fun biometricEnabled(): Boolean = prefs.getBoolean(KEY_BIOMETRIC, false)

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC, enabled).apply()
    }

    companion object {
        private const val PREFS_NAME = "prabhix_secure_session"
        private const val KEY_ACCESS = "access"
        private const val KEY_REFRESH = "refresh"
        private const val KEY_EXPIRES = "expires"
        private const val KEY_ORG = "org"
        private const val KEY_PERMISSIONS = "permissions"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_EMAIL = "email"
        private const val KEY_DISPLAY = "display"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_BIOMETRIC = "biometric"
    }
}
