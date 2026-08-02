package com.crome.freshrss.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.state.PreferencesGlanceStateDefinition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Persist widget numbers into SharedPreferences + Glance state, then redraw.
 * Call from the app after load, refresh, mark read/unread, or mark category read.
 */
object FreshRssWidgetUpdater {

    private const val TAG = "FreshRssWidget"

    suspend fun publish(context: Context, unreadCount: Int, lastUpdatedEpochMs: Long) {
        val app = context.applicationContext
        val unread = unreadCount.coerceAtLeast(0)
        val updated = lastUpdatedEpochMs.coerceAtLeast(0L)

        withContext(Dispatchers.IO) {
            WidgetStateStore.save(app, unread, updated)
        }

        try {
            val manager = GlanceAppWidgetManager(app)
            val ids = manager.getGlanceIds(FreshRssGlanceWidget::class.java)
            if (ids.isEmpty()) {
                Log.d(TAG, "No widget instances placed; state saved only")
                return
            }
            for (id in ids) {
                updateAppWidgetState(app, PreferencesGlanceStateDefinition, id) { prefs ->
                    prefs.toMutablePreferences().apply {
                        this[WidgetPrefsKeys.UNREAD] = unread
                        this[WidgetPrefsKeys.UPDATED_MS] = updated
                    }
                }
            }
            FreshRssGlanceWidget().updateAll(app)
            Log.i(TAG, "Updated ${ids.size} widget(s): unread=$unread updatedMs=$updated")
        } catch (e: Exception) {
            Log.w(TAG, "Widget update failed: ${e.message}", e)
        }
    }
}
