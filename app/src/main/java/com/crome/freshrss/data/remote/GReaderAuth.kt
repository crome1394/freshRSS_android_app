package com.crome.freshrss.data.remote

import com.crome.freshrss.data.model.FreshRssConfig
import com.crome.freshrss.data.secure.AuthTokenStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Google Reader ClientLogin + ~25 min cache
 * (mirrors ~/.cache/quickshell/freshrss-greader.auth).
 *
 * Disk cache is encrypted via [AuthTokenStore] when provided.
 */
internal class GReaderAuth(
    private val client: OkHttpClient,
    private val tokenStore: AuthTokenStore? = null,
) {
    private val mutex = Mutex()
    private var memoryToken: CachedAuth? = null

    data class CachedAuth(
        val auth: String,
        val user: String,
        val base: String,
        val ts: Long,
    )

    suspend fun token(config: FreshRssConfig): String = mutex.withLock {
        val base = config.normalizedBaseUrl
        val user = config.user
        val now = System.currentTimeMillis()
        val maxAgeMs = 25 * 60 * 1000L

        memoryToken?.let { c ->
            if (c.user == user && c.base == base && now - c.ts < maxAgeMs && c.auth.isNotBlank()) {
                return c.auth
            }
        }

        loadDisk(user, base, now, maxAgeMs)?.let { tok ->
            memoryToken = CachedAuth(tok, user, base, now)
            return tok
        }

        val tok = clientLogin(base, user, config.apiPassword)
        if (tok.isBlank()) throw IllegalStateException("greader ClientLogin failed")
        memoryToken = CachedAuth(tok, user, base, now)
        saveDisk(tok, user, base, now)
        tok
    }

    fun invalidate() {
        memoryToken = null
        tokenStore?.clear()
    }

    private suspend fun clientLogin(base: String, user: String, password: String): String {
        val req = Request.Builder()
            .url("$base/api/greader.php/accounts/ClientLogin")
            .header("User-Agent", HttpSupport.USER_AGENT)
            .post(
                HttpSupport.formBody(
                    "Email" to user,
                    "Passwd" to password,
                ),
            )
            .build()
        val raw = HttpSupport.bodyString(client, req)
        for (line in raw.lineSequence()) {
            if (line.startsWith("Auth=")) {
                return line.substringAfter("=").trim()
            }
        }
        return ""
    }

    private fun loadDisk(user: String, base: String, now: Long, maxAgeMs: Long): String? {
        val raw = tokenStore?.load() ?: return null
        return try {
            val obj = JSONObject(raw)
            if (obj.optString("user") != user || obj.optString("base") != base) return null
            val tsSec = obj.optDouble("ts", 0.0)
            // desktop stores unix seconds; we store ms — accept both
            val tsMs = if (tsSec > 1e12) tsSec.toLong() else (tsSec * 1000).toLong()
            if (now - tsMs > maxAgeMs) return null
            obj.optString("auth").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun saveDisk(auth: String, user: String, base: String, now: Long) {
        val store = tokenStore ?: return
        try {
            val json = JSONObject()
                .put("auth", auth)
                .put("user", user)
                .put("base", base)
                // store seconds for compatibility with desktop cache format
                .put("ts", now / 1000.0)
                .toString()
            store.save(json)
        } catch (_: Exception) {
            // non-fatal
        }
    }

    fun authHeader(token: String): String = "GoogleLogin auth=$token"
}
