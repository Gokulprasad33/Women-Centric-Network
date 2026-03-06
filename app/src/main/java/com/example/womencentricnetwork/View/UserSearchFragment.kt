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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.womencentricnetwork.Firebase.FirestoreManager
import com.example.womencentricnetwork.R
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

data class UserItem(
    val uid: String = "",
    val name: String = "",
    val email: String = ""
)

class UserSearchFragment : Fragment() {

    private val firestoreManager by lazy { FirestoreManager() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                MaterialTheme {
                    UserSearchScreen(
                        firestoreManager = firestoreManager,
                        currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: "",
                        onChatStarted = { chatId, otherName ->
                            val bundle = Bundle().apply {
                                putString("chatId", chatId)
                                putString("otherName", otherName)
                            }
                            findNavController().navigate(R.id.privateChatFragment, bundle)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun UserSearchScreen(
    firestoreManager: FirestoreManager,
    currentUserId: String,
    onChatStarted: (chatId: String, otherName: String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var allUsers by remember { mutableStateOf<List<UserItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var startingChat by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Load all users once
    LaunchedEffect(Unit) {
        isLoading = true
        val result = firestoreManager.getAllUsers()
        result.onSuccess { users ->
            allUsers = users
                .filter { it.uid != currentUserId }
                .sortedBy { it.name.lowercase() }
        }
        isLoading = false
    }

    val filtered = if (searchQuery.isBlank()) allUsers
    else allUsers.filter {
        it.name.contains(searchQuery, ignoreCase = true) ||
                it.email.contains(searchQuery, ignoreCase = true)
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Find Users", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search by name...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))

        when {
            isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            filtered.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (allUsers.isEmpty()) "No other users registered yet"
                        else "No users match \"$searchQuery\"",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filtered, key = { it.uid }) { user ->
                        val isBusy = startingChat == user.uid
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isBusy) {
                                    startingChat = user.uid
                                    scope.launch {
                                        val result = firestoreManager.getOrCreatePrivateChat(user.uid, user.name)
                                        result.onSuccess { chatId ->
                                            onChatStarted(chatId, user.name)
                                        }
                                        result.onFailure { err ->
                                            Toast.makeText(context, "Failed: ${err.message}", Toast.LENGTH_SHORT).show()
                                        }
                                        startingChat = null
                                    }
                                },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                Modifier.padding(16.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(user.name, fontWeight = FontWeight.SemiBold)
                                    Text(user.email, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (isBusy) {
                                    CircularProgressIndicator(Modifier.size(24.dp))
                                } else {
                                    Text("Chat", color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

