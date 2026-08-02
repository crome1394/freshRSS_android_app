package com.crome.freshrss.ui.article

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crome.freshrss.ui.home.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleScreen(
    articleId: String,
    vm: HomeViewModel,
    onBack: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val article = state.items.firstOrNull { it.id == articleId }
    val context = LocalContext.current

    // Auto mark-read when opening (desktop often does this on select / open)
    LaunchedEffect(articleId, state.writable) {
        val a = vm.articleById(articleId)
        if (a != null && state.writable && !a.isRead) {
            vm.markRead(articleId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        article?.feedTitle?.ifBlank { "Article" } ?: "Article",
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (article != null) {
                        IconButton(
                            onClick = { shareArticle(context, article.title, article.url) },
                            enabled = article.url.isNotBlank() || article.title.isNotBlank(),
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }
                        if (state.writable) {
                            IconButton(onClick = { vm.toggleStar(article.id) }) {
                                Icon(
                                    if (article.isSaved) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = "Star",
                                )
                            }
                            IconButton(onClick = { vm.markRead(article.id) }) {
                                Icon(Icons.Default.Done, contentDescription = "Mark read")
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (article == null) {
            Text(
                "Article not in current list. Go back and refresh.",
                modifier = Modifier
                    .padding(padding)
                    .padding(16.dp),
            )
            return@Scaffold
        }

        ArticleContent(
            article = article,
            writable = state.writable,
            onToggleStar = { vm.toggleStar(article.id) },
            onMarkRead = { vm.markRead(article.id) },
            modifier = Modifier.padding(padding),
            showInlineActions = false,
        )
    }
}
