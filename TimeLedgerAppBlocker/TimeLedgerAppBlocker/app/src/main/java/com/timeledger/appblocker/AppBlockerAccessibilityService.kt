package com.timeledger.appblocker

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

/**
 * Watches which app is in the foreground. Two things trigger a block check:
 *  1. The moment the foreground app changes (a TYPE_WINDOW_STATE_CHANGED
 *     event), so a limited app is caught the instant it's opened.
 *  2. A repeating poll every [POLL_INTERVAL_MS] while the same app stays in
 *     the foreground, so a limit reached *while the app is already open*
 *     (without switching away and back) is still caught promptly.
 *
 * "Closing" the app means sending the user Home (GLOBAL_ACTION_HOME).
 * Android does not expose an API for one app to force-kill another
 * arbitrary app's process, but returning the user to the home screen the
 * instant the limited app tries to draw itself has the same practical
 * effect, and it's the same mechanism real screen-time/blocking apps use.
 */
class AppBlockerAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var currentPackage: String? = null
    private var pollRunnable: Runnable? = null
    private var lastBlockedPackage: String? = null
    private var lastBlockedAt: Long = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return // ignore our own UI
        if (pkg == currentPackage) return

        currentPackage = pkg
        checkAndMaybeBlock(pkg)
        restartPolling(pkg)
    }

    private fun restartPolling(pkg: String) {
        pollRunnable?.let { handler.removeCallbacks(it) }
        val runnable = object : Runnable {
            override fun run() {
                checkAndMaybeBlock(pkg)
                handler.postDelayed(this, POLL_INTERVAL_MS)
            }
        }
        pollRunnable = runnable
        handler.postDelayed(runnable, POLL_INTERVAL_MS)
    }

    private fun checkAndMaybeBlock(pkg: String) {
        val entry = LimitsStore(this).get(pkg) ?: return
        if (!entry.enabled || entry.limitMinutes <= 0) return

        val since = UsageStatsHelper.effectiveWindowStart(entry.resetOverrideMillis)
        val usedMillis = UsageStatsHelper.usageForPackageSince(this, pkg, since)
        val limitMillis = entry.limitMinutes * 60_000L

        if (usedMillis >= limitMillis) {
            // Avoid spamming repeated Home actions/toasts for the same
            // already-blocked app within a short window.
            val now = System.currentTimeMillis()
            if (pkg == lastBlockedPackage && now - lastBlockedAt < 2000L) return
            lastBlockedPackage = pkg
            lastBlockedAt = now

            performGlobalAction(GLOBAL_ACTION_HOME)
            val label = appLabelOrPackage(pkg)
            handler.post {
                Toast.makeText(
                    this,
                    getString(R.string.limit_reached_toast, label),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun appLabelOrPackage(pkg: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            pkg
        }
    }

    override fun onInterrupt() {
        pollRunnable?.let { handler.removeCallbacks(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        pollRunnable?.let { handler.removeCallbacks(it) }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 3000L
    }
}
