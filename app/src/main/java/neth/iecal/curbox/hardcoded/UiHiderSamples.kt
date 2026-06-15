package neth.iecal.curbox.hardcoded

import neth.iecal.curbox.data.models.UiHiderScript

/**
 * Starter scripts shipped (disabled) with UIHider. These are UIHider equivalents of the built-in
 * [DEFAULT_VB_RULES] ViewBlocker rules, plus a worked Instagram example. View-id selectors may need
 * adjusting as target apps update their layouts.
 *
 * Notes on the conversion:
 *  - The ViewBlocker `descres:` matcher (resolve a string resource by name from the target app) maps
 *    to the script builtin `appString("res_name")`, so these stay locale-independent.
 *  - ViewBlocker `path:` rules (class-index hierarchy walks) are reproduced with the `step()` helper
 *    that descends to the n-th child of a given class — the same semantics as the path syntax.
 */
val DEFAULT_UIHIDER_SCRIPTS: List<UiHiderScript> = listOf(

    // ── Instagram: hide the home feed (worked example, not a 1:1 of a built-in rule) ──
    UiHiderScript(
        id = "ig_feed",
        packageName = "com.instagram.android",
        label = "Instagram: hide home feed",
        isEnabled = false,
        source = """
            if app != "com.instagram.android" {
                return
            }
            
            # The stories view
            top = find(desc="reels tray container")
            nav = find(id="com.instagram.android:id/feed_tab")

            if top != null and top.visible and nav != null and nav.visible {
                # Ensure anchors are within the visible screen bounds
             
                    log("found and visible")
                    y = top.bottom
                    height = nav.top - y
                    if height > 0 {
                        draw(0, y, screen.width, height)
                    }
                
            } else {
                log("not found or not visible")
            }
        """.trimIndent()
    ),

    // ── YouTube: hide everything (recommendations, comments, description) except the video ──
    // VB rule: yt_video_thingies  ->  overlay id/watch_list
    UiHiderScript(
        id = "yt_video_thingies",
        packageName = "com.google.android.youtube",
        label = "YouTube: hide everything except the video",
        isEnabled = false,
        source = """
            if app != "com.google.android.youtube" {
                return
            }
            
            watch = find(id="com.google.android.youtube:id/watch_list")
            if watch != null {
                hide(watch)
            }
        """.trimIndent()
    ),

    // ── YouTube: hide the feed, only allow search results ──
    // VB rule: yt_video_everything_except_results  ->  overlay id/results,
    //          only when the filter bar (descres:accessibility_feed_filter_bar_content_description) is present
    UiHiderScript(
        id = "yt_video_everything_except_results",
        packageName = "com.google.android.youtube",
        label = "YouTube: hide feed, allow search results",
        isEnabled = false,
        source = """
            if app != "com.google.android.youtube" {
                return
            }
            filterBar = appString("accessibility_feed_filter_bar_content_description")
            if filterBar != null and find(desc=filterBar) != null {
                results = find(id="com.google.android.youtube:id/results")
                if results != null {
                    hide(results)
                }
            }
        """.trimIndent()
    ),

    // ── Reddit: hide the home feed, but still allow custom feeds ──
    // VB rule: reddit_block_main  ->  overlay a fixed class-index path
    UiHiderScript(
        id = "reddit_block_main",
        packageName = "com.reddit.frontpage",
        label = "Reddit: hide home feed (allow custom feeds)",
        isEnabled = false,
        source = """
            if app != "com.reddit.frontpage" {
                return
            }

            # Walk to the n-th child of a given class, mirroring ViewBlocker's path syntax.
            fn step(node, cls, idx) {
                if node == null {
                    return null
                }
                count = 0
                for c in node.children() {
                    if c.class == cls {
                        if count == idx {
                            return c
                        }
                        count = count + 1
                    }
                }
                return null
            }

            # androidx...DrawerLayout[0] > FrameLayout[0] > ViewGroup[0] > View[0] x10 > View[1]
            path = [
                ["androidx.drawerlayout.widget.DrawerLayout", 0],
                ["android.widget.FrameLayout", 0],
                ["android.view.ViewGroup", 0],
                ["android.view.View", 0],
                ["android.view.View", 0],
                ["android.view.View", 0],
                ["android.view.View", 0],
                ["android.view.View", 0],
                ["android.view.View", 0],
                ["android.view.View", 0],
                ["android.view.View", 0],
                ["android.view.View", 0],
                ["android.view.View", 0],
                ["android.view.View", 1]
            ]

            n = root()
            for seg in path {
                n = step(n, seg[0], seg[1])
            }
            if n != null {
                hide(n)
            }
        """.trimIndent()
    ),

    // ── X / Twitter: hide 'For You', only allow the Following tab ──
    // VB rule: x_feed_but_allow_following  ->  overlay android:id/list,
    //          only when the 'For You' tab (descres:guide_tab_title_for_you) is selected
    UiHiderScript(
        id = "x_feed_but_allow_following",
        packageName = "com.twitter.android",
        label = "X: hide 'For You', allow Following",
        isEnabled = false,
        source = """
            if app != "com.twitter.android" {
                return
            }
            forYou = appString("guide_tab_title_for_you")
            if forYou != null and find(desc=forYou, selected=true) != null {
                list = find(id="android:id/list")
                if list != null {
                    hide(list)
                }
            }
        """.trimIndent()
    )
)
