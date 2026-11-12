package com.focusblock.app.detector

import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

/**
 * Result data class specifying the type of distraction detected.
 */
data class DetectionResult(
    val isDistraction: Boolean,
    val isTabSelected: Boolean = false,
    val tabType: String = ""
)

/**
 * Visible-node detector for short-form video screens. IDs vary by app version.
 * - Instagram (Reels tab & Fullscreen Clips Viewer only; intended to preserve Stories, Feed, and DMs)
 * - Snapchat (Spotlight tab & Fullscreen Spotlight viewer only; intended to preserve Camera, Chats, Stories)
 * - YouTube & YouTube ReVanced (Shorts tab & Fullscreen Shorts player only; intended to preserve Normal videos and Home)
 * - Facebook & FB Lite (Reels tab & Shorts viewer only; intended to preserve normal posts and feed)
 */
class ShortsReelsDetector {

    companion object {
        const val PACKAGE_YOUTUBE = "com.google.android.youtube"
        const val PACKAGE_YOUTUBE_REVANCED = "app.revanced.android.youtube"
        const val PACKAGE_INSTAGRAM = "com.instagram.android"
        const val PACKAGE_SNAPCHAT = "com.snapchat.android"
        const val PACKAGE_FACEBOOK = "com.facebook.katana"
        const val PACKAGE_FACEBOOK_LITE = "com.facebook.lite"

        val SUPPORTED_PACKAGES = setOf(
            PACKAGE_YOUTUBE,
            PACKAGE_YOUTUBE_REVANCED,
            PACKAGE_INSTAGRAM,
            PACKAGE_SNAPCHAT,
            PACKAGE_FACEBOOK,
            PACKAGE_FACEBOOK_LITE
        )

        private const val MAX_NODES_TO_SCAN = 800
        private const val MAX_SCAN_DEPTH = 35

        // ==================== INSTAGRAM SIGNATURES ====================

        private val IG_CLIPS_ID_KEYWORDS = setOf(
            "clips_viewer_view_pager",
            "clips_video_container",
            "clips_viewer_root",
            "clips_viewer_media_info_layout",
            "clips_ufi_container",
            "clips_viewer_fragment",
            "clips_item_container",
            "clips_swipe_refresh_layout",
            "clips_action_sheet"
        )

        // Strict whitelist for Instagram: Stories, Direct Messages, Main Feed Header/Search
        private val IG_WHITELIST_KEYWORDS = setOf(
            "story_viewer_fragment_container",
            "story_viewer_container",
            "reel_viewer_progress_bar",
            "stories_tray",
            "reel_viewer_avatar",
            "story_comment_composer_container",
            "reel_viewer_toolbar",
            "direct_thread_feed",
            "direct_inbox",
            "direct_thread_fragment",
            "message_composer_container",
            "direct_expiring_media_viewer"
        )

        // ==================== SNAPCHAT SIGNATURES ====================

        private val SNAP_SPOTLIGHT_ID_KEYWORDS = setOf(
            "spotlight_carousel",
            "spotlight_fullscreen",
            "spotlight_player",
            "spotlight_story_player",
            "spotlight_swipe_layer",
            "spotlight_container",
            "spotlight_video",
            "spotlight_overlay",
            "spotlight_fragment",
            "spotlight_unified_carousel",
        )

        private val SNAP_WHITELIST_KEYWORDS = setOf(
            "chat_fragment",
            "friend_feed",
            "message_input",
            "camera_fragment",
            "camera_preview",
            "capture_button",
            "map_fragment",
            "map_view"
        )

        // ==================== YOUTUBE SIGNATURES ====================

        private val YT_SHORTS_ID_KEYWORDS = setOf(
            "reel_recycler",
            "reel_player_page_view",
            "reel_watch_fragment_container",
            "reel_watch_fragment_root",
            "reel_watch_pager",
            "reel_video_player",
            "reel_content_root",
            "reel_player_view_stub",
            "shorts_container",
            "shorts_player"
        )

        private val YT_NORMAL_ID_KEYWORDS = setOf(
            "time_bar",
            "youtube_controls_overlay",
            "watch_player",
            "player_fragment_container",
            "player_view",
            "results",
            "search_results_editor",
            "search_edit_text",
            "home_page"
        )

        // ==================== FACEBOOK SIGNATURES ====================
        private val FB_REELS_ID_KEYWORDS = setOf(
            "fb_shorts_container",
            "fb_shorts_viewer_fragment",
            "fb_shorts_viewer_page",
            "fb_shorts_video_container"
        )
    }

