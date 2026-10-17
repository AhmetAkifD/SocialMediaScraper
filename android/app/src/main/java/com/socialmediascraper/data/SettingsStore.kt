package com.socialmediascraper.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AppSettings(
    val backgroundEnabled: Boolean = true,
    val intervalMinutes: Int = 60,
    val notificationsEnabled: Boolean = true,
)

/** Takip edilen hesaplar ve uygulama ayarları (SharedPreferences). */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _accounts = MutableStateFlow(loadAccounts())
    val accounts: StateFlow<List<TrackedAccount>> = _accounts.asStateFlow()

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    @Synchronized
    fun updateAccounts(transform: (List<TrackedAccount>) -> List<TrackedAccount>) {
        _accounts.update(transform)
        prefs.edit { putString(KEY_ACCOUNTS, JsonCodec.accountsToJson(_accounts.value)) }
    }

    @Synchronized
    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        _settings.update(transform)
        val s = _settings.value
        prefs.edit {
            putBoolean(KEY_BACKGROUND, s.backgroundEnabled)
            putInt(KEY_INTERVAL, s.intervalMinutes)
            putBoolean(KEY_NOTIFICATIONS, s.notificationsEnabled)
        }
    }

    private fun loadAccounts(): List<TrackedAccount> =
        prefs.getString(KEY_ACCOUNTS, null)?.let { runCatching { JsonCodec.accountsFromJson(it) }.getOrNull() }
            ?: emptyList()

    private fun loadSettings() = AppSettings(
        backgroundEnabled = prefs.getBoolean(KEY_BACKGROUND, true),
        intervalMinutes = prefs.getInt(KEY_INTERVAL, 60),
        notificationsEnabled = prefs.getBoolean(KEY_NOTIFICATIONS, true),
    )

    private companion object {
        const val KEY_ACCOUNTS = "accounts"
        const val KEY_BACKGROUND = "background_enabled"
        const val KEY_INTERVAL = "interval_minutes"
        const val KEY_NOTIFICATIONS = "notifications_enabled"
    }
}
