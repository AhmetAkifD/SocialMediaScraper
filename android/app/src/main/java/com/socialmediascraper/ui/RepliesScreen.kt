package com.socialmediascraper.ui

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.socialmediascraper.data.Post
import com.socialmediascraper.scraper.ConversationResult
import com.socialmediascraper.scraper.XWebScraper
import kotlinx.coroutines.launch

/**
 * Bir gönderiye gelen yanıtları gösterir. Veriler, görünmeyen bir WebView'da açılan
 * gönderi sayfasından (oturum açılmış X hesabıyla) yakalanır; yalnızca kullanıcı
 * istediğinde daha fazlası yüklenir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepliesScreen(
    handle: String,
    tweetId: String,
    initialPost: Post?,
    onBack: () -> Unit,
    onShowReplies: (Post) -> Unit,
) {
    val context = LocalContext.current
    val scraper = remember { XWebScraper(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var webView by remember { mutableStateOf<WebView?>(null) }
    var focal by remember { mutableStateOf(initialPost) }
    var replies by remember { mutableStateOf<List<Post>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var endReached by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun apply(result: ConversationResult) {
        when (result) {
            is ConversationResult.Failure -> message = result.message
            is ConversationResult.Success -> {
                result.focal?.let { focal = it }
                replies = result.replies
                endReached = result.endReached
                message = result.note ?: when {
                    result.replies.isEmpty() -> "Bu gönderiye henüz yanıt yok"
                    result.endReached && result.newCount == 0 -> "Başka yanıt bulunamadı"
                    else -> null
                }
            }
        }
        loading = false
    }

    LaunchedEffect(webView) {
        val view = webView ?: return@LaunchedEffect
        apply(scraper.openConversation(view, handle, tweetId))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Yanıtlar") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            // Veriyi toplayan tarayıcı: düzgün çalışması için tam boyutta ama görünmez.
            AndroidView(
                modifier = Modifier.fillMaxSize().alpha(0f),
                factory = { ctx ->
                    WebView(ctx).also {
                        scraper.attach(it)
                        webView = it
                    }
                },
                onRelease = { it.destroy() },
            )
            LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                focal?.let { item(key = "focal") { PostCard(it, onShowReplies = null) } }
                items(replies, key = { it.id }) { reply ->
                    val nested = reply.replyToId != null && reply.replyToId != tweetId
                    PostCard(
                        reply,
                        modifier = if (nested) Modifier.padding(start = 28.dp) else Modifier,
                        showReplyTo = nested,
                        onShowReplies = onShowReplies,
                    )
                }
                item(key = "footer") {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                    ) {
                        message?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                                textAlign = TextAlign.Center,
                            )
                        }
                        when {
                            loading -> {
                                CircularProgressIndicator(Modifier.size(28.dp))
                                Text(
                                    if (replies.isEmpty()) "Yanıtlar yükleniyor…" else "Daha fazla yanıt yükleniyor…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                            replies.isNotEmpty() && !endReached -> OutlinedButton(onClick = {
                                val view = webView ?: return@OutlinedButton
                                loading = true
                                scope.launch { apply(scraper.loadMoreReplies(view, tweetId)) }
                            }) { Text("Daha fazla yanıt yükle") }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}
