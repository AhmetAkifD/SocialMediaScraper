package com.socialmediascraper.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    vm: MainViewModel,
    snackbar: SnackbarHostState,
    onOpenSettings: () -> Unit,
    onLogin: () -> Unit,
    onDeepFetch: (handle: String) -> Unit,
) {
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val posts by vm.posts.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val xLoggedIn by vm.xLoggedIn.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var showReplies by rememberSaveable { mutableStateOf(true) }

    // Filtrelenen hesap silinirse filtreyi kaldır.
    if (filter != null && accounts.none { it.handle.equals(filter, ignoreCase = true) }) filter = null

    val visible = remember(posts, filter, showReplies) {
        posts.filter { p ->
            (filter == null || p.account.equals(filter, ignoreCase = true)) && (showReplies || p.replyTo == null)
        }
    }
    val hasReplies = remember(posts) { posts.any { it.replyTo != null } }
    val failing = accounts.filter { it.lastError != null }
    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gönderiler") },
                actions = {
                    IconButton(onClick = { vm.refresh() }, enabled = !refreshing) {
                        Icon(Icons.Outlined.Refresh, "Yenile")
                    }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, "Ayarlar") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { vm.refresh() },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            if (accounts.isEmpty()) {
                EmptyState(
                    title = "Henüz hesap eklenmedi",
                    body = "Gönderilerini takip etmek istediğiniz X (Twitter) hesaplarını Ayarlar'dan ekleyin.",
                    action = "Hesap ekle",
                    onAction = onOpenSettings,
                )
                return@PullToRefreshBox
            }
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                if (accounts.size > 1 || hasReplies) {
                    item(key = "filters") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (hasReplies) {
                                item {
                                    FilterChip(
                                        selected = showReplies,
                                        onClick = { showReplies = !showReplies },
                                        label = { Text("Yanıtlar") },
                                    )
                                }
                            }
                            item {
                                FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("Tümü") })
                            }
                            items(accounts, key = { it.handle }) { a ->
                                FilterChip(
                                    selected = filter.equals(a.handle, ignoreCase = true),
                                    onClick = { filter = if (filter.equals(a.handle, true)) null else a.handle },
                                    label = { Text("@${a.handle}") },
                                    leadingIcon = if (a.lastError != null) {
                                        { Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error) }
                                    } else null,
                                )
                            }
                        }
                    }
                }
                if (failing.isNotEmpty()) {
                    item(key = "errors") {
                        Text(
                            failing.joinToString("\n") { "@${it.handle}: ${it.lastError}" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
                if (visible.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            if (refreshing) "Gönderiler çekiliyor…" else "Henüz gönderi yok. Yenilemek için aşağı çekin.",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                        )
                    }
                }
                items(visible, key = { it.id }) { post -> PostCard(post) }
                item(key = "more") {
                    LoadMoreFooter(
                        handle = filter ?: accounts.singleOrNull()?.handle,
                        loggedIn = xLoggedIn,
                        onLogin = onLogin,
                        onDeepFetch = onDeepFetch,
                    )
                }
            }
        }
    }
}

/** Liste sonunda: giriş yapılmışsa seçili hesabın yanıtlarını/eski gönderilerini elle yükleme. */
@Composable
private fun LoadMoreFooter(handle: String?, loggedIn: Boolean, onLogin: () -> Unit, onDeepFetch: (String) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
    ) {
        when {
            !loggedIn -> {
                Text(
                    "Yanıtlar ve son 5'ten eski gönderiler için X'e giriş yapın.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onLogin) { Text("X'e giriş yap") }
            }
            handle == null -> Text(
                "Yanıtları ve eski gönderileri yüklemek için yukarıdan bir hesap seçin.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center,
            )
            else -> OutlinedButton(onClick = { onDeepFetch(handle) }) {
                Text("@$handle: yanıtları ve eski gönderileri yükle")
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, body: String, action: String, onAction: () -> Unit) {
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(body, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction) { Text(action) }
        }
    }
}
