package com.crome.freshrss.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.crome.freshrss.MainActivity
import com.crome.freshrss.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Compact home-screen widget: app icon, unread ("new") count, last updated.
 * Tap opens [MainActivity].
 */
class FreshRssGlanceWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Single

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Seed Glance state from SharedPreferences on first composition / cold start.
        val disk = WidgetStateStore.load(context)
        provideContent {
            val prefs = currentState<Preferences>()
            val fromGlance = WidgetStateStore.fromGlancePrefs(prefs)
            val state = if (fromGlance.hasData) fromGlance else disk
            GlanceTheme {
                WidgetContent(state = state)
            }
        }
    }
}

@Composable
private fun WidgetContent(state: WidgetState) {
    val context = LocalContext.current
    val countLabel = if (state.hasData) {
        val n = state.unreadCount
        if (n == 1) "1 new" else "$n new"
    } else {
        "Open to load"
    }
    val updatedLabel = if (state.hasData) {
        "Updated ${formatWidgetTime(state.lastUpdatedEpochMs)}"
    } else {
        "Not updated yet"
    }

    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(R.mipmap.ic_launcher),
            contentDescription = context.getString(R.string.app_name),
            modifier = GlanceModifier.size(40.dp),
        )
        Spacer(GlanceModifier.width(10.dp))
        Column(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = context.getString(R.string.app_name),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
            Text(
                text = countLabel,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            Text(
                text = updatedLabel,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 11.sp,
                ),
                maxLines = 1,
            )
        }
    }
}

private fun formatWidgetTime(epochMs: Long): String {
    if (epochMs <= 0L) return ""
    return SimpleDateFormat("MMM d  HH:mm", Locale.getDefault()).format(Date(epochMs))
}

class FreshRssGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FreshRssGlanceWidget()
}
