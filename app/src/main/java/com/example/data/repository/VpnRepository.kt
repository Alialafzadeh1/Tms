package com.example.data.repository

import android.content.Context
import com.example.data.model.AuthAuditLog
import com.example.data.model.ConfigOrigin
import com.example.data.model.FailoverPolicy
import com.example.data.model.UserProfile
import com.example.data.model.VpnConfig
import com.example.data.model.VpnServer
import com.example.data.security.LocalUserAuthManager
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
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

class VpnRepository(private val dbProvider: () -> FirebaseFirestore?) {

    constructor(db: FirebaseFirestore) : this({ db })

    constructor(context: Context) : this(createFirestoreProvider(context.applicationContext))

    companion object {
        private fun createFirestoreProvider(appCtx: Context): () -> FirebaseFirestore? = {
            runCatching {
                if (FirebaseApp.getApps(appCtx).isEmpty()) {
                    FirebaseApp.initializeApp(appCtx)
                }
                FirebaseFirestore.getInstance(
                    appCtx.getString(com.example.R.string.firestore_database_id)
                )
            }.getOrNull()
        }
    }

    private val db: FirebaseFirestore
        get() = dbProvider()
            ?: throw IllegalStateException("Cloud Firestore is unavailable on this device or session.")

    private val authOrNull: FirebaseAuth?
        get() = runCatching { Firebase.auth }.getOrNull()

    private val validIdRegex = Regex("^[a-zA-Z0-9_\\-]+$")

    private fun requireUserId(): String {
        return authOrNull?.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in before accessing TMS VPN Firestore.")
    }

    // --- VPN CONFIGURATIONS ---

    fun observeConfigs(): Flow<List<VpnConfig>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/vpn_configs"
        emitAll(
            db.collection("users").document(uid).collection("vpn_configs")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        VpnConfig::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && authOrNull?.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addConfig(config: VpnConfig): String {
        val uid = requireUserId()
        val collectionRef = db.collection("users").document(uid).collection("vpn_configs")
        val docRef = if (config.id.isNotBlank() && config.id.length <= 128 && validIdRegex.matches(config.id)) {
            collectionRef.document(config.id)
        } else {
            collectionRef.document()
        }
        val payload = mapOf(
            "userId" to uid,
            "name" to config.name.take(100).ifBlank { "Unnamed Node" },
            "protocol" to config.protocol.lowercase(),
            "server" to config.server.take(256),
            "port" to config.port.coerceIn(1, 65535),
            "uuidOrPassword" to config.uuidOrPassword.take(512),
            "transport" to config.transport.take(32),
            "security" to config.security.take(32),
            "sni" to config.sni.take(256),
            "pathOrServiceName" to config.pathOrServiceName.take(256),
            "rawConfig" to config.rawConfig.take(4096),
            "pingMs" to config.pingMs,
            "status" to config.status,
            "isFavorite" to config.isFavorite,
            "origin" to ConfigOrigin.fromString(config.origin).key,
            "subscriptionId" to config.subscriptionId.take(128),
            "endpointId" to config.endpointId.take(128),
            "allocationVersion" to config.allocationVersion.coerceAtLeast(0),
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
        val docRef = db.collection("users").document(uid).collection("vpn_configs").document(config.id)
        try {
            docRef.update(
                mapOf(
                    "name" to config.name.take(100),
                    "pingMs" to config.pingMs,
                    "status" to config.status,
                    "isFavorite" to config.isFavorite,
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

    suspend fun updateConfigPing(configId: String, pingMs: Int, status: String) {
        val uid = requireUserId()
        if (configId.isBlank() || !validIdRegex.matches(configId)) return
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
        require(configId.isNotBlank() && validIdRegex.matches(configId)) { "Invalid config ID" }
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

    /**
     * Batch-deletes multiple configuration documents belonging strictly to the signed-in user.
     */
    suspend fun deleteConfigsBatch(configIds: List<String>): Result<List<String>> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val validIds = configIds.filter { it.isNotBlank() && validIdRegex.matches(it) }.distinct()
        if (validIds.isEmpty()) return Result.success(emptyList())

        return try {
            val collectionRef = db.collection("users").document(uid).collection("vpn_configs")
            validIds.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { id -> batch.delete(collectionRef.document(id)) }
                batch.commit().await()
            }
            Result.success(validIds)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$uid/vpn_configs")
            Result.failure(e)
        }
    }

    // --- VPN SERVERS ---

    fun observeServers(): Flow<List<VpnServer>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/vpn_servers"
        emitAll(
            db.collection("users").document(uid).collection("vpn_servers")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        VpnServer::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && authOrNull?.currentUser?.uid == uid) {
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
                .map { snapshot ->
                    snapshot.toObjects(
                        FailoverPolicy::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && authOrNull?.currentUser?.uid == uid) {
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
                .map { snapshot ->
                    snapshot.toObject(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && authOrNull?.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeAllUserProfilesForAdmin(): Flow<List<UserProfile>> = flow {
        val uid = requireUserId()
        val path = "users"
        emitAll(
            db.collection("users")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && authOrNull?.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
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
            val isAdminAccount = LocalUserAuthManager.isAdminEmail(email)
            val payload = mapOf(
                "userId" to uid,
                "displayName" to (if (displayName.isNotBlank()) displayName else "TMS User"),
                "email" to (if (email.isNotBlank()) email else "$uid@tmsvpn.local"),
                "role" to if (isAdminAccount) "SUPER_ADMIN" else "user",
                "deviceCount" to 1,
                "maxDevices" to 10,
                "dataUsageBytes" to 0.0,
                "dataLimitBytes" to 500.0 * 1024 * 1024 * 1024,
                "subscriptionStatus" to "expired",
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
                .map { snapshot ->
                    snapshot.toObjects(
                        AuthAuditLog::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && authOrNull?.currentUser?.uid == uid) {
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