    /**
     * Evaluates whether the active window is displaying a Short, Reel, or Spotlight feed.
     */
    fun detectDistraction(
        packageName: String?,
        rootNode: AccessibilityNodeInfo?
    ): DetectionResult {
        if (packageName !in SUPPORTED_PACKAGES || rootNode == null ||
            rootNode.packageName?.toString() != packageName || !rootNode.isVisibleToUser) {
            return DetectionResult(false)
        }

        return when (packageName) {
            PACKAGE_INSTAGRAM -> evaluateInstagram(rootNode)
            PACKAGE_SNAPCHAT -> evaluateSnapchat(rootNode)
            PACKAGE_YOUTUBE, PACKAGE_YOUTUBE_REVANCED -> evaluateYouTube(rootNode)
            PACKAGE_FACEBOOK, PACKAGE_FACEBOOK_LITE -> evaluateFacebook(rootNode)
            else -> DetectionResult(false)
        }
    }

    // ==================== INSTAGRAM EVALUATION ====================
    private fun evaluateInstagram(
        rootNode: AccessibilityNodeInfo
    ): DetectionResult {
        var isReelsTabSelected = false
        var isClipsViewerFound = false
        var isWhitelistedState = false

        traverseHierarchy(rootNode) { node ->
            val viewId = node.viewIdResourceName
            val isNavigationTab = viewId == "com.instagram.android:id/clips_tab"

            if (viewId != null) {
                val cleanId = viewId.substringAfter(":id/").lowercase()

                // If in Story viewer or DM conversation, whitelist immediately
                if (IG_WHITELIST_KEYWORDS.any { cleanId.contains(it) }) {
                    isWhitelistedState = true
                    return@traverseHierarchy false
                }

                if (IG_CLIPS_ID_KEYWORDS.any { cleanId.contains(it) }) {
                    isClipsViewerFound = true
                }
            }

            if (isNavigationTab && node.isSelected) {
                isReelsTabSelected = true
            }

            true
        }

        if (isWhitelistedState) {
            return DetectionResult(false)
        }

        if (isReelsTabSelected) {
            return DetectionResult(isDistraction = true, isTabSelected = true, tabType = "reels")
        }

        if (isClipsViewerFound) {
            return DetectionResult(isDistraction = true, isTabSelected = false)
        }

        return DetectionResult(false)
    }

    // ==================== SNAPCHAT EVALUATION ====================
    private fun evaluateSnapchat(
        rootNode: AccessibilityNodeInfo
    ): DetectionResult {
        var isSpotlightTabSelected = false
        var isSpotlightViewerFound = false
        var isWhitelistedState = false

        traverseHierarchy(rootNode) { node ->
            val viewId = node.viewIdResourceName
            val isNavigationTab = viewId == "com.snapchat.android:id/spotlight_tab"

            if (viewId != null) {
                val cleanId = viewId.substringAfter(":id/").lowercase()

                if (SNAP_WHITELIST_KEYWORDS.any { cleanId.contains(it) }) {
                    isWhitelistedState = true
                    return@traverseHierarchy false
                }

                if (SNAP_SPOTLIGHT_ID_KEYWORDS.any { cleanId.contains(it) }) {
                    isSpotlightViewerFound = true
                }
            }

            if (isNavigationTab && node.isSelected) {
                isSpotlightTabSelected = true
            }

            true
        }

        if (isWhitelistedState) {
            return DetectionResult(false)
        }

        if (isSpotlightTabSelected) {
            return DetectionResult(isDistraction = true, isTabSelected = true, tabType = "spotlight")
        }

        if (isSpotlightViewerFound) {
            return DetectionResult(isDistraction = true, isTabSelected = false)
        }

        return DetectionResult(false)
    }

