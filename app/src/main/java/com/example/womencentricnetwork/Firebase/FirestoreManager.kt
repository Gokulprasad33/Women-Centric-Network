package com.example.womencentricnetwork.Firebase

import com.example.womencentricnetwork.Model.Incident
import com.example.womencentricnetwork.Model.Settings.EmergencyContactEntity
import com.example.womencentricnetwork.Model.Settings.SosEventEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
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
}

