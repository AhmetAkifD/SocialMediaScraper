package com.socialmediascraper.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.socialmediascraper.data.Media
import com.socialmediascraper.data.MediaType
import com.socialmediascraper.data.Post
import com.socialmediascraper.data.QuotedPost

@Composable
fun PostCard(post: Post, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(
        modifier
            .fillMaxWidth()
            .clickable { openUrl(context, post.url) }
            .padding(start = 16.dp, end = 8.dp, top = 12.dp),
    ) {
        if (post.isPinned) ContextLabel(Icons.Outlined.PushPin, "Sabitlenmiş")
        post.retweetedBy?.let { ContextLabel(Icons.Outlined.Repeat, "@$it yeniden paylaştı") }
        Row {
            Avatar(post.authorAvatar, post.authorName)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        post.authorName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "@${post.authorHandle} · ${formatRelative(post.createdAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                post.replyTo?.let {
                    Text(
                        "@$it kullanıcısına yanıt",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                if (post.text.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(linkify(post.text, MaterialTheme.colorScheme.primary), style = MaterialTheme.typography.bodyMedium)
                }
                if (post.media.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    MediaGrid(post.media, onClick = { m -> openUrl(context, m.videoUrl ?: m.imageUrl.toLargeImage()) })
                }
                post.quoted?.let {
                    Spacer(Modifier.height(8.dp))
                    QuotedBox(it, onClick = { openUrl(context, it.url) })
                }
                PostActions(post, context)
            }
        }
        HorizontalDivider(Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

@Composable
private fun ContextLabel(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 28.dp, bottom = 4.dp)) {
        Icon(icon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
fun Avatar(url: String?, name: String, size: Int = 40) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.take(1).uppercase(), style = MaterialTheme.typography.titleMedium)
        if (url != null) {
            AsyncImage(model = url, contentDescription = name, modifier = Modifier.size(size.dp), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
private fun MediaGrid(media: List<Media>, onClick: (Media) -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val items = media.take(4)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (items.size == 1) {
            val m = items[0]
            val ratio = if (m.width > 0 && m.height > 0) (m.width.toFloat() / m.height).coerceIn(0.75f, 1.9f) else 16f / 9f
            MediaTile(m, Modifier.fillMaxWidth().aspectRatio(ratio), onClick)
        } else {
            items.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    row.forEach { m -> MediaTile(m, Modifier.weight(1f).aspectRatio(if (items.size == 2) 0.9f else 1.6f), onClick) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MediaTile(media: Media, modifier: Modifier, onClick: (Media) -> Unit) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant).clickable { onClick(media) }, contentAlignment = Alignment.Center) {
        AsyncImage(
            model = media.imageUrl.toMediumImage(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        if (media.type != MediaType.PHOTO) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Oynat", tint = Color.White)
            }
            if (media.type == MediaType.GIF) {
                Text(
                    "GIF",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
    }
}

@Composable
private fun QuotedBox(quoted: QuotedPost, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Row {
            Text(quoted.authorName, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
            Spacer(Modifier.width(4.dp))
            Text("@${quoted.authorHandle}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary, maxLines = 1)
        }
        if (quoted.text.isNotBlank()) {
            Text(quoted.text, style = MaterialTheme.typography.bodySmall, maxLines = 6, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PostActions(post: Post, context: Context) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Stat(Icons.Outlined.ChatBubbleOutline, post.replyCount)
        Stat(Icons.Outlined.Repeat, post.retweetCount)
        Stat(Icons.Outlined.FavoriteBorder, post.likeCount)
        post.viewCount?.let { Stat(Icons.Outlined.BarChart, it) }
        Spacer(Modifier.weight(1f))
        SmallIconButton(Icons.Outlined.ContentCopy, "Metni kopyala") { copyText(context, post.text) }
        SmallIconButton(Icons.Outlined.Share, "Paylaş") { share(context, post.url) }
        SmallIconButton(Icons.AutoMirrored.Outlined.OpenInNew, "X'te aç") { openUrl(context, post.url) }
    }
}

@Composable
private fun Stat(icon: ImageVector, value: Long) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 14.dp)) {
        Icon(icon, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.width(3.dp))
        Text(formatCount(value), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun SmallIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(icon, description, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.secondary)
    }
}

private val URL_PATTERN = Regex("""https?://[^\s]+""")

private fun linkify(text: String, color: Color): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (match in URL_PATTERN.findAll(text)) {
        append(text.substring(last, match.range.first))
        val url = match.value.trimEnd('.', ',', ')', '!', '?', ';', ':')
        withLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = color)))) {
            append(url.removePrefix("https://").removePrefix("http://").removePrefix("www.").let {
                if (it.length > 40) it.take(38) + "…" else it
            })
        }
        last = match.range.first + url.length
    }
    append(text.substring(last))
}

private fun String.toMediumImage() =
    if (contains("pbs.twimg.com")) substringBefore('?') + "?name=medium" else this

private fun String.toLargeImage() =
    if (contains("pbs.twimg.com")) substringBefore('?') + "?name=large" else this

fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        Toast.makeText(context, "Bağlantı açılamadı", Toast.LENGTH_SHORT).show()
    }
}

private fun share(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
    context.startActivity(Intent.createChooser(intent, "Paylaş").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun copyText(context: Context, text: String) {
    val cm = context.getSystemService(ClipboardManager::class.java)
    cm.setPrimaryClip(ClipData.newPlainText("Gönderi", text))
    Toast.makeText(context, "Kopyalandı", Toast.LENGTH_SHORT).show()
}
