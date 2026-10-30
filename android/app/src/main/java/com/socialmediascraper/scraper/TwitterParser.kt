package com.socialmediascraper.scraper

import com.socialmediascraper.data.Media
import com.socialmediascraper.data.MediaType
import com.socialmediascraper.data.Post
import com.socialmediascraper.data.Profile
import com.socialmediascraper.data.QuotedPost
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

class ParsedProfilePage(
    val profile: Profile?,
    val posts: List<Post>,
    /** Yanıtlarda bir zaman tüneli (instructions) bulundu mu? */
    val timelineFound: Boolean = false,
    /** Zaman tünelindeki tweet girdisi sayısı (ayrıştırılamasalar bile). */
    val tweetEntries: Int = 0,
    val isProtected: Boolean = false,
    /** X kullanıcı sorgusuna boş yanıt verdi (hesap yok). */
    val userMissing: Boolean = false,
)

/**
 * X'in GraphQL yanıtlarını ayrıştırır.
 *
 * Oturum açılmadan istenen profil sayfası sunucu tarafında render edilir ve yanıtlar
 * `kind:"Result.Ok",value:"<JSON>"` biçiminde JavaScript string'i olarak gömülüdür.
 * Oturum açılmış tarayıcıdan yakalanan yanıtlar ise düz JSON'dur; ikisi de
 * [parsePayloads] ile okunur. Böylece kırılgan HTML seçicilerine ihtiyaç kalmaz.
 */
object TwitterParser {

    private const val RESULT_MARKER = "kind:\"Result.Ok\",value:\""
    private val LEADING_MENTIONS = Regex("""^(@\w+\s+)+""")

    fun hasEmbeddedData(html: String) = html.contains(RESULT_MARKER)

    fun parse(html: String, account: String, now: Long = System.currentTimeMillis()): ParsedProfilePage =
        parsePayloads(extractGraphQlPayloads(html), account, ownOnly = false, now = now)

    /**
     * @param ownOnly true ise yalnızca [account] tarafından yazılan ya da yeniden paylaşılan
     * gönderiler tutulur (yanıtlar sekmesinde başkalarının konuşma tweet'leri de gelir).
     */
    fun parsePayloads(
        payloads: List<JSONObject>,
        account: String,
        ownOnly: Boolean,
        now: Long = System.currentTimeMillis(),
    ): ParsedProfilePage {
        var profile: Profile? = null
        var timelineFound = false
        var tweetEntries = 0
        var isProtected = false
        var userMissing = false
        val posts = LinkedHashMap<String, Post>()

        for (payload in payloads) {
            val data = payload.optJSONObject("data") ?: continue
            val userWrapper = data.optJSONObject("user_result_by_screen_name") ?: data.optJSONObject("user")
            val user = userWrapper?.optJSONObject("result")
            if (data.length() == 0 || (userWrapper != null && user == null)) userMissing = true
            if (user != null) {
                // Sayfada aynı kullanıcının eksik (yalnızca kullanıcı adı) ve tam kopyaları olabilir.
                val candidate = parseProfile(user)
                if (candidate != null && (profile == null || profile.completeness() < candidate.completeness())) {
                    profile = candidate
                }
                if (isProtectedUser(user)) isProtected = true
            }

            val timeline = findTimelineEntries(data)
            if (timeline.instructionsFound) timelineFound = true
            tweetEntries += timeline.tweetEntries
            timeline.tweets.forEach { (tweet, pinned) ->
                val post = parseTweet(tweet, account, pinned, now) ?: return@forEach
                if (ownOnly && !post.authorHandle.equals(account, true) && !post.retweetedBy.equals(account, true)) {
                    return@forEach
                }
                val existing = posts[post.id]
                posts[post.id] = if (existing != null) existing.copy(isPinned = existing.isPinned || pinned) else post
            }
        }
        return ParsedProfilePage(
            profile = profile,
            posts = posts.values.sortedByDescending { it.createdAt },
            timelineFound = timelineFound,
            tweetEntries = tweetEntries,
            isProtected = isProtected,
            userMissing = userMissing && profile == null,
        )
    }

    private fun Profile.completeness() = (if (avatarUrl != null) 2 else 0) + (if (name != handle) 1 else 0)

    private fun isProtectedUser(user: JSONObject): Boolean =
        user.optJSONObject("privacy")?.optBoolean("protected") == true ||
            user.optJSONObject("legacy")?.optBoolean("protected") == true

