package com.focusblock.app.service

import com.focusblock.app.detector.ShortsReelsDetector

/** An event's identity must match the current active window before any action. */
data class WindowTarget(val packageName: String, val windowId: Int) {
    fun matches(actualPackage: String?, actualWindowId: Int): Boolean =
        packageName == actualPackage && windowId == actualWindowId

    companion object {
        fun from(packageName: String?, windowId: Int): WindowTarget? =
            if (packageName in ShortsReelsDetector.SUPPORTED_PACKAGES && windowId >= 0) {
                WindowTarget(requireNotNull(packageName), windowId)
            } else null
    }
}

/** At most two navigation attempts until a safe screen or another window is observed. */
class WindowActionGuard {
    private var target: WindowTarget? = null
    private var attempts = 0
    private var lastAttemptAt: Long? = null

    fun tryAcquire(next: WindowTarget, now: Long): Boolean {
        if (target != next) {
            target = next
            attempts = 0
        }
        val last = lastAttemptAt
        if (attempts >= 2 || (last != null && now - last < 300L)) return false
        attempts++
        lastAttemptAt = now
        return true
    }

    fun resetEpisode() {
        target = null
        attempts = 0
    }
}

/** No text matching: only exact application-owned navigation resource IDs. */
object SafeNavigation {
    fun resourceIds(packageName: String): List<String> = when (packageName) {
        ShortsReelsDetector.PACKAGE_INSTAGRAM -> listOf(
            "com.instagram.android:id/feed_tab",
            "com.instagram.android:id/direct_tab"
        )
        // Until navigation IDs are verified on-device, other apps use guarded Back.
        else -> emptyList()
    }

    fun canClick(target: WindowTarget, packageName: String?, windowId: Int, resourceId: String?,
                 visible: Boolean, enabled: Boolean, clickable: Boolean, selected: Boolean): Boolean =
        target.matches(packageName, windowId) && resourceId in resourceIds(target.packageName) &&
            visible && enabled && clickable && !selected
}
