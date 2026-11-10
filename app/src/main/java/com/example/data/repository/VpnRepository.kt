package com.example.data.repository

import android.content.Context
import com.example.data.model.AuthAuditLog
import com.example.data.model.FailoverPolicy
import com.example.data.model.OverviewMetrics
import com.example.data.model.UserProfile
import com.example.data.model.VpnConfig
import com.example.data.model.VpnServer
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class VpnRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(
                context.resources.getIdentifier("firestore_database_id", "string", context.packageName)
            )
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing TMS VPN Firestore.")
    }

    // --- VPN CONFIGURATIONS ---

    fun observeConfigs(): Flow<List<VpnConfig>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/vpn_configs"
        emitAll(
            db.collection("users").document(uid).collection("vpn_configs")
                .snapshots()
                .map { snapshot -> snapshot.toObjects(VpnConfig::class.java) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addConfig(config: VpnConfig): String {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("vpn_configs").document()
        val payload = mapOf(
            "userId" to uid,
            "name" to config.name,
            "protocol" to config.protocol,
            "server" to config.server,
            "port" to config.port,
            "uuidOrPassword" to config.uuidOrPassword,
            "transport" to config.transport,
            "security" to config.security,
            "sni" to config.sni,
            "pathOrServiceName" to config.pathOrServiceName,
            "rawConfig" to config.rawConfig,
            "pingMs" to config.pingMs,
            "status" to config.status,
            "isFavorite" to config.isFavorite,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateConfig(config: VpnConfig) {
        val uid = requireUserId()
        require(config.id.isNotBlank()) { "Config ID must not be blank for update" }
        requireNotNull(config.createdAt) { "Config createdAt must not be null for update" }
        val docRef = db.collection("users").document(uid).collection("vpn_configs").document(config.id)
        try {
            docRef.set(config.copy(updatedAt = null)).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun updateConfigPing(configId: String, pingMs: Int, status: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("vpn_configs").document(configId)
        try {
            docRef.update(
                mapOf(
                    "pingMs" to pingMs,
                    "status" to status,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteConfig(configId: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("vpn_configs").document(configId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- VPN SERVERS ---

    fun observeServers(): Flow<List<VpnServer>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/vpn_servers"
        emitAll(
            db.collection("users").document(uid).collection("vpn_servers")
                .snapshots()
                .map { snapshot -> snapshot.toObjects(VpnServer::class.java) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addServer(server: VpnServer): String {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("vpn_servers").document()
        val payload = mapOf(
            "userId" to uid,
            "name" to server.name,
            "country" to server.country,
            "countryCode" to server.countryCode,
            "host" to server.host,
            "port" to server.port,
            "protocol" to server.protocol,
            "status" to server.status,
            "latencyMs" to server.latencyMs,
            "uptimePercentage" to server.uptimePercentage,
            "bandwidthUsageGb" to server.bandwidthUsageGb,
            "bandwidthLimitGb" to server.bandwidthLimitGb,
            "activeUsers" to server.activeUsers,
            "stabilityScore" to server.stabilityScore,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateServer(server: VpnServer) {
        val uid = requireUserId()
        require(server.id.isNotBlank()) { "Server ID must not be blank" }
        requireNotNull(server.createdAt) { "Server createdAt must not be null" }
        val docRef = db.collection("users").document(uid).collection("vpn_servers").document(server.id)
        try {
            docRef.set(server.copy(updatedAt = null)).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun updateServerHealth(serverId: String, latencyMs: Int, status: String, stabilityScore: Int) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("vpn_servers").document(serverId)
        try {
            docRef.update(
                mapOf(
                    "latencyMs" to latencyMs,
                    "status" to status,
                    "stabilityScore" to stabilityScore,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteServer(serverId: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("vpn_servers").document(serverId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- FAILOVER POLICIES ---

    fun observeFailoverPolicies(): Flow<List<FailoverPolicy>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/failover_policies"
        emitAll(
            db.collection("users").document(uid).collection("failover_policies")
                .snapshots()
                .map { snapshot -> snapshot.toObjects(FailoverPolicy::class.java) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addFailoverPolicy(policy: FailoverPolicy): String {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("failover_policies").document()
        val payload = mapOf(
            "userId" to uid,
            "name" to policy.name,
            "primaryServerId" to policy.primaryServerId,
            "backupServerId" to policy.backupServerId,
            "latencyThresholdMs" to policy.latencyThresholdMs,
            "autoSwitchEnabled" to policy.autoSwitchEnabled,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteFailoverPolicy(policyId: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("failover_policies").document(policyId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- USER PROFILE ---

    fun observeUserProfile(): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snapshot -> snapshot.toObject(UserProfile::class.java) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureUserProfile(displayName: String, email: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        val snapshot = docRef.get().await()
        if (!snapshot.exists()) {
            val payload = mapOf(
                "userId" to uid,
                "displayName" to (if (displayName.isNotBlank()) displayName else "Admin Operator"),
                "email" to (if (email.isNotBlank()) email else "admin@tmsvpn.local"),
                "role" to "admin",
                "deviceCount" to 1,
                "maxDevices" to 5,
                "dataUsageBytes" to 0.0,
                "dataLimitBytes" to 100.0 * 1024 * 1024 * 1024,
                "subscriptionStatus" to "active",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
        }
    }

    // --- AUDIT LOGS ---

    fun observeAuditLogs(): Flow<List<AuthAuditLog>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/audit_logs"
        emitAll(
            db.collection("users").document(uid).collection("audit_logs")
                .snapshots()
                .map { snapshot -> snapshot.toObjects(AuthAuditLog::class.java) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addAuditLog(log: AuthAuditLog): String {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("audit_logs").document()
        val payload = mapOf(
            "userId" to uid,
            "action" to log.action,
            "role" to log.role,
            "module" to log.module,
            "status" to log.status,
            "ipOrDevice" to log.ipOrDevice,
            "details" to log.details,
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateUserRole(newRole: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        try {
            docRef.update(
                mapOf(
                    "role" to newRole,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun updateUserQuota(deviceCount: Int, maxDevices: Int, dataLimitBytes: Double) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        try {
            docRef.update(
                mapOf(
                    "deviceCount" to deviceCount,
                    "maxDevices" to maxDevices,
                    "dataLimitBytes" to dataLimitBytes,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }
}
