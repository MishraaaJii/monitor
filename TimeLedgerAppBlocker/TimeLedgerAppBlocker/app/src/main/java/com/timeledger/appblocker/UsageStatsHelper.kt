package com.timeledger.appblocker

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import java.util.Calendar

/**
 * All reads of Android's UsageStatsManager go through here, plus the
 * permission/settings checks the rest of the app needs.
 */
object UsageStatsHelper {

    /** True if the user has granted this app "Usage access" in Settings. */
    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** True if our AppBlockerAccessibilityService is enabled in Settings. */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedComponent = "${context.packageName}/${AppBlockerAccessibilityService::class.java.name}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.split(':').any { it.equals(expectedComponent, ignoreCase = true) }
    }

    /** True if this app is already exempt from battery optimization. */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Midnight (start of the current calendar day), in device-local time. */
    fun startOfToday(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * Foreground time in milliseconds, per package, for the window
     * [sinceMillis, now]. Built from raw UsageEvents (foreground/background
     * transitions) rather than queryUsageStats' daily buckets, since we need
     * an accurate *partial* day and a window that can start mid-day after a
     * manual per-app reset.
     */
    fun computeUsageSince(context: Context, sinceMillis: Long): Map<String, Long> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        if (sinceMillis >= end) return emptyMap()

        val events = usm.queryEvents(sinceMillis, end)
        val lastForegroundStart = HashMap<String, Long>()
        val totals = HashMap<String, Long>()
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    lastForegroundStart[pkg] = event.timeStamp
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    val start = lastForegroundStart.remove(pkg)
                    if (start != null && event.timeStamp > start) {
                        totals[pkg] = (totals[pkg] ?: 0L) + (event.timeStamp - start)
                    }
                }
            }
        }
        // Anything still "in foreground" at the end of the window (i.e. the
        // app currently on screen) counts up to now.
        for ((pkg, start) in lastForegroundStart) {
            if (end > start) {
                totals[pkg] = (totals[pkg] ?: 0L) + (end - start)
            }
        }
        return totals
    }

    /** Same as [computeUsageSince] but filtered to a single package (cheaper for polling). */
    fun usageForPackageSince(context: Context, packageName: String, sinceMillis: Long): Long {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        if (sinceMillis >= end) return 0L

        val events = usm.queryEvents(sinceMillis, end)
        var lastStart: Long? = null
        var total = 0L
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.packageName != packageName) continue
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> lastStart = event.timeStamp
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    val start = lastStart
                    if (start != null && event.timeStamp > start) {
                        total += (event.timeStamp - start)
                    }
                    lastStart = null
                }
            }
        }
        val start = lastStart
        if (start != null && end > start) {
            total += (end - start)
        }
        return total
    }

    /** The effective start of the counting window for a package: the later of
     *  start-of-today and its last manual reset (0 if never reset today). */
    fun effectiveWindowStart(resetOverrideMillis: Long): Long {
        val dayStart = startOfToday()
        return if (resetOverrideMillis > dayStart) resetOverrideMillis else dayStart
    }

    fun formatDuration(millis: Long): String {
        val totalMinutes = millis / 60000
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return if (h > 0) "${h}h ${m}m today" else "${m}m today"
    }
}
