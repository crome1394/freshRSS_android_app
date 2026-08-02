package com.crome.freshrss.widget

import android.content.Context
import androidx.core.content.edit

/**
 * Tiny on-disk state for the home-screen widget.
 * Written by the app after refresh / offline load; read by Glance.
 */
data class WidgetState(
    val unreadCount: Int = 0,
    val lastUpdatedEpochMs: Long = 0L,
) {
    val hasData: Boolean get() = lastUpdatedEpochMs > 0L
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

    fun save(context: Context, unreadCount: Int, lastUpdatedEpochMs: Long) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putInt(KEY_UNREAD, unreadCount.coerceAtLeast(0))
            putLong(KEY_UPDATED, lastUpdatedEpochMs.coerceAtLeast(0L))
        }
    }
}
