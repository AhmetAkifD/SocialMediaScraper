package com.socialmediascraper.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.socialmediascraper.data.AddAccountResult
import com.socialmediascraper.data.AppSettings
import com.socialmediascraper.data.Repository
import com.socialmediascraper.scraper.DeepResult
import com.socialmediascraper.scraper.XSession
import com.socialmediascraper.work.RefreshWorker
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository.get(app)

    val accounts = repo.accounts
    val posts = repo.posts
    val settings = repo.settings
    val xLoggedIn = XSession.loggedIn

    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    private val _adding = MutableStateFlow(false)
    val adding = _adding.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var autoRefreshed = false

    /** Uygulama ilk açıldığında bir kez otomatik yenile. */
    fun refreshOnStart() {
        if (autoRefreshed) return
        autoRefreshed = true
        if (accounts.value.isNotEmpty()) refresh(silent = true)
    }

    fun refresh(silent: Boolean = false) {
        if (_refreshing.value) return
        if (accounts.value.isEmpty()) {
            if (!silent) _messages.trySend("Önce Ayarlar'dan bir hesap ekleyin")
            return
        }
        viewModelScope.launch {
            _refreshing.value = true
            try {
                val newPosts = repo.refreshAll()
                val errors = accounts.value.count { it.lastError != null }
                when {
                    newPosts.isNotEmpty() -> _messages.send("${newPosts.size} yeni gönderi")
                    errors > 0 -> _messages.send("$errors hesap alınamadı, ayrıntılar Ayarlar'da")
                    !silent -> _messages.send("Güncel")
                }
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun addAccount(input: String, onAdded: () -> Unit) {
        if (_adding.value) return
        viewModelScope.launch {
            _adding.value = true
            try {
                when (val result = repo.addAccount(input)) {
                    is AddAccountResult.Added -> {
                        onAdded()
                        val a = result.account
                        _messages.send(
                            if (a.lastError != null) "@${a.handle} eklendi, ancak şu an veri alınamadı: ${a.lastError}"
                            else "@${a.handle} eklendi",
                        )
                    }
                    is AddAccountResult.Error -> _messages.send(result.message)
                }
            } finally {
                _adding.value = false
            }
        }
    }

    fun notify(message: String) {
        _messages.trySend(message)
    }

    fun refreshLoginState() {
        XSession.refresh()
    }

    fun onLoginFinished(loggedIn: Boolean) {
        if (loggedIn) _messages.trySend("X'e giriş yapıldı")
    }

    fun logout() {
        XSession.logout()
        _messages.trySend("X oturumu kapatıldı")
    }

    fun oldestPostTime(handle: String) = repo.oldestPostTime(handle)

    fun onDeepFetchFinished(handle: String, result: DeepResult) {
        val added = repo.saveDeepResult(handle, result)
        _messages.trySend(
            when (result) {
                is DeepResult.Failure -> "@$handle: ${result.message}"
                is DeepResult.Success -> buildString {
                    append(if (added > 0) "@$handle: $added yeni gönderi eklendi" else "@$handle: yeni gönderi bulunamadı")
                    result.note?.let { append(" ($it)") }
                }
            },
        )
    }

    fun removeAccount(handle: String) {
        repo.removeAccount(handle)
        _messages.trySend("@$handle kaldırıldı")
    }

    fun clearPosts() {
        repo.postStore.clear()
        _messages.trySend("Kayıtlı gönderiler silindi")
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val before = settings.value
        repo.settingsStore.updateSettings(transform)
        val after = settings.value
        if (before.backgroundEnabled != after.backgroundEnabled || before.intervalMinutes != after.intervalMinutes) {
            RefreshWorker.schedule(getApplication(), after)
        }
    }
}
