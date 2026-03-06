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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.womencentricnetwork.Firebase.FirestoreManager
import com.example.womencentricnetwork.Model.Article
import com.example.womencentricnetwork.R
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ArticlesFragment : Fragment() {

    private val firestoreManager by lazy { FirestoreManager() }
    private var listener: ListenerRegistration? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        // Seed demo data
        viewLifecycleOwner.lifecycleScope.launch { firestoreManager.seedDemoDataIfEmpty() }

        return ComposeView(requireContext()).apply {
            setContent {
                MaterialTheme {
                    ArticlesScreen(
                        firestoreManager = firestoreManager,
                        onRegisterListener = { listener = it },
                        onArticleClick = { article ->
                            val bundle = Bundle().apply {
                                putString("articleId", article.id)
                                putString("title", article.title)
                                putString("content", article.content)
                                putString("authorName", article.authorName)
                                putLong("timestamp", article.timestamp)
                                putString("category", article.category)
                            }
                            findNavController().navigate(R.id.articleDetailFragment, bundle)
                        },
                        onCreateClick = {
                            findNavController().navigate(R.id.createArticleFragment)
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
fun ArticlesScreen(
    firestoreManager: FirestoreManager,
    onRegisterListener: (ListenerRegistration) -> Unit,
    onArticleClick: (Article) -> Unit,
    onCreateClick: () -> Unit
) {
    var articles by remember { mutableStateOf<List<Article>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf("All") }
    val categories = listOf("All", "Safety Tips", "News", "Community Posts")
    val sdf = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    DisposableEffect(Unit) {
        val reg = firestoreManager.listenForArticles { articles = it }
        onRegisterListener(reg)
        onDispose { reg.remove() }
    }

    val filtered = if (selectedCategory == "All") articles
        else articles.filter { it.category == selectedCategory }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateClick) {
                Icon(Icons.Default.Add, contentDescription = "Create Article")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Articles", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))

            // Category filter chips
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No articles yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(filtered, key = { it.id }) { article ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { onArticleClick(article) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    AssistChip(onClick = {}, label = { Text(article.category) })
                                    if (article.timestamp > 0) {
                                        Text(sdf.format(Date(article.timestamp)),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(article.title, fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(4.dp))
                                Text(article.content, maxLines = 3, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(8.dp))
                                Text("By ${article.authorName}", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

