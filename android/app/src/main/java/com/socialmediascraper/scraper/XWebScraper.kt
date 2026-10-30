package com.socialmediascraper.scraper

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.socialmediascraper.BuildConfig
import com.socialmediascraper.data.Post
import com.socialmediascraper.data.Profile
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File

data class DeepProgress(val pages: Int, val found: Int)

sealed interface ConversationResult {
    data class Success(
        val focal: Post?,
        val replies: List<Post>,
        val newCount: Int,
        /** Son denemede yeni yanıt gelmedi; muhtemelen hepsi yüklendi. */
        val endReached: Boolean,
        val note: String? = null,
    ) : ConversationResult
    data class Failure(val message: String) : ConversationResult
}

sealed interface DeepResult {
    /** [note] tarama erken bittiyse (ör. istek sınırı) kullanıcıya gösterilecek açıklama. */
    data class Success(val profile: Profile?, val posts: List<Post>, val note: String? = null) : DeepResult
    data class Failure(val message: String) : DeepResult
}

/**
 * Oturum açılmış WebView'da X sayfalarını açar, gerektiğinde aşağı kaydırarak sonrakileri
 * yükletir ve sayfanın kendi GraphQL yanıtlarını yakalayıp ayrıştırır: hesabın eski
 * gönderileri ([fetchTimeline]) ve bir gönderiye gelen yanıtlar ([openConversation]).
 *
 * Yalnızca kullanıcı istediğinde çalışır; her kaydırma X'e bir istek demektir.
 */
class XWebScraper(private val context: Context) {

    private class Captured(val status: Int, val payloads: List<JSONObject>)

    private val captured = Channel<Captured>(Channel.UNLIMITED)
    private var dumpIndex = 0

    private val bridge = object {
        @JavascriptInterface
        fun onResponse(url: String, status: Int, body: String) {
            dump(url.substringAfterLast("/").substringBefore("?"), body)
            val json = runCatching { JSONObject(body) }.getOrNull()
            captured.trySend(Captured(status, listOfNotNull(json)))
        }

        @JavascriptInterface
        fun onEmbedded(text: String) {
            dump("embedded", text)
            captured.trySend(Captured(200, TwitterParser.extractGraphQlPayloads(text)))
        }
    }

