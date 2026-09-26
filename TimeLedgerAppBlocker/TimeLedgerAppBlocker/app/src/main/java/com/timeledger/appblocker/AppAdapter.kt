package com.timeledger.appblocker

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppAdapter(
    private val onRowClicked: (AppListItem) -> Unit,
    private val onToggleChanged: (AppListItem, Boolean) -> Unit
) : RecyclerView.Adapter<AppAdapter.ViewHolder>() {

    private val items = mutableListOf<AppListItem>()

    fun submitList(newItems: List<AppListItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.appIcon)
        private val name: TextView = itemView.findViewById(R.id.appName)
        private val usage: TextView = itemView.findViewById(R.id.appUsage)
        private val limit: TextView = itemView.findViewById(R.id.appLimit)
        private val switch: Switch = itemView.findViewById(R.id.appSwitch)

        fun bind(item: AppListItem) {
            icon.setImageDrawable(item.icon)
            name.text = item.label
            usage.text = UsageStatsHelper.formatDuration(item.usageTodayMillis)

            val entry = item.limitEntry
            limit.text = when {
                entry == null || entry.limitMinutes <= 0 -> itemView.context.getString(R.string.no_limit)
                entry.enabled -> itemView.context.getString(R.string.limit_format, entry.limitMinutes)
                else -> itemView.context.getString(R.string.limit_off_format, entry.limitMinutes)
            }

            // Avoid firing the listener while we set the initial state.
            switch.setOnCheckedChangeListener(null)
            switch.isChecked = entry?.enabled == true && (entry.limitMinutes > 0)
            switch.isEnabled = entry != null && entry.limitMinutes > 0
            switch.setOnCheckedChangeListener { _, isChecked ->
                onToggleChanged(item, isChecked)
            }

            itemView.setOnClickListener { onRowClicked(item) }
        }
    }
}
