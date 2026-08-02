package com.crome.freshrss.data.offline

import android.content.Context
import com.crome.freshrss.data.model.Article
import com.crome.freshrss.data.model.ClientMode
import com.crome.freshrss.data.model.FeedRef
import com.crome.freshrss.data.model.ReadScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Disk snapshot of the last successful fetch so the list can open offline.
 */
@Serializable
data class OfflineSnapshot(
    val savedAtEpochMs: Long,
    val scope: String,
    val unread: Int = 0,
    val mode: String = "fever",
    val writable: Boolean = true,
    val statusLine: String = "",
    val items: List<Article> = emptyList(),
    val feeds: List<FeedRef> = emptyList(),
    val feedUnreadByTitle: Map<String, Int> = emptyMap(),
)

class OfflineCache(context: Context) {
    private val file = File(context.filesDir, "offline_snapshot.json")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun save(snapshot: OfflineSnapshot) = withContext(Dispatchers.IO) {
        try {
            val tmp = File(file.absolutePath + ".tmp")
            tmp.writeText(json.encodeToString(snapshot))
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        } catch (_: Exception) {
            // non-fatal
        }
    }

    suspend fun load(): OfflineSnapshot? = withContext(Dispatchers.IO) {
        try {
            if (!file.isFile) return@withContext null
            json.decodeFromString<OfflineSnapshot>(file.readText())
        } catch (_: Exception) {
            null
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        try {
            file.delete()
        } catch (_: Exception) {
        }
    }
}

fun OfflineSnapshot.toReadScope(): ReadScope = ReadScope.fromApi(scope)

fun OfflineSnapshot.toClientMode(): ClientMode =
    when (mode.lowercase()) {
        "fever" -> ClientMode.FEVER
        else -> ClientMode.RSS
    }