    /** HTML içindeki tüm başarılı GraphQL yanıtlarını JSON olarak döndürür. */
    fun extractGraphQlPayloads(html: String): List<JSONObject> {
        val result = mutableListOf<JSONObject>()
        var index = html.indexOf(RESULT_MARKER)
        while (index >= 0) {
            val start = index + RESULT_MARKER.length
            val decoded = decodeJsString(html, start)
            if (decoded != null) {
                runCatching { JSONObject(decoded.first) }.getOrNull()?.let(result::add)
                index = html.indexOf(RESULT_MARKER, decoded.second)
            } else {
                index = html.indexOf(RESULT_MARKER, start)
            }
        }
        return result
    }

    /**
     * [start] konumundan başlayan (açılış tırnağından sonraki) JavaScript string'ini çözer.
     * Çözülmüş metni ve kapanış tırnağından sonraki konumu döndürür.
     */
    fun decodeJsString(src: String, start: Int): Pair<String, Int>? {
        val sb = StringBuilder()
        var i = start
        while (i < src.length) {
            val c = src[i]
            when {
                c == '"' -> return sb.toString() to i + 1
                c == '\\' && i + 1 < src.length -> {
                    val n = src[i + 1]
                    i += 2
                    when (n) {
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'v' -> sb.append('\u000B')
                        '0' -> sb.append('\u0000')
                        'x' -> {
                            if (i + 2 > src.length) return null
                            sb.append(src.substring(i, i + 2).toInt(16).toChar())
                            i += 2
                        }
                        'u' -> {
                            if (i < src.length && src[i] == '{') {
                                val end = src.indexOf('}', i)
                                if (end < 0) return null
                                sb.appendCodePoint(src.substring(i + 1, end).toInt(16))
                                i = end + 1
                            } else {
                                if (i + 4 > src.length) return null
                                sb.append(src.substring(i, i + 4).toInt(16).toChar())
                                i += 4
                            }
                        }
                        '\r' -> if (i < src.length && src[i] == '\n') i++ // satır devamı
                        '\n', ' ', ' ' -> Unit
                        else -> sb.append(n)
                    }
                    continue
                }
                else -> sb.append(c)
            }
            i++
        }
        return null
    }

    private fun parseProfile(user: JSONObject): Profile? {
        if (user.optString("__typename") != "User") return null
        val core = user.optJSONObject("core")
        val legacy = user.optJSONObject("legacy")
        val handle = core?.optStringOrNull("screen_name") ?: legacy?.optStringOrNull("screen_name") ?: return null
        val name = core?.optStringOrNull("name") ?: legacy?.optStringOrNull("name") ?: handle
        val avatar = user.optJSONObject("avatar")?.optStringOrNull("image_url")
            ?: legacy?.optStringOrNull("profile_image_url_https")
        return Profile(handle, name, avatar?.let(::biggerAvatar))
    }

    private class TimelineScan(
        val tweets: List<Pair<JSONObject, Boolean>>,
        val instructionsFound: Boolean,
        val tweetEntries: Int,
    )

    /** Zaman tüneli talimatlarındaki (instructions) tweet sonuçlarını sırayla bulur. */
    private fun findTimelineEntries(root: JSONObject): TimelineScan {
        val out = mutableListOf<Pair<JSONObject, Boolean>>()
        val instructionLists = mutableListOf<JSONArray>()
        collectArrays(root, "instructions", instructionLists)
        var tweetEntries = 0

        for (instructions in instructionLists) {
            for (i in 0 until instructions.length()) {
                val instruction = instructions.optJSONObject(i) ?: continue
                val type = instruction.optString("__typename", instruction.optString("type"))
                val entries = mutableListOf<JSONObject>()
                instruction.optJSONObject("entry")?.let(entries::add)
                instruction.optJSONArray("entries")?.let { arr ->
                    for (j in 0 until arr.length()) arr.optJSONObject(j)?.let(entries::add)
                }
                for (entry in entries) {
                    val entryId = entry.optString("entryId", entry.optString("entry_id"))
                    if (entryId.startsWith("promoted")) continue // reklamlar
                    if (entryId.startsWith("tweet-") || entryId.startsWith("profile-conversation-")) tweetEntries++
                    val pinned = type == "TimelinePinEntry" || entry.toString().contains("\"context_type\":\"Pin\"")
                    val tweetResults = mutableListOf<JSONObject>()
                    collectTweetResults(entry, tweetResults)
                    tweetResults.forEach { out += it to pinned }
                }
            }
        }
        return TimelineScan(out, instructionLists.isNotEmpty(), tweetEntries)
    }

    private fun collectArrays(obj: Any?, key: String, out: MutableList<JSONArray>) {
        when (obj) {
            is JSONObject -> obj.keys().forEach { k ->
                val v = obj.opt(k)
                if (k == key && v is JSONArray) out += v else collectArrays(v, key, out)
            }
            is JSONArray -> for (i in 0 until obj.length()) collectArrays(obj.opt(i), key, out)
        }
    }

