package com.socialmediascraper.ui

import android.webkit.WebView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.socialmediascraper.scraper.DeepProgress
import com.socialmediascraper.scraper.XWebScraper

/**
 * Oturum açılmış tarayıcıda hesabın "Yanıtlar" sekmesini açıp aşağı kaydırarak
 * yanıtları ve eski gönderileri toplar. Sayfa görünür kalır; X bir doğrulama
 * isterse kullanıcı doğrudan buradan yanıtlayabilir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeepFetchScreen(vm: MainViewModel, handle: String, onDone: () -> Unit) {
    val context = LocalContext.current
    val scraper = remember { XWebScraper(context.applicationContext) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableStateOf(DeepProgress(0, 0)) }

    LaunchedEffect(webView) {
        val view = webView ?: return@LaunchedEffect
        val result = scraper.run(view, handle, vm.oldestPostTime(handle)) { progress = it }
        vm.onDeepFetchFinished(handle, result)
        onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("@$handle taranıyor") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.Filled.Close, "İptal") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(
                if (progress.pages == 0) "Yanıtlar sayfası açılıyor…"
                else "${progress.pages}. sayfa · @$handle hesabına ait ${progress.found} gönderi bulundu",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).also {
                        scraper.attach(it)
                        webView = it
                    }
                },
                onRelease = { it.destroy() },
            )
        }
    }
}
