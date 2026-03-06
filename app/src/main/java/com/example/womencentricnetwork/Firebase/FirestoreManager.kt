package com.example.womencentricnetwork.Firebase

import android.util.Log
import com.example.womencentricnetwork.Model.Article
import com.example.womencentricnetwork.Model.Community
import com.example.womencentricnetwork.Model.Incident
import com.example.womencentricnetwork.Model.Message
import com.example.womencentricnetwork.Model.PrivateChat
import com.example.womencentricnetwork.Model.Settings.EmergencyContactEntity
import com.example.womencentricnetwork.View.UserItem
import com.example.womencentricnetwork.Model.Settings.SosEventEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * Manages all Firestore collections for the Women Centric Network app.
 *
 * Firestore Structure:
 * ────────────────────
 * users/
 *   {uid}/
 *     name: String
 *     email: String
 *     phone: String
 *     createdAt: Long
 *     deviceToken: String
 *     lastLocation/
 *       latitude: Double
 *       longitude: Double
 *       updatedAt: Long
 *
 * contacts/                       ← top-level collection
 *   {contactId}/
 *     userId: String
 *     name: String
 *     phoneNumber: String
 *     relation: String
 *
 * incidents/                      ← top-level collection
 *   {incidentId}/
 *     userId: String
 *     latitude: Double
 *     longitude: Double
 *     description: String
 *     timestamp: Long
 *     reportedBy: String
 *
 * helplines/                      ← top-level collection (admin-managed)
 *   {id}/
 *     name: String
 *     phoneNumber: String
 *     category: String
 *     region: String
 *
 * communityRooms/                 ← top-level collection
 *   {roomId}/
 *     roomId: String
 *     name: String
 *     createdAt: Long
 *
 * messages/                       ← top-level collection
 *   {messageId}/
 *     roomId: String
 *     senderId: String
 *     senderName: String
 *     messageText: String
 *     timestamp: Long
 */
class FirestoreManager {

    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private val uid: String?
        get() = auth.currentUser?.uid

    // ═══════════════════════════════════════════════════════════════════
    // User Profile
    // ═══════════════════════════════════════════════════════════════════

