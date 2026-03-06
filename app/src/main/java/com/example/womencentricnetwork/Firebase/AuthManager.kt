package com.example.womencentricnetwork.Firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.tasks.await

/**
 * Wraps Firebase Authentication for the Women Centric Network app.
 *
 * Usage:
 *   val auth = AuthManager()
 *   val result = auth.register("user@example.com", "password123")
 *   result.onSuccess { user -> … }
 *   result.onFailure { error -> … }
 */
class AuthManager {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    /** Currently signed-in user, or null. */
    val currentUser: FirebaseUser?
        get() = auth.currentUser

    /** True when a user is signed in. */
    val isLoggedIn: Boolean
        get() = auth.currentUser != null

    /** UID of the current user, or null. */
    val uid: String?
        get() = auth.currentUser?.uid

    // ── Register ────────────────────────────────────────────────────────

    suspend fun register(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user
            if (user != null) {
                Result.success(user)
            } else {
                Result.failure(Exception("Registration succeeded but user is null"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Login ───────────────────────────────────────────────────────────

    suspend fun login(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val user = result.user
            if (user != null) {
                Result.success(user)
            } else {
                Result.failure(Exception("Login succeeded but user is null"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Logout ──────────────────────────────────────────────────────────

    fun logout() {
        auth.signOut()
    }

    // ── Password Reset ──────────────────────────────────────────────────

    suspend fun resetPassword(email: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

