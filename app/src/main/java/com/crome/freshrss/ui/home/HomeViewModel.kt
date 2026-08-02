package com.crome.freshrss.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.crome.freshrss.data.model.Article
import com.crome.freshrss.data.model.ClientMode
import com.crome.freshrss.data.model.FeedRef
import com.crome.freshrss.data.model.FreshRssConfig
import com.crome.freshrss.data.model.ReadScope
import com.crome.freshrss.data.model.ReaderDefaults
import com.crome.freshrss.data.offline.OfflineCache
import com.crome.freshrss.data.offline.OfflineSnapshot
import com.crome.freshrss.data.offline.toClientMode
import com.crome.freshrss.data.offline.toReadScope
import com.crome.freshrss.data.prefs.SettingsRepository
import com.crome.freshrss.data.remote.FreshRssClient
import com.crome.freshrss.util.ServerUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class HomeUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val statusLine: String = "",
    val unread: Int = 0,
    val writable: Boolean = false,
    val mode: ClientMode = ClientMode.RSS,
    val scope: ReadScope = ReaderDefaults.readScope,
    val searchQuery: String = "",
    /** Client-side media type filter (Videos / Sound chips). */
    val mediaFilter: MediaFilter = MediaFilter.NONE,
    val items: List<Article> = emptyList(),
    /** All known subscriptions (All/Read) — ensures every feed shows as a category. */
    val knownFeeds: List<FeedRef> = emptyList(),
    /** category → collapsed */
    val collapsed: Map<String, Boolean> = emptyMap(),
    /** "category\u001fYYYY-MM-DD" → collapsed */
    val collapsedDates: Map<String, Boolean> = emptyMap(),
    val feedUnreadByTitle: Map<String, Int> = emptyMap(),
    val config: FreshRssConfig = FreshRssConfig(),
    val itemLimit: Int = ReaderDefaults.itemLimit,
    val perFeedLimit: Int = ReaderDefaults.perFeedLimit,
    /** Days of history to download; >0 overrides item/per-feed. Default 30. */
    val historyDays: Int = 30,
    /** Epoch ms of last successful network or offline load. */
    val lastUpdatedEpochMs: Long = 0L,
    val isOffline: Boolean = false,
    val chromeAtBottom: Boolean = false,
    /** Scope / media chips sit above the bottom chrome (or screen bottom). */
    val scopeChipsAtBottom: Boolean = false,
    /** Filters panel (dates / search / download) sits at the bottom of the list column. */
    val filtersAtBottom: Boolean = false,
    /** Allow http:// to the configured FreshRSS host. Default false. */
    val allowCleartextHttp: Boolean = false,
    /** Title-bar Tailscale (key) action. Default true. */
    val showTailscaleButton: Boolean = true,
    val filtersExpanded: Boolean = false,
    /** Client-side date window on loaded items. */
    val dateFilter: DateFilter = DateFilter.ALL,
    /** Selected article for tablet dual-pane reader (null = empty pane). */
    val selectedArticleId: String? = null,
) {
    /** True when client-side filters narrow the loaded list. */
    val hasClientFilters: Boolean
        get() = mediaFilter != MediaFilter.NONE ||
            dateFilter != DateFilter.ALL ||
            searchQuery.isNotBlank()

    /**
     * Bright label for the active top-level chip (scope + optional media filter).
     * Shown next to the Filters expand/collapse control.
     */
    val activeChipLabel: String
        get() {
            val scopeLabel = when (scope) {
                ReadScope.UNREAD -> "Unread"
                ReadScope.ALL -> "All"
                ReadScope.READ -> "Read"
                ReadScope.SAVED -> "Starred"
            }
            return when (mediaFilter) {
                MediaFilter.NONE -> scopeLabel
                MediaFilter.VIDEO -> "$scopeLabel · Videos"
                MediaFilter.SOUND -> "$scopeLabel · Sound"
            }
        }

    val filtered: List<Article>
        get() {
            val q = searchQuery.trim().lowercase()
            val window = dateFilter.window()
            return items.filter { it ->
                val mediaOk = when (mediaFilter) {
                    MediaFilter.NONE -> true
                    MediaFilter.VIDEO -> it.isVideo
                    MediaFilter.SOUND -> it.isAudio
                }
                if (!mediaOk) return@filter false
                if (window.hasBound) {
                    val t = it.createdOnTime
                    if (t <= 0) return@filter false
                    if (window.minEpoch > 0 && t < window.minEpoch) return@filter false
                    if (window.maxEpochExclusive > 0 && t >= window.maxEpochExclusive) {
                        return@filter false
                    }
                }
                if (q.isNotEmpty()) {
                    val blob = listOf(
                        it.title, it.author, it.feedTitle, it.groupTitle, it.category, it.summary,
                    ).joinToString("\n").lowercase()
                    if (q !in blob) return@filter false
                }
                true
            }.sortedByDescending { it.createdOnTime }
        }

    /**
     * Sectioned rows: **FreshRSS feed title** header → date → articles.
     *
     * Source of truth for headers is [knownFeeds] / unread title map (subscriptions),
     * NOT free-text publisher tags on items (e.g. "U.S. News").
     * Articles are attached by feedId first, then feedTitle.
     *
     * Header counters always reflect the **currently filtered** list so they
     * update when scope, media, date, or search filters change.
     */
    val listRows: List<ListRow>
        get() {
            val articles = filtered
            val byId = articles.groupBy { it.feedId }.mapValues { it.value.toMutableList() }
            val byTitle = articles.groupBy { it.feedTitle.trim().lowercase() }
                .mapValues { it.value.toMutableList() }
            val usedIds = mutableSetOf<String>()
            val clientFilters = hasClientFilters

            // Build ordered feed list: known subscriptions + any unread map titles
            val feedsOrdered = LinkedHashMap<String, FeedRef>()
            for (f in knownFeeds) {
                if (f.title.isNotBlank()) feedsOrdered[f.title] = f
            }
            for (title in feedUnreadByTitle.keys.sortedWith(String.CASE_INSENSITIVE_ORDER)) {
                if (title.isNotBlank() && title !in feedsOrdered) {
                    feedsOrdered[title] = FeedRef(id = 0, title = title)
                }
            }
            // Only if we still have no subscription list, fall back to feed titles on items
            // (still feedTitle — never article free-text category tags).
            if (feedsOrdered.isEmpty()) {
                for (a in articles) {
                    val t = a.feedTitle.trim()
                    if (t.isBlank()) continue
                    if (t !in feedsOrdered) {
                        feedsOrdered[t] = FeedRef(id = a.feedId, title = t)
                    }
                }
            }

            val rows = mutableListOf<ListRow>()
            val sortedFeeds = feedsOrdered.values.sortedBy { it.title.lowercase() }

            fun takeArticlesFor(feed: FeedRef): List<Article> {
                val out = mutableListOf<Article>()
                if (feed.id > 0) {
                    byId[feed.id]?.let { list ->
                        for (a in list) {
                            if (usedIds.add(a.id)) out += a
                        }
                    }
                }
                byTitle[feed.title.trim().lowercase()]?.let { list ->
                    for (a in list) {
                        if (usedIds.add(a.id)) out += a
                    }
                }
                return out.sortedByDescending { it.createdOnTime }
            }

            for (feed in sortedFeeds) {
                val list = takeArticlesFor(feed)
                // With client filters active, skip empty feeds so counters stay meaningful.
                if (list.isEmpty() && clientFilters) continue
                val cat = feed.title
                val isCollapsed = collapsed[cat] == true
                // Always count from the filtered list (not server-wide unread totals).
                val loadedUnread = list.count { !it.isRead }
                rows += ListRow.Header(
                    category = cat,
                    feedId = feed.id,
                    unread = loadedUnread,
                    shown = list.size,
                    collapsed = isCollapsed,
                )
                if (isCollapsed) continue
                if (list.isEmpty()) {
                    rows += ListRow.EmptyFeed(category = cat)
                    continue
                }
                appendDateGroups(rows, cat, list, collapsedDates)
            }

            // Orphans (shouldn't happen if feedTitle is set correctly)
            val orphans = articles.filter { it.id !in usedIds }
            if (orphans.isNotEmpty()) {
                val cat = "Other"
                val isCollapsed = collapsed[cat] == true
                rows += ListRow.Header(
                    category = cat,
                    feedId = 0,
                    unread = orphans.count { !it.isRead },
                    shown = orphans.size,
                    collapsed = isCollapsed,
                )
                if (!isCollapsed) {
                    appendDateGroups(rows, cat, orphans.sortedByDescending { it.createdOnTime }, collapsedDates)
                }
            }
            return rows
        }
}

