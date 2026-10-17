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
                    response.code == 404 -> ScrapeResult.Failure("@$handle bulunamadı", notFound = true)
                    response.code == 429 -> ScrapeResult.Failure("X istek sınırına ulaşıldı, daha sonra tekrar denenecek")
                    !response.isSuccessful -> ScrapeResult.Failure("X sunucusu hata döndürdü (HTTP ${response.code})")
                    else -> {
                        val page = TwitterParser.parse(body, handle)
                        if (page.profile == null && page.posts.isEmpty()) {
                            if (response.request.url.encodedPath.contains("login")) {
                                ScrapeResult.Failure("X giriş sayfasına yönlendirdi")
                            } else {
                                ScrapeResult.Failure("Sayfada gönderi verisi bulunamadı (hesap gizli olabilir ya da X sayfa yapısını değiştirmiş olabilir)")
                            }
                        } else {
                            ScrapeResult.Success(page.profile, page.posts)
                        }
                    }
                }
            }
        } catch (e: UnknownHostException) {
            ScrapeResult.Failure("İnternet bağlantısı yok")
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
