package com.example.womencentricnetwork.View

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import java.text.SimpleDateFormat
import java.util.*

class ArticleDetailFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val title = arguments?.getString("title") ?: ""
        val content = arguments?.getString("content") ?: ""
        val authorName = arguments?.getString("authorName") ?: ""
        val timestamp = arguments?.getLong("timestamp") ?: 0L
        val category = arguments?.getString("category") ?: ""

        return ComposeView(requireContext()).apply {
            setContent {
                MaterialTheme { ArticleDetailScreen(title, content, authorName, timestamp, category) }
            }
        }
    }
}

@Composable
fun ArticleDetailScreen(title: String, content: String, authorName: String, timestamp: Long, category: String) {
    val sdf = remember { SimpleDateFormat("MMM dd, yyyy  hh:mm a", Locale.getDefault()) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        if (category.isNotBlank()) {
            AssistChip(onClick = {}, label = { Text(category) })
            Spacer(Modifier.height(8.dp))
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("By $authorName", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (timestamp > 0) {
                Text(sdf.format(Date(timestamp)), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 16.dp))
        Text(content, style = MaterialTheme.typography.bodyLarge)
    }
}

