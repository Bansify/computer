package com.bansify.computer.ui.dialogs

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bansify.computer.data.AppEntry
import com.bansify.computer.databinding.ItemSearchAppBinding
import com.bansify.computer.ui.AppIcons
import com.bansify.computer.ui.view.SmoothOutlineProvider

class SearchAppAdapter(
    private val onAppClick: (AppEntry) -> Unit
) : ListAdapter<AppEntry, SearchAppAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSearchAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemSearchAppBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            val density = binding.root.resources.displayMetrics.density
            binding.imgAppIcon.outlineProvider = SmoothOutlineProvider(6f * density, 0.6f)
            binding.imgAppIcon.clipToOutline = true
            binding.imgLaunchArrow.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_CHEVRON_RIGHT, 0x80FFFFFF.toInt()))
            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onAppClick(getItem(pos))
                }
            }
        }

        fun bind(app: AppEntry) {
            binding.tvAppLabel.text = app.label
            binding.tvAppPkg.text = when {
                app.pkg.startsWith("pwa://") -> "Web App"
                app.pkg == "com.bansify.computer.files" ||
                    app.pkg == "com.bansify.computer.webapp" ||
                    app.pkg == "com.bansify.computer.store" -> "Built-in PC App"
                else -> "App"
            }

            val bmp = app.icon ?: app.banner
            if (bmp != null) {
                binding.imgAppIcon.setImageBitmap(bmp)
            } else {
                binding.imgAppIcon.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_APPS, Color.WHITE))
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<AppEntry>() {
            override fun areItemsTheSame(oldItem: AppEntry, newItem: AppEntry) = oldItem.pkg == newItem.pkg
            override fun areContentsTheSame(oldItem: AppEntry, newItem: AppEntry) = oldItem == newItem
        }
    }
}