    /** Bir girdideki en üst seviye tweet'leri toplar (alıntılanan tweet'lerin içine inmez). */
    private fun collectTweetResults(obj: Any?, out: MutableList<JSONObject>) {
        when (obj) {
            is JSONObject -> {
                val result = obj.optJSONObject("tweet_results")?.optJSONObject("result")
                if (result != null) {
                    unwrapTweet(result)?.let(out::add)
                    return
                }
                obj.keys().forEach { collectTweetResults(obj.opt(it), out) }
            }
            is JSONArray -> for (i in 0 until obj.length()) collectTweetResults(obj.opt(i), out)
        }
    }

    private fun unwrapTweet(result: JSONObject): JSONObject? = when (result.optString("__typename")) {
        "Tweet" -> result
        "TweetWithVisibilityResults" -> result.optJSONObject("tweet")
        else -> if (result.has("rest_id")) result else null
    }

    private fun parseTweet(tweet: JSONObject, account: String, pinned: Boolean, now: Long): Post? {
        val id = tweet.optStringOrNull("rest_id") ?: tweet.optJSONObject("legacy")?.optStringOrNull("id_str") ?: return null

        // Yeniden paylaşım: içerik asıl tweet'ten, zaman ve kimlik yeniden paylaşımdan alınır.
        val retweeted = (tweet.optJSONObject("legacy")?.optJSONObject("retweeted_status_result")
            ?: tweet.optJSONObject("retweeted_status_result"))?.optJSONObject("result")?.let(::unwrapTweet)
        if (retweeted != null) {
            val original = parseTweet(retweeted, account, pinned = false, now = now) ?: return null
            val retweeter = tweet.optJSONObject("core")?.optJSONObject("user_results")?.optJSONObject("result")
                ?.let(::parseProfile)?.handle ?: account
            return original.copy(
                id = id,
                createdAt = tweetTime(tweet, id),
                isPinned = pinned,
                retweetedBy = retweeter,
                fetchedAt = now,
            )
        }

        val details = tweet.optJSONObject("details")
        val legacy = tweet.optJSONObject("legacy")

        val author = tweet.optJSONObject("core")?.optJSONObject("user_results")?.optJSONObject("result")
        val authorProfile = author?.let(::parseProfile)
        val handle = authorProfile?.handle ?: account
        val name = authorProfile?.name ?: handle

        val media = parseMedia(tweet)
        val quoted = tweet.optJSONObject("quoted_tweet_results")?.optJSONObject("result")
            ?.let(::unwrapTweet)?.let(::parseQuoted)

        val text = tweetText(tweet, stripTrailingLink = media.isNotEmpty() || quoted != null)
        val createdAt = tweetTime(tweet, id)
        val replyTo = legacy?.optStringOrNull("in_reply_to_screen_name")
            ?: details?.optStringOrNull("in_reply_to_screen_name")
            ?: tweet.optStringOrNull("in_reply_to_screen_name")
            ?: tweet.optJSONObject("reply_to_user_results")?.optJSONObject("result")?.let(::parseProfile)?.handle

        val counts = tweet.optJSONObject("counts") ?: legacy
        val views = tweet.optJSONObject("views")?.optStringOrNull("count")?.toLongOrNull()

        return Post(
            id = id,
            account = account,
            authorHandle = handle,
            authorName = name,
            authorAvatar = authorProfile?.avatarUrl,
            // X yanıtların başındaki @bahsetmeleri arayüzde göstermiyor; etiket zaten var.
            text = if (replyTo != null) text.replace(LEADING_MENTIONS, "").ifBlank { text } else text,
            createdAt = createdAt,
            url = "https://x.com/$handle/status/$id",
            media = media,
            quoted = quoted,
            isPinned = pinned,
            replyTo = replyTo,
            replyCount = counts?.optLong("reply_count") ?: 0,
            retweetCount = counts?.optLong("retweet_count") ?: 0,
            likeCount = counts?.optLong("favorite_count") ?: 0,
            viewCount = views,
            fetchedAt = now,
        )
    }

    private fun tweetTime(tweet: JSONObject, id: String): Long =
        tweet.optJSONObject("details")?.optLong("created_at_ms", 0L)?.takeIf { it > 0 }
            ?: tweet.optJSONObject("legacy")?.optStringOrNull("created_at")?.let(::parseLegacyDate)
            ?: snowflakeTime(id)

