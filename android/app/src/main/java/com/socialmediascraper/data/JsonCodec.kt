package com.socialmediascraper.data

import org.json.JSONArray
import org.json.JSONObject

/** Modellerin diske yazılması için basit JSON dönüştürücüler. */
internal object JsonCodec {

    fun postsToJson(posts: Collection<Post>): String = JSONArray().apply {
        posts.forEach { put(postToJson(it)) }
    }.toString()

    fun postsFromJson(json: String): List<Post> {
        val arr = JSONArray(json)
        return (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::postFromJson) }
    }

    fun accountsToJson(accounts: List<TrackedAccount>): String = JSONArray().apply {
        accounts.forEach { a ->
            put(JSONObject().apply {
                put("handle", a.handle)
                putOpt("displayName", a.displayName)
                putOpt("avatarUrl", a.avatarUrl)
                putOpt("lastCheckedAt", a.lastCheckedAt)
                putOpt("lastError", a.lastError)
            })
        }
    }.toString()

    fun accountsFromJson(json: String): List<TrackedAccount> {
        val arr = JSONArray(json)
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            TrackedAccount(
                handle = o.getString("handle"),
                displayName = o.optStringOrNull("displayName"),
                avatarUrl = o.optStringOrNull("avatarUrl"),
                lastCheckedAt = if (o.has("lastCheckedAt")) o.getLong("lastCheckedAt") else null,
                lastError = o.optStringOrNull("lastError"),
            )
        }
    }

    private fun postToJson(p: Post) = JSONObject().apply {
        put("id", p.id)
        put("account", p.account)
        put("authorHandle", p.authorHandle)
        put("authorName", p.authorName)
        putOpt("authorAvatar", p.authorAvatar)
        put("text", p.text)
        put("createdAt", p.createdAt)
        put("url", p.url)
        put("isPinned", p.isPinned)
        put("replyCount", p.replyCount)
        put("retweetCount", p.retweetCount)
        put("likeCount", p.likeCount)
        putOpt("viewCount", p.viewCount)
        put("fetchedAt", p.fetchedAt)
        put("media", JSONArray().apply {
            p.media.forEach { m ->
                put(JSONObject().apply {
                    put("type", m.type.name)
                    put("imageUrl", m.imageUrl)
                    putOpt("videoUrl", m.videoUrl)
                    put("width", m.width)
                    put("height", m.height)
                })
            }
        })
        p.quoted?.let { q ->
            put("quoted", JSONObject().apply {
                put("id", q.id)
                put("authorHandle", q.authorHandle)
                put("authorName", q.authorName)
                put("text", q.text)
                put("url", q.url)
            })
        }
    }

    private fun postFromJson(o: JSONObject): Post? = runCatching {
        val mediaArr = o.optJSONArray("media") ?: JSONArray()
        val media = (0 until mediaArr.length()).mapNotNull { i ->
            val m = mediaArr.optJSONObject(i) ?: return@mapNotNull null
            Media(
                type = runCatching { MediaType.valueOf(m.getString("type")) }.getOrDefault(MediaType.PHOTO),
                imageUrl = m.getString("imageUrl"),
                videoUrl = m.optStringOrNull("videoUrl"),
                width = m.optInt("width"),
                height = m.optInt("height"),
            )
        }
        val quoted = o.optJSONObject("quoted")?.let { q ->
            QuotedPost(
                id = q.getString("id"),
                authorHandle = q.getString("authorHandle"),
                authorName = q.getString("authorName"),
                text = q.getString("text"),
                url = q.getString("url"),
            )
        }
        Post(
            id = o.getString("id"),
            account = o.getString("account"),
            authorHandle = o.getString("authorHandle"),
            authorName = o.getString("authorName"),
            authorAvatar = o.optStringOrNull("authorAvatar"),
            text = o.getString("text"),
            createdAt = o.getLong("createdAt"),
            url = o.getString("url"),
            media = media,
            quoted = quoted,
            isPinned = o.optBoolean("isPinned"),
            replyCount = o.optLong("replyCount"),
            retweetCount = o.optLong("retweetCount"),
            likeCount = o.optLong("likeCount"),
            viewCount = if (o.has("viewCount")) o.getLong("viewCount") else null,
            fetchedAt = o.optLong("fetchedAt"),
        )
    }.getOrNull()

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key) else null
}
