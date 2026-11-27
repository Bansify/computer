package com.bansify.computer.ui.dialogs

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bansify.computer.databinding.ItemNotificationBinding
import com.bansify.computer.service.NotificationItem
import com.bansify.computer.ui.AppIcons

class NotificationAdapter(
    private val onClick: (NotificationItem) -> Unit,
    private val onDismiss: (NotificationItem) -> Unit
) : ListAdapter<NotificationItem, NotificationAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemNotificationBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.btnDismissNotif.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_CLOSE, 0xB3FFFFFF.toInt()))
            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onClick(getItem(pos))
                }
            }
            binding.btnDismissNotif.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onDismiss(getItem(pos))
                }
            }
        }

        fun bind(item: NotificationItem) {
            binding.tvNotifAppName.text = item.appName.ifBlank { item.packageName }
            binding.tvNotifTitle.text = item.title
            val bodyText = if (item.text.isNotBlank()) item.text else item.subText.orEmpty()
            binding.tvNotifBody.text = bodyText
            binding.tvNotifBody.visibility = if (bodyText.isBlank()) View.GONE else View.VISIBLE

            val timeAgo = DateUtils.getRelativeTimeSpanString(
                item.postTime,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            )
            binding.tvNotifTime.text = timeAgo

            val iconBmp = item.nativeBitmap ?: item.appIcon
            if (iconBmp != null) {
                binding.imgNotifAppIcon.setImageBitmap(iconBmp)
            } else {
                binding.imgNotifAppIcon.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_BELL, 0xFF4FA7FA.toInt()))
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<NotificationItem>() {
            override fun areItemsTheSame(oldItem: NotificationItem, newItem: NotificationItem) = oldItem.key == newItem.key
            override fun areContentsTheSame(oldItem: NotificationItem, newItem: NotificationItem) = oldItem == newItem
        }
    }
}