    private fun parseQuoted(tweet: JSONObject): QuotedPost? {
        val id = tweet.optStringOrNull("rest_id") ?: return null
        val author = tweet.optJSONObject("core")?.optJSONObject("user_results")?.optJSONObject("result")?.let(::parseProfile)
        val handle = author?.handle ?: "i"
        val hasMedia = parseMedia(tweet).isNotEmpty()
        return QuotedPost(
            id = id,
            authorHandle = handle,
            authorName = author?.name ?: handle,
            text = tweetText(tweet, stripTrailingLink = hasMedia || tweet.has("quoted_tweet_results")),
            url = "https://x.com/$handle/status/$id",
        )
    }

    private fun tweetText(tweet: JSONObject, stripTrailingLink: Boolean): String {
        val note = tweet.optJSONObject("note_tweet")?.optJSONObject("note_tweet_results")?.optJSONObject("result")
        var text = note?.optStringOrNull("text")
            ?: tweet.optJSONObject("details")?.optStringOrNull("full_text")
            ?: tweet.optJSONObject("legacy")?.optStringOrNull("full_text")
            ?: ""

        // t.co kısaltmalarını gerçek adreslere çevir.
        val urls = mutableMapOf<String, String>()
        collectUrlEntities(note?.optJSONObject("entity_set"), urls)
        collectUrlEntities(tweet.opt("url_entities"), urls)
        collectUrlEntities(tweet.optJSONObject("legacy")?.optJSONObject("entities"), urls)
        for ((short, expanded) in urls) text = text.replace(short, expanded)

        if (stripTrailingLink) {
            // Medya ve alıntı tweet'ler metnin sonuna kendi t.co bağlantısını ekler.
            text = text.replace(Regex("""\s*https://t\.co/\w+\s*$"""), "")
        }
        return unescapeHtml(text).trim()
    }

    private fun collectUrlEntities(obj: Any?, out: MutableMap<String, String>) {
        when (obj) {
            is JSONObject -> {
                val short = obj.optStringOrNull("url")
                val expanded = obj.optStringOrNull("expanded_url")
                if (short != null && expanded != null && short.startsWith("https://t.co/")) {
                    out[short] = expanded
                } else {
                    obj.keys().forEach { collectUrlEntities(obj.opt(it), out) }
                }
            }
            is JSONArray -> for (i in 0 until obj.length()) collectUrlEntities(obj.opt(i), out)
        }
    }

    private fun parseMedia(tweet: JSONObject): List<Media> {
        val arr = tweet.optJSONArray("media_entities2")
            ?: tweet.optJSONObject("legacy")?.optJSONObject("extended_entities")?.optJSONArray("media")
            ?: return emptyList()
        val list = mutableListOf<Media>()
        for (i in 0 until arr.length()) {
            val m = arr.optJSONObject(i) ?: continue
            val image = m.optStringOrNull("media_url_https") ?: continue
            val info = m.optJSONObject("original_info")
            val type = when (m.optString("type")) {
                "video" -> MediaType.VIDEO
                "animated_gif" -> MediaType.GIF
                else -> MediaType.PHOTO
            }
            list += Media(
                type = type,
                imageUrl = image,
                videoUrl = if (type == MediaType.PHOTO) null else bestMp4(m.optJSONObject("video_info")),
                width = info?.optInt("width") ?: 0,
                height = info?.optInt("height") ?: 0,
            )
        }
        return list
    }

    /** Mobil veri için makul (≤ ~2 Mbps) en yüksek kaliteli MP4'ü seçer. */
    private fun bestMp4(videoInfo: JSONObject?): String? {
        val variants = videoInfo?.optJSONArray("variants") ?: return null
        val mp4s = (0 until variants.length()).mapNotNull { variants.optJSONObject(it) }
            .filter { it.optString("content_type") == "video/mp4" && it.has("url") }
            .sortedBy { it.optLong("bitrate") }
        if (mp4s.isEmpty()) return null
        return (mp4s.lastOrNull { it.optLong("bitrate") <= 2_500_000 } ?: mp4s.first()).optString("url")
    }

    private fun parseLegacyDate(value: String): Long? = runCatching {
        SimpleDateFormat("EEE MMM dd HH:mm:ss Z yyyy", Locale.ENGLISH).parse(value)?.time
    }.getOrNull()

    /** Tweet kimliğinden (snowflake) oluşturulma zamanını hesaplar. */
    private fun snowflakeTime(id: String): Long = id.toLongOrNull()?.let { (it shr 22) + 1288834974657L } ?: 0L

    private fun biggerAvatar(url: String) = url.replace("_normal.", "_bigger.")

    private fun unescapeHtml(s: String) = s
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&amp;", "&")

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotEmpty() } else null
}
