package com.timeledger.appblocker

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class PermissionsActivity : AppCompatActivity() {

    private lateinit var statusUsage: TextView
    private lateinit var statusAccessibility: TextView
    private lateinit var statusBattery: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permissions)

        statusUsage = findViewById(R.id.statusUsage)
        statusAccessibility = findViewById(R.id.statusAccessibility)
        statusBattery = findViewById(R.id.statusBattery)

        findViewById<Button>(R.id.btnOpenUsageSettings).setOnClickListener {
            // There is no reliable way to deep-link straight to this app's row
            // on every OEM's usage-access screen, so we open the general list;
            // the intro text tells the user to find "Time Ledger" in it.
            try {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }

        findViewById<Button>(R.id.btnOpenAccessibilitySettings).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }

        findViewById<Button>(R.id.btnOpenBatterySettings).setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }

        findViewById<Button>(R.id.btnDone).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        refreshStatuses()
    }

    private fun refreshStatuses() {
        val usageOk = UsageStatsHelper.hasUsageAccess(this)
        statusUsage.text = getString(if (usageOk) R.string.perm_usage_granted else R.string.perm_usage_not_granted)
        statusUsage.setTextColor(
            ContextCompat.getColor(this, if (usageOk) R.color.green_ok else R.color.red_alert)
        )

        val accessibilityOk = UsageStatsHelper.isAccessibilityServiceEnabled(this)
        statusAccessibility.text = getString(
            if (accessibilityOk) R.string.perm_accessibility_granted else R.string.perm_accessibility_not_granted
        )
        statusAccessibility.setTextColor(
            ContextCompat.getColor(this, if (accessibilityOk) R.color.green_ok else R.color.red_alert)
        )

        val batteryOk = UsageStatsHelper.isIgnoringBatteryOptimizations(this)
        statusBattery.text = getString(
            if (batteryOk) R.string.perm_battery_granted else R.string.perm_battery_not_granted
        )
        statusBattery.setTextColor(
            ContextCompat.getColor(this, if (batteryOk) R.color.green_ok else R.color.gray_mid)
        )
    }
}
