package com.crome.freshrss.data.model

import kotlinx.serialization.Serializable

/**
 * Domain models matching the JSON shape produced by
 * `~/.config/quickshell/scripts/freshrss-api.sh`.
 */

enum class ReadScope(val apiValue: String) {
    /** Fever unread_item_ids */
    UNREAD("unread"),

    /** Google Reader per-feed streams (read + unread) */
    ALL("all"),

    /** Google Reader per-feed streams, read only */
    READ("read"),

    /** Fever saved_item_ids */
    SAVED("saved");

    companion object {
        fun fromApi(value: String): ReadScope =
            entries.firstOrNull { it.apiValue == value.lowercase() }
                ?: when (value.lowercase()) {
                    "starred" -> SAVED
                    else -> ALL
                }
    }
}

enum class ClientMode {
    /** Fever + GReader with API password */
    FEVER,

    /** Public RSS `/i/?a=rss` (read-only) */
    RSS,
}

data class FreshRssConfig(
    /** Empty until the user enters their FreshRSS host in Settings. */
    val baseUrl: String = DEFAULT_BASE_URL,
    val user: String = DEFAULT_USER,
    /** Profile → API password (not the web form password). Empty = RSS fallback. */
    val apiPassword: String = "",
) {
    val normalizedBaseUrl: String
        get() = baseUrl.trim().trimEnd('/')

    val hasBaseUrl: Boolean
        get() = normalizedBaseUrl.isNotBlank()

    val hasApiPassword: Boolean
        get() = apiPassword.isNotBlank()

    companion object {
        /** No default host — first-run users must enter their server URL. */
        const val DEFAULT_BASE_URL = ""
        const val DEFAULT_USER = "admin"
        const val DEFAULT_ITEM_LIMIT = 80
        const val DEFAULT_PER_FEED_LIMIT = 12
    }
}

/**
 * Single article — mirrors the QML item object from freshrss-api.sh.
 */
@Serializable
data class Article(
    val id: String,
    val idHash: String = id,
    val feedId: Long = 0,
    val feedTitle: String = "",
    val groupId: Long = 0,
    val groupTitle: String = "",
    val category: String = "",
    val title: String = "(no title)",
    val author: String = "",
    val url: String = "",
    val html: String = "",
    val text: String = "",
    val summary: String = "",
    val isRead: Boolean = false,
    val isSaved: Boolean = false,
    val isVideo: Boolean = false,
    /** Direct audio file, podcast host, or HTML &lt;audio&gt; enclosure. */
    val isAudio: Boolean = false,
    val mediaUrl: String = "",
    /** Unix epoch seconds */
    val createdOnTime: Long = 0,
    val pubDate: String = "",
) {
    /**
     * List section key — always the FreshRSS **feed title** (e.g. "Alex Jones Live",
     * "Dark Journalist"), never publisher free-text tags like "U.S. News" that
     * GReader attaches as extra categories on items.
     */
    val displayCategory: String
        get() = feedTitle.ifBlank { category.ifBlank { "Other" } }
}

/**
 * Result of `status` — unread badge + per-feed maps (FreshRSS sidebar style).
 */
data class StatusResult(
    val ok: Boolean,
    val mode: ClientMode = ClientMode.RSS,
    val auth: Boolean = false,
    val writable: Boolean = false,
    val unread: Int = 0,
    /** feed id string → unread count */
    val feeds: Map<String, Int> = emptyMap(),
    /** feed title → unread count */
    val titles: Map<String, Int> = emptyMap(),
    /** label → unread count */
    val labels: Map<String, Int> = emptyMap(),
    val source: String = "",
    val error: String? = null,
)

/** Subscription entry so the UI can show feeds even when no articles loaded. */
@Serializable
data class FeedRef(
    val id: Long,
    val title: String,
)

/**
 * Result of `items [limit] [scope] [per_feed]`.
 */
data class ItemsResult(
    val ok: Boolean,
    val mode: ClientMode = ClientMode.RSS,
    val auth: Boolean = false,
    val writable: Boolean = false,
    val scope: ReadScope = ReadScope.ALL,
    val count: Int = 0,
    val total: Int = 0,
    val items: List<Article> = emptyList(),
    /** All subscriptions (All/Read scopes) so quiet/empty feeds still appear as categories. */
    val feeds: List<FeedRef> = emptyList(),
    val source: String = "",
    val feedsFetched: Int = 0,
    val elapsedMs: Long = 0,
    val errors: List<String> = emptyList(),
    val error: String? = null,
)

data class ActionResult(
    val ok: Boolean,
    val message: String = "",
    val error: String? = null,
)

/**
 * Defaults that match Config.qml / FreshRssPill.qml.
 */
object ReaderDefaults {
    val readScope: ReadScope = ReadScope.ALL
    const val dateFilter: String = "all" // all | today | yesterday | week | 30d
    const val filterMode: String = "all" // all | video | sound
    const val itemLimit: Int = FreshRssConfig.DEFAULT_ITEM_LIMIT
    const val perFeedLimit: Int = FreshRssConfig.DEFAULT_PER_FEED_LIMIT
    const val pollIntervalMs: Long = 60_000L
    const val autoCollapseOnLoad: Boolean = true

    /** Truncation limits from freshrss-api.sh (list payload). */
    const val HTML_MAX = 6000
    const val TEXT_MAX = 1200
    const val SUMMARY_MAX = 220

    /** UI steppers (same spirit as FreshRssPill.qml). */
    val perFeedChoices: List<Int> = listOf(5, 8, 10, 12, 15, 20, 25, 30)
    val itemLimitChoices: List<Int> = listOf(20, 40, 50, 80, 100, 150, 200, 300, 500)
}
