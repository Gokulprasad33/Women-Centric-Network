package com.example.womencentricnetwork.View

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.womencentricnetwork.Firebase.FirestoreManager
import kotlinx.coroutines.launch

class CreateArticleFragment : Fragment() {

    private val firestoreManager by lazy { FirestoreManager() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                MaterialTheme {
                    CreateArticleScreen(
                        firestoreManager = firestoreManager,
                        onCreated = { findNavController().popBackStack() }
                    )
                }
            }
        }
    }
}

@Composable
fun CreateArticleScreen(firestoreManager: FirestoreManager, onCreated: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Safety Tips") }
    var isLoading by remember { mutableStateOf(false) }
    val categories = listOf("Safety Tips", "News", "Community Posts")
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Text("Create Article", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = title, onValueChange = { title = it },
            label = { Text("Title") }, singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = content, onValueChange = { content = it },
            label = { Text("Content") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
            maxLines = 10
        )
        Spacer(Modifier.height(16.dp))

        Text("Category", fontWeight = FontWeight.SemiBold)
        Column(Modifier.selectableGroup()) {
            categories.forEach { cat ->
                Row(
                    Modifier.fillMaxWidth()
                        .selectable(selected = selectedCategory == cat, onClick = { selectedCategory = cat }, role = Role.RadioButton)
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selectedCategory == cat, onClick = null)
                    Spacer(Modifier.width(8.dp))
                    Text(cat)
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                if (title.isBlank() || content.isBlank()) {
                    Toast.makeText(context, "Title and content are required", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                isLoading = true
                scope.launch {
                    val result = firestoreManager.createArticle(title.trim(), content.trim(), selectedCategory)
                    isLoading = false
                    result.onSuccess {
                        Toast.makeText(context, "Article published!", Toast.LENGTH_SHORT).show()
                        onCreated()
                    }
                    result.onFailure {
                        Toast.makeText(context, "Failed: ${it.message}", Toast.LENGTH_LONG).show()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            enabled = !isLoading
        ) {
            if (isLoading) CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
            else Text("Publish Article", style = MaterialTheme.typography.titleMedium)
        }
    }
}

