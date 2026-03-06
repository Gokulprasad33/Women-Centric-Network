package com.example.womencentricnetwork.View

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.womencentricnetwork.Firebase.AuthManager
import com.example.womencentricnetwork.Firebase.FirestoreManager
import com.example.womencentricnetwork.R
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class RegisterFragment : Fragment() {

    private val authManager = AuthManager()
    private val firestoreManager = FirestoreManager()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                MaterialTheme {
                    RegisterScreen(
                        onRegister = { name, email, phone, password ->
                            register(name, email, phone, password)
                        },
                        onNavigateToLogin = {
                            findNavController().popBackStack()
                        }
                    )
                }
            }
        }
    }

    private fun register(name: String, email: String, phone: String, password: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = authManager.register(email, password)
            result.onSuccess { user ->
                // Create user document in Firestore
                try {
                    firestoreManager.saveUserProfile(
                        name = name,
                        email = email,
                        phone = phone
                    )

                    // Save FCM device token
                    try {
                        val token = FirebaseMessaging.getInstance().token.await()
                        firestoreManager.saveFcmToken(token)
                    } catch (_: Exception) {
                        // Token save is non-critical
                    }
                } catch (_: Exception) {
                    // Firestore save is non-critical for registration flow
                }

                Toast.makeText(context, "Account created successfully!", Toast.LENGTH_SHORT).show()
                findNavController().navigate(R.id.action_registerFragment_to_homeFragment)
            }
            result.onFailure { error ->
                val message = parseFirebaseError(error)
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun parseFirebaseError(error: Throwable): String {
        val msg = error.message ?: "Registration failed"
        return when {
            msg.contains("badly formatted", ignoreCase = true) -> "Invalid email format"
            msg.contains("already in use", ignoreCase = true) -> "Email already registered. Please login."
            msg.contains("weak password", ignoreCase = true) -> "Password is too weak. Use at least 6 characters."
            msg.contains("network", ignoreCase = true) -> "Network error. Check your connection."
            else -> msg
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Register Composable
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun RegisterScreen(
    onRegister: (name: String, email: String, phone: String, password: String) -> Unit,
    onNavigateToLogin: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    var nameError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var phoneError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Title
            Text(
                text = "Create Account",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Join the Women Centric Network",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(36.dp))

            // Name
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; nameError = null },
                label = { Text("Full Name") },
                isError = nameError != null,
                supportingText = nameError?.let { { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            // Email
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; emailError = null },
                label = { Text("Email") },
                isError = emailError != null,
                supportingText = emailError?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            // Phone
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it; phoneError = null },
                label = { Text("Phone Number") },
                isError = phoneError != null,
                supportingText = phoneError?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            // Password
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; passwordError = null },
                label = { Text("Password") },
                isError = passwordError != null,
                supportingText = passwordError?.let { { Text(it) } },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(24.dp))

            // Register button
            Button(
                onClick = {
                    // Validate
                    nameError = if (name.isBlank()) "Name is required" else null
                    emailError = when {
                        email.isBlank() -> "Email is required"
                        !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> "Invalid email format"
                        else -> null
                    }
                    phoneError = when {
                        phone.isBlank() -> "Phone number is required"
                        phone.length < 7 -> "Phone number is too short"
                        else -> null
                    }
                    passwordError = when {
                        password.isBlank() -> "Password is required"
                        password.length < 6 -> "Password must be at least 6 characters"
                        else -> null
                    }

                    if (nameError == null && emailError == null && phoneError == null && passwordError == null) {
                        isLoading = true
                        onRegister(name.trim(), email.trim(), phone.trim(), password)
                        isLoading = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Create Account", style = MaterialTheme.typography.titleMedium)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Back to login
            TextButton(onClick = onNavigateToLogin) {
                Text("Already have an account? Login")
            }
        }
    }
}

