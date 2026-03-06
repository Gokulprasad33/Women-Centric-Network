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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.womencentricnetwork.Firebase.FirestoreManager
import com.example.womencentricnetwork.Model.Community
import com.example.womencentricnetwork.R
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

class CommunityListFragment : Fragment() {

    private val firestoreManager by lazy { FirestoreManager() }
    private var listener: ListenerRegistration? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        // Seed demo data on first load
        viewLifecycleOwner.lifecycleScope.launch { firestoreManager.seedDemoDataIfEmpty() }

        return ComposeView(requireContext()).apply {
            setContent {
                MaterialTheme {
                    CommunityListScreen(
                        firestoreManager = firestoreManager,
                        onRegisterListener = { listener = it },
                        onCommunityClick = { communityId, communityName ->
                            val bundle = Bundle().apply {
                                putString("communityId", communityId)
                                putString("communityName", communityName)
                            }
                            findNavController().navigate(R.id.communityChatFragment, bundle)
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
fun CommunityListScreen(
    firestoreManager: FirestoreManager,
    onRegisterListener: (ListenerRegistration) -> Unit,
    onCommunityClick: (communityId: String, communityName: String) -> Unit
) {
    var communities by remember { mutableStateOf<List<Community>>(emptyList()) }
    var joinedMap by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val reg = firestoreManager.listenForCommunities { list ->
            communities = list
            // Check membership for each
            scope.launch {
                val map = mutableMapOf<String, Boolean>()
                for (c in list) {
                    map[c.id] = firestoreManager.isUserInCommunity(c.id)
                }
                joinedMap = map
            }
        }
        onRegisterListener(reg)
        onDispose { reg.remove() }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Communities", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        if (communities.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(communities, key = { it.id }) { community ->
                    val isJoined = joinedMap[community.id] ?: false
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            if (isJoined) onCommunityClick(community.id, community.name)
                            else Toast.makeText(context, "Join the community first", Toast.LENGTH_SHORT).show()
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isJoined) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(community.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(community.description, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Text("${community.memberCount} members", style = MaterialTheme.typography.bodySmall)
                                if (isJoined) {
                                    OutlinedButton(onClick = {
                                        scope.launch {
                                            firestoreManager.leaveCommunity(community.id)
                                            joinedMap = joinedMap.toMutableMap().apply { put(community.id, false) }
                                        }
                                    }) { Text("Leave") }
                                } else {
                                    Button(onClick = {
                                        scope.launch {
                                            firestoreManager.joinCommunity(community.id)
                                            joinedMap = joinedMap.toMutableMap().apply { put(community.id, true) }
                                        }
                                    }) { Text("Join") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