    // ==================== YOUTUBE EVALUATION ====================
    private fun evaluateYouTube(
        rootNode: AccessibilityNodeInfo
    ): DetectionResult {
        var isShortsTabSelected = false
        var isShortsFound = false
        var isWhitelistedState = false

        traverseHierarchy(rootNode) { node ->
            val viewId = node.viewIdResourceName
            // YouTube reuses this ID for every bottom tab. Inspect a label only on
            // that known navigation control, never arbitrary content or messages.
            val isNavigationTab = viewId == "${node.packageName}:id/pivot_bar_item" &&
                (node.contentDescription?.toString()?.substringBefore(',')?.trim()
                    .equals("Shorts", ignoreCase = true) ||
                    node.text?.toString().equals("Shorts", ignoreCase = true))

            if (viewId != null) {
                val cleanId = viewId.substringAfter(":id/").lowercase()

                if (YT_NORMAL_ID_KEYWORDS.any { cleanId.contains(it) }) {
                    isWhitelistedState = true
                    return@traverseHierarchy false
                }

                if (YT_SHORTS_ID_KEYWORDS.any { cleanId.contains(it) }) {
                    isShortsFound = true
                }
            }

            if (isNavigationTab && node.isSelected) {
                isShortsTabSelected = true
            }

            true
        }

        if (isWhitelistedState) {
            return DetectionResult(false)
        }

        if (isShortsTabSelected) {
            return DetectionResult(isDistraction = true, isTabSelected = true, tabType = "shorts")
        }

        if (isShortsFound) {
            return DetectionResult(isDistraction = true, isTabSelected = false)
        }

        return DetectionResult(false)
    }

    // ==================== FACEBOOK EVALUATION ====================
    private fun evaluateFacebook(
        rootNode: AccessibilityNodeInfo
    ): DetectionResult {
        var isFbShortsDetected = false
        var isReelsTabSelected = false

        traverseHierarchy(rootNode) { node ->
            val viewId = node.viewIdResourceName
            val isNavigationTab = viewId == "${node.packageName}:id/reels_tab"

            if (viewId != null) {
                val cleanId = viewId.substringAfter(":id/").lowercase()
                if (FB_REELS_ID_KEYWORDS.any { cleanId.contains(it) }) {
                    isFbShortsDetected = true
                }
            }

            if (isNavigationTab && node.isSelected) {
                isReelsTabSelected = true
            }

            true
        }

        if (isReelsTabSelected) {
            return DetectionResult(isDistraction = true, isTabSelected = true, tabType = "reels")
        }

        if (isFbShortsDetected) {
            return DetectionResult(isDistraction = true, isTabSelected = false)
        }

        return DetectionResult(false)
    }

    /**
     * BFS traversal bounded by depth, processed nodes, and queued nodes. Hidden or foreign
     * subtrees cannot provide detection evidence.
     */
    private inline fun traverseHierarchy(
        root: AccessibilityNodeInfo,
        crossinline onNode: (AccessibilityNodeInfo) -> Boolean
    ) {
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.add(root to 0)
        var scannedCount = 0

        while (queue.isNotEmpty() && scannedCount < MAX_NODES_TO_SCAN) {
            val (current, depth) = queue.poll() ?: break
            scannedCount++
            if (!current.isVisibleToUser || current.packageName?.toString() != root.packageName?.toString()) continue

            val shouldContinue = try {
                onNode(current)
            } catch (e: Exception) {
                true
            }

            if (!shouldContinue) {
                break
            }

            if (depth < MAX_SCAN_DEPTH) {
                val childCount = try {
                    current.childCount
                } catch (e: Exception) {
                    0
                }

                val remaining = MAX_NODES_TO_SCAN - scannedCount - queue.size
                for (i in 0 until minOf(childCount, remaining.coerceAtLeast(0))) {
                    val child = try {
                        current.getChild(i)
                    } catch (e: Exception) {
                        null
                    }
                    if (child != null) {
                        queue.add(child to depth + 1)
                    }
                }
            }
        }
    }
}
