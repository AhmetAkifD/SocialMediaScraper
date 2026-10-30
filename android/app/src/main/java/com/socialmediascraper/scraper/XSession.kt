package com.socialmediascraper.scraper

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Uygulama içi WebView'da açılan X oturumu.
 *
 * Şifre uygulamaya hiç gelmez: kullanıcı x.com'un kendi giriş sayfasında giriş yapar,
 * uygulama yalnızca WebView'ın sakladığı oturum çerezinin varlığına bakar.
 */
object XSession {
    private const val X_URL = "https://x.com"

    private val _loggedIn = MutableStateFlow(false)
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    fun refresh(): Boolean {
        val cookies = runCatching { CookieManager.getInstance().getCookie(X_URL) }.getOrNull().orEmpty()
        val value = cookies.split(';').any { it.trim().startsWith("auth_token=") }
        _loggedIn.value = value
        return value
    }

    fun logout() {
        CookieManager.getInstance().apply {
            removeAllCookies(null)
            flush()
        }
        WebStorage.getInstance().deleteAllData()
        _loggedIn.value = false
    }

    /** X'in giriş ve zaman tüneli sayfaları için ortak WebView ayarları. */
    @SuppressLint("SetJavaScriptEnabled")
    fun configure(webView: WebView) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            // "wv" işareti olan WebView tarayıcı kimliği bazı sitelerde girişi engelliyor.
            userAgentString = userAgentString.replace("; wv", "").replace(Regex("""Version/\d+(\.\d+)* """), "")
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
    }
}