private fun appendDateGroups(
    rows: MutableList<ListRow>,
    cat: String,
    list: List<Article>,
    collapsedDates: Map<String, Boolean>,
) {
    val byDate = list.groupBy { dateKeyForEpoch(it.createdOnTime) }
    val dateKeys = byDate.keys.sortedWith { a, b ->
        when {
            a == "unknown" -> 1
            b == "unknown" -> -1
            else -> b.compareTo(a)
        }
    }
    for (dk in dateKeys) {
        val dayItems = byDate[dk].orEmpty().sortedByDescending { it.createdOnTime }
        val dKey = dateCollapseKey(cat, dk)
        val dateCollapsed = collapsedDates[dKey] == true
        val sampleEpoch = dayItems.firstOrNull()?.createdOnTime ?: 0L
        val dayUnread = dayItems.count { !it.isRead }
        rows += ListRow.DateHeader(
            category = cat,
            dateKey = dk,
            dateLabel = dateLabelForKey(dk, sampleEpoch),
            unread = dayUnread,
            shown = dayItems.size,
            collapsed = dateCollapsed,
        )
        if (dateCollapsed) continue
        for (a in dayItems) {
            rows += ListRow.Item(a)
        }
    }
}

sealed class ListRow {
    data class Header(
        val category: String,
        val feedId: Long,
        val unread: Int,
        val shown: Int,
        val collapsed: Boolean,
    ) : ListRow()

