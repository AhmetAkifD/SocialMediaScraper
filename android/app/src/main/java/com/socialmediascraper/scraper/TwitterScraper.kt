package com.socialmediascraper.scraper

import com.socialmediascraper.data.Post
import com.socialmediascraper.data.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

sealed interface ScrapeResult {
    data class Success(val profile: Profile?, val posts: List<Post>) : ScrapeResult
    data class Failure(val message: String, val notFound: Boolean = false) : ScrapeResult
}

/** Kullanıcıya gösterilen hata mesajları. */
object ScrapeErrors {
    const val LAYOUT_CHANGED =
        "X sayfa yapısı değişmiş, gönderiler okunamadı. Uygulamanın güncellenmesi gerekiyor."
    const val PROTECTED = "Hesap gizli; gönderileri görülemiyor"
    const val RATE_LIMIT = "X istek sınırına ulaşıldı, daha sonra tekrar deneyin"
    const val LOGIN_REQUIRED = "X oturumu sona ermiş. Ayarlar'dan tekrar giriş yapın."
    const val NO_INTERNET = "İnternet bağlantısı yok"

    fun notFound(handle: String) = "@$handle bulunamadı"

    /** Ayrıştırılmış sayfaya bakarak bir hata olup olmadığına karar verir. */
    fun check(page: ParsedProfilePage, handle: String): ScrapeResult.Failure? = when {
        page.isProtected && page.posts.isEmpty() -> ScrapeResult.Failure(PROTECTED)
        page.userMissing -> ScrapeResult.Failure(notFound(handle), notFound = true)
        !page.timelineFound -> ScrapeResult.Failure(LAYOUT_CHANGED)
        page.posts.isEmpty() && page.tweetEntries > 0 -> ScrapeResult.Failure(LAYOUT_CHANGED)
        else -> null
    }
}

class TwitterScraper(
    private val client: OkHttpClient = defaultClient,
) {
    suspend fun fetch(handle: String): ScrapeResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://x.com/$handle")
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                when {
                    response.code == 404 -> ScrapeResult.Failure(ScrapeErrors.notFound(handle), notFound = true)
                    response.code == 429 -> ScrapeResult.Failure(ScrapeErrors.RATE_LIMIT)
                    !response.isSuccessful -> ScrapeResult.Failure("X sunucusu hata döndürdü (HTTP ${response.code})")
                    response.request.url.encodedPath.contains("login") ->
                        ScrapeResult.Failure("X giriş sayfasına yönlendirdi")
                    !TwitterParser.hasEmbeddedData(body) -> ScrapeResult.Failure(ScrapeErrors.LAYOUT_CHANGED)
                    else -> {
                        val page = TwitterParser.parse(body, handle)
                        ScrapeErrors.check(page, handle) ?: ScrapeResult.Success(page.profile, page.posts)
                    }
                }
            }
        } catch (e: UnknownHostException) {
            ScrapeResult.Failure(ScrapeErrors.NO_INTERNET)
        } catch (e: SocketTimeoutException) {
            ScrapeResult.Failure("X yanıt vermedi (zaman aşımı)")
        } catch (e: IOException) {
            ScrapeResult.Failure("Bağlantı hatası: ${e.message ?: e.javaClass.simpleName}")
        } catch (e: Exception) {
            ScrapeResult.Failure("Beklenmeyen hata: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36"

        val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()
        }

        private val HANDLE_REGEX = Regex("^[A-Za-z0-9_]{1,15}$")
        private val URL_REGEX = Regex("""(?:https?://)?(?:www\.|mobile\.)?(?:twitter|x)\.com/([A-Za-z0-9_]{1,15})""", RegexOption.IGNORE_CASE)

        /** "@Hesap", "Hesap" veya "https://x.com/Hesap" girdilerini kullanıcı adına çevirir. */
        fun normalizeHandle(input: String): String? {
            val trimmed = input.trim()
            URL_REGEX.find(trimmed)?.let { return it.groupValues[1] }
            val handle = trimmed.removePrefix("@").trim()
            return handle.takeIf { HANDLE_REGEX.matches(it) }
        }
    }
}
