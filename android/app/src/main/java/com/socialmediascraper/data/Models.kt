package com.socialmediascraper.data

enum class MediaType { PHOTO, VIDEO, GIF }

data class Media(
    val type: MediaType,
    /** Fotoğrafın kendisi ya da video/GIF için önizleme görseli. */
    val imageUrl: String,
    /** Video/GIF için oynatılabilir MP4 adresi. */
    val videoUrl: String? = null,
    val width: Int = 0,
    val height: Int = 0,
)

data class QuotedPost(
    val id: String,
    val authorHandle: String,
    val authorName: String,
    val text: String,
    val url: String,
)

data class Post(
    val id: String,
    /** Takip edilen hesabın kullanıcı adı (küçük/büyük harf ayarlardaki gibi). */
    val account: String,
    val authorHandle: String,
    val authorName: String,
    val authorAvatar: String?,
    val text: String,
    val createdAt: Long,
    val url: String,
    val media: List<Media> = emptyList(),
    val quoted: QuotedPost? = null,
    val isPinned: Boolean = false,
    val replyCount: Long = 0,
    val retweetCount: Long = 0,
    val likeCount: Long = 0,
    val viewCount: Long? = null,
    /** Uygulamanın bu gönderiyi ilk gördüğü zaman. */
    val fetchedAt: Long = 0,
)

data class Profile(
    val handle: String,
    val name: String,
    val avatarUrl: String?,
)

data class TrackedAccount(
    val handle: String,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val lastCheckedAt: Long? = null,
    val lastError: String? = null,
)
