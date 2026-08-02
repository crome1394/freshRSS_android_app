package com.crome.freshrss.widget

import android.content.Context
import androidx.core.content.edit
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey

/**
 * Shared numbers for the home-screen widget.
 * Mirror is kept in app SharedPreferences for quick reads; Glance also holds
 * a copy via [PreferencesGlanceStateDefinition] keys below.
 */
data class WidgetState(
    val unreadCount: Int = 0,
    val lastUpdatedEpochMs: Long = 0L,
) {
    val hasData: Boolean get() = lastUpdatedEpochMs > 0L
}

object WidgetPrefsKeys {
    val UNREAD = intPreferencesKey("widget_unread")
    val UPDATED_MS = longPreferencesKey("widget_updated_ms")
}

object WidgetStateStore {
    private const val PREFS = "freshrss_widget_state"
    private const val KEY_UNREAD = "unread"
    private const val KEY_UPDATED = "updated_ms"

    fun load(context: Context): WidgetState {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return WidgetState(
            unreadCount = p.getInt(KEY_UNREAD, 0).coerceAtLeast(0),
            lastUpdatedEpochMs = p.getLong(KEY_UPDATED, 0L),
        )
    }

    fun fromGlancePrefs(prefs: Preferences): WidgetState {
        val unread = prefs[WidgetPrefsKeys.UNREAD]
        val updated = prefs[WidgetPrefsKeys.UPDATED_MS]
        // Prefer Glance state; fall back is handled by caller if empty.
        return WidgetState(
            unreadCount = (unread ?: 0).coerceAtLeast(0),
            lastUpdatedEpochMs = updated ?: 0L,
        )
    }

    fun save(context: Context, unreadCount: Int, lastUpdatedEpochMs: Long) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putInt(KEY_UNREAD, unreadCount.coerceAtLeast(0))
            putLong(KEY_UPDATED, lastUpdatedEpochMs.coerceAtLeast(0L))
        }
    }
}
