package com.timeledger.appblocker

import android.graphics.drawable.Drawable

/**
 * One row in the main list: an installed, launchable app plus its
 * computed usage-today and its stored limit configuration (if any).
 */
data class AppListItem(
    val packageName: String,
    val label: String,
    val icon: Drawable,
    val usageTodayMillis: Long,
    val limitEntry: LimitEntry?
)

/**
 * Stored per-app configuration, persisted via LimitsStore.
 *
 * @param limitMinutes daily limit in minutes; -1 means "no limit set".
 * @param enabled whether the limiter is turned on for this app. A limit can
 *        exist (limitMinutes > 0) while enabled is false ("limiter off").
 * @param resetOverrideMillis timestamp of the last manual "reset today" tap
 *        for this app. Usage is only counted from max(startOfToday, this)
 *        onward, so a manual reset effectively zeroes today's count without
 *        needing to erase anything from Android's own usage stats.
 */
data class LimitEntry(
    val limitMinutes: Int = -1,
    val enabled: Boolean = false,
    val resetOverrideMillis: Long = 0L
)
