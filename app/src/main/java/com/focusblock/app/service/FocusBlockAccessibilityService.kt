package com.focusblock.app.service

import android.accessibilityservice.AccessibilityService
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.focusblock.app.data.PreferencesHelper
import com.focusblock.app.detector.ShortsReelsDetector

/** Inspects supported application windows and performs bounded escape navigation. */
class FocusBlockAccessibilityService : AccessibilityService() {
    private lateinit var preferencesHelper: PreferencesHelper
    private val detector = ShortsReelsDetector()
    private val handler = Handler(Looper.getMainLooper())
    private val actionGuard = WindowActionGuard()
    private var pendingTarget: WindowTarget? = null
    private var lastEvaluationTime = 0L

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == PreferencesHelper.KEY_PROTECTION_ENABLED) cancelPendingEvaluation()
        // Re-enabling waits for a fresh event; no cached screen is acted on.
    }

    override fun onCreate() {
        super.onCreate()
        preferencesHelper = PreferencesHelper.getInstance(this)
        preferencesHelper.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
        cancelPendingEvaluation()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !preferencesHelper.isProtectionEnabled) {
            cancelPendingEvaluation()
            return
        }
        val target = WindowTarget.from(event.packageName?.toString(), event.windowId)
        if (target == null || activeRoot(target) == null) {
            cancelPendingEvaluation()
            return
        }
        // Keep only package/window identity, never event text, nodes, or activity names.
        pendingTarget = target
        handler.removeCallbacks(evaluationRunnable)
        val delay = (DEBOUNCE_MS - (SystemClock.elapsedRealtime() - lastEvaluationTime))
            .coerceAtLeast(0L)
        handler.postDelayed(evaluationRunnable, delay)
    }

    private val evaluationRunnable = Runnable {
        val target = pendingTarget
        pendingTarget = null
        if (target != null) evaluateCurrentScreen(target)
    }

    private fun evaluateCurrentScreen(target: WindowTarget) {
        lastEvaluationTime = SystemClock.elapsedRealtime()
        val root = activeRoot(target) ?: run {
            actionGuard.resetEpisode()
            return
        }
        val result = detector.detectDistraction(target.packageName, root)
        if (!result.isDistraction) {
            actionGuard.resetEpisode()
            return
        }
        // Fetch and classify a fresh active tree immediately before navigation.
        val freshRoot = activeRoot(target) ?: return
        val freshResult = detector.detectDistraction(target.packageName, freshRoot)
        if (!freshResult.isDistraction) {
            actionGuard.resetEpisode()
            return
        }
        if (!actionGuard.tryAcquire(target, SystemClock.elapsedRealtime())) return
        if (freshResult.isTabSelected && tryClickSafeTab(target, freshRoot)) return
        // No timer retries: wait for a new event and revalidate the window and screen.
        val finalRoot = activeRoot(target) ?: return
        if (detector.detectDistraction(target.packageName, finalRoot).isDistraction) {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    private fun activeRoot(target: WindowTarget): AccessibilityNodeInfo? {
        if (!isServiceRunning || !preferencesHelper.isProtectionEnabled) return null
        return try {
            val root = rootInActiveWindow ?: return null
            if (!target.matches(root.packageName?.toString(), root.windowId) ||
                !root.isVisibleToUser) return null
            // Reject system dialogs, notification shades and background multi-window panes.
            if (windows.none { it.id == target.windowId && it.isActive && it.isFocused &&
                    it.type == AccessibilityWindowInfo.TYPE_APPLICATION }) return null
            root
        } catch (_: Exception) {
            null
        }
    }

    private fun tryClickSafeTab(target: WindowTarget, root: AccessibilityNodeInfo): Boolean {
        return try {
            for (id in SafeNavigation.resourceIds(target.packageName)) {
                for (node in root.findAccessibilityNodeInfosByViewId(id).orEmpty()) {
                    if (!node.refresh()) continue
                    if (!SafeNavigation.canClick(target, node.packageName?.toString(), node.windowId,
                            node.viewIdResourceName, node.isVisibleToUser, node.isEnabled,
                            node.isClickable, node.isSelected)) continue
                    val checkedRoot = activeRoot(target) ?: return false
                    val checkedResult = detector.detectDistraction(target.packageName, checkedRoot)
                    if (!checkedResult.isDistraction || !checkedResult.isTabSelected) return false
                    // Click this exact navigation node, never arbitrary ancestors.
                    if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun cancelPendingEvaluation() {
        handler.removeCallbacks(evaluationRunnable)
        pendingTarget = null
        actionGuard.resetEpisode()
    }

    override fun onInterrupt() {
        cancelPendingEvaluation()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        isServiceRunning = false
        cancelPendingEvaluation()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        isServiceRunning = false
        cancelPendingEvaluation()
        preferencesHelper.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        super.onDestroy()
    }

    companion object {
        private const val DEBOUNCE_MS = 40L
        @Volatile
        var isServiceRunning: Boolean = false
            private set
    }
}
