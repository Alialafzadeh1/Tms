package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AllocatedEndpointRecord
import com.example.data.model.AllocationHistoryRecord
import com.example.data.model.ConfigOrigin
import com.example.data.model.InventoryShortageAlert
import com.example.data.model.ManagedEndpoint
import com.example.data.model.PaymentRequestRecord
import com.example.data.model.PaymentStatus
import com.example.data.model.RotationAuditLog
import com.example.data.model.SubscriptionHistoryEvent
import com.example.data.model.SubscriptionPlanId
import com.example.data.model.SubscriptionStatus
import com.example.data.model.UserSubscription
import com.example.data.subscription.BatchRotationResult
import com.example.data.subscription.DedicatedIpAllocationEngine
import com.example.data.subscription.SubscriptionLifecycleEngine
import com.example.data.subscription.UserRotationOutcome
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Firestore Repository for Central Subscriptions, Endpoint Inventory, Private Allocations,
 * Daily Rotation Transactions, Payment Verification Requests, Shortage Alerts, and Audit Logs.
 */
class SubscriptionRepository(private val dbProvider: () -> FirebaseFirestore?) {

    constructor(db: FirebaseFirestore) : this({ db })

    constructor(context: Context) : this(createFirestoreProvider(context.applicationContext))

    companion object {
        private fun createFirestoreProvider(appCtx: Context): () -> FirebaseFirestore? = {
            runCatching {
                if (FirebaseApp.getApps(appCtx).isEmpty()) {
                    FirebaseApp.initializeApp(appCtx)
                }
                FirebaseFirestore.getInstance(
                    appCtx.getString(R.string.firestore_database_id)
                )
            }.getOrNull()
        }
    }

    private val db: FirebaseFirestore
        get() = dbProvider()
            ?: throw IllegalStateException("Cloud Firestore is unavailable on this device or session.")

    private val authOrNull: FirebaseAuth?
        get() = runCatching { Firebase.auth }.getOrNull()

    private fun requireUserId(): String {
        return authOrNull?.currentUser?.uid
            ?: throw IllegalStateException("Authenticated user required for subscription access.")
    }

    // --- USER STREAMS (STRICTLY OWNER-SCOPED) ---

