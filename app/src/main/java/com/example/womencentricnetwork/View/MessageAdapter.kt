package com.example.womencentricnetwork.View

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.womencentricnetwork.Model.Message
import com.example.womencentricnetwork.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * RecyclerView adapter for community chat messages.
 * Supports two view types: incoming (other users) and outgoing (current user).
 */
class MessageAdapter(
    private val currentUserId: String
) : ListAdapter<Message, RecyclerView.ViewHolder>(MessageDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_INCOMING = 0
        private const val VIEW_TYPE_OUTGOING = 1
    }

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).senderId == currentUserId) {
            VIEW_TYPE_OUTGOING
        } else {
            VIEW_TYPE_INCOMING
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_OUTGOING) {
            val view = inflater.inflate(R.layout.item_message_outgoing, parent, false)
            OutgoingViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_message_incoming, parent, false)
            IncomingViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        when (holder) {
            is OutgoingViewHolder -> holder.bind(message)
            is IncomingViewHolder -> holder.bind(message)
        }
    }

    // ── Outgoing (current user) ─────────────────────────────────────────

    class OutgoingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMessageText: TextView = itemView.findViewById(R.id.tvMessageText)
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)

        fun bind(message: Message) {
            tvMessageText.text = message.messageText
            tvTimestamp.text = formatTimestamp(message.timestamp)
        }
    }

    // ── Incoming (other users) ──────────────────────────────────────────

    class IncomingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvSenderName: TextView = itemView.findViewById(R.id.tvSenderName)
        private val tvMessageText: TextView = itemView.findViewById(R.id.tvMessageText)
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)

        fun bind(message: Message) {
            tvSenderName.text = message.senderName
            tvMessageText.text = message.messageText
            tvTimestamp.text = formatTimestamp(message.timestamp)
        }
    }

    // ── DiffUtil ────────────────────────────────────────────────────────

    class MessageDiffCallback : DiffUtil.ItemCallback<Message>() {
        override fun areItemsTheSame(oldItem: Message, newItem: Message): Boolean {
            // Use Firestore document ID for unique identification
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Message, newItem: Message): Boolean {
            return oldItem == newItem
        }
    }
}

// ── Helper ──────────────────────────────────────────────────────────────

private fun formatTimestamp(timestamp: Long): String {
    if (timestamp == 0L) return ""
    val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

