package com.crome.freshrss.data.remote

import com.crome.freshrss.data.model.ActionResult
import com.crome.freshrss.data.model.FreshRssConfig
import com.crome.freshrss.data.model.ItemsResult
import com.crome.freshrss.data.model.ReadScope
import com.crome.freshrss.data.model.StatusResult

/**
 * Android port of `scripts/freshrss-api.sh` command surface.
 *
 * | Script command              | Method                          |
 * |-----------------------------|---------------------------------|
 * | status                      | [status]                        |
 * | items [limit] [scope] [pf]  | [items]                         |
 * | mark-read / mark-unread     | [markItem]                      |
 * | star / unstar               | [starItem]                      |
 * | mark-feed-read / unread     | [markFeed]                      |
 *
 * Backends (same as desktop):
 * - **All / Read** → Google Reader API, parallel per-feed streams
 * - **Unread / Saved** → Fever API id lists
 * - No API password → public RSS `/i/?a=rss` (read-only)
 */
interface FreshRssClient {

    var config: FreshRssConfig

    /**
     * Unread badge + per-feed maps.
     * Prefer GReader `unread-count` when API password is set.
     */
    suspend fun status(): StatusResult

    /**
     * @param limit total cap (Unread/Saved) when [historyDays] is 0
     * @param scope unread | all | read | saved
     * @param perFeed recent items per feed for All/Read when [historyDays] is 0
     * @param historyDays if > 0, download articles newer than this many days and
     *   ignore [limit]/[perFeed] as the primary caps (safety max still applies)
     */
    suspend fun items(
        limit: Int = 80,
        scope: ReadScope = ReadScope.ALL,
        perFeed: Int = 12,
        historyDays: Int = 0,
    ): ItemsResult

    /** Fever: mark=item as=read|unread */
    suspend fun markItem(id: String, read: Boolean): ActionResult

    /** Fever: mark=item as=saved|unsaved */
    suspend fun starItem(id: String, saved: Boolean): ActionResult

    /**
     * Whole feed → read (GReader mark-all-as-read + Fever mark=feed)
     * or unread (GReader edit-tag batch).
     * @param feedId numeric FreshRSS/Fever feed id
     */
    suspend fun markFeed(feedId: Long, read: Boolean): ActionResult
}
