package com.focusblock.app.detector

import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShortsReelsDetectorTest {
    private val detector = ShortsReelsDetector()
    private val instagram = "com.instagram.android"

    @Suppress("DEPRECATION")
    private fun node(pkg: String, id: String, visible: Boolean = true,
                     selected: Boolean = false): AccessibilityNodeInfo =
        AccessibilityNodeInfo.obtain().apply {
            packageName = pkg
            viewIdResourceName = "$pkg:id/$id"
            isVisibleToUser = visible
            isSelected = selected
        }

    @Test fun foreignAndHiddenViewersCannotTriggerBlocking() {
        assertFalse(detector.detectDistraction(instagram,
            node("com.android.settings", "clips_viewer_root")).isDistraction)
        assertFalse(detector.detectDistraction(instagram,
            node(instagram, "clips_viewer_root", visible = false)).isDistraction)
        assertFalse(detector.detectDistraction(instagram, null).isDistraction)
    }

    @Test fun visibleViewerIsBlockedAndInboxAndStoriesRemainAllowed() {
        assertTrue(detector.detectDistraction(instagram,
            node(instagram, "clips_viewer_root")).isDistraction)
        for (id in listOf("direct_inbox", "message_composer_container", "stories_tray")) {
            assertFalse(detector.detectDistraction(instagram, node(instagram, id)).isDistraction)
        }
    }

    @Test fun userTextNamedReelsIsNotATab() {
        val message = node(instagram, "message_text", selected = true).apply {
            text = "Reels"
            contentDescription = "Reels tab"
        }
        assertFalse(detector.detectDistraction(instagram, message).isDistraction)
    }

    @Test fun instagramTabNeedsSelectionButNoEnglishLabel() {
        assertFalse(detector.detectDistraction(instagram,
            node(instagram, "clips_tab")).isDistraction)
        val selected = node(instagram, "clips_tab", selected = true).apply {
            contentDescription = "रील्स"
        }
        assertTrue(detector.detectDistraction(instagram, selected).isTabSelected)
    }

    @Test fun presenceOfSnapchatOrFacebookReelsTabDoesNotBlockTheFeed() {
        for ((pkg, id) in listOf("com.snapchat.android" to "spotlight_tab",
                "com.facebook.katana" to "reels_tab")) {
            assertFalse(detector.detectDistraction(pkg, node(pkg, id)).isDistraction)
            assertTrue(detector.detectDistraction(pkg, node(pkg, id, selected = true)).isTabSelected)
        }
    }

    @Test fun youtubeHomeDoesNotMatchTheSharedShortsNavigationId() {
        val youtube = "com.google.android.youtube"
        val home = node(youtube, "pivot_bar_item", selected = true).apply {
            contentDescription = "Home"
        }
        assertFalse(detector.detectDistraction(youtube, home).isDistraction)
        val shorts = node(youtube, "pivot_bar_item", selected = true).apply {
            contentDescription = "Shorts, tab"
        }
        assertTrue(detector.detectDistraction(youtube, shorts).isTabSelected)
    }
}
