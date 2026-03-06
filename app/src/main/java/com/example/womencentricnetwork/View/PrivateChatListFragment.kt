package com.example.womencentricnetwork.View

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.womencentricnetwork.Firebase.FirestoreManager
import com.example.womencentricnetwork.Model.PrivateChat
import com.example.womencentricnetwork.R
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.*

class PrivateChatListFragment : Fragment() {

    private val firestoreManager by lazy { FirestoreManager() }
    private var listener: ListenerRegistration? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                MaterialTheme {
                    PrivateChatListScreen(
                        firestoreManager = firestoreManager,
                        onRegisterListener = { listener = it },
                        onChatClick = { chatId, otherName ->
                            val bundle = Bundle().apply {
                                putString("chatId", chatId)
                                putString("otherName", otherName)
                            }
                            findNavController().navigate(R.id.privateChatFragment, bundle)
                        },
                        onFindUsersClick = {
                            findNavController().navigate(R.id.userSearchFragment)
                        }
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
fun PrivateChatListScreen(
    firestoreManager: FirestoreManager,
    onRegisterListener: (ListenerRegistration) -> Unit,
    onChatClick: (chatId: String, otherName: String) -> Unit,
    onFindUsersClick: () -> Unit = {}
) {
    var chats by remember { mutableStateOf<List<PrivateChat>>(emptyList()) }
    val sdf = remember { SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()) }

    DisposableEffect(Unit) {
        val reg = firestoreManager.listenForPrivateChats { chats = it }
        onRegisterListener(reg)
        onDispose { reg.remove() }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onFindUsersClick) {
                Icon(Icons.Default.Add, contentDescription = "Find users")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Private Chats", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            if (chats.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No conversations yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = onFindUsersClick) {
                            Text("Find users to chat with")
                        }
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(chats, key = { it.id }) { chat ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { onChatClick(chat.id, chat.otherUserName) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(chat.otherUserName, fontWeight = FontWeight.SemiBold)
                                    if (chat.lastTimestamp > 0) {
                                        Text(sdf.format(Date(chat.lastTimestamp)),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (chat.lastMessage.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(chat.lastMessage, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

