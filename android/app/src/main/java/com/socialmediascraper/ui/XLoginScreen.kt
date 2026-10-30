package com.socialmediascraper.ui

import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.socialmediascraper.scraper.XSession
import kotlinx.coroutines.delay

/** x.com'un kendi giriş sayfasını gösterir; oturum çerezi oluşunca kapanır. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XLoginScreen(onDone: (loggedIn: Boolean) -> Unit) {
    LaunchedEffect(Unit) {
        while (!XSession.refresh()) delay(1_000)
        CookieManager.getInstance().flush()
        onDone(true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("X'e giriş yap") },
                navigationIcon = { IconButton(onClick = { onDone(XSession.refresh()) }) { Icon(Icons.Filled.Close, "Kapat") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                "Giriş bilgileriniz yalnızca x.com'a gönderilir; uygulama şifrenizi görmez ve saklamaz.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        XSession.configure(this)
                        webViewClient = WebViewClient()
                        loadUrl("https://x.com/i/flow/login")
                    }
                },
                onRelease = { it.destroy() },
            )
        }
    }
}