    data class DateHeader(
        val category: String,
        val dateKey: String,
        val dateLabel: String,
        val unread: Int,
        val shown: Int,
        val collapsed: Boolean,
    ) : ListRow()

    data class Item(val article: Article) : ListRow()

    /** Expanded feed with no articles in the current scope/download. */
    data class EmptyFeed(val category: String) : ListRow()
}

fun dateKeyForEpoch(epoch: Long): String {
    if (epoch <= 0) return "unknown"
    val cal = Calendar.getInstance().apply { timeInMillis = epoch * 1000 }
    val y = cal.get(Calendar.YEAR)
    val m = cal.get(Calendar.MONTH) + 1
    val d = cal.get(Calendar.DAY_OF_MONTH)
    return "%04d-%02d-%02d".format(y, m, d)
}

/**
 * Client-side date window on already-loaded articles.
 * [YESTERDAY] uses both a lower and exclusive upper bound so it does not
 * include today's items (unlike a min-only filter).
 */
enum class DateFilter(val label: String) {
    ALL("All"),
    TODAY("Today"),
    YESTERDAY("-1"),
    DAYS_7("-7"),
    DAYS_14("-14"),
    DAYS_30("-30");

    data class Window(
        /** Inclusive lower bound (unix seconds). 0 = none. */
        val minEpoch: Long = 0L,
        /** Exclusive upper bound (unix seconds). 0 = none. */
        val maxEpochExclusive: Long = 0L,
    ) {
        val hasBound: Boolean get() = minEpoch > 0 || maxEpochExclusive > 0
    }

    fun window(): Window {
        if (this == ALL) return Window()
        val startOfToday = startOfLocalDay(0)
        return when (this) {
            ALL -> Window()
            TODAY -> Window(minEpoch = startOfToday)
            YESTERDAY -> Window(
                minEpoch = startOfLocalDay(-1),
                maxEpochExclusive = startOfToday,
            )
            // Inclusive windows: today + previous (N-1) days.
            DAYS_7 -> Window(minEpoch = startOfLocalDay(-6))
            DAYS_14 -> Window(minEpoch = startOfLocalDay(-13))
            DAYS_30 -> Window(minEpoch = startOfLocalDay(-29))
        }
    }

