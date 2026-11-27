package com.bansify.computer.ui.pc.settings

import android.graphics.Bitmap
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bansify.computer.data.BitmapSampling
import com.bansify.computer.databinding.ItemPcWallpaperThumbBinding
import com.bansify.computer.ui.PC_WALLPAPERS
import com.bansify.computer.ui.PcWallpaperPreset

class PcWallpaperAdapter(
    private var selectedId: Int = 0,
    private var isCustomSelected: Boolean = false,
    private val onSelected: (PcWallpaperPreset) -> Unit
) : RecyclerView.Adapter<PcWallpaperAdapter.WallpaperViewHolder>() {

    // Downsampled thumbnail cache to prevent OOM when decoding full-resolution drawable-nodpi wallpapers
    private val thumbCache = LruCache<Int, Bitmap>(16)

    fun setSelection(id: Int, custom: Boolean) {
        val prevId = selectedId
        selectedId = id
        isCustomSelected = custom

        val prevIdx = PC_WALLPAPERS.indexOfFirst { it.id == prevId }
        val newIdx = PC_WALLPAPERS.indexOfFirst { it.id == id }
        if (prevIdx != -1) notifyItemChanged(prevIdx)
        if (newIdx != -1) notifyItemChanged(newIdx)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WallpaperViewHolder {
        val binding = ItemPcWallpaperThumbBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return WallpaperViewHolder(binding)
    }

    override fun onBindViewHolder(holder: WallpaperViewHolder, position: Int) {
        holder.bind(PC_WALLPAPERS[position])
    }

    override fun getItemCount(): Int = PC_WALLPAPERS.size

    inner class WallpaperViewHolder(
        private val binding: ItemPcWallpaperThumbBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(preset: PcWallpaperPreset) {
            val cached = thumbCache.get(preset.resId)
            val bmp = cached ?: runCatching {
                BitmapSampling.decodeResource(binding.root.resources, preset.resId, 320, 180)
            }.getOrNull()?.also {
                thumbCache.put(preset.resId, it)
            }

            if (bmp != null) {
                binding.imgWallpaperThumb.setImageBitmap(bmp)
            } else {
                binding.imgWallpaperThumb.setImageDrawable(null)
            }
            binding.tvWallpaperTitle.text = preset.name

            val isSelected = !isCustomSelected && preset.id == selectedId
            binding.viewSelectedBorder.visibility = if (isSelected) View.VISIBLE else View.GONE

            binding.wallpaperThumbRoot.setOnClickListener {
                setSelection(preset.id, false)
                onSelected(preset)
            }
        }
    }
}
