package neth.iecal.curbox.hardcoded

import neth.iecal.curbox.data.models.ViewBlockerRule

val DEFAULT_VB_RULES : List<ViewBlockerRule> = listOf(
//            // Instagram
//            ViewBlockerRule("ig_stories_tray", "com.instagram.android", "Hide Stories",
//                path = "androidx.viewpager.widget.ViewPager[0]>android.widget.FrameLayout[0]>androidx.recyclerview.widget.RecyclerView[0]>android.widget.LinearLayout[0]",
//                requirePresent = listOf("viewId:com.instagram.android:id/title_logo")),
//            ViewBlockerRule("ig_search_suggestions", "com.instagram.android", "Hide Explore Grid",
//                path = "androidx.viewpager.widget.ViewPager[0]>android.widget.FrameLayout[3]>androidx.recyclerview.widget.RecyclerView[0]>android.widget.FrameLayout[*]"),
//            ViewBlockerRule("ig_feed_1", "com.instagram.android", "Hide main feed",
//                path = "androidx.viewpager.widget.ViewPager[0]>android.widget.FrameLayout[0]>androidx.recyclerview.widget.RecyclerView[0]>android.view.ViewGroup[*]"),
//            ViewBlockerRule("ig_feed_2", "com.instagram.android", "Hide main feed but let me use the following tab",
//                path = "androidx.viewpager.widget.ViewPager[0]>android.widget.FrameLayout[0]>androidx.recyclerview.widget.RecyclerView[0]>android.view.ViewGroup[*]",
//                requirePresent = listOf("viewId:com.instagram.android:id/title_logo")),
//            ViewBlockerRule("ig_reel_interactive_reels", "com.instagram.android", "Hide interactive buttons (like, share, comment) in the reels tab",
//                viewId = "com.instagram.android:id/clips_ufi_component"),

    // YouTube
    ViewBlockerRule("yt_video_thingies", "com.google.android.youtube", "Hide everything (recommendations, comments, description etc) except the video",
        viewId = "com.google.android.youtube:id/watch_list"),
    ViewBlockerRule("yt_video_everything_except_results", "com.google.android.youtube", "Hide feed and only let me access search results",
        viewId = "com.google.android.youtube:id/results",
        requirePresent = listOf("descres:accessibility_feed_filter_bar_content_description")),

    // Reddit
    ViewBlockerRule("reddit_block_main","com.reddit.frontpage","Hide home feed but let me access custom feeds",
        path = "androidx.drawerlayout.widget.DrawerLayout[0]>android.widget.FrameLayout[0]>android.view.ViewGroup[0]>android.view.View[0]>android.view.View[0]>android.view.View[0]>android.view.View[0]>android.view.View[0]>android.view.View[0]>android.view.View[0]>android.view.View[0]>android.view.View[0]>android.view.View[0]>android.view.View[1]"),
    // X / Twitter
    ViewBlockerRule("x_feed_but_allow_following", "com.twitter.android", "Hide 'For You' and only let me access the Following tab",
        viewId = "android:id/list",
        requirePresent = listOf("descres:guide_tab_title_for_you;isSelected:true")),
)
