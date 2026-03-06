package com.example.womencentricnetwork.View

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.womencentricnetwork.Firebase.FirestoreManager
import com.example.womencentricnetwork.Firebase.NotificationHelper
import com.example.womencentricnetwork.Model.Message
import com.example.womencentricnetwork.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

class ChatScreen : Fragment(R.layout.fragment_chat) {

    companion object {
        private const val TAG = "ChatScreen"
        private const val DEFAULT_ROOM_ID = "chennai_general"
    }

    private val firestoreManager by lazy { FirestoreManager() }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val notificationHelper by lazy { NotificationHelper(requireContext()) }

    private lateinit var rvMessages: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageView
    private lateinit var tvEmptyState: TextView

    private var messageAdapter: MessageAdapter? = null
    private var messageListener: ListenerRegistration? = null

    // Track last known message count to detect new messages
    private var lastMessageCount = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_chat, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Bind views
        rvMessages = view.findViewById(R.id.rvMessages)
        etMessage = view.findViewById(R.id.etMessage)
        btnSend = view.findViewById(R.id.btnSend)
        tvEmptyState = view.findViewById(R.id.tvEmptyState)

        // Setup RecyclerView
        val currentUserId = auth.currentUser?.uid ?: ""
        messageAdapter = MessageAdapter(currentUserId)

        val layoutManager = LinearLayoutManager(requireContext())
        layoutManager.stackFromEnd = true // New messages appear at bottom
        rvMessages.layoutManager = layoutManager
        rvMessages.adapter = messageAdapter

        // Ensure chat room exists
        ensureChatRoom()

        // Start listening for messages
        startMessageListener()

        // Send button
        btnSend.setOnClickListener { sendMessage() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        messageListener?.remove()
        messageListener = null
    }

    // ── Ensure default chat room exists ─────────────────────────────────

    private fun ensureChatRoom() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                firestoreManager.ensureChatRoomExists(DEFAULT_ROOM_ID, "Chennai General")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to ensure chat room: ${e.message}")
            }
        }
    }

    // ── Real-time message listener ──────────────────────────────────────

    private fun startMessageListener() {
        val currentUserId = auth.currentUser?.uid ?: ""
        Log.d(TAG, "Starting message listener for room: $DEFAULT_ROOM_ID, userId: $currentUserId")

        messageListener = firestoreManager.listenForMessages(DEFAULT_ROOM_ID) { messages ->
            if (!isAdded) return@listenForMessages

            Log.d(TAG, "Received ${messages.size} messages from Firestore")

            // Force a new list instance — ListAdapter ignores submitList if same reference
            val freshList = messages.toList()

            // Ensure UI updates happen on main thread
            rvMessages.post {
                messageAdapter?.submitList(freshList) {
                    // Scroll to bottom when new messages arrive
                    if (freshList.isNotEmpty()) {
                        rvMessages.scrollToPosition(freshList.size - 1)
                    }
                }

                // Toggle empty state
                tvEmptyState.visibility = if (freshList.isEmpty()) View.VISIBLE else View.GONE

                Log.d(TAG, "Adapter updated with ${freshList.size} messages")
            }

            // Notify for new messages from other users
            if (messages.size > lastMessageCount && lastMessageCount > 0) {
                val latestMessage = messages.last()
                if (latestMessage.senderId != currentUserId) {
                    notificationHelper.showCommunityAlert(
                        title = latestMessage.senderName,
                        body = latestMessage.messageText,
                        notificationId = System.currentTimeMillis().toInt()
                    )
                }
            }
            lastMessageCount = messages.size
        }
    }

    // ── Send message ────────────────────────────────────────────────────

    private fun sendMessage() {
        // Check if user is logged in
        if (auth.currentUser == null) {
            Toast.makeText(context, "Please log in to send messages", Toast.LENGTH_LONG).show()
            return
        }

        val text = etMessage.text.toString().trim()

        // Validate
        if (text.isEmpty()) {
            Toast.makeText(context, "Message cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        // Clear input immediately for responsive feel
        etMessage.text.clear()

        viewLifecycleOwner.lifecycleScope.launch {
            val result = firestoreManager.sendMessage(DEFAULT_ROOM_ID, text)
            result.onFailure { error ->
                Log.e(TAG, "Send message failed: ${error.message}")
                Toast.makeText(context, "Failed to send message: ${error.message}", Toast.LENGTH_SHORT).show()
                // Restore the text if send failed
                etMessage.setText(text)
            }
        }
    }
}