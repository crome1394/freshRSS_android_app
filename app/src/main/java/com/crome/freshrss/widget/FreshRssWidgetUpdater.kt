package com.crome.freshrss.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Persist widget numbers and push a Glance redraw.
 * Call from the app after a successful load or offline restore (app-driven only).
 */
object FreshRssWidgetUpdater {

    private const val TAG = "FreshRssWidget"

    suspend fun publish(context: Context, unreadCount: Int, lastUpdatedEpochMs: Long) {
        withContext(Dispatchers.IO) {
            WidgetStateStore.save(context, unreadCount, lastUpdatedEpochMs)
        }
        try {
            FreshRssGlanceWidget().updateAll(context.applicationContext)
        } catch (e: Exception) {
            // Widget may not be placed yet — non-fatal
            Log.d(TAG, "Widget update skipped: ${e.message}")
        }
    }
}
