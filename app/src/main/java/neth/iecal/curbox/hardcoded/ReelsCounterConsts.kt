package neth.iecal.curbox.hardcoded

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import neth.iecal.curbox.trackers.ReelCounterData
import java.util.UUID

class ReelsCounterConsts {
    companion object{
        val reelData: Map<String, ReelCounterData> = mapOf(
            "com.instagram.android" to ReelCounterData(
                viewId = "com.instagram.android:id/clips_viewer_view_pager",
                requiresPresent = listOf("com.instagram.android:id/clips_ufi_component"),
                dynamicComparator = listOf("com.instagram.android:id/clips_captions_component", "com.instagram.android:id/clips_author_username")
            ),

            "com.myinsta.android" to ReelCounterData(
                viewId = "com.myinsta.android:id/clips_viewer_view_pager",
                requiresPresent = listOf("com.myinsta.android:id/clips_ufi_component"),
                dynamicComparator = listOf("com.myinsta.android:id/clips_captions_component", "com.myinsta.android:id/clips_author_username")
            ),

            "com.google.android.youtube" to ReelCounterData(
                viewId = "com.google.android.youtube:id/reel_recycler",
                requiresPresent = listOf(),
                dynamicComparator = listOf("com.google.android.youtube:id/reel_player_page_content"),
                comparsionResultCleanser = {
                    if(it.contains("PostPostPostlike")) return@ReelCounterData ""
                    if(it.length <= 15) return@ReelCounterData ""
                    it.replace("Video Progress","")
                        .replace("Tap to watch live","")
                        .replace("Go to channel","")
                        .replace("soundVideo ProgressSearchMoreHomeHomeShortsShortsCreateSubscriptions","")
                        .replace("soundSearchMoreHomeHomeShortsShortsCreateSubscriptions","")
                },
                eventType = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            ),
            "app.revanced.android.youtube" to ReelCounterData(
                viewId = "app.revanced.android.youtube:id/reel_recycler",
                requiresPresent = listOf(),
                dynamicComparator = listOf("app.revanced.android.youtube:id/reel_player_page_content"),
                comparsionResultCleanser = {
                    if(it.contains("PostPostPostlike")) return@ReelCounterData ""
                    if(it.length <= 15) return@ReelCounterData ""
                    it.replace("Video Progress","")
                        .replace("Tap to watch live","")
                        .replace("Go to channel","")
                        .replace("soundVideo ProgressSearchMoreHomeHomeShortsShortsCreateSubscriptions","")
                        .replace("soundSearchMoreHomeHomeShortsShortsCreateSubscriptions","")
                },
                eventType = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            ),
            "com.reddit.frontpage" to ReelCounterData(
                viewId = "path:android.view.ViewGroup[0]>android.view.View[0]>android.view.View[0]>android.widget.ScrollView[0]>android.view.View[0]>android.view.View[0]>android.view.View[0]>android.view.View[0]>android.view.View[0]>android.view.View[0]>androidx.compose.ui.viewinterop.ViewFactoryHolder[0]",
                requiresPresent = listOf(),
                dynamicComparator = listOf("descContains:post creator"),
                comparsionResultCleanser = {
                    return@ReelCounterData it.replace("[deleted]", UUID.randomUUID().toString())
                },
                eventType = AccessibilityEvent.TYPE_VIEW_SCROLLED,

            )
        )

    }
}