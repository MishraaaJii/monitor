package com.timeledger.appblocker

import android.content.Context
import org.json.JSONObject

/**
 * Stores per-app limit configuration as a single JSON blob in
 * SharedPreferences. No external dependency (Room, etc.) is needed for a
 * dataset this small, and org.json ships with Android.
 */
class LimitsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun getAll(): Map<String, LimitEntry> {
        val raw = prefs.getString(KEY_DATA, null) ?: return emptyMap()
        val obj = try {
            JSONObject(raw)
        } catch (e: Exception) {
            return emptyMap()
        }
        val result = LinkedHashMap<String, LimitEntry>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val pkg = keys.next()
            val e = obj.optJSONObject(pkg) ?: continue
            result[pkg] = LimitEntry(
                limitMinutes = e.optInt("limit", -1),
                enabled = e.optBoolean("enabled", false),
                resetOverrideMillis = e.optLong("reset", 0L)
            )
        }
        return result
    }

    fun get(packageName: String): LimitEntry? = getAll()[packageName]

    @Synchronized
    private fun save(packageName: String, entry: LimitEntry) {
        val raw = prefs.getString(KEY_DATA, null)
        val obj = if (raw != null) {
            try { JSONObject(raw) } catch (e: Exception) { JSONObject() }
        } else JSONObject()

        val e = JSONObject()
        e.put("limit", entry.limitMinutes)
        e.put("enabled", entry.enabled)
        e.put("reset", entry.resetOverrideMillis)
        obj.put(packageName, e)

        prefs.edit().putString(KEY_DATA, obj.toString()).apply()
    }

    /** Sets (or replaces) the daily limit for [packageName] and turns the limiter on. */
    fun setLimit(packageName: String, minutes: Int) {
        val current = get(packageName) ?: LimitEntry()
        save(packageName, current.copy(limitMinutes = minutes, enabled = true))
    }

    /** Turns the limiter on/off for [packageName] without touching the stored minutes. */
    fun setEnabled(packageName: String, enabled: Boolean) {
        val current = get(packageName) ?: LimitEntry()
        save(packageName, current.copy(enabled = enabled))
    }

    /** Clears the limit entirely (no limit, limiter off). */
    fun removeLimit(packageName: String) {
        val current = get(packageName) ?: return
        save(packageName, current.copy(limitMinutes = -1, enabled = false))
    }

    /** Manual "reset today's usage" for one app: usage counts from now onward only. */
    fun resetToday(packageName: String) {
        val current = get(packageName) ?: LimitEntry()
        save(packageName, current.copy(resetOverrideMillis = System.currentTimeMillis()))
    }

    companion object {
        private const val PREFS_NAME = "time_ledger_prefs"
        private const val KEY_DATA = "limits_data"
    }
}
