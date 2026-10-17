package com.socialmediascraper.data

import android.content.Context
import com.socialmediascraper.scraper.ScrapeResult
import com.socialmediascraper.scraper.TwitterScraper
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

sealed interface AddAccountResult {
    data class Added(val account: TrackedAccount) : AddAccountResult
    data class Error(val message: String) : AddAccountResult
}

class Repository private constructor(context: Context) {
    val settingsStore = SettingsStore(context)
    val postStore = PostStore(context)
    private val scraper = TwitterScraper()
    private val refreshMutex = Mutex()

    val accounts get() = settingsStore.accounts
    val settings get() = settingsStore.settings
    val posts get() = postStore.posts

    /** Hesabı doğrulayıp ekler ve son gönderilerini ilk kez çeker. */
    suspend fun addAccount(input: String): AddAccountResult {
        val handle = TwitterScraper.normalizeHandle(input)
            ?: return AddAccountResult.Error("Geçersiz kullanıcı adı. Örnek: @TwitterHesabi")
        if (accounts.value.any { it.handle.equals(handle, ignoreCase = true) }) {
            return AddAccountResult.Error("@$handle zaten listede")
        }
        return when (val result = scraper.fetch(handle)) {
            is ScrapeResult.Failure ->
                if (result.notFound) AddAccountResult.Error(result.message)
                else {
                    // Geçici bir sorun olabilir; hesabı yine de ekleyip hatayı gösteriyoruz.
                    val account = TrackedAccount(handle, lastCheckedAt = System.currentTimeMillis(), lastError = result.message)
                    settingsStore.updateAccounts { it + account }
                    AddAccountResult.Added(account)
                }
            is ScrapeResult.Success -> {
                val realHandle = result.profile?.handle ?: handle
                val account = TrackedAccount(
                    handle = realHandle,
                    displayName = result.profile?.name,
                    avatarUrl = result.profile?.avatarUrl,
                    lastCheckedAt = System.currentTimeMillis(),
                )
                settingsStore.updateAccounts { it + account }
                postStore.merge(realHandle, result.posts.map { it.copy(account = realHandle) })
                AddAccountResult.Added(account)
            }
        }
    }

    fun removeAccount(handle: String) {
        settingsStore.updateAccounts { list -> list.filterNot { it.handle.equals(handle, ignoreCase = true) } }
        postStore.removeAccount(handle)
    }

    /**
     * Tüm hesapları sırayla tarar. Bildirim gönderilmesi gereken yeni gönderileri döndürür:
     * daha önce hiç görülmemiş ve hesabın bilinen en yeni gönderisinden daha yeni olanlar.
     */
    suspend fun refreshAll(): List<Post> = refreshMutex.withLock {
        val newPosts = mutableListOf<Post>()
        val list = accounts.value
        list.forEachIndexed { index, account ->
            newPosts += refreshAccount(account)
            // X'e art arda istek atmamak için hesaplar arasında kısa, rastgele bir bekleme.
            if (index < list.lastIndex) delay(Random.nextLong(1_500, 3_500))
        }
        newPosts.sortedByDescending { it.createdAt }
    }

    private suspend fun refreshAccount(account: TrackedAccount): List<Post> {
        val now = System.currentTimeMillis()
        val result = scraper.fetch(account.handle)
        // Tarama sürerken hesap silinmiş olabilir.
        if (accounts.value.none { it.handle.equals(account.handle, ignoreCase = true) }) return emptyList()
        return when (result) {
            is ScrapeResult.Failure -> {
                updateAccount(account.handle) { it.copy(lastCheckedAt = now, lastError = result.message) }
                emptyList()
            }
            is ScrapeResult.Success -> {
                updateAccount(account.handle) {
                    it.copy(
                        displayName = result.profile?.name ?: it.displayName,
                        avatarUrl = result.profile?.avatarUrl ?: it.avatarUrl,
                        lastCheckedAt = now,
                        lastError = null,
                    )
                }
                val newestKnown = posts.value
                    .filter { it.account.equals(account.handle, ignoreCase = true) }
                    .maxOfOrNull { it.createdAt }
                val added = postStore.merge(account.handle, result.posts.map { it.copy(account = account.handle) })
                if (newestKnown == null) emptyList() else added.filter { it.createdAt > newestKnown }
            }
        }
    }

    private fun updateAccount(handle: String, transform: (TrackedAccount) -> TrackedAccount) {
        settingsStore.updateAccounts { list ->
            list.map { if (it.handle.equals(handle, ignoreCase = true)) transform(it) else it }
        }
    }

    companion object {
        @Volatile private var instance: Repository? = null

        fun get(context: Context): Repository = instance ?: synchronized(this) {
            instance ?: Repository(context.applicationContext).also { instance = it }
        }
    }
}