    /** Create or update the user profile document. */
    suspend fun saveUserProfile(
        name: String,
        email: String,
        phone: String
    ): Result<Unit> {
        val userId = uid ?: return Result.failure(Exception("Not logged in"))
        return try {
            val data = hashMapOf(
                "name" to name,
                "email" to email,
                "phone" to phone,
                "createdAt" to System.currentTimeMillis()
            )
            db.collection("users").document(userId)
                .set(data, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Save FCM device token to the user document. */
    suspend fun saveFcmToken(token: String): Result<Unit> {
        val userId = uid ?: return Result.failure(Exception("Not logged in"))
        return try {
            db.collection("users").document(userId)
                .update("deviceToken", token)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Update the user's last known location. */
    suspend fun updateLastLocation(latitude: Double, longitude: Double): Result<Unit> {
        val userId = uid ?: return Result.failure(Exception("Not logged in"))
        return try {
            val locationData = hashMapOf(
                "lastLocation" to hashMapOf(
                    "latitude" to latitude,
                    "longitude" to longitude,
                    "updatedAt" to System.currentTimeMillis()
                )
            )
            db.collection("users").document(userId)
                .set(locationData, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Emergency Contacts  (top-level "contacts" collection)
    // ═══════════════════════════════════════════════════════════════════

    /** Sync a single emergency contact to Firestore. */
    suspend fun syncContact(contact: EmergencyContactEntity): Result<Unit> {
        val userId = uid ?: return Result.failure(Exception("Not logged in"))
        return try {
            val data = hashMapOf(
                "userId" to userId,
                "name" to contact.name,
                "phoneNumber" to contact.phoneNumber,
                "relation" to (contact.relation ?: "")
            )
            db.collection("contacts")
                .document(contact.id.toString())
                .set(data)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Sync all emergency contacts to Firestore (batch write). */
    suspend fun syncAllContacts(contacts: List<EmergencyContactEntity>): Result<Unit> {
        val userId = uid ?: return Result.failure(Exception("Not logged in"))
        return try {
            val batch = db.batch()
            for (contact in contacts) {
                val docRef = db.collection("contacts").document(contact.id.toString())
                val data = hashMapOf(
                    "userId" to userId,
                    "name" to contact.name,
                    "phoneNumber" to contact.phoneNumber,
                    "relation" to (contact.relation ?: "")
                )
                batch.set(docRef, data)
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Delete a contact from Firestore. */
    suspend fun deleteContact(contactId: Long): Result<Unit> {
        return try {
            db.collection("contacts")
                .document(contactId.toString())
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Fetch all contacts for the current user from Firestore. */
    suspend fun getContacts(): Result<List<Map<String, Any>>> {
        val userId = uid ?: return Result.failure(Exception("Not logged in"))
        return try {
            val snapshot = db.collection("contacts")
                .whereEqualTo("userId", userId)
                .get()
                .await()
            val contacts = snapshot.documents.mapNotNull { it.data }
            Result.success(contacts)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // SOS Events  (sub-collection under users/{uid})
    // ═══════════════════════════════════════════════════════════════════

    /** Store an SOS event in Firestore. */
    suspend fun saveSosEvent(event: SosEventEntity): Result<String> {
        val userId = uid ?: return Result.failure(Exception("Not logged in"))
        return try {
            val data = hashMapOf(
                "userId" to userId,
                "timestamp" to event.timestamp,
                "latitude" to event.latitude,
                "longitude" to event.longitude,
                "message" to event.message,
                "status" to "sent"
            )
            val docRef = db.collection("users").document(userId)
                .collection("sosEvents")
                .add(data)
                .await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Incidents  (top-level "incidents" collection)
    // ═══════════════════════════════════════════════════════════════════

    /** Submit an incident report. */
    suspend fun submitIncidentReport(
        latitude: Double,
        longitude: Double,
        description: String
    ): Result<String> {
        val userId = uid ?: return Result.failure(Exception("Not logged in"))
        return try {
            val data = hashMapOf(
                "userId" to userId,
                "latitude" to latitude,
                "longitude" to longitude,
                "description" to description,
                "timestamp" to System.currentTimeMillis(),
                "reportedBy" to (auth.currentUser?.email ?: userId)
            )
            val docRef = db.collection("incidents")
                .add(data)
                .await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Fetch all incident reports (for map overlay later). */
    suspend fun getIncidentReports(): Result<List<Map<String, Any>>> {
        return try {
            val snapshot = db.collection("incidents")
                .get()
                .await()
            val reports = snapshot.documents.mapNotNull { it.data }
            Result.success(reports)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Fetch all incident reports as typed Incident objects. */
    suspend fun getIncidentReportsList(): Result<List<Incident>> {
        return try {
            val snapshot = db.collection("incidents")
                .get()
                .await()
            val incidents = snapshot.documents.mapNotNull { doc ->
                try {
                    Incident(
                        latitude = (doc.getDouble("latitude") ?: 0.0),
                        longitude = (doc.getDouble("longitude") ?: 0.0),
                        description = (doc.getString("description") ?: ""),
                        timestamp = (doc.getLong("timestamp") ?: 0L),
                        reportedBy = (doc.getString("reportedBy") ?: "")
                    )
                } catch (e: Exception) {
                    null
                }
            }
            Result.success(incidents)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Real-time snapshot listener for incidents collection.
     * Calls [onUpdate] with the latest list of Incident objects whenever data changes.
     * Returns a [ListenerRegistration] that must be removed when no longer needed.
     */
    fun listenForIncidents(onUpdate: (List<Incident>) -> Unit): ListenerRegistration {
        return db.collection("incidents")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                val incidents = snapshot.documents.mapNotNull { doc ->
                    try {
                        Incident(
                            latitude = (doc.getDouble("latitude") ?: 0.0),
                            longitude = (doc.getDouble("longitude") ?: 0.0),
                            description = (doc.getString("description") ?: ""),
                            timestamp = (doc.getLong("timestamp") ?: 0L),
                            reportedBy = (doc.getString("reportedBy") ?: "")
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                onUpdate(incidents)
            }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Community Chat
    // ═══════════════════════════════════════════════════════════════════

    /** Ensure a chat room document exists. Creates it if missing. */
    suspend fun ensureChatRoomExists(roomId: String, name: String): Result<Unit> {
        return try {
            val docRef = db.collection("communityRooms").document(roomId)
            val snapshot = docRef.get().await()
            if (!snapshot.exists()) {
                val data = hashMapOf(
                    "roomId" to roomId,
                    "name" to name,
                    "createdAt" to System.currentTimeMillis()
                )
                docRef.set(data).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Send a message to a chat room.
     * Fetches the current user's display name from Firestore user profile.
     */
    suspend fun sendMessage(roomId: String, messageText: String): Result<String> {
        val user = auth.currentUser ?: return Result.failure(Exception("Not logged in"))
        return try {
            // Try to get the user's display name from Firestore
            val senderName = try {
                val userDoc = db.collection("users").document(user.uid).get().await()
                userDoc.getString("name") ?: (user.email ?: "Anonymous")
            } catch (e: Exception) {
                user.email ?: "Anonymous"
            }

            val data = hashMapOf(
                "roomId" to roomId,
                "senderId" to user.uid,
                "senderName" to senderName,
                "messageText" to messageText,
                "timestamp" to FieldValue.serverTimestamp()
            )

            val docRef = db.collection("messages").add(data).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Real-time snapshot listener for messages in a specific room.
     * Tries ordered query first; if composite index is missing, falls back to
     * unordered query with client-side sorting so messages appear immediately.
     */
    fun listenForMessages(roomId: String, onUpdate: (List<Message>) -> Unit): ListenerRegistration {
        Log.d("CHAT_FIRESTORE", "Starting listener for roomId=$roomId")

        // Helper to parse documents into Message objects
        fun parseDocs(docs: List<com.google.firebase.firestore.DocumentSnapshot>): List<Message> {
            return docs.mapNotNull { doc ->
                try {
                    val timestamp = doc.getTimestamp("timestamp")
                    val timestampMillis = timestamp?.toDate()?.time
                        ?: doc.getLong("timestamp")
                        ?: System.currentTimeMillis()
                    Message(
                        id = doc.id,
                        senderId = doc.getString("senderId") ?: "",
                        senderName = doc.getString("senderName") ?: "",
                        messageText = doc.getString("messageText") ?: "",
                        roomId = doc.getString("roomId") ?: "",
                        timestamp = timestampMillis
                    )
                } catch (e: Exception) {
                    Log.e("CHAT_FIRESTORE", "Failed to parse doc ${doc.id}: ${e.message}")
                    null
                }
            }
        }

        var useFallback = false
        var fallbackRegistration: ListenerRegistration? = null

        // Primary: ordered query (requires composite index)
        val primaryRegistration = db.collection("messages")
            .whereEqualTo("roomId", roomId)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("CHAT_FIRESTORE", "Ordered query failed: ${error.message}")

                    // If index error, switch to fallback (unordered + client sort)
                    if (!useFallback) {
                        useFallback = true
                        Log.w("CHAT_FIRESTORE", "Falling back to unordered query (create the composite index for better performance)")

                        fallbackRegistration = db.collection("messages")
                            .whereEqualTo("roomId", roomId)
                            .addSnapshotListener { fbSnapshot, fbError ->
                                if (fbError != null) {
                                    Log.e("CHAT_FIRESTORE", "Fallback query also failed: ${fbError.message}")
                                    return@addSnapshotListener
                                }
                                if (fbSnapshot == null) return@addSnapshotListener

                                Log.d("CHAT_DEBUG", "Fallback snapshot: ${fbSnapshot.size()} documents")
                                val messages = parseDocs(fbSnapshot.documents).sortedBy { it.timestamp }
                                onUpdate(messages)
                            }
                    }
                    return@addSnapshotListener
                }

                if (snapshot == null) return@addSnapshotListener

                Log.d("CHAT_DEBUG", "Ordered snapshot: ${snapshot.size()} documents")
                val messages = parseDocs(snapshot.documents)
                onUpdate(messages)
            }

        // Return a composite registration that cleans up both listeners
        return object : ListenerRegistration {
            override fun remove() {
                primaryRegistration.remove()
                fallbackRegistration?.remove()
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Helplines  (top-level collection, admin-managed)
    // ═══════════════════════════════════════════════════════════════════

    /** Fetch all helpline entries. */
    suspend fun getHelplines(): Result<List<Map<String, Any>>> {
        return try {
            val snapshot = db.collection("helplines")
                .get()
                .await()
            val helplines = snapshot.documents.mapNotNull { it.data }
            Result.success(helplines)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Firebase Connectivity Test
    // ═══════════════════════════════════════════════════════════════════

    /** Quick connectivity test — writes a document to "test" collection. */
    suspend fun testConnection(): Result<String> {
        return try {
            val testData = hashMapOf(
                "message" to "firebase connected",
                "time" to System.currentTimeMillis()
            )
            val docRef = db.collection("test")
                .add(testData)
                .await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Communities
    // ═══════════════════════════════════════════════════════════════════

    fun listenForCommunities(onUpdate: (List<Community>) -> Unit): ListenerRegistration {
        return db.collection("communities")
            .orderBy("name", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("Firestore", "Communities listen failed", error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    Community(
                        id = doc.id,
                        name = doc.getString("name") ?: "",
                        description = doc.getString("description") ?: "",
                        createdAt = doc.getLong("createdAt") ?: 0L,
                        memberCount = doc.getLong("memberCount")?.toInt() ?: 0
                    )
                } ?: emptyList()
                onUpdate(list)
            }
    }

    suspend fun joinCommunity(communityId: String): Result<Unit> {
        val userId = uid ?: return Result.failure(Exception("Not logged in"))
        return try {
            val docId = "${communityId}_$userId"
            db.collection("communityMembers").document(docId)
                .set(hashMapOf(
                    "communityId" to communityId,
                    "userId" to userId,
                    "joinedAt" to System.currentTimeMillis()
                )).await()
            db.collection("communities").document(communityId)
                .update("memberCount", FieldValue.increment(1)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun leaveCommunity(communityId: String): Result<Unit> {
        val userId = uid ?: return Result.failure(Exception("Not logged in"))
        return try {
            val docId = "${communityId}_$userId"
            db.collection("communityMembers").document(docId).delete().await()
            db.collection("communities").document(communityId)
                .update("memberCount", FieldValue.increment(-1)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun isUserInCommunity(communityId: String): Boolean {
        val userId = uid ?: return false
        return try {
            val docId = "${communityId}_$userId"
            db.collection("communityMembers").document(docId).get().await().exists()
        } catch (e: Exception) {
            false
        }
    }

    suspend fun sendCommunityMessage(communityId: String, messageText: String): Result<String> {
        val user = auth.currentUser ?: return Result.failure(Exception("Not logged in"))
        return try {
            val senderName = try {
                db.collection("users").document(user.uid).get().await()
                    .getString("name") ?: (user.email ?: "Anonymous")
            } catch (e: Exception) { user.email ?: "Anonymous" }

            db.collection("communityMessages").add(hashMapOf(
                "communityId" to communityId,
                "senderId" to user.uid,
                "senderName" to senderName,
                "messageText" to messageText,
                "timestamp" to FieldValue.serverTimestamp()
            )).await().let { Result.success(it.id) }
        } catch (e: Exception) { Result.failure(e) }
    }

    fun listenForCommunityMessages(communityId: String, onUpdate: (List<Message>) -> Unit): ListenerRegistration {
        fun parseDocs(docs: List<com.google.firebase.firestore.DocumentSnapshot>): List<Message> {
            return docs.mapNotNull { doc ->
                try {
                    val ts = doc.getTimestamp("timestamp")?.toDate()?.time
                        ?: doc.getLong("timestamp") ?: System.currentTimeMillis()
                    Message(
                        id = doc.id,
                        senderId = doc.getString("senderId") ?: "",
                        senderName = doc.getString("senderName") ?: "",
                        messageText = doc.getString("messageText") ?: "",
                        roomId = doc.getString("communityId") ?: "",
                        timestamp = ts
                    )
                } catch (e: Exception) { null }
            }
        }

        var useFallback = false
        var fallbackReg: ListenerRegistration? = null

        val primaryReg = db.collection("communityMessages")
            .whereEqualTo("communityId", communityId)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("Firestore", "Community messages ordered query failed: ${error.message}")
                    if (!useFallback) {
                        useFallback = true
                        fallbackReg = db.collection("communityMessages")
                            .whereEqualTo("communityId", communityId)
                            .addSnapshotListener { fbSnap, fbErr ->
                                if (fbErr != null || fbSnap == null) return@addSnapshotListener
                                onUpdate(parseDocs(fbSnap.documents).sortedBy { it.timestamp })
                            }
                    }
                    return@addSnapshotListener
                }
                onUpdate(parseDocs(snapshot?.documents ?: emptyList()))
            }

        return object : ListenerRegistration {
            override fun remove() { primaryReg.remove(); fallbackReg?.remove() }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // User Discovery
    // ═══════════════════════════════════════════════════════════════════

    /** Fetch all registered users from Firestore. */
    suspend fun getAllUsers(): Result<List<UserItem>> {
        return try {
            val snapshot = db.collection("users").get().await()
            val users = snapshot.documents.mapNotNull { doc ->
                try {
                    UserItem(
                        uid = doc.id,
                        name = doc.getString("name") ?: "",
                        email = doc.getString("email") ?: ""
                    )
                } catch (e: Exception) { null }
            }
            Result.success(users)
        } catch (e: Exception) {
            Log.e("Firestore", "getAllUsers failed: ${e.message}")
            Result.failure(e)
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Private Chat
    // ═══════════════════════════════════════════════════════════════════

    fun listenForPrivateChats(onUpdate: (List<PrivateChat>) -> Unit): ListenerRegistration {
        val userId = uid ?: ""
        return db.collection("privateChats")
            .whereArrayContains("participants", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("Firestore", "Private chats listen failed", error)
                    return@addSnapshotListener
                }
                val chats = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        @Suppress("UNCHECKED_CAST")
                        val participants = doc.get("participants") as? List<String> ?: emptyList()
                        val otherUid = participants.firstOrNull { it != userId } ?: ""
                        val ts = doc.getTimestamp("lastTimestamp")?.toDate()?.time
                            ?: doc.getLong("lastTimestamp") ?: 0L
                        PrivateChat(
                            id = doc.id,
                            participants = participants,
                            lastMessage = doc.getString("lastMessage") ?: "",
                            lastTimestamp = ts,
                            otherUserName = doc.getString("otherName_$userId") ?: otherUid
                        )
                    } catch (e: Exception) { null }
                } ?: emptyList()
                onUpdate(chats.sortedByDescending { it.lastTimestamp })
            }
    }

    suspend fun getOrCreatePrivateChat(otherUserId: String, otherUserName: String): Result<String> {
        val userId = uid ?: return Result.failure(Exception("Not logged in"))
        return try {
            // Check if chat already exists
            val existing = db.collection("privateChats")
                .whereArrayContains("participants", userId)
                .get().await()
            val found = existing.documents.firstOrNull { doc ->
                @Suppress("UNCHECKED_CAST")
                val p = doc.get("participants") as? List<String> ?: emptyList()
                p.contains(otherUserId)
            }
            if (found != null) return Result.success(found.id)

            val myName = try {
                db.collection("users").document(userId).get().await()
                    .getString("name") ?: "User"
            } catch (e: Exception) { "User" }

            val doc = db.collection("privateChats").add(hashMapOf(
                "participants" to listOf(userId, otherUserId),
                "lastMessage" to "",
                "lastTimestamp" to FieldValue.serverTimestamp(),
                "otherName_$userId" to otherUserName,
                "otherName_$otherUserId" to myName
            )).await()
            Result.success(doc.id)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun sendPrivateMessage(chatId: String, messageText: String): Result<String> {
        val user = auth.currentUser ?: return Result.failure(Exception("Not logged in"))
        return try {
            val senderName = try {
                db.collection("users").document(user.uid).get().await()
                    .getString("name") ?: (user.email ?: "Anonymous")
            } catch (e: Exception) { user.email ?: "Anonymous" }

            db.collection("privateMessages").add(hashMapOf(
                "chatId" to chatId,
                "senderId" to user.uid,
                "senderName" to senderName,
                "messageText" to messageText,
                "timestamp" to FieldValue.serverTimestamp()
            )).await()

            // Update last message
            db.collection("privateChats").document(chatId).update(
                mapOf(
                    "lastMessage" to messageText,
                    "lastTimestamp" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(chatId)
        } catch (e: Exception) { Result.failure(e) }
    }

    fun listenForPrivateMessages(chatId: String, onUpdate: (List<Message>) -> Unit): ListenerRegistration {
        fun parseDocs(docs: List<com.google.firebase.firestore.DocumentSnapshot>): List<Message> {
            return docs.mapNotNull { doc ->
                try {
                    val ts = doc.getTimestamp("timestamp")?.toDate()?.time
                        ?: doc.getLong("timestamp") ?: System.currentTimeMillis()
                    Message(
                        id = doc.id,
                        senderId = doc.getString("senderId") ?: "",
                        senderName = doc.getString("senderName") ?: "",
                        messageText = doc.getString("messageText") ?: "",
                        roomId = chatId,
                        timestamp = ts
                    )
                } catch (e: Exception) { null }
            }
        }

        var useFallback = false
        var fallbackReg: ListenerRegistration? = null

        val primaryReg = db.collection("privateMessages")
            .whereEqualTo("chatId", chatId)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("Firestore", "Private messages ordered query failed: ${error.message}")
                    if (!useFallback) {
                        useFallback = true
                        fallbackReg = db.collection("privateMessages")
                            .whereEqualTo("chatId", chatId)
                            .addSnapshotListener { fbSnap, fbErr ->
                                if (fbErr != null || fbSnap == null) return@addSnapshotListener
                                onUpdate(parseDocs(fbSnap.documents).sortedBy { it.timestamp })
                            }
                    }
                    return@addSnapshotListener
                }
                onUpdate(parseDocs(snapshot?.documents ?: emptyList()))
            }

        return object : ListenerRegistration {
            override fun remove() { primaryReg.remove(); fallbackReg?.remove() }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Articles
    // ═══════════════════════════════════════════════════════════════════

    fun listenForArticles(onUpdate: (List<Article>) -> Unit): ListenerRegistration {
        return db.collection("articles")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("Firestore", "Articles listen failed", error)
                    return@addSnapshotListener
                }
                val articles = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        val ts = doc.getTimestamp("timestamp")?.toDate()?.time
                            ?: doc.getLong("timestamp") ?: 0L
                        Article(
                            id = doc.id,
                            title = doc.getString("title") ?: "",
                            content = doc.getString("content") ?: "",
                            authorName = doc.getString("authorName") ?: "",
                            timestamp = ts,
                            category = doc.getString("category") ?: "",
                            imageUrl = doc.getString("imageUrl")
                        )
                    } catch (e: Exception) { null }
                } ?: emptyList()
                onUpdate(articles)
            }
    }

    suspend fun createArticle(title: String, content: String, category: String): Result<String> {
        val user = auth.currentUser ?: return Result.failure(Exception("Not logged in"))
        return try {
            val authorName = try {
                db.collection("users").document(user.uid).get().await()
                    .getString("name") ?: (user.email ?: "Anonymous")
            } catch (e: Exception) { user.email ?: "Anonymous" }

            val doc = db.collection("articles").add(hashMapOf(
                "title" to title,
                "content" to content,
                "authorName" to authorName,
                "category" to category,
                "timestamp" to FieldValue.serverTimestamp(),
                "userId" to user.uid
            )).await()
            Result.success(doc.id)
        } catch (e: Exception) { Result.failure(e) }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Seed Demo Data
    // ═══════════════════════════════════════════════════════════════════

    suspend fun seedDemoDataIfEmpty() {
        try {
            // Seed communities
            val commSnap = db.collection("communities").limit(1).get().await()
            if (commSnap.isEmpty) {
                val communities = listOf(
                    hashMapOf("name" to "Chennai Safety Network", "description" to "Stay safe in Chennai. Share alerts and updates.", "createdAt" to System.currentTimeMillis(), "memberCount" to 0),
                    hashMapOf("name" to "Women Night Travel Safety", "description" to "Tips and alerts for safe night travel.", "createdAt" to System.currentTimeMillis(), "memberCount" to 0),
                    hashMapOf("name" to "College Safety", "description" to "Safety network for college students.", "createdAt" to System.currentTimeMillis(), "memberCount" to 0),
                    hashMapOf("name" to "Public Transport Safety", "description" to "Report and discuss public transport safety.", "createdAt" to System.currentTimeMillis(), "memberCount" to 0)
                )
                for (c in communities) { db.collection("communities").add(c).await() }
                Log.d("Firestore", "Seeded demo communities")
            }

            // Seed articles
            val artSnap = db.collection("articles").limit(1).get().await()
            if (artSnap.isEmpty) {
                val articles = listOf(
                    hashMapOf("title" to "5 Safety Tips for Night Travel", "content" to "1. Always share your live location with a trusted contact.\n2. Avoid isolated streets and poorly lit areas.\n3. Keep your phone charged and emergency numbers saved.\n4. Trust your instincts — if something feels wrong, leave.\n5. Use verified ride services and share trip details.", "authorName" to "WCN Team", "category" to "Safety Tips", "timestamp" to FieldValue.serverTimestamp(), "userId" to "system"),
                    hashMapOf("title" to "Emergency Contacts Every Woman Should Know", "content" to "Women Helpline: 181\nPolice: 100\nAmbulance: 108\nNational Commission for Women: 7827-170-170\nCyber Crime: 1930\n\nSave these numbers and share with friends and family.", "authorName" to "WCN Team", "category" to "Safety Tips", "timestamp" to FieldValue.serverTimestamp(), "userId" to "system"),
                    hashMapOf("title" to "Recent Safety Updates in Chennai", "content" to "The Chennai Police have increased patrolling in key areas. New CCTV cameras installed at bus stops across the city. Women-only buses now available on 5 new routes. Download the WCN app to report unsafe areas and get real-time alerts.", "authorName" to "WCN Team", "category" to "News", "timestamp" to FieldValue.serverTimestamp(), "userId" to "system")
                )
                for (a in articles) { db.collection("articles").add(a).await() }
                Log.d("Firestore", "Seeded demo articles")
            }
        } catch (e: Exception) {
            Log.e("Firestore", "Seed data failed: ${e.message}")
        }
    }
}