    fun observeMySubscription(): Flow<UserSubscription?> = flow {
        val uid = requireUserId()
        val path = "subscriptions"
        emitAll(
            db.collection("subscriptions")
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        UserSubscription::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).firstOrNull()
                }
                .catch { error ->
                    rethrowOrLog(error, OperationType.LIST, path, uid)
                }
        )
    }

    fun observeMyAllocatedEndpoints(): Flow<List<AllocatedEndpointRecord>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/allocated_endpoints"
        emitAll(
            db.collection("users").document(uid).collection("allocated_endpoints")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        AllocatedEndpointRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).filter { it.allocationStatus == "ACTIVE" }
                }
                .catch { error ->
                    rethrowOrLog(error, OperationType.LIST, path, uid)
                }
        )
    }

    fun observeMySubscriptionHistory(): Flow<List<SubscriptionHistoryEvent>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/subscription_history"
        emitAll(
            db.collection("users").document(uid).collection("subscription_history")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SubscriptionHistoryEvent::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    rethrowOrLog(error, OperationType.LIST, path, uid)
                }
        )
    }

    fun observeMyAllocationHistory(): Flow<List<AllocationHistoryRecord>> = flow {
        val uid = requireUserId()
        observeAllocationHistoryForUser(uid).collect { emit(it) }
    }

    fun observeAllocationHistoryForUser(userId: String): Flow<List<AllocationHistoryRecord>> = flow {
        val actorUid = requireUserId()
        val path = "users/$userId/allocation_history"
        emitAll(
            db.collection("users").document(userId).collection("allocation_history")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(100)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        AllocationHistoryRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    rethrowOrLog(error, OperationType.LIST, path, actorUid)
                }
        )
    }

    fun observeMyPaymentRequests(): Flow<List<PaymentRequestRecord>> = flow {
        val uid = requireUserId()
        val path = "payment_requests"
        emitAll(
            db.collection("payment_requests")
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        PaymentRequestRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                }
                .catch { error ->
                    rethrowOrLog(error, OperationType.LIST, path, uid)
                }
        )
    }

    // --- ADMIN STREAMS ---

    fun observeAllSubscriptionsForAdmin(): Flow<List<UserSubscription>> = flow {
        val uid = requireUserId()
        val path = "subscriptions"
        emitAll(
            db.collection("subscriptions")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        UserSubscription::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt?.toDate()?.time ?: 0L }
                }
                .catch { error ->
                    rethrowOrLog(error, OperationType.LIST, path, uid)
                }
        )
    }

    fun observeEndpointInventoryForAdmin(): Flow<List<ManagedEndpoint>> = flow {
        val uid = requireUserId()
        val path = "endpoint_inventory"
        emitAll(
            db.collection("endpoint_inventory")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ManagedEndpoint::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.priorityTier }
                }
                .catch { error ->
                    rethrowOrLog(error, OperationType.LIST, path, uid)
                }
        )
    }

    fun observeAllPaymentRequestsForAdmin(): Flow<List<PaymentRequestRecord>> = flow {
        val uid = requireUserId()
        val path = "payment_requests"
        emitAll(
            db.collection("payment_requests")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        PaymentRequestRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                }
                .catch { error ->
                    rethrowOrLog(error, OperationType.LIST, path, uid)
                }
        )
    }

    fun observeShortageAlertsForAdmin(): Flow<List<InventoryShortageAlert>> = flow {
        val uid = requireUserId()
        val path = "shortage_alerts"
        emitAll(
            db.collection("shortage_alerts")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        InventoryShortageAlert::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                }
                .catch { error ->
                    rethrowOrLog(error, OperationType.LIST, path, uid)
                }
        )
    }

    fun observeRotationLogsForAdmin(): Flow<List<RotationAuditLog>> = flow {
        val uid = requireUserId()
        val path = "rotation_logs"
        emitAll(
            db.collection("rotation_logs")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        RotationAuditLog::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    rethrowOrLog(error, OperationType.LIST, path, uid)
                }
        )
    }

    // --- USER PAYMENT SUBMISSION (UNCONFIRMED INTENT ONLY) ---

    suspend fun submitPaymentRequest(
        plan: SubscriptionPlanId,
        actionType: String,
        subscriptionId: String,
        referenceCode: String,
        note: String,
        userEmail: String
    ): Result<String> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docRef = db.collection("payment_requests").document()
        val status = if (referenceCode.isNotBlank()) {
            SubscriptionStatus.PENDING_PAYMENT.name
        } else {
            SubscriptionStatus.PENDING_PAYMENT.name
        }
        val payload = mapOf(
            "userId" to uid,
            "userEmail" to userEmail.take(256),
            "subscriptionId" to subscriptionId.take(128),
            "planId" to plan.id,
            "actionType" to actionType,
            "provider" to "TRUSTED_BACKEND_GATEWAY",
            "referenceCode" to referenceCode.trim().take(128),
            "status" to status,
            "note" to note.trim().take(512),
            "createdAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    // --- ADMIN / TRUSTED SERVER TIME SYNC ---

    /**
     * Fetches authoritative server timestamp by performing a small server-timestamp read/write
     * anchor or falling back to current epoch if admin doc not yet initialized.
     */
    suspend fun fetchTrustedServerEpochMs(fallbackMs: Long = System.currentTimeMillis()): Long {
        val uid = authOrNull?.currentUser?.uid ?: return fallbackMs
        return try {
            val anchorRef = db.collection("users").document(uid)
            val snap = anchorRef.get().await()
            snap.getTimestamp("updatedAt")?.toDate()?.time ?: fallbackMs
        } catch (_: Exception) {
            fallbackMs
        }
    }

    // --- ADMIN SUBSCRIPTION LIFECYCLE OPERATIONS ---

    suspend fun adminActivateSubscription(
        targetUserId: String,
        targetUserEmail: String,
        plan: SubscriptionPlanId,
        paymentRequestId: String? = null,
        trustedNowMs: Long
    ): Result<UserSubscription> {
        val adminUid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val subId = "sub_$targetUserId"
        val subRef = db.collection("subscriptions").document(subId)

        return try {
            val existingSnap = subRef.get().await()
            val existing = existingSnap.toObject(UserSubscription::class.java)
            val (activated, event) = SubscriptionLifecycleEngine.activateAfterPaymentConfirmation(
                subscriptionId = subId,
                userId = targetUserId,
                userEmail = targetUserEmail,
                plan = plan,
                actorId = adminUid,
                trustedServerNowMs = trustedNowMs,
                existingSubscription = existing
            )

            val userRef = db.collection("users").document(targetUserId)
            val existingUserSnap = userRef.get().await()

            db.runTransaction { tx ->
                val map = subscriptionWriteMap(activated, includeCreatedAt = !existingSnap.exists())
                tx.set(subRef, map, SetOptions.merge())

                val historyRef = db.collection("users").document(targetUserId)
                    .collection("subscription_history").document()
                tx.set(historyRef, historyEventMap(event))

                if (existingUserSnap.exists()) {
                    tx.update(
                        userRef,
                        mapOf(
                            "subscriptionStatus" to "active",
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    tx.set(
                        userRef,
                        mapOf(
                            "userId" to targetUserId,
                            "displayName" to targetUserEmail.substringBefore("@").ifBlank { "TMS User" },
                            "email" to targetUserEmail.ifBlank { "$targetUserId@tmsvpn.local" },
                            "role" to "user",
                            "subscriptionStatus" to "active",
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                if (!paymentRequestId.isNullOrBlank()) {
                    val payRef = db.collection("payment_requests").document(paymentRequestId)
                    tx.update(
                        payRef,
                        mapOf(
                            "status" to PaymentStatus.CONFIRMED.name,
                            "verifiedAt" to FieldValue.serverTimestamp(),
                            "verifiedBy" to adminUid
                        )
                    )
                }
            }.await()

            Result.success(activated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, subRef.path)
            Result.failure(e)
        }
    }

    suspend fun adminRenewSubscription(
        subscription: UserSubscription,
        plan: SubscriptionPlanId = subscription.planEnum(),
        trustedNowMs: Long
    ): Result<UserSubscription> {
        val adminUid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val (renewed, event) = SubscriptionLifecycleEngine.renew(
            existing = subscription,
            plan = plan,
            actorId = adminUid,
            trustedServerNowMs = trustedNowMs
        )
        return commitSubscriptionTransition(renewed, event, "active")
    }

    suspend fun adminChangePlan(
        subscription: UserSubscription,
        newPlan: SubscriptionPlanId,
        trustedNowMs: Long
    ): Result<UserSubscription> {
        val adminUid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val (changed, event) = SubscriptionLifecycleEngine.changePlan(
            existing = subscription,
            newPlan = newPlan,
            actorId = adminUid,
            trustedServerNowMs = trustedNowMs
        )
        return commitSubscriptionTransition(changed, event, "active")
    }

    suspend fun adminSetSubscriptionStatus(
        subscription: UserSubscription,
        newStatus: SubscriptionStatus,
        reason: String,
        trustedNowMs: Long
    ): Result<UserSubscription> {
        val adminUid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val (updated, event) = SubscriptionLifecycleEngine.updateStatus(
            existing = subscription,
            newStatus = newStatus,
            actorId = adminUid,
            reason = reason,
            trustedServerNowMs = trustedNowMs
        )
        val profileStatus = if (newStatus == SubscriptionStatus.ACTIVE) "active" else "expired"
        return commitSubscriptionTransition(updated, event, profileStatus)
    }

    suspend fun adminRejectPaymentRequest(paymentId: String, reason: String): Result<Unit> {
        val adminUid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val payRef = db.collection("payment_requests").document(paymentId)
        return try {
            payRef.update(
                mapOf(
                    "status" to "REJECTED",
                    "note" to reason.take(512),
                    "verifiedAt" to FieldValue.serverTimestamp(),
                    "verifiedBy" to adminUid
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, payRef.path)
            Result.failure(e)
        }
    }

    private suspend fun commitSubscriptionTransition(
        subscription: UserSubscription,
        event: SubscriptionHistoryEvent,
        profileSubStatus: String
    ): Result<UserSubscription> {
        val subId = subscription.subscriptionId.ifBlank { "sub_${subscription.userId}" }
        val subRef = db.collection("subscriptions").document(subId)
        val userRef = db.collection("users").document(subscription.userId)
        return try {
            val userSnap = userRef.get().await()
            db.runTransaction { tx ->
                tx.set(subRef, subscriptionWriteMap(subscription, includeCreatedAt = false), SetOptions.merge())
                val historyRef = db.collection("users").document(subscription.userId)
                    .collection("subscription_history").document()
                tx.set(historyRef, historyEventMap(event))
                if (userSnap.exists()) {
                    tx.update(
                        userRef,
                        mapOf(
                            "subscriptionStatus" to profileSubStatus,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    tx.set(
                        userRef,
                        mapOf(
                            "userId" to subscription.userId,
                            "displayName" to subscription.userEmail.substringBefore("@").ifBlank { "TMS User" },
                            "email" to subscription.userEmail.ifBlank { "${subscription.userId}@tmsvpn.local" },
                            "role" to "user",
                            "subscriptionStatus" to profileSubStatus,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()
            Result.success(subscription)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, subRef.path)
            Result.failure(e)
        }
    }

    // --- CENTRAL ENDPOINT INVENTORY MANAGEMENT ---

    suspend fun adminUpsertEndpoint(endpoint: ManagedEndpoint): Result<String> {
        try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val col = db.collection("endpoint_inventory")
        val docRef = if (endpoint.endpointId.isNotBlank()) col.document(endpoint.endpointId) else col.document()
        return try {
            val isNew = endpoint.endpointId.isBlank() ||
                (endpoint.createdAt == null && !docRef.get().await().exists())
            val payload = endpointWriteMap(endpoint, includeCreatedAt = isNew)
            docRef.set(payload, SetOptions.merge()).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun adminToggleEndpointHealth(endpoint: ManagedEndpoint, enabled: Boolean, healthy: Boolean): Result<Unit> {
        try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docRef = db.collection("endpoint_inventory").document(endpoint.endpointId)
        return try {
            docRef.update(
                mapOf(
                    "enabled" to enabled,
                    "healthy" to healthy,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun adminResolveShortageAlert(alertId: String): Result<Unit> {
        try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docRef = db.collection("shortage_alerts").document(alertId)
        return try {
            docRef.update("resolved", true).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    /**
     * Executes backend-aligned atomic Firestore transactions for dedicated IP allocation and rotation.
     * Each user allocation reads `/subscriptions/{subId}` and all candidate/owned `/endpoint_inventory/{epId}`
     * documents INSIDE `db.runTransaction` before mutating locks, ensuring Firestore MVCC prevents
     * double-assignment across concurrent requests while writing `allocation_history` atomically.
     */
    suspend fun executeRotationTransaction(
        targetSubscriptions: List<UserSubscription>,
        activeEndpointByUser: Map<String, String?>,
        triggerMode: String,
        trustedNowMs: Long,
        forceManual: Boolean
    ): Result<BatchRotationResult> {
        val adminUid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        if (targetSubscriptions.isEmpty()) {
            return Result.failure(IllegalArgumentException("No subscriptions targeted for rotation"))
        }

        return try {
            val cycleKey = SubscriptionLifecycleEngine.utcCycleKey(trustedNowMs)
            // Higher-priority plans (1Y -> 3M -> 1M) claim healthy endpoints first
            val orderedSubs = targetSubscriptions.sortedByDescending { it.planEnum().priorityLevel }

            val updatedSubs = mutableListOf<UserSubscription>()
            val allocsMap = mutableMapOf<String, List<AllocatedEndpointRecord>>()
            val rotatedOutMap = mutableMapOf<String, List<AllocatedEndpointRecord>>()
            val historyMap = mutableMapOf<String, List<AllocationHistoryRecord>>()
            val shortages = mutableListOf<InventoryShortageAlert>()
            var latestInventory: List<ManagedEndpoint> = emptyList()

            var rotatedUsers = 0
            var replacedTotal = 0

            for (sub in orderedSubs) {
                val userOutcome = executeAtomicUserAllocationTransaction(
                    targetSubscription = sub,
                    activeConnectedEndpointId = activeEndpointByUser[sub.userId],
                    actorUid = adminUid,
                    trustedNowMs = trustedNowMs,
                    forceManual = forceManual
                )
                updatedSubs.add(userOutcome.updatedSubscription)
                allocsMap[sub.userId] = userOutcome.activeAllocations
                rotatedOutMap[sub.userId] = userOutcome.rotatedOutAllocations
                if (userOutcome.allocationHistoryEvents.isNotEmpty()) {
                    historyMap[sub.userId] = userOutcome.allocationHistoryEvents
                }
                if (userOutcome.shortageAlert != null) {
                    shortages.add(userOutcome.shortageAlert)
                }
                if (!userOutcome.skippedIdempotent) {
                    rotatedUsers++
                    replacedTotal += userOutcome.replacedCount
                }
                latestInventory = userOutcome.updatedInventory
            }

            check(DedicatedIpAllocationEngine.verifyZeroDoubleAssignment(allocsMap)) {
                "Invariant violated: duplicate dedicated endpoint assignment detected after transactions"
            }

            val auditStatus = when {
                shortages.isNotEmpty() -> "PARTIAL_SHORTAGE"
                rotatedUsers == 0 && orderedSubs.isNotEmpty() -> "SKIPPED_IDEMPOTENT"
                else -> "SUCCESS"
            }
            val audit = RotationAuditLog(
                cycleKey = cycleKey,
                triggerMode = triggerMode,
                triggeredByUid = adminUid,
                targetUserCount = orderedSubs.size,
                rotatedUsersCount = rotatedUsers,
                replacedEndpointsCount = replacedTotal,
                shortageCount = shortages.size,
                status = auditStatus,
                details = "Cycle $cycleKey: targets=${orderedSubs.size}, rotated=$rotatedUsers, replaced=$replacedTotal, shortages=${shortages.size}",
                createdAt = SubscriptionLifecycleEngine.timestampOf(trustedNowMs)
            )

            val logRef = db.collection("rotation_logs").document()
            logRef.set(
                mapOf(
                    "cycleKey" to audit.cycleKey,
                    "triggerMode" to audit.triggerMode,
                    "triggeredByUid" to audit.triggeredByUid,
                    "targetUserCount" to audit.targetUserCount,
                    "rotatedUsersCount" to audit.rotatedUsersCount,
                    "replacedEndpointsCount" to audit.replacedEndpointsCount,
                    "shortageCount" to audit.shortageCount,
                    "status" to audit.status,
                    "details" to audit.details,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(
                BatchRotationResult(
                    updatedSubscriptions = updatedSubs,
                    updatedInventory = latestInventory,
                    allocationsByUser = allocsMap,
                    rotatedOutByUser = rotatedOutMap,
                    shortageAlerts = shortages,
                    auditLog = audit,
                    allocationHistoryByUser = historyMap
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "endpoint_inventory")
            Result.failure(e)
        }
    }

    /**
     * Atomically allocates or rotates dedicated endpoints for one user inside a single
     * Firestore MVCC transaction, performing live transactional `tx.get()` reads on
     * `/subscriptions/{subId}` and all candidate `/endpoint_inventory/{epId}` records so
     * concurrent workers cannot double-assign the same endpoint.
     */
    suspend fun executeAtomicUserAllocationTransaction(
        targetSubscription: UserSubscription,
        activeConnectedEndpointId: String?,
        actorUid: String,
        trustedNowMs: Long,
        forceManual: Boolean
    ): UserRotationOutcome {
        val uid = targetSubscription.userId
        val subId = targetSubscription.subscriptionId.ifBlank { "sub_$uid" }
        val subRef = db.collection("subscriptions").document(subId)
        val userCol = db.collection("users").document(uid)

        // Pre-discover document IDs to lock/read inside the transaction
        val inventorySnap = db.collection("endpoint_inventory").get().await()
        val preAllocsSnap = userCol.collection("allocated_endpoints")
            .whereEqualTo("allocationStatus", "ACTIVE")
            .get().await()
        val existingSubConfigIds = userCol.collection("vpn_configs")
            .whereEqualTo("origin", ConfigOrigin.SUBSCRIPTION_ASSIGNED.key)
            .get().await()
            .documents.map { it.id }.toSet()

        val preInventory = inventorySnap.toObjects(
            ManagedEndpoint::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
        )
        val preAllocs = preAllocsSnap.toObjects(
            AllocatedEndpointRecord::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
        )
        val userCredentialToken = preAllocs.firstOrNull { it.credentialToken.isNotBlank() }?.credentialToken
            ?: UUID.nameUUIDFromBytes("tms_$uid".toByteArray()).toString()

        // Limit transactional candidate reads cleanly to owned + top eligible unassigned candidates
        val maxReadCandidates = (targetSubscription.planEnum().dailyAllocationLimit * 2 + 20).coerceAtMost(140)
        val ownedEpIds = preAllocs.map { it.endpointId } +
            preInventory.filter { it.assignedUserId == uid }.map { it.endpointId }
        val freeCandidateIds = DedicatedIpAllocationEngine.selectEligiblePool(
            endpoints = preInventory,
            userId = uid,
            planPriority = targetSubscription.planEnum().priorityLevel,
            compatibleProtocols = setOf("vless", "vmess", "trojan", "shadowsocks"),
            trustedServerNowMs = trustedNowMs
        ).map { it.endpointId }.take(maxReadCandidates)
        val epIdsToReadInTx = (ownedEpIds + freeCandidateIds).distinct()
        val allocIdsToReadInTx = preAllocs.map { it.allocationId }.distinct()

        val outcome = db.runTransaction { tx ->
            // 1. TRANSACTIONAL READS (strictly before any writes)
            val liveSubSnap = tx.get(subRef)
            val liveSub = liveSubSnap.toObject(
                UserSubscription::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: targetSubscription

            val liveEpMap = mutableMapOf<String, ManagedEndpoint>()
            for (epId in epIdsToReadInTx) {
                val snap = tx.get(db.collection("endpoint_inventory").document(epId))
                if (snap.exists()) {
                    snap.toObject(
                        ManagedEndpoint::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )?.let { liveEpMap[epId] = it }
                }
            }

            val liveAllocs = mutableListOf<AllocatedEndpointRecord>()
            for (allocId in allocIdsToReadInTx) {
                val allocSnap = tx.get(userCol.collection("allocated_endpoints").document(allocId))
                if (allocSnap.exists()) {
                    allocSnap.toObject(
                        AllocatedEndpointRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )?.takeIf { it.allocationStatus == "ACTIVE" }?.let { liveAllocs.add(it) }
                }
            }

            // 2. PURE ENGINE EVALUATION ON LIVE TRANSACTIONAL SNAPSHOTS
            val txOutcome = DedicatedIpAllocationEngine.allocateOrRotateForUser(
                subscription = liveSub,
                inventory = liveEpMap.values.toList(),
                existingAllocations = liveAllocs,
                userCredentialToken = userCredentialToken,
                activeConnectedEndpointId = activeConnectedEndpointId,
                trustedServerNowMs = trustedNowMs,
                forceManualRotation = forceManual,
                actorId = actorUid
            )

            if (txOutcome.skippedIdempotent) {
                return@runTransaction txOutcome
            }

            // 3. ATOMIC WRITES WITH EXPLICIT DOUBLE-ASSIGNMENT DEFENSE
            for (ep in txOutcome.updatedInventory) {
                val prev = liveEpMap[ep.endpointId]
                if (prev == null || prev.assignedUserId != ep.assignedUserId || prev.assignedSubscriptionId != ep.assignedSubscriptionId) {
                    if (ep.assignedUserId == uid && prev != null) {
                        check(prev.assignedUserId.isBlank() || prev.assignedUserId == uid) {
                            "Atomic exclusivity violation: endpoint ${ep.endpointId} already assigned to ${prev.assignedUserId}"
                        }
                    }
                    val epRef = db.collection("endpoint_inventory").document(ep.endpointId)
                    tx.update(
                        epRef,
                        mapOf(
                            "assignedUserId" to ep.assignedUserId,
                            "assignedSubscriptionId" to ep.assignedSubscriptionId,
                            "allocationExpiresAt" to ep.allocationExpiresAt,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }

            tx.set(subRef, subscriptionWriteMap(txOutcome.updatedSubscription, includeCreatedAt = false), SetOptions.merge())

            for (alloc in txOutcome.activeAllocations) {
                val allocRef = userCol.collection("allocated_endpoints").document(alloc.allocationId)
                tx.set(allocRef, allocatedEndpointMap(alloc))
            }

            for (old in txOutcome.rotatedOutAllocations) {
                val oldRef = userCol.collection("allocated_endpoints").document(old.allocationId)
                tx.update(
                    oldRef,
                    mapOf(
                        "allocationStatus" to old.allocationStatus,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            for (hist in txOutcome.allocationHistoryEvents) {
                val histRef = userCol.collection("allocation_history").document(hist.historyId)
                tx.set(histRef, allocationHistoryMap(hist))
            }

            txOutcome.shortageAlert?.let { alert ->
                val alertRef = db.collection("shortage_alerts").document(alert.alertId)
                tx.set(
                    alertRef,
                    mapOf(
                        "userId" to alert.userId,
                        "subscriptionId" to alert.subscriptionId,
                        "planId" to alert.planId,
                        "requestedCount" to alert.requestedCount,
                        "allocatedCount" to alert.allocatedCount,
                        "shortfallCount" to alert.shortfallCount,
                        "cycleKey" to alert.cycleKey,
                        "reason" to alert.reason,
                        "resolved" to alert.resolved,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            txOutcome
        }.await()

        // 4. Sync SUBSCRIPTION_ASSIGNED entries inside users/{uid}/vpn_configs cleanly
        if (!outcome.skippedIdempotent) {
            val cfgBatch = db.batch()
            var hasCfgOps = false
            for (alloc in outcome.activeAllocations) {
                val cfg = alloc.toVpnConfig()
                val cfgRef = userCol.collection("vpn_configs").document(cfg.id)
                val cfgMap = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "name" to cfg.name.take(100),
                    "protocol" to cfg.protocol,
                    "server" to cfg.server,
                    "port" to cfg.port,
                    "uuidOrPassword" to cfg.uuidOrPassword,
                    "transport" to cfg.transport,
                    "security" to cfg.security,
                    "sni" to cfg.sni,
                    "pathOrServiceName" to cfg.pathOrServiceName,
                    "rawConfig" to cfg.rawConfig,
                    "pingMs" to cfg.pingMs,
                    "status" to cfg.status,
                    "isFavorite" to false,
                    "origin" to ConfigOrigin.SUBSCRIPTION_ASSIGNED.key,
                    "subscriptionId" to cfg.subscriptionId,
                    "endpointId" to cfg.endpointId,
                    "allocationVersion" to cfg.allocationVersion,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (cfg.id in existingSubConfigIds) {
                    cfgBatch.set(cfgRef, cfgMap, SetOptions.merge())
                } else {
                    cfgMap["createdAt"] = FieldValue.serverTimestamp()
                    cfgBatch.set(cfgRef, cfgMap)
                }
                hasCfgOps = true
            }
            for (old in outcome.rotatedOutAllocations) {
                val oldCfgId = "sub_${old.subscriptionId}_${old.endpointId}"
                if (oldCfgId in existingSubConfigIds) {
                    cfgBatch.delete(userCol.collection("vpn_configs").document(oldCfgId))
                    hasCfgOps = true
                }
            }
            if (hasCfgOps) {
                cfgBatch.commit().await()
            }
        }

        return outcome
    }

    /**
     * Atomically records an endpoint health/connection failure in `/endpoint_inventory/{endpointId}`,
     * applies temporary cooldown & releases exclusivity when threshold is reached, writes a
     * `COOLDOWN_EXCLUDED` audit entry into `/users/{userId}/allocation_history`, and triggers
     * healthy replacement allocation when an active subscription is supplied.
     */
    suspend fun recordEndpointFailureAndFailover(
        endpointId: String,
        targetUserId: String,
        subscription: UserSubscription?,
        activeConnectedEndpointId: String?,
        trustedNowMs: Long
    ): Result<UserRotationOutcome?> {
        val actorUid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val epRef = db.collection("endpoint_inventory").document(endpointId)
        val userCol = db.collection("users").document(targetUserId)

        return try {
            val updatedEp = db.runTransaction { tx ->
                val snap = tx.get(epRef)
                val current = snap.toObject(
                    ManagedEndpoint::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: throw IllegalStateException("Endpoint $endpointId not found")

                val next = DedicatedIpAllocationEngine.recordEndpointFailure(
                    endpoint = current,
                    trustedServerNowMs = trustedNowMs
                )
                tx.update(
                    epRef,
                    mapOf(
                        "healthy" to next.healthy,
                        "failureCount" to next.failureCount,
                        "cooldownUntil" to next.cooldownUntil,
                        "assignedUserId" to next.assignedUserId,
                        "assignedSubscriptionId" to next.assignedSubscriptionId,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                val cycleKey = SubscriptionLifecycleEngine.utcCycleKey(trustedNowMs)
                val hist = AllocationHistoryRecord(
                    historyId = "hist_fail_${endpointId}_${trustedNowMs}",
                    userId = targetUserId,
                    subscriptionId = subscription?.subscriptionId ?: current.assignedSubscriptionId,
                    planId = subscription?.planId ?: "",
                    endpointId = endpointId,
                    endpointName = current.name,
                    host = current.host,
                    port = current.port,
                    protocol = current.protocol,
                    action = "COOLDOWN_EXCLUDED",
                    allocationVersion = subscription?.currentAllocationVersion ?: 1,
                    cycleKey = cycleKey,
                    reason = "Failure #${next.failureCount} recorded; cooldown applied",
                    actorId = actorUid,
                    createdAt = SubscriptionLifecycleEngine.timestampOf(trustedNowMs)
                )
                tx.set(userCol.collection("allocation_history").document(hist.historyId), allocationHistoryMap(hist))
                next
            }.await()

            val failoverOutcome = if (subscription != null && (!updatedEp.healthy || updatedEp.cooldownUntil != null)) {
                executeAtomicUserAllocationTransaction(
                    targetSubscription = subscription,
                    activeConnectedEndpointId = activeConnectedEndpointId,
                    actorUid = actorUid,
                    trustedNowMs = trustedNowMs,
                    forceManual = false
                )
            } else {
                null
            }
            Result.success(failoverOutcome)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, epRef.path)
            Result.failure(e)
        }
    }

    // --- SERIALIZATION HELPERS (NULL-FILTERED, PARITY-ALIGNED) ---

    private fun subscriptionWriteMap(sub: UserSubscription, includeCreatedAt: Boolean): Map<String, Any> {
        val map = mutableMapOf<String, Any?>(
            "userId" to sub.userId,
            "userEmail" to sub.userEmail,
            "planId" to sub.planId,
            "planDurationDays" to sub.planDurationDays,
            "endpointAllocationLimit" to sub.endpointAllocationLimit,
            "startDate" to sub.startDate,
            "expirationDate" to sub.expirationDate,
            "status" to sub.status,
            "paymentStatus" to sub.paymentStatus,
            "lastDailyRotation" to sub.lastDailyRotation,
            "lastRotationCycleKey" to sub.lastRotationCycleKey,
            "currentAllocationVersion" to sub.currentAllocationVersion,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (includeCreatedAt) {
            map["createdAt"] = FieldValue.serverTimestamp()
        }
        return map.filterValues { it != null }.mapValues { it.value!! }
    }

    private fun endpointWriteMap(ep: ManagedEndpoint, includeCreatedAt: Boolean): Map<String, Any> {
        val map = mutableMapOf<String, Any?>(
            "name" to ep.name.take(120),
            "country" to ep.country.take(100),
            "countryCode" to ep.countryCode.take(8),
            "host" to ep.host.take(256),
            "port" to ep.port.coerceIn(1, 65535),
            "protocol" to ep.protocol.lowercase(),
            "transport" to ep.transport.take(32),
            "security" to ep.security.take(32),
            "sni" to ep.sni.take(256),
            "pathOrServiceName" to ep.pathOrServiceName.take(256),
            "enabled" to ep.enabled,
            "healthy" to ep.healthy,
            "priorityTier" to ep.priorityTier.coerceIn(1, 3),
            "latencyMs" to ep.latencyMs,
            "stabilityScore" to ep.stabilityScore.coerceIn(0, 100),
            "assignedUserId" to ep.assignedUserId,
            "assignedSubscriptionId" to ep.assignedSubscriptionId,
            "allocationExpiresAt" to ep.allocationExpiresAt,
            "failureCount" to ep.failureCount.coerceAtLeast(0),
            "cooldownUntil" to ep.cooldownUntil,
            "lastHealthCheckAt" to ep.lastHealthCheckAt,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (includeCreatedAt) {
            map["createdAt"] = FieldValue.serverTimestamp()
        }
        return map.filterValues { it != null }.mapValues { it.value!! }
    }

    private fun allocatedEndpointMap(rec: AllocatedEndpointRecord): Map<String, Any> {
        val map = mutableMapOf<String, Any?>(
            "userId" to rec.userId,
            "subscriptionId" to rec.subscriptionId,
            "endpointId" to rec.endpointId,
            "endpointName" to rec.endpointName.take(120),
            "country" to rec.country.take(100),
            "countryCode" to rec.countryCode.take(8),
            "host" to rec.host.take(256),
            "port" to rec.port.coerceIn(1, 65535),
            "protocol" to rec.protocol.lowercase(),
            "transport" to rec.transport.take(32),
            "security" to rec.security.take(32),
            "sni" to rec.sni.take(256),
            "pathOrServiceName" to rec.pathOrServiceName.take(256),
            "credentialToken" to rec.credentialToken.take(512),
            "rawConfig" to rec.rawConfig.take(4096),
            "allocationVersion" to rec.allocationVersion,
            "cycleKey" to rec.cycleKey.take(32),
            "allocationStatus" to rec.allocationStatus,
            "healthy" to rec.healthy,
            "latencyMs" to rec.latencyMs,
            "allocatedAt" to (rec.allocatedAt ?: FieldValue.serverTimestamp()),
            "expiresAt" to rec.expiresAt,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return map.filterValues { it != null }.mapValues { it.value!! }
    }

    private fun historyEventMap(ev: SubscriptionHistoryEvent): Map<String, Any> {
        return mapOf(
            "subscriptionId" to ev.subscriptionId,
            "userId" to ev.userId,
            "action" to ev.action,
            "fromPlanId" to ev.fromPlanId,
            "toPlanId" to ev.toPlanId,
            "statusAfter" to ev.statusAfter,
            "actorId" to ev.actorId,
            "details" to ev.details.take(512),
            "createdAt" to FieldValue.serverTimestamp()
        )
    }

    private fun allocationHistoryMap(rec: AllocationHistoryRecord): Map<String, Any> {
        return mapOf(
            "userId" to rec.userId,
            "subscriptionId" to rec.subscriptionId,
            "planId" to rec.planId,
            "endpointId" to rec.endpointId,
            "endpointName" to rec.endpointName.take(120),
            "host" to rec.host.take(256),
            "port" to rec.port.coerceIn(1, 65535),
            "protocol" to rec.protocol.lowercase(),
            "action" to rec.action,
            "allocationVersion" to rec.allocationVersion.coerceAtLeast(0),
            "cycleKey" to rec.cycleKey.take(32),
            "reason" to rec.reason.take(512),
            "actorId" to rec.actorId.take(128),
            "createdAt" to FieldValue.serverTimestamp()
        )
    }

    private fun rethrowOrLog(
        error: Throwable,
        operationType: OperationType,
        path: String,
        uid: String
    ): Nothing {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && authOrNull?.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
        throw error
    }
}
