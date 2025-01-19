package nethical.digipaws.ui.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.Log
import android.widget.RemoteViews
import nethical.digipaws.R
import nethical.digipaws.utils.SavedPreferencesLoader
import nethical.digipaws.utils.TimeTools

class ReelsWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "ReelsWidgetProvider"
        private const val ACTION_WIDGET_REFRESH = "nethical.digipaws.action.WIDGET_REFRESH"
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        try {
            appWidgetIds.forEach { widgetId ->
                updateWidget(context, appWidgetManager, widgetId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update widgets", e)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        try {
            when (intent.action) {
                ACTION_WIDGET_REFRESH -> handleRefresh(context, intent)
                else -> Log.d(TAG, "Received unhandled action: ${intent.action}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling widget receive", e)
        }
    }

    private fun handleRefresh(context: Context, intent: Intent) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val widgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)

        if (widgetIds == null) {
            Log.e(TAG, "No widget IDs provided for refresh")
            return
        }

        widgetIds.forEach { widgetId ->
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int
    ) {
        try {
            val views = RemoteViews(context.packageName, R.layout.widget_reels_count).apply {
                // Update reels count
                val preferencesLoader = SavedPreferencesLoader(context)
                val currentDate = TimeTools.getCurrentDate()
                val yesterdayDate = TimeTools.getPreviousDate()

                val softGreen = Color.parseColor("#4CAF50") // Muted green
                val softRed = Color.parseColor("#F44336")  // Muted red

                val reelsCountToday = preferencesLoader.getReelsScrolled()[currentDate] ?: 0
                val reelsCountYesterday = preferencesLoader.getReelsScrolled()[yesterdayDate] ?: 0

                // Calculate the change percentage
                val changePercentage = if (reelsCountYesterday > 0) {
                    ((reelsCountToday - reelsCountYesterday).toDouble() / reelsCountYesterday) * 100
                } else {
                    0.0 // No change percentage if no reels were scrolled yesterday
                }

                // Format and set the change percentage for display
                when {
                    changePercentage < 0 -> { // Reduction in usage
                        setTextViewText(
                            R.id.widget_reels_cout_percentage,
                            "-%.1f%%".format(-changePercentage) // Remove negative sign when displaying reduction
                        )
                        setTextColor(R.id.widget_reels_cout_percentage, softGreen) // Green for reduction
                    }
                    changePercentage > 0 -> { // Increase in usage
                        setTextViewText(
                            R.id.widget_reels_cout_percentage,
                            "+%.1f%%".format(changePercentage)
                        )
                        setTextColor(R.id.widget_reels_cout_percentage, softRed) // Red for increase
                    }
                    else -> { // No change
                        setTextViewText(
                            R.id.widget_reels_cout_percentage,
                            "0.0%%" // Display no change
                        )
                        setTextColor(R.id.widget_reels_cout_percentage, Color.WHITE) // Neutral color for no change
                    }
                }


                setTextViewText(R.id.widget_reels_cout, reelsCountToday.toString())

                // Set up refresh button
                val refreshIntent = createRefreshIntent(context, widgetId)
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    widgetId,
                    refreshIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                setOnClickPendingIntent(R.id.refresh_stats, pendingIntent)
            }

            appWidgetManager.updateAppWidget(widgetId, views)
            Log.d(TAG, "Widget $widgetId updated successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating widget $widgetId", e)
        }
    }

    private fun createRefreshIntent(context: Context, widgetId: Int): Intent {
        return Intent(context, ReelsWidgetProvider::class.java).apply {
            action = ACTION_WIDGET_REFRESH
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(widgetId))
        }
    }

}
