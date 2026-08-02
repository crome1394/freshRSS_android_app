package com.crome.freshrss.ui.article

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.crome.freshrss.data.model.Article
import com.crome.freshrss.util.MediaUtils
import com.crome.freshrss.util.SafeUrls
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun shareArticle(context: Context, title: String, url: String) {
    // Only include the URL in the share body if it is a safe http(s) link.
    val safeUrl = SafeUrls.normalizeHttpUrl(url).orEmpty()
    val text = buildString {
        if (title.isNotBlank()) append(title)
        if (safeUrl.isNotBlank()) {
            if (isNotEmpty()) append("\n")
            append(safeUrl)
        }
    }
    if (text.isBlank()) return
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Share article"))
}

/**
 * Shared article body used by full-screen [ArticleScreen] (phone) and the
 * tablet dual-pane reading column.
 */
@Composable
fun ArticleContent(
    article: Article,
    writable: Boolean,
    onToggleStar: () -> Unit,
    onMarkRead: () -> Unit,
    modifier: Modifier = Modifier,
    /** When true, show star/mark-read in a top action row (tablet pane). */
    showInlineActions: Boolean = false,
) {
    val context = LocalContext.current
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        if (showInlineActions) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    article.feedTitle.ifBlank { "Article" },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                IconButton(
                    onClick = { shareArticle(context, article.title, article.url) },
                    enabled = article.url.isNotBlank() || article.title.isNotBlank(),
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share")
                }
                if (writable) {
                    IconButton(onClick = onToggleStar) {
                        Icon(
                            if (article.isSaved) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Star",
                        )
                    }
                    IconButton(onClick = onMarkRead) {
                        Icon(Icons.Default.Done, contentDescription = "Mark read")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Text(
            article.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            buildString {
                if (article.author.isNotBlank()) append(article.author).append(" · ")
                if (article.createdOnTime > 0) {
                    append(
                        SimpleDateFormat("EEE, MMM d  HH:mm", Locale.getDefault())
                            .format(Date(article.createdOnTime * 1000)),
                    )
                }
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        val browserUrl = article.url
        val playUrl = MediaUtils.playableUrl(article.mediaUrl, article.url)
        Row(Modifier.fillMaxWidth()) {
            FilledTonalButton(
                onClick = { SafeUrls.openInBrowser(context, browserUrl) },
                enabled = SafeUrls.isSafeHttpUrl(browserUrl),
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                Text("  Browser", modifier = Modifier.padding(start = 4.dp))
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(
                onClick = { shareArticle(context, article.title, article.url) },
                enabled = article.title.isNotBlank() || SafeUrls.isSafeHttpUrl(article.url),
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Text("  Share", modifier = Modifier.padding(start = 4.dp))
            }
            if (article.isVideo || article.isAudio) {
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = { SafeUrls.openInBrowser(context, playUrl) },
                    enabled = SafeUrls.isSafeHttpUrl(playUrl),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Text(
                        if (article.isAudio && !article.isVideo) "  Listen" else "  Play",
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        val body = article.text.ifBlank { article.summary }
        Text(
            body.ifBlank { "(no content — open in browser)" },
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
fun ArticleEmptyPane(modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Select an article",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Choose a feed and article from the list",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
