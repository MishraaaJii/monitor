package com.timeledger.appblocker

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AppAdapter
    private lateinit var permissionsBanner: View
    private lateinit var emptyState: View
    private lateinit var limitsStore: LimitsStore

    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshIntervalMillis = 3000L
    private var refreshRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        limitsStore = LimitsStore(this)

        recyclerView = findViewById(R.id.appList)
        permissionsBanner = findViewById(R.id.permissionsBanner)
        emptyState = findViewById(R.id.emptyState)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = AppAdapter(
            onRowClicked = { item -> showLimitDialog(item) },
            onToggleChanged = { item, isChecked -> onToggleChanged(item, isChecked) }
        )
        recyclerView.adapter = adapter

        findViewById<TextView>(R.id.btnPermissions).setOnClickListener {
            startActivity(Intent(this, PermissionsActivity::class.java))
        }
        findViewById<TextView>(R.id.btnFixPermissions).setOnClickListener {
            startActivity(Intent(this, PermissionsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionsBanner()
        startRefreshLoop()
    }

    override fun onPause() {
        super.onPause()
        stopRefreshLoop()
    }

    private fun updatePermissionsBanner() {
        val ok = UsageStatsHelper.hasUsageAccess(this) &&
            UsageStatsHelper.isAccessibilityServiceEnabled(this)
        permissionsBanner.visibility = if (ok) View.GONE else View.VISIBLE
    }

    private fun startRefreshLoop() {
        stopRefreshLoop()
        refreshRunnable = object : Runnable {
            override fun run() {
                refreshList()
                refreshHandler.postDelayed(this, refreshIntervalMillis)
            }
        }
        refreshHandler.post(refreshRunnable!!)
    }

    private fun stopRefreshLoop() {
        refreshRunnable?.let { refreshHandler.removeCallbacks(it) }
        refreshRunnable = null
    }

    private fun refreshList() {
        if (!UsageStatsHelper.hasUsageAccess(this)) {
            // Without usage access we cannot compute any usage numbers; show
            // an empty list rather than all-zero (misleading) numbers.
            adapter.submitList(emptyList())
            emptyState.visibility = View.VISIBLE
            return
        }

        val pm = packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)

        val dayStart = UsageStatsHelper.startOfToday()
        val bulkUsage = UsageStatsHelper.computeUsageSince(this, dayStart)
        val limits = limitsStore.getAll()

        val items = mutableListOf<AppListItem>()
        val seenPackages = HashSet<String>()

        for (info in resolveInfos) {
            val appInfo: ApplicationInfo = info.activityInfo.applicationInfo
            val pkg = appInfo.packageName
            if (pkg == packageName) continue // don't list/track ourselves
            if (!seenPackages.add(pkg)) continue // some apps expose >1 launcher activity

            val entry = limits[pkg]
            val usage = if (entry != null && entry.resetOverrideMillis > dayStart) {
                UsageStatsHelper.usageForPackageSince(this, pkg, entry.resetOverrideMillis)
            } else {
                bulkUsage[pkg] ?: 0L
            }

            val label = try {
                pm.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                pkg
            }
            val icon = try {
                pm.getApplicationIcon(appInfo)
            } catch (e: Exception) {
                continue
            }

            items.add(AppListItem(pkg, label, icon, usage, entry))
        }

        // Most-used first; ties broken alphabetically for a stable order.
        items.sortWith(compareByDescending<AppListItem> { it.usageTodayMillis }.thenBy { it.label.lowercase() })

        adapter.submitList(items)
        emptyState.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun onToggleChanged(item: AppListItem, isChecked: Boolean) {
        limitsStore.setEnabled(item.packageName, isChecked)
        refreshList()
    }

    private fun showLimitDialog(item: AppListItem) {
        val view = layoutInflater.inflate(R.layout.dialog_set_limit, null)
        val nameView = view.findViewById<TextView>(R.id.dialogAppName)
        val usageView = view.findViewById<TextView>(R.id.dialogAppUsage)
        val editMinutes = view.findViewById<EditText>(R.id.editMinutes)
        val btnSave = view.findViewById<Button>(R.id.btnSaveLimit)
        val btnReset = view.findViewById<Button>(R.id.btnResetToday)
        val btnRemove = view.findViewById<Button>(R.id.btnRemoveLimit)
        val btnClose = view.findViewById<Button>(R.id.btnCloseDialog)

        nameView.text = item.label
        usageView.text = UsageStatsHelper.formatDuration(item.usageTodayMillis)
        item.limitEntry?.let {
            if (it.limitMinutes > 0) editMinutes.setText(it.limitMinutes.toString())
        }

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()

        btnSave.setOnClickListener {
            val minutes = editMinutes.text.toString().toIntOrNull()
            if (minutes == null || minutes <= 0) {
                Toast.makeText(this, R.string.toast_enter_valid_minutes, Toast.LENGTH_SHORT).show()
            } else {
                limitsStore.setLimit(item.packageName, minutes)
                Toast.makeText(this, R.string.toast_limit_saved, Toast.LENGTH_SHORT).show()
                refreshList()
                dialog.dismiss()
            }
        }
        btnReset.setOnClickListener {
            limitsStore.resetToday(item.packageName)
            Toast.makeText(this, R.string.toast_usage_reset, Toast.LENGTH_SHORT).show()
            refreshList()
            dialog.dismiss()
        }
        btnRemove.setOnClickListener {
            limitsStore.removeLimit(item.packageName)
            Toast.makeText(this, R.string.toast_limit_removed, Toast.LENGTH_SHORT).show()
            refreshList()
            dialog.dismiss()
        }
        btnClose.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }
}
