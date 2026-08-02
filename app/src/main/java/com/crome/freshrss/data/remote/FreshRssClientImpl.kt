package com.crome.freshrss.data.remote

import com.crome.freshrss.data.model.ActionResult
import com.crome.freshrss.data.model.Article
import com.crome.freshrss.data.model.ClientMode
import com.crome.freshrss.data.model.FreshRssConfig
import com.crome.freshrss.data.model.ItemsResult
import com.crome.freshrss.data.model.ReadScope
import com.crome.freshrss.data.model.ReaderDefaults
import com.crome.freshrss.data.model.StatusResult
import com.crome.freshrss.data.secure.AuthTokenStore
import com.crome.freshrss.util.FeverAuth
import com.crome.freshrss.util.HtmlUtils
import com.crome.freshrss.util.MediaUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Production client ported from freshrss-api.sh.
 *
 * Fever for Unread/Saved + mark/star; GReader for status unread-count,
 * All/Read per-feed streams, and mark-feed.
 */
class FreshRssClientImpl(
    private val http: OkHttpClient = HttpSupport.defaultClient(),
    authTokenStore: AuthTokenStore? = null,
) : FreshRssClient {

    override var config: FreshRssConfig = FreshRssConfig()

    private val greaderAuth = GReaderAuth(
        client = http,
        tokenStore = authTokenStore,
    )

    private val feverMode: Boolean
        get() = config.hasApiPassword

    // ── status ──────────────────────────────────────────────────────────────

    override suspend fun status(): StatusResult = withContext(Dispatchers.IO) {
        try {
            if (feverMode) {
                greaderStatus()
            } else {
                rssStatus()
            }
        } catch (e: Exception) {
            StatusResult(ok = false, error = e.message ?: "status failed")
        }
    }

    private suspend fun greaderStatus(): StatusResult {
        val base = config.normalizedBaseUrl
        val auth = greaderAuth.token(config)
        val authH = greaderAuth.authHeader(auth)

        fun get(path: String): JSONObject {
            val req = Request.Builder()
                .url("$base/api/greader.php$path")
                .header("User-Agent", HttpSupport.USER_AGENT)
                .header("Authorization", authH)
                .get()
                .build()
            // sync within IO dispatcher
            return JSONObject(http.newCall(req).execute().use { r ->
                val body = r.body?.string().orEmpty()
                if (!r.isSuccessful) throw HttpException(r.code, body.take(200))
                body
            })
        }

        val (counts, subs) = try {
            get("/reader/api/0/unread-count?output=json") to
                get("/reader/api/0/subscription/list?output=json")
        } catch (_: Exception) {
            greaderAuth.invalidate()
            val auth2 = greaderAuth.token(config)
            val authH2 = greaderAuth.authHeader(auth2)
            fun get2(path: String): JSONObject {
                val req = Request.Builder()
                    .url("$base/api/greader.php$path")
                    .header("User-Agent", HttpSupport.USER_AGENT)
                    .header("Authorization", authH2)
                    .get()
                    .build()
                return JSONObject(http.newCall(req).execute().use { r ->
                    val body = r.body?.string().orEmpty()
                    if (!r.isSuccessful) throw HttpException(r.code, body.take(200))
                    body
                })
            }
            get2("/reader/api/0/unread-count?output=json") to
                get2("/reader/api/0/subscription/list?output=json")
        }

        val byStream = mutableMapOf<String, Int>()
        val uc = counts.optJSONArray("unreadcounts") ?: JSONArray()
        for (i in 0 until uc.length()) {
            val u = uc.getJSONObject(i)
            val id = u.optString("id")
            if (id.isNotBlank()) byStream[id] = u.optInt("count", 0)
        }

        val feeds = mutableMapOf<String, Int>()
        val labels = mutableMapOf<String, Int>()
        for ((sid, c) in byStream) {
            when {
                sid.startsWith("feed/") -> feeds[sid.removePrefix("feed/")] = c
                "/label/" in sid -> {
                    val lab = sid.substringAfter("/label/").replace('+', ' ')
                    labels[java.net.URLDecoder.decode(lab, Charsets.UTF_8.name())] = c
                }
            }
        }

        val titles = mutableMapOf<String, Int>()
        val subsArr = subs.optJSONArray("subscriptions") ?: JSONArray()
        for (i in 0 until subsArr.length()) {
            val s = subsArr.getJSONObject(i)
            val sid = s.optString("id")
            val title = s.optString("title").ifBlank { sid }
            titles[title] = byStream[sid] ?: 0
        }

        val n = counts.optInt("max", 0).takeIf { it > 0 }
            ?: byStream["user/-/state/com.google/reading-list"]
            ?: 0

        return StatusResult(
            ok = true,
            mode = ClientMode.FEVER,
            auth = true,
            writable = true,
            unread = n,
            feeds = feeds,
            titles = titles,
            labels = labels,
            source = "greader",
        )
    }

    private suspend fun rssStatus(): StatusResult {
        val items = parseRssItems(limit = 5)
        return StatusResult(
            ok = true,
            mode = ClientMode.RSS,
            auth = false,
            writable = false,
            unread = items.count,
            source = "rss",
        )
    }

    // ── items ───────────────────────────────────────────────────────────────

    override suspend fun items(
        limit: Int,
        scope: ReadScope,
        perFeed: Int,
        historyDays: Int,
    ): ItemsResult = withContext(Dispatchers.IO) {
        try {
            if (!feverMode) {
                return@withContext parseRssItems(limit).let { r ->
                    if (historyDays > 0) filterByHistoryDays(r, historyDays) else r
                }
            }
            when (scope) {
                // Desktop: All/Read always use GReader per-feed streams (quiet channels included).
                ReadScope.ALL, ReadScope.READ ->
                    fetchGreaderPerFeed(scope, limit, perFeed, historyDays)
                // Unread/Starred: Fever id lists — but still attach full feed list for headers.
                ReadScope.UNREAD, ReadScope.SAVED ->
                    fetchFeverItems(scope, limit, historyDays)
            }
        } catch (e: Exception) {
            ItemsResult(ok = false, scope = scope, error = e.message ?: "items failed")
        }
    }

    /** Keep only articles with createdOnTime within the last [days] days. */
    private fun filterByHistoryDays(result: ItemsResult, days: Int): ItemsResult {
        if (!result.ok || days <= 0) return result
        val minEpoch = historyCutoffEpochSeconds(days)
        val kept = result.items.filter { it.createdOnTime <= 0 || it.createdOnTime >= minEpoch }
        return result.copy(
            items = kept,
            count = kept.size,
            total = kept.size,
        )
    }

    private fun historyCutoffEpochSeconds(days: Int): Long {
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_YEAR, -(days.coerceAtLeast(1) - 1))
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis / 1000L
    }

    private suspend fun fetchGreaderPerFeed(
        scope: ReadScope,
        limit: Int,
        perFeed: Int,
        historyDays: Int,
    ): ItemsResult {
        val base = config.normalizedBaseUrl
        val auth = greaderAuth.token(config)
        val authH = greaderAuth.authHeader(auth)
        // History mode: pull a large recent window per feed, then cut by date.
        // Count mode: honor per-feed stepper (legacy desktop-style).
        val useHistory = historyDays > 0
        val pf = if (useHistory) {
            100 // safety max per feed while covering N days
        } else {
            perFeed.coerceIn(1, 40)
        }
        val minEpoch = if (useHistory) historyCutoffEpochSeconds(historyDays) else 0L
        val t0 = System.currentTimeMillis()

        val subsReq = Request.Builder()
            .url("$base/api/greader.php/reader/api/0/subscription/list?output=json")
            .header("User-Agent", HttpSupport.USER_AGENT)
            .header("Authorization", authH)
            .get()
            .build()
        val subsJson = JSONObject(
            http.newCall(subsReq).execute().use { r ->
                val body = r.body?.string().orEmpty()
                if (!r.isSuccessful) throw HttpException(r.code, body.take(200))
                body
            },
        )
        val feedsArr = subsJson.optJSONArray("subscriptions") ?: JSONArray()
        val feedCount = feedsArr.length()

        // Full subscription list — UI uses this so every feed appears as a category
        // even when the stream returned 0 items (quiet channel / fetch error).
        val feedRefs = mutableListOf<com.crome.freshrss.data.model.FeedRef>()
        for (i in 0 until feedCount) {
            val sub = feedsArr.getJSONObject(i)
            val stream = sub.optString("id")
            val fid = Regex("""feed/(\d+)$""").find(stream)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val title = sub.optString("title").ifBlank { stream }
            if (title.isNotBlank()) {
                feedRefs += com.crome.freshrss.data.model.FeedRef(id = fid, title = title)
            }
        }

        val out = ConcurrentHashMap.newKeySet<String>()
        val articles = mutableListOf<Article>()
        val errors = mutableListOf<String>()
        val lock = Any()

        // Parallel streams — same spirit as desktop (thread pool ~12).
        val workers = 12
        val semaphore = Semaphore(permits = workers)
        coroutineScope {
            val jobs = (0 until feedCount).map { i ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        val sub = feedsArr.getJSONObject(i)
                        val title = sub.optString("title").ifBlank { sub.optString("id") }
                        try {
                            val rows = fetchOneFeedStream(base, authH, sub, scope, pf, minEpoch)
                            synchronized(lock) {
                                for (row in rows) {
                                    if (out.add(row.id)) articles += row
                                }
                            }
                        } catch (e: Exception) {
                            // One retry (auth blip / transient LAN error)
                            try {
                                val rows = fetchOneFeedStream(base, authH, sub, scope, pf, minEpoch)
                                synchronized(lock) {
                                    for (row in rows) {
                                        if (out.add(row.id)) articles += row
                                    }
                                }
                            } catch (e2: Exception) {
                                synchronized(lock) {
                                    errors += "$title: ${e2.message}"
                                }
                            }
                        }
                    }
                }
            }
            jobs.awaitAll()
        }

        // Keep every per-feed result. Do NOT apply a global "newest 800" trim —
        // that erased quiet channels (Dark Journalist, etc.) on desktop-sized installs.
        val capped = articles.sortedByDescending { it.createdOnTime }

        return ItemsResult(
            ok = true,
            mode = ClientMode.FEVER,
            auth = true,
            writable = true,
            scope = scope,
            count = capped.size,
            total = capped.size,
            items = capped,
            feeds = feedRefs.sortedBy { it.title.lowercase() },
            source = if (useHistory) "greader-per-feed-${historyDays}d" else "greader-per-feed",
            feedsFetched = feedCount,
            elapsedMs = System.currentTimeMillis() - t0,
            errors = errors.take(20),
        )
    }

    private fun fetchOneFeedStream(
        base: String,
        authH: String,
        sub: JSONObject,
        scope: ReadScope,
        perFeed: Int,
        minEpoch: Long = 0L,
    ): List<Article> {
        val stream = sub.optString("id")
        if (stream.isBlank()) return emptyList()
        val ftitle = sub.optString("title")
        val site = sub.optString("htmlUrl").ifBlank { sub.optString("url") }
        var groupTitle = ""
        val cats = sub.optJSONArray("categories")
        if (cats != null && cats.length() > 0) {
            val c0 = cats.getJSONObject(0)
            groupTitle = c0.optString("label").ifBlank { c0.optString("id") }
        }
        val fid = Regex("""feed/(\d+)$""").find(stream)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        // Prefer raw path feed/N (FreshRSS accepts it). Avoid double-encoding pitfalls.
        val url = "$base/api/greader.php/reader/api/0/stream/contents/$stream?n=$perFeed&output=json"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", HttpSupport.USER_AGENT)
            .header("Authorization", authH)
            .get()
            .build()
        val data = JSONObject(
            http.newCall(req).execute().use { r ->
                val body = r.body?.string().orEmpty()
                if (!r.isSuccessful) throw HttpException(r.code, body.take(200))
                body
            },
        )

        val rows = mutableListOf<Article>()
        val items = data.optJSONArray("items") ?: JSONArray()
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val iid = greaderIdToFever(item.optString("id"))
            if (iid.isBlank()) continue

            val categories = item.optJSONArray("categories") ?: JSONArray()
            val catList = buildList {
                for (ci in 0 until categories.length()) {
                    runCatching { categories.getString(ci) }.getOrNull()?.let { add(it) }
                }
            }
            val isRead = catList.any { it.endsWith("/state/com.google/read") }
            val isSaved = catList.any { it.endsWith("/state/com.google/starred") }
            if (scope == ReadScope.READ && !isRead) continue
            if (scope == ReadScope.SAVED && !isSaved) continue

            var link = ""
            for (key in listOf("canonical", "alternate")) {
                val arr = item.optJSONArray(key) ?: continue
                for (j in 0 until arr.length()) {
                    val href = arr.getJSONObject(j).optString("href")
                    if (href.isNotBlank()) {
                        link = href
                        break
                    }
                }
                if (link.isNotBlank()) break
            }

            var html = ""
            item.optJSONObject("content")?.let { c -> html = c.optString("content") }
            if (html.isBlank()) {
                item.optJSONObject("summary")?.let { s -> html = s.optString("content") }
            }
            val media = MediaUtils.extractMedia(html, link, ftitle, site)
            var plain = HtmlUtils.stripHtml(html)
            html = HtmlUtils.truncateHtml(html)
            plain = HtmlUtils.truncateText(plain)

            // Section by FreshRSS subscription title ONLY.
            // GReader also attaches publisher tags (e.g. "U.S. News") and labels
            // (user/-/label/News) — those must NOT become list categories.
            val origin = item.optJSONObject("origin")
            val originTitle = origin?.optString("title").orEmpty()
            val feedTitle = ftitle.ifBlank { originTitle }.ifBlank { "Other" }
            var gtitle = groupTitle
            for (c in catList) {
                if ("/label/" in c) {
                    gtitle = java.net.URLDecoder.decode(
                        c.substringAfter("/label/").replace('+', ' '),
                        Charsets.UTF_8.name(),
                    )
                    break
                }
            }
            val created = jsonEpochSeconds(item, "published", "updated")
            // History window: drop items older than cutoff (still request a large n so quiet days fill).
            if (minEpoch > 0 && created > 0 && created < minEpoch) continue
            rows += Article(
                id = iid,
                idHash = iid,
                feedId = fid,
                feedTitle = feedTitle,
                groupTitle = gtitle,
                category = feedTitle,
                title = item.optString("title").ifBlank { "(no title)" },
                author = item.optString("author"),
                url = link,
                html = html,
                text = plain,
                summary = HtmlUtils.summary(plain),
                isRead = isRead,
                isSaved = isSaved,
                isVideo = media.isVideo,
                isAudio = media.isAudio,
                mediaUrl = media.mediaUrl,
                createdOnTime = created,
            )
        }
        return rows
    }

    /** Parse GReader/Fever timestamps (seconds or milliseconds). */
    private fun jsonEpochSeconds(obj: JSONObject, vararg keys: String): Long {
        for (key in keys) {
            if (!obj.has(key) || obj.isNull(key)) continue
            val n = when (val raw = obj.opt(key)) {
                is Number -> raw.toLong()
                is String -> raw.trim().toLongOrNull() ?: continue
                else -> continue
            }
            if (n <= 0) continue
            // ms (13 digits) → seconds
            return if (n > 10_000_000_000L) n / 1000L else n
        }
        return 0L
    }

    private fun greaderIdToFever(gid: String): String {
        if (gid.isBlank()) return ""
        val hexpart = gid.substringAfterLast('/')
        return try {
            hexpart.toLong(16).toString()
        } catch (_: Exception) {
            gid
        }
    }

    private suspend fun fetchFeverItems(
        scope: ReadScope,
        limit: Int,
        historyDays: Int,
    ): ItemsResult {
        val key = FeverAuth.apiKey(config.user, config.apiPassword)
        val base = config.normalizedBaseUrl
        val useHistory = historyDays > 0
        val minEpoch = if (useHistory) historyCutoffEpochSeconds(historyDays) else 0L
        // History mode: pull many ids (date filter applied after body fetch).
        val idCap = if (useHistory) 2000 else limit.coerceAtLeast(1)

        // auth check
        val authResp = feverPost(base, key, "api")
        if (authResp.optInt("auth", 0) != 1) {
            return ItemsResult(ok = false, scope = scope, error = "fever auth failed")
        }

        // Fever field: unread_item_ids or saved_item_ids
        val idField = if (scope == ReadScope.SAVED) {
            feverPost(base, key, "api&saved_item_ids").optString("saved_item_ids")
        } else {
            feverPost(base, key, "api&unread_item_ids").optString("unread_item_ids")
        }

        val ids = idField.split(',')
            .map { it.trim() }
            .filter { it.matches(Regex("""^\d+$""")) }
            .mapNotNull { it.toLongOrNull() }
            .sorted()
            .takeLast(idCap)
            .sortedDescending()

        val feedsBlob = runCatching { feverPost(base, key, "api&feeds") }.getOrNull()
        val groupsBlob = runCatching { feverPost(base, key, "api&groups") }.getOrNull()
        val feedMap = mutableMapOf<Long, JSONObject>()
        feedsBlob?.optJSONArray("feeds")?.let { arr ->
            for (i in 0 until arr.length()) {
                val f = arr.getJSONObject(i)
                feedMap[f.optLong("id")] = f
            }
        }
        val feedRefs = feedMap.values.map { f ->
            com.crome.freshrss.data.model.FeedRef(
                id = f.optLong("id"),
                title = f.optString("title").ifBlank { "feed/${f.optLong("id")}" },
            )
        }.sortedBy { it.title.lowercase() }

        if (ids.isEmpty()) {
            return ItemsResult(
                ok = true,
                mode = ClientMode.FEVER,
                auth = true,
                writable = true,
                scope = scope,
                count = 0,
                total = 0,
                items = emptyList(),
                feeds = feedRefs,
                source = "fever",
            )
        }
        val groupTitle = mutableMapOf<Long, String>()
        groupsBlob?.optJSONArray("groups")?.let { arr ->
            for (i in 0 until arr.length()) {
                val g = arr.getJSONObject(i)
                groupTitle[g.optLong("id")] = g.optString("title")
            }
        }
        val feedToGroup = mutableMapOf<Long, Long>()
        groupsBlob?.optJSONArray("feeds_groups")?.let { arr ->
            for (i in 0 until arr.length()) {
                val fg = arr.getJSONObject(i)
                val gid = fg.optLong("group_id")
                for (part in fg.optString("feed_ids").split(',')) {
                    val p = part.trim()
                    if (p.isNotEmpty()) feedToGroup[p.toLong()] = gid
                }
            }
        }

        val collected = mutableListOf<JSONObject>()
        for (chunk in ids.chunked(40)) {
            val withIds = chunk.joinToString(",")
            val chunkJson = feverPost(base, key, "api&items&with_ids=$withIds")
            val arr = chunkJson.optJSONArray("items") ?: JSONArray()
            for (i in 0 until arr.length()) collected += arr.getJSONObject(i)
        }

        var out = collected.map { item -> mapFeverItem(item, feedMap, feedToGroup, groupTitle) }
            .sortedByDescending { it.createdOnTime }
        if (useHistory && minEpoch > 0) {
            out = out.filter { it.createdOnTime <= 0 || it.createdOnTime >= minEpoch }
        } else {
            out = out.take(limit.coerceIn(1, 500))
        }

        return ItemsResult(
            ok = true,
            mode = ClientMode.FEVER,
            auth = true,
            writable = true,
            scope = scope,
            count = out.size,
            total = out.size,
            items = out,
            feeds = feedRefs,
            source = if (useHistory) "fever-${historyDays}d" else "fever",
        )
    }

    private fun mapFeverItem(
        it: JSONObject,
        feedMap: Map<Long, JSONObject>,
        feedToGroup: Map<Long, Long>,
        groupTitle: Map<Long, String>,
    ): Article {
        var html = it.optString("html")
        val url = it.optString("url")
        val fid = it.optLong("feed_id")
        val fmeta = feedMap[fid]
        val media = MediaUtils.extractMedia(
            html,
            url,
            fmeta?.optString("title"),
            fmeta?.optString("site_url"),
        )
        var plain = HtmlUtils.stripHtml(html)
        html = HtmlUtils.truncateHtml(html)
        plain = HtmlUtils.truncateText(plain)
        val gid = feedToGroup[fid]
        val gtitle = gid?.let { groupTitle[it] }.orEmpty()
        // Always FreshRSS feed title — never publisher/html categories.
        val ftitle = fmeta?.optString("title").orEmpty().ifBlank { "feed/$fid" }
        return Article(
            id = it.opt("id")?.toString() ?: "",
            idHash = it.opt("id")?.toString() ?: "",
            feedId = fid,
            feedTitle = ftitle,
            groupId = gid ?: 0,
            groupTitle = gtitle,
            category = ftitle,
            title = it.optString("title").ifBlank { "(no title)" },
            author = it.optString("author"),
            url = url,
            html = html,
            text = plain,
            summary = HtmlUtils.summary(plain),
            isRead = it.optInt("is_read") == 1 || it.opt("is_read") == true,
            isSaved = it.optInt("is_saved") == 1 || it.opt("is_saved") == true,
            isVideo = media.isVideo,
            isAudio = media.isAudio,
            mediaUrl = media.mediaUrl,
            createdOnTime = jsonEpochSeconds(it, "created_on_time"),
        )
    }

    private fun feverPost(base: String, apiKey: String, query: String, extra: Map<String, String> = emptyMap()): JSONObject {
        val form = FormBodyBuilder()
            .add("api_key", apiKey)
        for ((k, v) in extra) form.add(k, v)
        val req = Request.Builder()
            .url("$base/api/fever.php?$query")
            .header("User-Agent", HttpSupport.USER_AGENT)
            .post(form.build())
            .build()
        val body = http.newCall(req).execute().use { r ->
            val b = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw HttpException(r.code, b.take(200))
            b
        }
        return JSONObject(body.ifBlank { "{}" })
    }

    // small helper so we don't import FormBody.Builder name clash
    private class FormBodyBuilder {
        private val b = okhttp3.FormBody.Builder()
        fun add(n: String, v: String) = apply { b.add(n, v) }
        fun build() = b.build()
    }

    // ── mark / star ─────────────────────────────────────────────────────────

    override suspend fun markItem(id: String, read: Boolean): ActionResult =
        withContext(Dispatchers.IO) {
            if (!feverMode) {
                return@withContext ActionResult(ok = false, error = "mark requires API password")
            }
            try {
                val key = FeverAuth.apiKey(config.user, config.apiPassword)
                val asVal = if (read) "read" else "unread"
                val resp = feverPost(
                    config.normalizedBaseUrl,
                    key,
                    "api",
                    mapOf("mark" to "item", "as" to asVal, "id" to id),
                )
                if (resp.optInt("auth", 0) != 1) {
                    return@withContext ActionResult(ok = false, error = "fever auth failed")
                }
                ActionResult(ok = true, message = "marked $asVal")
            } catch (e: Exception) {
                ActionResult(ok = false, error = e.message)
            }
        }

    override suspend fun starItem(id: String, saved: Boolean): ActionResult =
        withContext(Dispatchers.IO) {
            if (!feverMode) {
                return@withContext ActionResult(ok = false, error = "star requires API password")
            }
            try {
                val key = FeverAuth.apiKey(config.user, config.apiPassword)
                val asVal = if (saved) "saved" else "unsaved"
                val resp = feverPost(
                    config.normalizedBaseUrl,
                    key,
                    "api",
                    mapOf("mark" to "item", "as" to asVal, "id" to id),
                )
                if (resp.optInt("auth", 0) != 1) {
                    return@withContext ActionResult(ok = false, error = "fever auth failed")
                }
                ActionResult(ok = true, message = asVal)
            } catch (e: Exception) {
                ActionResult(ok = false, error = e.message)
            }
        }

    override suspend fun markFeed(feedId: Long, read: Boolean): ActionResult =
        withContext(Dispatchers.IO) {
            if (!feverMode) {
                return@withContext ActionResult(ok = false, error = "mark-feed requires API password")
            }
            try {
                if (read) markFeedRead(feedId) else markFeedUnread(feedId)
            } catch (e: Exception) {
                ActionResult(ok = false, error = e.message)
            }
        }

    private suspend fun markFeedRead(feedId: Long): ActionResult {
        val base = config.normalizedBaseUrl
        val auth = greaderAuth.token(config)
        val stream = "feed/$feedId"
        val ts = "${System.currentTimeMillis()}000" // µs-ish padding like script
        val req = Request.Builder()
            .url("$base/api/greader.php/reader/api/0/mark-all-as-read")
            .header("User-Agent", HttpSupport.USER_AGENT)
            .header("Authorization", greaderAuth.authHeader(auth))
            .post(HttpSupport.formBody("s" to stream, "ts" to ts))
            .build()
        http.newCall(req).execute().use { /* best effort */ }

        // Fever fallback
        val key = FeverAuth.apiKey(config.user, config.apiPassword)
        val before = (System.currentTimeMillis() / 1000).toString()
        runCatching {
            feverPost(
                base,
                key,
                "api",
                mapOf(
                    "mark" to "feed",
                    "as" to "read",
                    "id" to feedId.toString(),
                    "before" to before,
                ),
            )
        }
        return ActionResult(ok = true, message = "feed $feedId marked read")
    }

    private suspend fun markFeedUnread(feedId: Long): ActionResult {
        val base = config.normalizedBaseUrl
        val auth = greaderAuth.token(config)
        val stream = "feed/$feedId"
        val authH = greaderAuth.authHeader(auth)

        val ids = mutableListOf<String>()
        var cont: String? = null
        repeat(20) {
            val q = buildString {
                append("s=${URLEncoder.encode(stream, "UTF-8")}")
                append("&n=1000&output=json")
                if (cont != null) append("&c=${URLEncoder.encode(cont, "UTF-8")}")
            }
            val req = Request.Builder()
                .url("$base/api/greader.php/reader/api/0/stream/items/ids?$q")
                .header("User-Agent", HttpSupport.USER_AGENT)
                .header("Authorization", authH)
                .get()
                .build()
            val data = JSONObject(
                http.newCall(req).execute().use { r ->
                    r.body?.string().orEmpty()
                },
            )
            val refs = data.optJSONArray("itemRefs") ?: JSONArray()
            for (i in 0 until refs.length()) {
                val iid = refs.getJSONObject(i).opt("id")?.toString() ?: continue
                val hexid = try {
                    iid.toLong().toString(16).padStart(16, '0')
                } catch (_: Exception) {
                    continue
                }
                ids += "tag:google.com,2005:reader/item/$hexid"
            }
            cont = data.optString("continuation").takeIf { it.isNotBlank() }
            if (cont == null || refs.length() == 0) return@repeat
        }

        val readTag = "user/-/state/com.google/read"
        var marked = 0
        for (batch in ids.chunked(50)) {
            val form = okhttp3.FormBody.Builder().add("r", readTag)
            for (item in batch) form.add("i", item)
            val req = Request.Builder()
                .url("$base/api/greader.php/reader/api/0/edit-tag")
                .header("User-Agent", HttpSupport.USER_AGENT)
                .header("Authorization", authH)
                .post(form.build())
                .build()
            http.newCall(req).execute().use { }
            marked += batch.size
        }
        return ActionResult(ok = true, message = "feed $feedId: $marked items unmarked read")
    }

    // ── public RSS fallback ─────────────────────────────────────────────────

    private fun parseRssItems(limit: Int): ItemsResult {
        val base = config.normalizedBaseUrl
        val req = Request.Builder()
            .url("$base/i/?a=rss")
            .header("User-Agent", HttpSupport.USER_AGENT)
            .get()
            .build()
        val xml = http.newCall(req).execute().use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw HttpException(r.code, body.take(200))
            body
        }

        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        val items = mutableListOf<Article>()
        var event = parser.eventType
        var inItem = false
        var title = ""
        var link = ""
        var guid = ""
        var author = ""
        var category = ""
        var pubDate = ""
        var description = ""
        var contentEncoded = ""

        fun text(): String {
            return if (parser.next() == XmlPullParser.TEXT) parser.text ?: "" else ""
        }

        while (event != XmlPullParser.END_DOCUMENT && items.size < limit.coerceIn(1, 200)) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name
                    when {
                        name.equals("item", ignoreCase = true) -> {
                            inItem = true
                            title = ""; link = ""; guid = ""; author = ""
                            category = ""; pubDate = ""; description = ""; contentEncoded = ""
                        }
                        inItem && name.equals("title", ignoreCase = true) -> title = text()
                        inItem && name.equals("link", ignoreCase = true) -> link = text()
                        inItem && name.equals("guid", ignoreCase = true) -> guid = text()
                        inItem && name.equals("author", ignoreCase = true) -> author = text()
                        inItem && name.equals("creator", ignoreCase = true) -> author = text()
                        inItem && name.equals("category", ignoreCase = true) -> {
                            if (category.isBlank()) category = text()
                        }
                        inItem && name.equals("pubDate", ignoreCase = true) -> pubDate = text()
                        inItem && name.equals("description", ignoreCase = true) -> description = text()
                        inItem && name.equals("encoded", ignoreCase = true) -> contentEncoded = text()
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name.equals("item", ignoreCase = true) && inItem) {
                        inItem = false
                        val html = contentEncoded.ifBlank { description }
                        val idStr = guid.ifBlank { link.ifBlank { title } }
                        val media = MediaUtils.extractMedia(html, link, category, null)
                        val plain = HtmlUtils.stripHtml(html)
                        val epoch = parseRfc822(pubDate)
                        // Public RSS has no per-feed title — do NOT use item <category>
                        // (e.g. "U.S. News") as a FreshRSS feed name.
                        val feedName = "FreshRSS (anonymous RSS)"
                        items += Article(
                            id = idStr,
                            idHash = idStr.hashCode().toUInt().toString(16),
                            feedTitle = feedName,
                            category = feedName,
                            title = title.ifBlank { "(no title)" },
                            author = author,
                            url = link,
                            html = HtmlUtils.truncateHtml(html),
                            text = HtmlUtils.truncateText(plain),
                            summary = HtmlUtils.summary(plain),
                            isRead = false,
                            isSaved = false,
                            isVideo = media.isVideo,
                            isAudio = media.isAudio,
                            mediaUrl = media.mediaUrl,
                            createdOnTime = epoch,
                            pubDate = pubDate,
                        )
                    }
                }
            }
            event = parser.next()
        }

        items.sortByDescending { it.createdOnTime }

        // Optional HTML title "(N)" unread — best effort
        var count = items.size
        try {
            val pageReq = Request.Builder()
                .url("$base/i/")
                .header("User-Agent", HttpSupport.USER_AGENT)
                .get()
                .build()
            val page = http.newCall(pageReq).execute().use { it.body?.string().orEmpty().take(8000) }
            Regex("""<title>\((\d+)\)""").find(page)?.groupValues?.get(1)?.toIntOrNull()?.let {
                count = it
            }
        } catch (_: Exception) {
        }

        return ItemsResult(
            ok = true,
            mode = ClientMode.RSS,
            auth = false,
            writable = false,
            scope = ReadScope.ALL,
            count = count,
            total = items.size,
            items = items,
            source = "rss",
        )
    }

    private fun parseRfc822(pub: String): Long {
        if (pub.isBlank()) return 0
        return try {
            // Android has no javax.mail; use DateUtils-style fallbacks
            val formats = listOf(
                java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", java.util.Locale.US),
                java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", java.util.Locale.US),
            )
            for (f in formats) {
                try {
                    f.parse(pub)?.time?.let { return it / 1000 }
                } catch (_: Exception) {
                }
            }
            0
        } catch (_: Exception) {
            0
        }
    }
}