    /** @deprecated Prefer [window]. */
    fun minCreatedOnTimeEpochSeconds(): Long = window().minEpoch
}

/** Start of local calendar day [dayOffset] days from today, as unix seconds. */
private fun startOfLocalDay(dayOffset: Int): Long {
    val cal = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, dayOffset)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis / 1000L
}

/** Content-type filter chips next to Starred (independent of [ReadScope]). */
enum class MediaFilter(val label: String) {
    NONE("All media"),
    VIDEO("Videos"),
    SOUND("Sound"),
}

fun dateCollapseKey(cat: String, dateKey: String): String = "$cat\u001f$dateKey"

fun dateLabelForKey(key: String, epoch: Long): String {
    if (key.isBlank() || key == "unknown") return "Unknown date"
    val todayKey = dateKeyForEpoch(System.currentTimeMillis() / 1000)
    val yest = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val yestKey = dateKeyForEpoch(yest.timeInMillis / 1000)
    if (key == todayKey) return "Today"
    if (key == yestKey) return "Yesterday"
    if (epoch > 0) {
        return SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())
            .format(Date(epoch * 1000))
    }
    val parts = key.split("-")
    if (parts.size == 3) {
        return try {
            val cal = Calendar.getInstance().apply {
                set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
            }
            SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()).format(cal.time)
        } catch (_: Exception) {
            key
        }
    }
    return key
}


