package com.example.womencentricnetwork.View

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
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
import com.example.womencentricnetwork.R
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {

    private val authManager = AuthManager()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                MaterialTheme {
                    LoginScreen(
                        onLogin = { email, password -> login(email, password) },
                        onNavigateToRegister = {
                            findNavController().navigate(R.id.action_loginFragment_to_registerFragment)
                        }
                    )
                }
            }
        }
    }

    private fun login(email: String, password: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = authManager.login(email, password)
            result.onSuccess {
                findNavController().navigate(R.id.action_loginFragment_to_homeFragment)
            }
            result.onFailure { error ->
                val message = parseFirebaseError(error)
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun parseFirebaseError(error: Throwable): String {
        val msg = error.message ?: "Login failed"
        return when {
            msg.contains("badly formatted", ignoreCase = true) -> "Invalid email format"
            msg.contains("no user record", ignoreCase = true) -> "User not found. Please register first."
            msg.contains("password is invalid", ignoreCase = true) -> "Wrong password"
            msg.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true) -> "Invalid email or password"
            msg.contains("network", ignoreCase = true) -> "Network error. Check your connection."
            msg.contains("too many", ignoreCase = true) -> "Too many attempts. Try again later."
            else -> msg
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Login Composable
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun LoginScreen(
    onLogin: (email: String, password: String) -> Unit,
    onNavigateToRegister: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App title
            Text(
                text = "Women Centric Network",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Stay safe. Stay connected.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(48.dp))

            // Email
            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    emailError = null
                },
                label = { Text("Email") },
                isError = emailError != null,
                supportingText = emailError?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            // Password
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    passwordError = null
                },
                label = { Text("Password") },
                isError = passwordError != null,
                supportingText = passwordError?.let { { Text(it) } },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(24.dp))

            // Login button
            Button(
                onClick = {
                    // Validate
                    emailError = when {
                        email.isBlank() -> "Email is required"
                        !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> "Invalid email format"
                        else -> null
                    }
                    passwordError = when {
                        password.isBlank() -> "Password is required"
                        password.length < 6 -> "Password must be at least 6 characters"
                        else -> null
                    }

                    if (emailError == null && passwordError == null) {
                        isLoading = true
                        onLogin(email.trim(), password)
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
                    Text("Login", style = MaterialTheme.typography.titleMedium)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Create account
            TextButton(onClick = onNavigateToRegister) {
                Text("Don't have an account? Create one")
            }
        }
    }
}

