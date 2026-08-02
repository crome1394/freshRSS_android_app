package com.crome.freshrss.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.VerticalDivider
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crome.freshrss.BuildConfig
import com.crome.freshrss.data.model.Article
import com.crome.freshrss.data.model.ClientMode
import com.crome.freshrss.data.model.ReadScope
import com.crome.freshrss.ui.article.ArticleContent
import com.crome.freshrss.ui.article.ArticleEmptyPane
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Width ≥ this (dp) → list + reading pane (tablet landscape). */
const val DUAL_PANE_MIN_WIDTH_DP = 840

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onOpenSettings: () -> Unit,
    onOpenArticle: (String) -> Unit,
    /** When true (wide window), show list + article side by side. */
    dualPane: Boolean = false,
) {
    val state by vm.state.collectAsStateWithLifecycle()

    val titleBlock: @Composable () -> Unit = {
        Column {
            Text("FreshRSS", fontWeight = FontWeight.Bold)
            Text(
                text = buildString {
                    append("v${BuildConfig.VERSION_NAME} · ")
                    if (state.isOffline) append("offline · ")
                    if (state.unread > 0) append("${state.unread} unread · ")
                    append(
                        if (state.mode == ClientMode.FEVER && state.writable) "API r/w"
                        else if (state.mode == ClientMode.FEVER) "API"
                        else "RSS",
                    )
                    if (state.knownFeeds.isNotEmpty()) {
                        append(" · ${state.knownFeeds.size} feeds")
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.lastUpdatedEpochMs > 0) {
                Text(
                    text = "Updated ${formatLastUpdated(state.lastUpdatedEpochMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    val context = LocalContext.current
    val actionButtons: @Composable () -> Unit = {
        if (state.showTailscaleButton) {
            IconButton(onClick = { openTailscale(context) }) {
                Icon(Icons.Default.VpnKey, contentDescription = "Open Tailscale")
            }
        }
        IconButton(onClick = { vm.refresh() }) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
        }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Default.Settings, contentDescription = "Settings")
        }
    }

    Scaffold(
        topBar = {
            if (!state.chromeAtBottom) {
                TopAppBar(
                    title = { titleBlock() },
                    actions = { actionButtons() },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            }
        },
        bottomBar = {
            if (state.chromeAtBottom) {
                BottomAppBar(
                    actions = {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.weight(1f)) { titleBlock() }
                            actionButtons()
                        }
                    },
                )
            }
        },
    ) { padding ->
        val scopeChips: @Composable () -> Unit = {
            ScopeChips(
                selected = state.scope,
                onSelect = vm::setScope,
                mediaFilter = state.mediaFilter,
                onMediaFilter = vm::setMediaFilter,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        val filtersExpandedBody: @Composable () -> Unit = {
            Column {
                // Download steppers only when history mode is off (Settings → History = 0).
                if (state.historyDays == 0) {
                    DownloadLimitBar(
                        scope = state.scope,
                        itemLimit = state.itemLimit,
                        perFeedLimit = state.perFeedLimit,
                        enabled = true,
                        onNudgePerFeed = vm::nudgePerFeed,
                        onNudgeItemLimit = vm::nudgeItemLimit,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                } else {
                    Text(
                        "History: last ${state.historyDays} days (Settings).",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
                DateFilterChips(
                    selected = state.dateFilter,
                    onSelect = vm::setDateFilter,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = vm::setSearch,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    singleLine = true,
                    placeholder = { Text("Search title, feed, summary…") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    textStyle = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        // Expand / collapse filters (download limits, dates, search).
        // When docked at bottom, expanded body opens *above* the toggle row.
        val filtersPanel: @Composable () -> Unit = {
            Column {
                if (state.filtersAtBottom) {
                    AnimatedVisibility(visible = state.filtersExpanded) {
                        filtersExpandedBody()
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { vm.toggleFiltersExpanded() }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (state.filtersExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.height(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (state.filtersExpanded) "Hide filters"
                        else "Filters",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(Modifier.width(10.dp))
                    // Active scope / media chip label (bright white so icons are understandable).
                    Text(
                        text = state.activeChipLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.dateFilter != DateFilter.ALL) {
                        Text(
                            state.dateFilter.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                    IconButton(onClick = {
                        if (state.collapsed.isEmpty()) vm.collapseAll() else vm.expandAll()
                    }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Expand/collapse feeds")
                    }
                }
                if (!state.filtersAtBottom) {
                    AnimatedVisibility(visible = state.filtersExpanded) {
                        filtersExpandedBody()
                    }
                }
            }
        }

        val listColumn: @Composable (Modifier) -> Unit = { listMod ->
            Column(listMod) {
                if (!state.scopeChipsAtBottom) {
                    scopeChips()
                }

                if (!state.filtersAtBottom) {
                    filtersPanel()
                }

                if (state.statusLine.isNotBlank() || state.error != null) {
                    Text(
                        text = state.error ?: state.statusLine,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (state.error != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                PullToRefreshBox(
                    isRefreshing = state.loading,
                    onRefresh = { vm.refresh() },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    if (state.loading && state.items.isEmpty() && state.knownFeeds.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (state.listRows.isEmpty() && !state.loading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "No articles. Pull to refresh or check Settings.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(24.dp),
                            )
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 24.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(
                                items = state.listRows,
                                key = { row ->
                                    when (row) {
                                        is ListRow.Header -> "h:${row.category}"
                                        is ListRow.DateHeader -> "d:${row.category}\u001f${row.dateKey}"
                                        is ListRow.Item -> "i:${row.article.id}"
                                        is ListRow.EmptyFeed -> "e:${row.category}"
                                    }
                                },
                            ) { row ->
                                when (row) {
                                    is ListRow.Header -> FeedHeaderRow(
                                        row = row,
                                        onClick = { vm.toggleCategory(row.category) },
                                        onLongClick = {
                                            vm.markCategoryRead(row.category, row.feedId)
                                        },
                                        onMarkRead = {
                                            vm.markCategoryRead(row.category, row.feedId)
                                        },
                                    )
                                    is ListRow.DateHeader -> DateHeaderRow(
                                        row = row,
                                        onClick = { vm.toggleDateGroup(row.category, row.dateKey) },
                                    )
                                    is ListRow.Item -> SwipeableArticleRow(
                                        article = row.article,
                                        selected = dualPane &&
                                            row.article.id == state.selectedArticleId,
                                        onClick = {
                                            if (dualPane) {
                                                vm.selectArticle(row.article.id)
                                            } else {
                                                onOpenArticle(row.article.id)
                                            }
                                        },
                                        onToggleStar = {
                                            if (state.writable) vm.toggleStar(row.article.id)
                                        },
                                        onMarkRead = { vm.markRead(row.article.id) },
                                        onMarkUnread = { vm.markUnread(row.article.id) },
                                    )
                                    is ListRow.EmptyFeed -> Text(
                                        text = when (state.scope) {
                                            ReadScope.UNREAD -> "No unread in this feed — try All"
                                            ReadScope.SAVED -> "No starred items"
                                            ReadScope.READ -> "No read items loaded"
                                            ReadScope.ALL -> "No articles returned for this feed"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(
                                            start = 36.dp,
                                            end = 16.dp,
                                            top = 6.dp,
                                            bottom = 10.dp,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom docking order (above title bar when chrome is bottom):
                // article list → Filters panel → scope chips → (Scaffold bottomBar)
                if (state.filtersAtBottom) {
                    filtersPanel()
                }
                if (state.scopeChipsAtBottom) {
                    scopeChips()
                }
            }
        }

        if (dualPane) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                listColumn(
                    Modifier
                        .weight(0.38f)
                        .fillMaxHeight(),
                )
                VerticalDivider(Modifier.fillMaxHeight())
                Box(
                    Modifier
                        .weight(0.62f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surface),
                ) {
                    val selected = state.selectedArticleId?.let { id ->
                        state.items.firstOrNull { it.id == id }
                    }
                    if (selected != null) {
                        ArticleContent(
                            article = selected,
                            writable = state.writable,
                            onToggleStar = { vm.toggleStar(selected.id) },
                            onMarkRead = { vm.markRead(selected.id) },
                            showInlineActions = true,
                        )
                    } else {
                        ArticleEmptyPane()
                    }
                }
            }
        } else {
            listColumn(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableArticleRow(
    article: Article,
    selected: Boolean = false,
    onClick: () -> Unit,
    onToggleStar: () -> Unit,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> {
                    // Swipe left → mark read
                    if (!article.isRead) onMarkRead()
                    false // don't dismiss the row
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    // Swipe right → mark unread
                    if (article.isRead) onMarkUnread()
                    false
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        },
    )

    // Reset visual position after action
    LaunchedEffect(dismissState.currentValue, article.id, article.isRead) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val dir = dismissState.dismissDirection
            val color = when (dir) {
                SwipeToDismissBoxValue.EndToStart -> Color(0xFF2E7D32) // mark read
                SwipeToDismissBoxValue.StartToEnd -> Color(0xFF1565C0) // mark unread
                else -> Color.Transparent
            }
            val icon = when (dir) {
                SwipeToDismissBoxValue.EndToStart -> Icons.Default.Done
                SwipeToDismissBoxValue.StartToEnd -> Icons.Default.MarkEmailUnread
                else -> null
            }
            val align = when (dir) {
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                else -> Alignment.Center
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = align,
            ) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = Color.White)
                }
            }
        },
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
    ) {
        ArticleRow(
            article = article,
            selected = selected,
            onClick = onClick,
            onToggleStar = onToggleStar,
        )
    }
}

@Composable
private fun DownloadLimitBar(
    scope: ReadScope,
    itemLimit: Int,
    perFeedLimit: Int,
    enabled: Boolean = true,
    onNudgePerFeed: (Int) -> Unit,
    onNudgeItemLimit: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val perFeedMode = scope == ReadScope.ALL || scope == ReadScope.READ
    val label = if (perFeedMode) "Per feed" else "Max articles"
    val value = if (perFeedMode) perFeedLimit else itemLimit
    val onMinus = { if (perFeedMode) onNudgePerFeed(-1) else onNudgeItemLimit(-1) }
    val onPlus = { if (perFeedMode) onNudgePerFeed(+1) else onNudgeItemLimit(+1) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.45f)

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Download",
                style = MaterialTheme.typography.labelMedium,
                color = muted,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = muted,
                    modifier = Modifier.padding(end = 4.dp),
                )
                IconButton(onClick = onMinus, modifier = Modifier.width(36.dp), enabled = enabled) {
                    Icon(Icons.Default.Remove, contentDescription = "Fewer articles")
                }
                Text(
                    text = value.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        muted
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(min = 36.dp),
                )
                IconButton(onClick = onPlus, modifier = Modifier.width(36.dp), enabled = enabled) {
                    Icon(Icons.Default.Add, contentDescription = "More articles")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScopeChips(
    selected: ReadScope,
    onSelect: (ReadScope) -> Unit,
    mediaFilter: MediaFilter,
    onMediaFilter: (MediaFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val order = listOf(ReadScope.UNREAD, ReadScope.ALL, ReadScope.READ, ReadScope.SAVED)
    val chipIconMod = Modifier.height(18.dp)
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (scope in order) {
            FilterChip(
                selected = selected == scope,
                onClick = { onSelect(scope) },
                label = {
                    when (scope) {
                        // Filled bullet = unread items (not an email glyph).
                        ReadScope.UNREAD -> Icon(
                            Icons.Default.FiberManualRecord,
                            contentDescription = "Unread",
                            modifier = chipIconMod,
                        )
                        ReadScope.ALL -> Text("All")
                        // Check circle = already read (not an email glyph).
                        ReadScope.READ -> Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Read",
                            modifier = chipIconMod,
                        )
                        ReadScope.SAVED -> Icon(
                            Icons.Default.Star,
                            contentDescription = "Starred",
                            modifier = chipIconMod,
                        )
                    }
                },
            )
        }
        // Icon-only media filters (client-side) sit next to Starred.
        FilterChip(
            selected = mediaFilter == MediaFilter.VIDEO,
            onClick = { onMediaFilter(MediaFilter.VIDEO) },
            label = {
                Icon(
                    Icons.Default.Videocam,
                    contentDescription = "Videos",
                    modifier = chipIconMod,
                )
            },
        )
        FilterChip(
            selected = mediaFilter == MediaFilter.SOUND,
            onClick = { onMediaFilter(MediaFilter.SOUND) },
            label = {
                Icon(
                    Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Sound",
                    modifier = chipIconMod,
                )
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DateFilterChips(
    selected: DateFilter,
    onSelect: (DateFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (f in DateFilter.entries) {
            FilterChip(
                selected = selected == f,
                onClick = { onSelect(f) },
                label = { Text(f.label) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeedHeaderRow(
    row: ListRow.Header,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMarkRead: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (row.collapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = row.category,
            modifier = Modifier.weight(1f),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${row.unread}",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = " ·${row.shown}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
        if (row.feedId > 0) {
            Text(
                text = "  ✓",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable(onClick = onMarkRead)
                    .padding(start = 8.dp),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun DateHeaderRow(
    row: ListRow.DateHeader,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(start = 28.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (row.collapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.height(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = row.dateLabel,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (row.unread > 0) {
            Text(
                text = "${row.unread}",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "/${row.shown}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        } else {
            Text(
                text = "${row.shown}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArticleRow(
    article: Article,
    selected: Boolean = false,
    onClick: () -> Unit,
    onToggleStar: () -> Unit,
) {
    val bg = when {
        selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        else -> MaterialTheme.colorScheme.surface
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(bg)
            .combinedClickable(onClick = onClick, onLongClick = onToggleStar)
            .padding(start = 36.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = article.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (article.isRead) FontWeight.Normal else FontWeight.SemiBold,
                color = if (article.isRead) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (article.isVideo) {
                    Icon(
                        Icons.Default.Videocam,
                        contentDescription = "Video",
                        modifier = Modifier
                            .height(14.dp)
                            .padding(end = 4.dp),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                } else if (article.isAudio) {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Audio",
                        modifier = Modifier
                            .height(14.dp)
                            .padding(end = 4.dp),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                }
                Text(
                    text = formatTime(article.createdOnTime),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (article.summary.isNotBlank()) {
                    Text(
                        text = " · ${article.summary}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
        }
        IconButton(onClick = onToggleStar) {
            Icon(
                if (article.isSaved) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = "Star",
                tint = if (article.isSaved) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

private fun formatTime(epoch: Long): String {
    if (epoch <= 0) return ""
    val fmt = SimpleDateFormat("MMM d  HH:mm", Locale.getDefault())
    return fmt.format(Date(epoch * 1000))
}

private fun formatLastUpdated(epochMs: Long): String {
    if (epochMs <= 0) return ""
    val fmt = SimpleDateFormat("MMM d  HH:mm", Locale.getDefault())
    return fmt.format(Date(epochMs))
}

private const val TAILSCALE_PACKAGE = "com.tailscale.ipn"

private fun openTailscale(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage(TAILSCALE_PACKAGE)
    if (launch != null) {
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launch)
    } else {
        Toast.makeText(context, "Tailscale not installed", Toast.LENGTH_SHORT).show()
    }
}
