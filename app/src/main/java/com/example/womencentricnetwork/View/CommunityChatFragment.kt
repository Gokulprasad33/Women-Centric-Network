package com.example.womencentricnetwork.View

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import com.example.womencentricnetwork.Firebase.FirestoreManager
import com.example.womencentricnetwork.Model.Message
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class CommunityChatFragment : Fragment() {

    private val firestoreManager by lazy { FirestoreManager() }
    private var listener: ListenerRegistration? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val communityId = arguments?.getString("communityId") ?: ""
        val communityName = arguments?.getString("communityName") ?: "Community"

        return ComposeView(requireContext()).apply {
            setContent {
                MaterialTheme {
                    CommunityChatScreen(
                        communityId = communityId,
                        communityName = communityName,
                        firestoreManager = firestoreManager,
                        currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: "",
                        onRegisterListener = { listener = it }
                    )
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listener?.remove()
    }
}

@Composable
fun CommunityChatScreen(
    communityId: String,
    communityName: String,
    firestoreManager: FirestoreManager,
    currentUserId: String,
    onRegisterListener: (ListenerRegistration) -> Unit
) {
    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var messageText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val sdf = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

    DisposableEffect(communityId) {
        val reg = firestoreManager.listenForCommunityMessages(communityId) { messages = it }
        onRegisterListener(reg)
        onDispose { reg.remove() }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(Modifier.fillMaxSize()) {
        // Header
        Surface(color = MaterialTheme.colorScheme.primary, tonalElevation = 4.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(communityName, color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Community Chat", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall)
            }
        }

        // Messages
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No messages yet. Start the conversation!",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(messages, key = { it.id }) { msg ->
                val isMe = msg.senderId == currentUserId
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isMe) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.widthIn(max = 280.dp)
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            if (!isMe) {
                                Text(msg.senderName, fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary)
                                Spacer(Modifier.height(2.dp))
                            }
                            Text(msg.messageText,
                                color = if (isMe) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(if (msg.timestamp > 0) sdf.format(Date(msg.timestamp)) else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isMe) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }

        // Input bar
        Surface(tonalElevation = 2.dp) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type a message...") },
                    maxLines = 3
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    val text = messageText.trim()
                    if (text.isNotEmpty()) {
                        messageText = ""
                        scope.launch { firestoreManager.sendCommunityMessage(communityId, text) }
                    }
                }) { Text("Send") }
            }
        }
    }
}