    /** WebView'ı hazırlar; sayfa yüklenmeden önce çağrılmalıdır. */
    fun attach(webView: WebView) {
        XSession.configure(webView)
        webView.addJavascriptInterface(bridge, "SMSBridge")
        val script = context.assets.open("x_hook.js").bufferedReader().use { it.readText() }
        val documentStart = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        if (documentStart) WebViewCompat.addDocumentStartJavaScript(webView, script, setOf("https://x.com"))
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                // Eski WebView sürümleri için yedek: mümkün olan en erken anda yükle.
                if (!documentStart) view.evaluateJavascript(script, null)
            }
        }
        // Önceki taramanın dökümlerini temizle (release sürümünde hiç yazılmaz).
        File(context.filesDir, "debug").apply {
            deleteRecursively()
            if (BuildConfig.DEBUG) mkdirs()
        }
    }

    private val payloads = mutableListOf<JSONObject>()
    private var rateLimited = false

    /** Yeni bir zaman tüneli/konuşma yanıtı gelene kadar bekler; gelirse true. */
    private suspend fun awaitTimeline(timeoutMs: Long): Boolean = withTimeoutOrNull(timeoutMs) {
        var found = false
        while (!found && !rateLimited) {
            val c = captured.receive()
            if (c.status == 429 || c.payloads.any(::isRateLimitError)) rateLimited = true
            payloads += c.payloads
            found = c.payloads.any { TwitterParser.parsePayloads(listOf(it), "", ownOnly = false).timelineFound }
        }
        found
    } ?: false

    private fun sessionLost(webView: WebView): Boolean {
        val url = webView.url.orEmpty()
        return url.contains("/login") || url.contains("/i/flow") || !XSession.refresh()
    }

    /**
     * Hesabın "Gönderiler" sekmesini aşağı kaydırarak eski gönderileri toplar. Hesabın
     * başkalarına yazdığı yanıtlar atlanır; yeniden paylaşımlar tutulur.
     *
     * @param oldestKnown bu hesabın cihazdaki en eski gönderisinin zamanı; tarama bundan
     * daha eski [TARGET_OLDER] gönderi bulununca durur.
     */
    suspend fun fetchTimeline(
        webView: WebView,
        handle: String,
        oldestKnown: Long?,
        onProgress: (DeepProgress) -> Unit,
    ): DeepResult {
        if (!XSession.refresh()) return DeepResult.Failure(ScrapeErrors.LOGIN_REQUIRED)
        var pages = 0

        fun current() = TwitterParser.parsePayloads(payloads, handle, ownOnly = true)
        fun ownPosts(page: ParsedProfilePage) = page.posts.filter { it.replyTo == null }

        webView.loadUrl("https://x.com/$handle")
        if (awaitTimeline(FIRST_PAGE_TIMEOUT_MS)) pages++

        if (pages > 0) {
            var idle = 0
            for (scroll in 1..MAX_SCROLLS) {
                val posts = ownPosts(current())
                onProgress(DeepProgress(pages, posts.size))
                val older = posts.count { oldestKnown == null || it.createdAt < oldestKnown }
                if (older >= TARGET_OLDER || rateLimited || idle >= 2) break

                delay(SCROLL_PAUSE_MS)
                repeat(SWIPES_PER_STEP) { swipeUp(webView) }
                if (awaitTimeline(PAGE_TIMEOUT_MS)) {
                    pages++
                    idle = 0
                } else {
                    idle++ // zaman tünelinin sonuna gelinmiş olabilir
                }
            }
        }

        val page = current()
        val posts = ownPosts(page)
        onProgress(DeepProgress(pages, posts.size))
        return when {
            sessionLost(webView) -> DeepResult.Failure(ScrapeErrors.LOGIN_REQUIRED)
            pages == 0 && rateLimited -> DeepResult.Failure(ScrapeErrors.RATE_LIMIT)
            pages == 0 && page.userMissing -> DeepResult.Failure(ScrapeErrors.notFound(handle))
            pages == 0 && page.isProtected -> DeepResult.Failure(ScrapeErrors.PROTECTED)
            pages == 0 -> DeepResult.Failure(ScrapeErrors.LAYOUT_CHANGED)
            page.posts.isEmpty() && page.tweetEntries > 0 -> DeepResult.Failure(ScrapeErrors.LAYOUT_CHANGED)
            else -> DeepResult.Success(
                profile = page.profile,
                posts = posts,
                note = if (rateLimited) "X istek sınırına ulaşıldı, tarama erken durduruldu" else null,
            )
        }
    }

    /** Bir gönderinin detay sayfasını açıp ona gelen ilk yanıtları döndürür. */
    suspend fun openConversation(webView: WebView, handle: String, tweetId: String): ConversationResult {
        if (!XSession.refresh()) return ConversationResult.Failure(ScrapeErrors.LOGIN_REQUIRED)
        webView.loadUrl("https://x.com/$handle/status/$tweetId")
        val loaded = awaitTimeline(FIRST_PAGE_TIMEOUT_MS)
        return conversationResult(webView, tweetId, loaded, previousCount = 0)
    }

    /** Sayfayı kaydırıp daha fazla yanıt yükletir. */
    suspend fun loadMoreReplies(webView: WebView, tweetId: String): ConversationResult {
        val before = TwitterParser.parseConversation(payloads, tweetId).replies.size
        rateLimited = false
        var loaded = false
        for (attempt in 1..2) {
            // X kalan yanıtların bir kısmını "Diğer yanıtları göster" düğmesinin arkasında tutar.
            webView.evaluateJavascript(SHOW_MORE_REPLIES_JS, null)
            repeat(SWIPES_PER_STEP) { swipeUp(webView) }
            if (awaitTimeline(PAGE_TIMEOUT_MS)) {
                loaded = true
                break
            }
        }
        return conversationResult(webView, tweetId, loaded = true, previousCount = before, gotNewPage = loaded)
    }

    private fun conversationResult(
        webView: WebView,
        tweetId: String,
        loaded: Boolean,
        previousCount: Int,
        gotNewPage: Boolean = loaded,
    ): ConversationResult {
        val conversation = TwitterParser.parseConversation(payloads, tweetId)
        return when {
            sessionLost(webView) -> ConversationResult.Failure(ScrapeErrors.LOGIN_REQUIRED)
            !loaded && rateLimited -> ConversationResult.Failure(ScrapeErrors.RATE_LIMIT)
            !loaded -> ConversationResult.Failure(ScrapeErrors.LAYOUT_CHANGED)
            conversation.focal == null && conversation.tweetEntries > 0 ->
                ConversationResult.Failure(ScrapeErrors.LAYOUT_CHANGED)
            else -> ConversationResult.Success(
                focal = conversation.focal,
                replies = conversation.replies,
                newCount = conversation.replies.size - previousCount,
                endReached = !gotNewPage || conversation.replies.size == previousCount,
                note = if (rateLimited) ScrapeErrors.RATE_LIMIT else null,
            )
        }
    }

    /**
     * WebView'a parmakla yukarı kaydırma hareketi gönderir. X'in sonsuz kaydırması
     * JavaScript'le yapılan kaydırmaya güvenilir şekilde tepki vermiyor.
     */
    private suspend fun swipeUp(view: WebView) {
        val x = view.width / 2f
        val startY = view.height * 0.85f
        val endY = view.height * 0.15f
        val downTime = SystemClock.uptimeMillis()
        fun send(action: Int, y: Float) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
            view.dispatchTouchEvent(event)
            event.recycle()
        }
        send(MotionEvent.ACTION_DOWN, startY)
        val steps = 12
        for (i in 1..steps) {
            delay(16)
            send(MotionEvent.ACTION_MOVE, startY + (endY - startY) * i / steps)
        }
        send(MotionEvent.ACTION_UP, endY)
        delay(400)
    }

    private fun isRateLimitError(json: JSONObject): Boolean {
        val errors = json.optJSONArray("errors") ?: return false
        return (0 until errors.length()).any { errors.optJSONObject(it)?.optInt("code") == 88 }
    }

    /** Debug sürümünde yakalanan yanıtları inceleme için diske yazar. */
    private fun dump(name: String, body: String) {
        if (!BuildConfig.DEBUG) return
        Log.d(TAG, "yakalandı: $name (${body.length} karakter)")
        runCatching {
            File(context.filesDir, "debug/%03d_%s.json".format(dumpIndex++, name.take(40))).writeText(body)
        }
    }

    private companion object {
        const val TAG = "XWebScraper"
        const val FIRST_PAGE_TIMEOUT_MS = 30_000L
        const val PAGE_TIMEOUT_MS = 10_000L
        const val SCROLL_PAUSE_MS = 1_500L
        const val MAX_SCROLLS = 20
        const val TARGET_OLDER = 40
        const val SWIPES_PER_STEP = 3

        /** Konuşmanın altındaki "daha fazla yanıt / olası spam" düğmelerine basar. */
        const val SHOW_MORE_REPLIES_JS = """
            (function () {
              var pattern = /(more replies|additional replies|probable spam|daha fazla yanıt|diğer yanıt|ek yanıt|olası spam)/i;
              document.querySelectorAll('[data-testid="cellInnerDiv"] [role="button"]').forEach(function (el) {
                if (pattern.test(el.textContent || '')) el.click();
              });
            })();
        """

    }
}
