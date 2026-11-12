package com.focusblock.app.service

import org.junit.Assert.*
import org.junit.Test

class WindowActionGuardTest {
    private val instagram = "com.instagram.android"
    private val target = WindowTarget(instagram, 7)

    @Test fun onlySupportedKnownWindowsAreAccepted() {
        assertNull(WindowTarget.from(null, 7))
        assertNull(WindowTarget.from("com.android.settings", 7))
        assertNull(WindowTarget.from("com.instagram.android.evil", 7))
        assertNull(WindowTarget.from(instagram, -1))
        assertEquals(target, WindowTarget.from(instagram, 7))
    }

    @Test fun rejectsDifferentAppsAndNewWindowsInTheSameApp() {
        assertFalse(target.matches("com.android.systemui", 7))
        assertFalse(target.matches(instagram, 8))
        assertFalse(target.matches(null, 7))
        assertTrue(target.matches(instagram, 7))
    }

    @Test fun neverClicksUserContentOrForeignWindowNavigation() {
        fun allowed(pkg: String?, window: Int, id: String?) = SafeNavigation.canClick(
            target, pkg, window, id, true, true, true, false
        )
        assertFalse(allowed(instagram, 7, "com.instagram.android:id/comment_text"))
        assertFalse(allowed(instagram, 7, "Home"))
        assertFalse(allowed(instagram, 7, null))
        assertFalse(allowed("com.android.settings", 7, "$instagram:id/feed_tab"))
        assertFalse(allowed(instagram, 8, "$instagram:id/feed_tab"))
        assertTrue(allowed(instagram, 7, "$instagram:id/feed_tab"))
    }

    @Test fun requiresVisibleEnabledClickableUnselectedNavigation() {
        for (flags in listOf(
            listOf(false, true, true, false), listOf(true, false, true, false),
            listOf(true, true, false, false), listOf(true, true, true, true)
        )) {
            assertFalse(SafeNavigation.canClick(target, instagram, 7, "$instagram:id/feed_tab",
                flags[0], flags[1], flags[2], flags[3]))
        }
    }

    @Test fun limitsBackActionsAndUsesAMonotonicCooldown() {
        val guard = WindowActionGuard()
        assertTrue(guard.tryAcquire(target, 1000))
        assertFalse(guard.tryAcquire(target, 1200))
        assertTrue(guard.tryAcquire(target, 1300))
        assertFalse(guard.tryAcquire(target, 2000))
        guard.resetEpisode() // A safe screen was observed.
        assertFalse(guard.tryAcquire(target, 1200)) // A backwards clock cannot bypass cooldown.
        assertTrue(guard.tryAcquire(target, 2100))
    }

    @Test fun newWindowDoesNotBypassCooldown() {
        val guard = WindowActionGuard()
        assertTrue(guard.tryAcquire(target, 1000))
        assertFalse(guard.tryAcquire(WindowTarget(instagram, 8), 1100))
        assertTrue(guard.tryAcquire(WindowTarget(instagram, 8), 1300))
    }
}
