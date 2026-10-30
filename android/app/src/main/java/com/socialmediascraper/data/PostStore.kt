package com.socialmediascraper.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** Çekilen gönderileri uygulamanın iç belleğinde bir JSON dosyasında saklar. */
class PostStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "posts.json"))

    private val _posts = MutableStateFlow(load())
    val posts: StateFlow<List<Post>> = _posts.asStateFlow()

    /**
     * Bir hesabın yeni çekilen gönderilerini birleştirir ve daha önce görülmemiş olanları döndürür.
     * Mevcut gönderilerin sayaçları (beğeni vb.) güncellenir, ilk görülme zamanı korunur.
     */
    @Synchronized
    fun merge(account: String, fetched: List<Post>, resetPinned: Boolean = true): List<Post> {
        val byId = _posts.value.associateBy { it.id }.toMutableMap()
        val added = mutableListOf<Post>()
        for (post in fetched) {
            val old = byId[post.id]
            if (old == null) added += post
            byId[post.id] = if (old != null) post.copy(fetchedAt = old.fetchedAt) else post
        }
        // Gönderinin sabitlenme durumu yalnızca son çekimde görülenler için geçerlidir.
        if (resetPinned) {
            val fetchedIds = fetched.map { it.id }.toSet()
            byId.replaceAll { id, p ->
                if (p.account.equals(account, true) && p.isPinned && id !in fetchedIds) p.copy(isPinned = false) else p
            }
        }
        save(trim(byId.values))
        return added
    }

    @Synchronized
    fun removeAccount(account: String) {
        save(_posts.value.filterNot { it.account.equals(account, ignoreCase = true) })
    }

    @Synchronized
    fun clear() = save(emptyList())

    /** Her hesap için en yeni [MAX_PER_ACCOUNT] gönderiyi tutar. */
    private fun trim(posts: Collection<Post>): List<Post> =
        posts.groupBy { it.account.lowercase() }
            .flatMap { (_, list) -> list.sortedByDescending { it.createdAt }.take(MAX_PER_ACCOUNT) }
            .sortedByDescending { it.createdAt }

    private fun save(posts: List<Post>) {
        _posts.value = posts
        val out = file.startWrite()
        try {
            out.write(JsonCodec.postsToJson(posts).toByteArray())
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
        }
    }

    private fun load(): List<Post> = runCatching {
        JsonCodec.postsFromJson(String(file.readFully()))
    }.getOrDefault(emptyList()).sortedByDescending { it.createdAt }

    private companion object {
        const val MAX_PER_ACCOUNT = 500
    }
}
