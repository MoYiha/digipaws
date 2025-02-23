package nethical.digipaws.ui.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import nethical.digipaws.R
import nethical.digipaws.ui.fragments.usage.AllAppsUsageFragment
import nethical.digipaws.utils.SavedPreferencesLoader
import nethical.digipaws.utils.TimeTools
import nethical.digipaws.utils.UsageStatsHelper
import nethical.digipaws.utils.getDefaultLauncherPackageName

class ScreentimeWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "ScreentimeWidgetProvider"
        private const val ACTION_WIDGET_REFRESH = "nethical.digipaws.screentime.WIDGET_REFRESH"
        private const val MIN_WIDTH_CELLS = 2 // Minimum width in cells to show per-app stats
        private const val MIN_HEIGHT_CELLS = 1 // Minimum height in cells for full layout
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

                ACTION_WIDGET_REFRESH, "android.appwidget.action.APPWIDGET_UPDATE", "android.appwidget.action.APPWIDGET_UPDATE_OPTIONS" -> {
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val widgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
                        ?: appWidgetManager.getAppWidgetIds(
                            android.content.ComponentName(
                                context,
                                ScreentimeWidgetProvider::class.java
                            )
                        )
                    widgetIds.forEach { widgetId ->
                        updateWidget(context, appWidgetManager, widgetId)
                    }
                }

                else -> Log.d(TAG, "Received unhandled action: ${intent.action}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling widget receive", e)
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int
    ) {
        val usageStatsHelper = UsageStatsHelper(context)
        val ignoredPackages = mutableSetOf<String>()
        getDefaultLauncherPackageName(context.packageManager)?.let { ignoredPackages.add(it) }
        val savedPreferencesLoader = SavedPreferencesLoader(context)
        ignoredPackages.addAll(savedPreferencesLoader.loadIgnoredAppUsageTracker())

        val list = usageStatsHelper.getForegroundStatsByRelativeDay(0).filter {
            it.totalTime >= 180_000 && it.packageName !in ignoredPackages
        }
        val totalScreentime = list.sumOf { it.totalTime }

        // Get widget size in cells
        val options = appWidgetManager.getAppWidgetOptions(widgetId)
        val widthCells =
            getCellsForSize(options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH))
        val heightCells =
            getCellsForSize(options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT))
        Log.d(TAG, "Widget $widgetId size: ${widthCells}x${heightCells} cells")

        try {
            val views = if (widthCells < MIN_WIDTH_CELLS || heightCells < MIN_HEIGHT_CELLS) {
                // Small layout (e.g., 1x1): Only total screen time
                RemoteViews(context.packageName, R.layout.widget_app_stats_small).apply {
                    setTextViewText(R.id.screentime_widget, formatTime(totalScreentime))
                }
            } else {
                // Full layout: Total + per-app stats
                RemoteViews(context.packageName, R.layout.widget_app_stats).apply {
                    setTextViewText(R.id.screentime_widget, formatTime(totalScreentime))
                    setAppUsageText(this, 0, list, R.id.app_1_sm, context)
                    setAppUsageText(this, 1, list, R.id.app_2_sm, context)
                    setAppUsageText(this, 2, list, R.id.app_3_sm, context)
                }
            }

            // Set refresh button for both layouts
            val refreshIntent = createRefreshIntent(context, widgetId)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                widgetId,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.refresh_stats_screentime, pendingIntent)

            appWidgetManager.updateAppWidget(widgetId, views)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating widget $widgetId", e)
        }
    }

    private fun setAppUsageText(
        remoteViews: RemoteViews,
        index: Int,
        list: List<AllAppsUsageFragment.Stat>,
        textViewId: Int,
        context: Context
    ) {
        val item = list.getOrNull(index)
        if (item != null) {
            val usage = TimeTools.formatTimeForWidget(item.totalTime)
            val appName = context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(item.packageName, 0)
            )
            remoteViews.setTextViewText(textViewId, "$usage : $appName")
        } else {
            remoteViews.setTextViewText(textViewId, "")
        }
    }

    private fun createRefreshIntent(context: Context, widgetId: Int): Intent {
        return Intent(context, ScreentimeWidgetProvider::class.java).apply {
            action = ACTION_WIDGET_REFRESH
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(widgetId))
        }
    }

    private fun formatTime(timeInMillis: Long): String {
        val hours = timeInMillis / (1000 * 60 * 60)
        val minutes = (timeInMillis % (1000 * 60 * 60)) / (1000 * 60)
        return buildString {
            if (hours > 0) append("${hours}h")
            if (minutes > 0) append(" ${minutes}m")
        }.trim()
    }

    // Convert dp size to approximate cells (based on Android widget guidelines: ~70dp per cell)
    private fun getCellsForSize(sizeDp: Int): Int {
        return (sizeDp + 30) / 70 // Rough estimate; 70dp per cell + padding
    }
}