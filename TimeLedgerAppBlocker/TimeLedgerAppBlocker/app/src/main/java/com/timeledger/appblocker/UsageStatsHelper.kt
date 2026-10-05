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
     * Foreground time per package for [sinceMillis, now]. Android reports
     * resume/pause per ACTIVITY, so an app with several activities emits
     * overlapping events. We track the set of resumed activities per package
     * and count time while that set is non-empty (fixes undercounting that
     * kept limits from ever being reached).
     */
    fun computeUsageSince(context: Context, sinceMillis: Long): Map<String, Long> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        if (sinceMillis >= end) return emptyMap()

        val events = usm.queryEvents(sinceMillis, end)
        val resumed = HashMap<String, MutableSet<String>>()
        val since = HashMap<String, Long>()
        val totals = HashMap<String, Long>()
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            val cls = event.className ?: ""
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    val set = resumed.getOrPut(pkg) { HashSet() }
                    if (set.isEmpty()) since[pkg] = event.timeStamp
                    set.add(cls)
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    val set = resumed[pkg] ?: continue
                    set.remove(cls)
                    if (set.isEmpty()) {
                        val st = since.remove(pkg)
                        if (st != null && event.timeStamp > st) {
                            totals[pkg] = (totals[pkg] ?: 0L) + (event.timeStamp - st)
                        }
                    }
                }
            }
        }
        for ((pkg, st) in since) {
            if (resumed[pkg]?.isNotEmpty() == true && end > st) {
                totals[pkg] = (totals[pkg] ?: 0L) + (end - st)
            }
        }
        return totals
    }

    fun usageForPackageSince(context: Context, packageName: String, sinceMillis: Long): Long =
        computeUsageSince(context, sinceMillis)[packageName] ?: 0L

    /** Package of the app currently in the foreground, from recent usage events. */
    fun currentForegroundPackage(context: Context): String? {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val events = usm.queryEvents(end - 60 * 60_000L, end)
        val event = UsageEvents.Event()
        val resumed = LinkedHashMap<String, String>() // cls -> pkg, in resume order
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val key = "${event.packageName}/${event.className}"
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    resumed.remove(key); resumed[key] = event.packageName ?: continue
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND -> resumed.remove(key)
            }
        }
        return resumed.values.lastOrNull()
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