class HomeViewModel(
    application: Application,
    private val client: FreshRssClient,
    private val settings: SettingsRepository,
) : AndroidViewModel(application) {

    private val offline = OfflineCache(application.applicationContext)

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    /** Single in-flight network load — cancelled when a newer refresh starts. */
    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            combine(
                settings.config,
                settings.readScope,
                settings.itemLimit,
                settings.perFeedLimit,
                settings.chromeAtBottom,
            ) { cfg, scope, itemLimit, perFeed, chromeBottom ->
                Quint(cfg, scope, itemLimit, perFeed, chromeBottom)
            }.collect { (cfg, scope, itemLimit, perFeed, chromeBottom) ->
                client.config = cfg
                _state.update {
                    it.copy(
                        config = cfg,
                        scope = scope,
                        itemLimit = itemLimit,
                        perFeedLimit = perFeed,
                        chromeAtBottom = chromeBottom,
                    )
                }
            }
        }
        // Apply start-up expand preference once (session toggle is local only).
        viewModelScope.launch {
            val expandOnStart = settings.expandFiltersOnStart.first()
            _state.update { it.copy(filtersExpanded = expandOnStart) }
        }
        viewModelScope.launch {
            settings.historyDays.collect { days ->
                _state.update { it.copy(historyDays = days) }
            }
        }
        viewModelScope.launch {
            settings.scopeChipsAtBottom.collect { bottom ->
                _state.update { it.copy(scopeChipsAtBottom = bottom) }
            }
        }
        viewModelScope.launch {
            settings.filtersAtBottom.collect { bottom ->
                _state.update { it.copy(filtersAtBottom = bottom) }
            }
        }
        viewModelScope.launch {
            settings.allowCleartextHttp.collect { allow ->
                _state.update { it.copy(allowCleartextHttp = allow) }
            }
        }
        viewModelScope.launch {
            settings.showTailscaleButton.collect { show ->
                _state.update { it.copy(showTailscaleButton = show) }
            }
        }
        // Load offline cache immediately, then try network refresh
        viewModelScope.launch {
            loadOfflineIfAny()
            refresh()
        }
    }

    private suspend fun loadOfflineIfAny() {
        val snap = offline.load() ?: return
        val cats = snap.feeds.map { it.title }.filter { it.isNotBlank() }.toSet() +
            snap.items.map { it.displayCategory }.filter { it.isNotBlank() }
        _state.update {
            it.copy(
                loading = false,
                error = null,
                items = snap.items,
                knownFeeds = snap.feeds,
                unread = snap.unread,
                writable = snap.writable,
                mode = snap.toClientMode(),
                scope = snap.toReadScope(),
                feedUnreadByTitle = snap.feedUnreadByTitle,
                statusLine = (snap.statusLine.ifBlank { "offline cache" }) + " · offline",
                lastUpdatedEpochMs = snap.savedAtEpochMs,
                isOffline = true,
                collapsed = cats.associateWith { true },
                collapsedDates = emptyMap(),
            )
        }
    }

    private suspend fun persistOffline(
        scope: ReadScope,
        unread: Int,
        mode: ClientMode,
        writable: Boolean,
        statusLine: String,
        items: List<Article>,
        feeds: List<FeedRef>,
        titles: Map<String, Int>,
        savedAt: Long,
    ) {
        offline.save(
            OfflineSnapshot(
                savedAtEpochMs = savedAt,
                scope = scope.apiValue,
                unread = unread,
                mode = mode.name.lowercase(),
                writable = writable,
                statusLine = statusLine,
                items = items,
                feeds = feeds,
                feedUnreadByTitle = titles,
            ),
        )
    }

    fun refresh(scopeOverride: ReadScope? = null) {
        // Drop any previous in-flight load so rapid scope taps / pull-to-refresh
        // cannot apply stale results over a newer request.
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val cfg = settings.config.first()
            client.config = cfg
            val scope = scopeOverride ?: settings.readScope.first()
            val itemLimit = settings.itemLimit.first()
            val perFeed = settings.perFeedLimit.first()
            val historyDays = settings.historyDays.first()

            if (!cfg.hasBaseUrl) {
                val snap = offline.load()
                if (snap != null) {
                    loadOfflineIfAny()
                    if (!isActive) return@launch
                    _state.update {
                        it.copy(
                            error = "No server URL — showing offline cache. Set FRESHRSS_BASE_URL in Settings.",
                            loading = false,
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            loading = false,
                            error = "Set FRESHRSS_BASE_URL in Settings (your FreshRSS host).",
                            items = emptyList(),
                            knownFeeds = emptyList(),
                            scope = scope,
                            config = cfg,
                            mode = ClientMode.RSS,
                            writable = false,
                            statusLine = "not configured",
                            isOffline = false,
                        )
                    }
                }
                return@launch
            }

            val allowCleartext = settings.allowCleartextHttp.first()
            val urlCheck = ServerUrl.normalize(cfg.baseUrl, allowCleartext)
            if (!urlCheck.ok) {
                _state.update {
                    it.copy(
                        loading = false,
                        error = urlCheck.error,
                        config = cfg,
                        statusLine = "blocked URL",
                    )
                }
                return@launch
            }
            // Prefer normalized URL (https default / no trailing slash).
            val cfgUse = cfg.copy(baseUrl = urlCheck.normalized)
            client.config = cfgUse

            if (!cfgUse.hasApiPassword) {
                // Still show offline if we have it
                val snap = offline.load()
                if (snap != null) {
                    loadOfflineIfAny()
                    if (!isActive) return@launch
                    _state.update {
                        it.copy(
                            error = "No API password — showing offline cache. Set password in Settings.",
                            loading = false,
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            loading = false,
                            error = "Set FRESHRSS_API_PASSWORD in Settings (Profile → API password).",
                            items = emptyList(),
                            knownFeeds = emptyList(),
                            scope = scope,
                            config = cfgUse,
                            mode = ClientMode.RSS,
                            writable = false,
                            statusLine = "rss · no API password",
                            isOffline = false,
                        )
                    }
                }
                return@launch
            }

            try {
                val status = client.status()
                if (!isActive) return@launch
                val items = client.items(
                    limit = itemLimit,
                    scope = scope,
                    perFeed = perFeed,
                    historyDays = historyDays,
                )
                if (!isActive) return@launch

                if (!items.ok) {
                    val snap = offline.load()
                    if (snap != null) {
                        loadOfflineIfAny()
                        if (!isActive) return@launch
                        _state.update {
                            it.copy(
                                loading = false,
                                error = "Network failed (${items.error ?: "error"}) — showing offline cache.",
                                isOffline = true,
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(
                                loading = false,
                                error = items.error ?: status.error ?: "load failed",
                                items = emptyList(),
                                knownFeeds = emptyList(),
                                unread = status.unread,
                                writable = status.writable,
                                mode = status.mode,
                                scope = scope,
                                statusLine = "error · ${scope.apiValue}",
                                isOffline = false,
                            )
                        }
                    }
                    return@launch
                }

                if ((scope == ReadScope.ALL || scope == ReadScope.READ) &&
                    !items.source.startsWith("greader-per-feed")
                ) {
                    _state.update {
                        it.copy(
                            loading = false,
                            error = "All/Read did not use GReader per-feed (got source=${items.source}).",
                            items = emptyList(),
                            knownFeeds = items.feeds,
                            unread = status.unread,
                            scope = scope,
                            statusLine = "bad source · ${items.source}",
                            isOffline = false,
                        )
                    }
                    return@launch
                }

                val known = LinkedHashMap<String, FeedRef>()
                for (f in items.feeds) {
                    if (f.title.isNotBlank()) known[f.title] = f
                }
                for ((title, _) in status.titles) {
                    if (title.isNotBlank() && title !in known) {
                        known[title] = FeedRef(id = 0, title = title)
                    }
                }
                for ((fidStr, _) in status.feeds) {
                    val fid = fidStr.toLongOrNull() ?: continue
                    if (known.values.none { it.id == fid }) {
                        val fromItem = items.items.firstOrNull { it.feedId == fid }
                        val title = fromItem?.feedTitle
                        if (!title.isNullOrBlank() && title !in known) {
                            known[title] = FeedRef(id = fid, title = title)
                        }
                    }
                }
                val knownList = known.values.sortedBy { it.title.lowercase() }
                val allCats = knownList.map { it.title }.filter { it.isNotBlank() }.toSet()
                val collapsed = if (ReaderDefaults.autoCollapseOnLoad) {
                    allCats.associateWith { true }
                } else {
                    _state.value.collapsed
                }

                val errNote = if (items.errors.isNotEmpty()) {
                    " · ${items.errors.size} feed error(s)"
                } else ""

                val parts = buildList {
                    add(scope.apiValue)
                    add("${status.unread} unread")
                    add("${items.items.size} shown")
                    add("${knownList.size} feeds")
                    if (historyDays > 0) {
                        add("${historyDays}d history")
                    } else if (scope == ReadScope.ALL || scope == ReadScope.READ) {
                        add("$perFeed/feed")
                    } else {
                        add("max $itemLimit")
                    }
                    add(items.source.ifBlank { "fever" })
                    if (items.elapsedMs > 0) add("${items.elapsedMs}ms")
                }
                val statusLine = parts.joinToString(" · ") + errNote
                val now = System.currentTimeMillis()

                if (!isActive) return@launch
                _state.update {
                    it.copy(
                        loading = false,
                        error = null,
                        items = items.items,
                        knownFeeds = knownList,
                        unread = status.unread,
                        writable = items.writable || status.writable,
                        mode = items.mode,
                        collapsed = collapsed,
                        collapsedDates = emptyMap(),
                        feedUnreadByTitle = status.titles,
                        statusLine = statusLine,
                        scope = scope,
                        config = cfgUse,
                        itemLimit = itemLimit,
                        perFeedLimit = perFeed,
                        historyDays = historyDays,
                        lastUpdatedEpochMs = now,
                        isOffline = false,
                    )
                }

                persistOffline(
                    scope = scope,
                    unread = status.unread,
                    mode = items.mode,
                    writable = items.writable || status.writable,
                    statusLine = statusLine,
                    items = items.items,
                    feeds = knownList,
                    titles = status.titles,
                    savedAt = now,
                )
            } catch (e: CancellationException) {
                // Expected when a newer refresh cancels this job — do not flash an error.
                throw e
            } catch (e: Exception) {
                if (!isActive) return@launch
                val snap = offline.load()
                if (snap != null) {
                    loadOfflineIfAny()
                    if (!isActive) return@launch
                    _state.update {
                        it.copy(
                            loading = false,
                            error = "Offline: ${e.message ?: "network error"} — showing cached articles.",
                            isOffline = true,
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            loading = false,
                            error = e.message ?: "network error",
                            isOffline = false,
                        )
                    }
                }
            }
        }
    }

    fun setScope(scope: ReadScope) {
        viewModelScope.launch {
            _state.update { it.copy(scope = scope) }
            settings.setReadScope(scope)
            refresh(scopeOverride = scope)
        }
    }

    fun setSearch(q: String) {
        _state.update { it.copy(searchQuery = q) }
    }

    /**
     * Toggle a media filter chip. Selecting the active filter again clears it
     * (returns to [MediaFilter.NONE]). Videos and Sound are mutually exclusive.
     */
    fun setMediaFilter(filter: MediaFilter) {
        _state.update { s ->
            val next = if (s.mediaFilter == filter || filter == MediaFilter.NONE) {
                MediaFilter.NONE
            } else {
                filter
            }
            s.copy(mediaFilter = next)
        }
    }

    /** @deprecated Prefer [setMediaFilter]. */
    fun toggleVideoOnly() {
        setMediaFilter(
            if (_state.value.mediaFilter == MediaFilter.VIDEO) MediaFilter.NONE
            else MediaFilter.VIDEO,
        )
    }

    /** Session-only; start-up state is controlled in Settings → Expand filters on start. */
    fun toggleFiltersExpanded() {
        _state.update { it.copy(filtersExpanded = !it.filtersExpanded) }
    }

    fun setDateFilter(filter: DateFilter) {
        _state.update { it.copy(dateFilter = filter) }
    }

    fun nudgePerFeed(delta: Int) {
        val choices = ReaderDefaults.perFeedChoices
        val cur = _state.value.perFeedLimit
        var idx = choices.indexOf(cur)
        if (idx < 0) {
            idx = choices.indexOfFirst { it >= cur }.let { if (it < 0) choices.lastIndex else it }
        }
        idx = (idx + delta).coerceIn(0, choices.lastIndex)
        val next = choices[idx]
        if (next == cur) return
        viewModelScope.launch {
            settings.setPerFeedLimit(next)
            if (_state.value.scope == ReadScope.ALL || _state.value.scope == ReadScope.READ) {
                refresh()
            } else {
                _state.update { it.copy(perFeedLimit = next) }
            }
        }
    }

    fun nudgeItemLimit(delta: Int) {
        val choices = ReaderDefaults.itemLimitChoices
        val cur = _state.value.itemLimit
        var idx = choices.indexOf(cur)
        if (idx < 0) {
            idx = choices.indexOfFirst { it >= cur }.let { if (it < 0) choices.lastIndex else it }
        }
        idx = (idx + delta).coerceIn(0, choices.lastIndex)
        val next = choices[idx]
        if (next == cur) return
        viewModelScope.launch {
            settings.setItemLimit(next)
            if (_state.value.scope == ReadScope.UNREAD || _state.value.scope == ReadScope.SAVED) {
                refresh()
            } else {
                _state.update { it.copy(itemLimit = next) }
            }
        }
    }

    fun toggleCategory(cat: String) {
        _state.update { s ->
            val next = s.collapsed.toMutableMap()
            if (next[cat] == true) next.remove(cat) else next[cat] = true
            s.copy(collapsed = next)
        }
    }

    fun toggleDateGroup(cat: String, dateKey: String) {
        val key = dateCollapseKey(cat, dateKey)
        _state.update { s ->
            val next = s.collapsedDates.toMutableMap()
            if (next[key] == true) next.remove(key) else next[key] = true
            s.copy(collapsedDates = next)
        }
    }

    fun expandAll() {
        _state.update { it.copy(collapsed = emptyMap(), collapsedDates = emptyMap()) }
    }

    fun collapseAll() {
        val cats = buildSet {
            _state.value.items.forEach { add(it.displayCategory) }
            _state.value.knownFeeds.forEach { add(it.title) }
            _state.value.feedUnreadByTitle.keys.forEach { add(it) }
        }.filter { it.isNotBlank() }.associateWith { true }
        _state.update { it.copy(collapsed = cats, collapsedDates = emptyMap()) }
    }

    fun articleById(id: String): Article? =
        _state.value.items.firstOrNull { it.id == id }

    fun selectArticle(id: String) {
        _state.update { it.copy(selectedArticleId = id) }
        val a = articleById(id)
        if (a != null && _state.value.writable && !a.isRead) {
            markRead(id)
        }
    }

    fun clearArticleSelection() {
        _state.update { it.copy(selectedArticleId = null) }
    }

    fun markRead(id: String) = setReadState(id, read = true)

    fun markUnread(id: String) = setReadState(id, read = false)

    private fun setReadState(id: String, read: Boolean) {
        viewModelScope.launch {
            val cur = articleById(id)
            if (cur != null && cur.isRead == read) return@launch
            // Optimistic UI
            _state.update { s ->
                s.copy(
                    items = s.items.map {
                        if (it.id == id) it.copy(isRead = read) else it
                    },
                    unread = when {
                        read -> (s.unread - 1).coerceAtLeast(0)
                        else -> s.unread + 1
                    },
                )
            }
            if (!_state.value.writable) {
                // Offline-only flip; persist cache
                persistCurrentCache()
                return@launch
            }
            val r = client.markItem(id, read = read)
            if (!r.ok) {
                // Revert
                _state.update { s ->
                    s.copy(
                        items = s.items.map {
                            if (it.id == id) it.copy(isRead = !read) else it
                        },
                        unread = when {
                            read -> s.unread + 1
                            else -> (s.unread - 1).coerceAtLeast(0)
                        },
                        error = r.error,
                    )
                }
            } else {
                persistCurrentCache()
            }
        }
    }

    private suspend fun persistCurrentCache() {
        val s = _state.value
        if (s.items.isEmpty() && s.knownFeeds.isEmpty()) return
        persistOffline(
            scope = s.scope,
            unread = s.unread,
            mode = s.mode,
            writable = s.writable,
            statusLine = s.statusLine,
            items = s.items,
            feeds = s.knownFeeds,
            titles = s.feedUnreadByTitle,
            savedAt = s.lastUpdatedEpochMs.takeIf { it > 0 } ?: System.currentTimeMillis(),
        )
    }

    fun toggleStar(id: String) {
        viewModelScope.launch {
            val cur = articleById(id) ?: return@launch
            val wantSaved = !cur.isSaved
            val r = client.starItem(id, saved = wantSaved)
            if (r.ok) {
                _state.update { s ->
                    s.copy(
                        items = s.items.map {
                            if (it.id == id) it.copy(isSaved = wantSaved) else it
                        },
                    )
                }
                persistCurrentCache()
            } else {
                _state.update { it.copy(error = r.error) }
            }
        }
    }

    fun markFeedRead(feedId: Long) {
        if (feedId <= 0) return
        viewModelScope.launch {
            val r = client.markFeed(feedId, read = true)
            if (r.ok) refresh() else _state.update { it.copy(error = r.error) }
        }
    }

    /** Long-press category: mark whole feed read (server + local list). */
    fun markCategoryRead(category: String, feedId: Long) {
        viewModelScope.launch {
            if (feedId > 0 && _state.value.writable) {
                val r = client.markFeed(feedId, read = true)
                if (r.ok) {
                    refresh()
                    return@launch
                }
                _state.update { it.copy(error = r.error ?: "mark feed read failed") }
            }
            // Fallback: mark each loaded unread item in this category
            val targets = _state.value.items.filter {
                it.displayCategory == category && !it.isRead
            }
            if (targets.isEmpty()) {
                _state.update { it.copy(statusLine = "No unread in “$category”") }
                return@launch
            }
            for (a in targets) {
                if (_state.value.writable) {
                    client.markItem(a.id, read = true)
                }
            }
            _state.update { s ->
                s.copy(
                    items = s.items.map {
                        if (it.displayCategory == category) it.copy(isRead = true) else it
                    },
                    unread = (s.unread - targets.size).coerceAtLeast(0),
                    statusLine = "Marked “$category” read (${targets.size})",
                )
            }
            persistCurrentCache()
        }
    }

    companion object {
        fun factory(
            application: Application,
            client: FreshRssClient,
            settings: SettingsRepository,
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                HomeViewModel(application, client, settings) as T
        }
    }
}

private data class Quint<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)
