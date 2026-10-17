package com.socialmediascraper.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.socialmediascraper.data.TrackedAccount
import com.socialmediascraper.notify.Notifier

private val INTERVALS = listOf(15, 30, 60, 180, 360, 720, 1440)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, snackbar: SnackbarHostState, onBack: () -> Unit) {
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val adding by vm.adding.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<TrackedAccount?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.updateSettings { it.copy(notificationsEnabled = granted) }
    }

    fun submit() {
        if (input.isNotBlank()) vm.addAccount(input) { input = "" }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ayarlar") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar, Modifier.imePadding()) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item { SectionTitle("Takip edilen hesaplar") }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        readOnly = adding,
                        label = { Text("X (Twitter) hesabı") },
                        placeholder = { Text("@TwitterHesabi") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (!adding) submit() }),
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { submit() }, enabled = !adding && input.isNotBlank()) {
                        if (adding) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Ekle")
                    }
                }
                Text(
                    "Kullanıcı adını @ ile ya da profil bağlantısıyla yazabilirsiniz. Hesap eklenirken doğrulanır.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            if (accounts.isEmpty()) {
                item {
                    Text(
                        "Henüz hesap yok.",
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(accounts, key = { it.handle }) { account ->
                AccountRow(account, onDelete = { pendingDelete = account })
            }

            item { HorizontalDivider(Modifier.padding(top = 12.dp)) }
            item { SectionTitle("Otomatik kontrol") }
            item {
                SwitchRow(
                    title = "Arka planda kontrol et",
                    subtitle = "Uygulama kapalıyken de hesapları belirli aralıklarla tarar",
                    checked = settings.backgroundEnabled,
                    onChange = { v -> vm.updateSettings { it.copy(backgroundEnabled = v) } },
                )
            }
            item {
                IntervalPicker(
                    value = settings.intervalMinutes,
                    enabled = settings.backgroundEnabled,
                    onChange = { v -> vm.updateSettings { it.copy(intervalMinutes = v) } },
                )
            }
            item {
                SwitchRow(
                    title = "Yeni gönderi bildirimi",
                    subtitle = "Arka plan kontrolünde yeni gönderi bulunduğunda bildirim gönder",
                    checked = settings.notificationsEnabled && Notifier.canNotify(context),
                    enabled = settings.backgroundEnabled,
                    onChange = { v ->
                        if (v && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifier.canNotify(context)) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            vm.updateSettings { it.copy(notificationsEnabled = v) }
                        }
                    },
                )
            }
            item {
                Text(
                    "Android pil tasarrufu nedeniyle kontroller birkaç dakika gecikebilir. En kısa aralık 15 dakikadır.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            item { HorizontalDivider(Modifier.padding(top = 12.dp)) }
            item { SectionTitle("Veriler") }
            item {
                OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text("Kayıtlı gönderileri temizle")
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    pendingDelete?.let { account ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("@${account.handle} kaldırılsın mı?") },
            text = { Text("Bu hesaba ait kayıtlı gönderiler de silinir.") },
            confirmButton = {
                TextButton(onClick = { vm.removeAccount(account.handle); pendingDelete = null }) { Text("Kaldır") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Vazgeç") } },
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Gönderiler temizlensin mi?") },
            text = { Text("Takip edilen hesaplar silinmez; gönderiler bir sonraki yenilemede tekrar çekilir.") },
            confirmButton = { TextButton(onClick = { vm.clearPosts(); confirmClear = false }) { Text("Temizle") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Vazgeç") } },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun AccountRow(account: TrackedAccount, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(account.avatarUrl, account.displayName ?: account.handle)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(account.displayName ?: "@${account.handle}", style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text("@${account.handle}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            val status = when {
                account.lastError != null -> account.lastError
                account.lastCheckedAt != null -> "Son kontrol: ${formatDateTime(account.lastCheckedAt)}"
                else -> null
            }
            status?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (account.lastError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                )
            }
        }
        IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Kaldır") }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntervalPicker(value: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        OutlinedTextField(
            value = "Her ${formatInterval(value)}",
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text("Kontrol sıklığı") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            INTERVALS.forEach { minutes ->
                DropdownMenuItem(
                    text = { Text("Her ${formatInterval(minutes)}") },
                    onClick = { onChange(minutes); expanded = false },
                )
            }
        }
    }
}
