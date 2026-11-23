package com.example.data.subscription

import com.example.data.model.AllocatedEndpointRecord
import com.example.data.model.AllocationHistoryRecord
import com.example.data.model.InventoryShortageAlert
import com.example.data.model.ManagedEndpoint
import com.example.data.model.RotationAuditLog
import com.example.data.model.UserSubscription
import java.util.UUID

data class UserRotationOutcome(
    val updatedSubscription: UserSubscription,
    val updatedInventory: List<ManagedEndpoint>,
    val activeAllocations: List<AllocatedEndpointRecord>,
    val rotatedOutAllocations: List<AllocatedEndpointRecord>,
    val shortageAlert: InventoryShortageAlert?,
    val skippedIdempotent: Boolean,
    val replacedCount: Int,
    val allocationHistoryEvents: List<AllocationHistoryRecord> = emptyList()
)

data class BatchRotationResult(
    val updatedSubscriptions: List<UserSubscription>,
    val updatedInventory: List<ManagedEndpoint>,
    val allocationsByUser: Map<String, List<AllocatedEndpointRecord>>,
    val rotatedOutByUser: Map<String, List<AllocatedEndpointRecord>>,
    val shortageAlerts: List<InventoryShortageAlert>,
    val auditLog: RotationAuditLog,
    val allocationHistoryByUser: Map<String, List<AllocationHistoryRecord>> = emptyMap()
)

/**
 * Central Dedicated IP Allocation & Idempotent Daily Rotation Engine.
 *
 * Guarantees:
 * 1. Plan-Scoped Quotas: strictly enforces 5 (1M), 15 (3M), or 50 (1Y) distinct endpoints.
 * 2. Exclusivity (Zero Double-Assignment): an endpoint in [inventory] may belong to at most
 *    one active `assignedUserId`; verified before commitment.
 * 3. Compatibility & Health: only enabled, healthy, compatible, non-cooldown endpoints are allocated.
 * 4. Active Tunnel Preservation: an active connected endpoint that remains healthy is preserved.
 * 5. Credential Isolation: user credentials stay inside private [AllocatedEndpointRecord]s only.
 * 6. Idempotency: re-running rotation within the same [cycleKey] does not create duplicate changes.
 * 7. Allocation History Tracking: emits immutable [AllocationHistoryRecord] events on every assign/rotate/revoke.
 * 8. Honest Shortage Handling: never fabricates fake endpoints or assigns broken endpoints.
 */
object DedicatedIpAllocationEngine {

    const val DEFAULT_COOLDOWN_MS = 6L * 60L * 60L * 1000L // 6 hours

    fun allocateOrRotateForUser(
        subscription: UserSubscription,
        inventory: List<ManagedEndpoint>,
        existingAllocations: List<AllocatedEndpointRecord>,
        userCredentialToken: String,
        compatibleProtocols: Set<String> = setOf("vless", "vmess", "trojan", "shadowsocks"),
        activeConnectedEndpointId: String? = null,
        trustedServerNowMs: Long,
        forceManualRotation: Boolean = false,
        actorId: String = "SYSTEM"
    ): UserRotationOutcome {
        val uid = subscription.userId
        val cycleKey = SubscriptionLifecycleEngine.utcCycleKey(trustedServerNowMs)
        val nowTs = SubscriptionLifecycleEngine.timestampOf(trustedServerNowMs)

        // Ensure expired or inactive subscriptions release allocations safely
        val (checkedSub, _) = SubscriptionLifecycleEngine.evaluateExpiration(subscription, trustedServerNowMs)
        if (!SubscriptionLifecycleEngine.isEntitled(checkedSub, trustedServerNowMs)) {
            val releasedInventory = inventory.map { ep ->
                if (ep.assignedUserId == uid) {
                    ep.copy(
                        assignedUserId = "",
                        assignedSubscriptionId = "",
                        allocationExpiresAt = null,
                        updatedAt = nowTs
                    )
                } else {
                    ep
                }
            }
            val currentActive = existingAllocations.filter { it.allocationStatus == "ACTIVE" }
            val revoked = currentActive.map { alloc ->
                alloc.copy(
                    allocationStatus = "REVOKED",
                    updatedAt = nowTs
                )
            }
            val revokedHistory = revoked.map { alloc ->
                AllocationHistoryRecord(
                    historyId = "hist_${checkedSub.subscriptionId}_${alloc.endpointId}_rev_${trustedServerNowMs}",
                    userId = uid,
                    subscriptionId = checkedSub.subscriptionId,
                    planId = checkedSub.planId,
                    endpointId = alloc.endpointId,
                    endpointName = alloc.endpointName,
                    host = alloc.host,
                    port = alloc.port,
                    protocol = alloc.protocol,
                    action = "REVOKED_EXPIRED",
                    allocationVersion = alloc.allocationVersion,
                    cycleKey = cycleKey,
                    reason = "Subscription inactive or expired (${checkedSub.status})",
                    actorId = actorId,
                    createdAt = nowTs
                )
            }
            return UserRotationOutcome(
                updatedSubscription = checkedSub,
                updatedInventory = releasedInventory,
                activeAllocations = emptyList(),
                rotatedOutAllocations = revoked,
                shortageAlert = null,
                skippedIdempotent = false,
                replacedCount = 0,
                allocationHistoryEvents = revokedHistory
            )
        }

        val planMax = checkedSub.planEnum().dailyAllocationLimit
        val targetLimit = minOf(checkedSub.endpointAllocationLimit, planMax).coerceIn(1, planMax)
        val planPriority = checkedSub.planEnum().priorityLevel
        val allocationExpMs = minOf(
            checkedSub.expirationDate?.toDate()?.time ?: (trustedServerNowMs + SubscriptionLifecycleEngine.MILLIS_PER_DAY),
            trustedServerNowMs + 2L * SubscriptionLifecycleEngine.MILLIS_PER_DAY
        )

        val currentActive = existingAllocations.filter { it.allocationStatus == "ACTIVE" }
        val previouslyActiveIds = currentActive.map { it.endpointId }.toSet()
        val invMap = inventory.associateBy { it.endpointId }.toMutableMap()

        // Evaluate which of the user's current active allocations remain valid, healthy, enabled, and compatible
        val validCurrent = mutableListOf<AllocatedEndpointRecord>()
        val failedOrRemovedIds = mutableSetOf<String>()
        val removalReasonByEp = mutableMapOf<String, String>()

        for (alloc in currentActive) {
            val ep = invMap[alloc.endpointId]
            val cooldownEndMs = ep?.cooldownUntil?.toDate()?.time ?: 0L
            val stillValid = ep != null &&
                ep.enabled &&
                ep.healthy &&
                cooldownEndMs <= trustedServerNowMs &&
                (ep.assignedUserId == uid || ep.assignedUserId.isBlank()) &&
                ep.protocol.lowercase() in compatibleProtocols.map { it.lowercase() }

            if (stillValid) {
                validCurrent.add(alloc)
            } else {
                failedOrRemovedIds.add(alloc.endpointId)
                removalReasonByEp[alloc.endpointId] = when {
                    ep == null -> "REPLACED_FAILED"
                    !ep.enabled || !ep.healthy -> "REPLACED_FAILED"
                    cooldownEndMs > trustedServerNowMs -> "COOLDOWN_EXCLUDED"
                    else -> "ROTATED_OUT"
                }
                if (ep != null && ep.assignedUserId == uid) {
                    invMap[ep.endpointId] = ep.copy(
                        assignedUserId = "",
                        assignedSubscriptionId = "",
                        allocationExpiresAt = null,
                        updatedAt = nowTs
                    )
                }
            }
        }

        // Trimming if user downgraded plan limit while preserving connected active endpoint first
        if (validCurrent.size > targetLimit) {
            val prioritizedToKeep = validCurrent.sortedWith(
                compareByDescending<AllocatedEndpointRecord> { it.endpointId == activeConnectedEndpointId }
                    .thenBy { if (it.latencyMs > 0) it.latencyMs else Int.MAX_VALUE }
            )
            val kept = prioritizedToKeep.take(targetLimit)
            val dropped = prioritizedToKeep.drop(targetLimit)
            dropped.forEach { d ->
                failedOrRemovedIds.add(d.endpointId)
                removalReasonByEp[d.endpointId] = "ROTATED_OUT"
                val ep = invMap[d.endpointId]
                if (ep != null && ep.assignedUserId == uid) {
                    invMap[d.endpointId] = ep.copy(
                        assignedUserId = "",
                        assignedSubscriptionId = "",
                        allocationExpiresAt = null,
                        updatedAt = nowTs
                    )
                }
            }
            validCurrent.clear()
            validCurrent.addAll(kept)
        }

        val needsDailyCycleRotation = checkedSub.lastRotationCycleKey != cycleKey || forceManualRotation
        val alreadyHealthyAndFullSameCycle = !needsDailyCycleRotation &&
            failedOrRemovedIds.isEmpty() &&
            validCurrent.size == targetLimit

        if (alreadyHealthyAndFullSameCycle) {
            return UserRotationOutcome(
                updatedSubscription = checkedSub,
                updatedInventory = inventory,
                activeAllocations = validCurrent,
                rotatedOutAllocations = emptyList(),
                shortageAlert = null,
                skippedIdempotent = true,
                replacedCount = 0,
                allocationHistoryEvents = emptyList()
            )
        }

        // Preserve connected endpoint & compatible healthy nodes if rotating blindly would invalidate live tunnel,
        // otherwise rotate stale non-active allocations when daily rotation or manual rotation runs.
        val keptAfterRotation = mutableListOf<AllocatedEndpointRecord>()
        val candidatePool = selectEligiblePool(
            endpoints = invMap.values.toList(),
            userId = uid,
            planPriority = planPriority,
            compatibleProtocols = compatibleProtocols,
            trustedServerNowMs = trustedServerNowMs
        ).filter { it.assignedUserId.isBlank() }

        var candidateCursor = 0
        var replacedCount = failedOrRemovedIds.size

        // Keep active connected endpoint protected if valid
        val activeConnectedRecord = validCurrent.firstOrNull { it.endpointId == activeConnectedEndpointId }
        if (activeConnectedRecord != null) {
            keptAfterRotation.add(activeConnectedRecord)
        }

        // Decide which non-connected valid endpoints to rotate vs keep
        for (alloc in validCurrent) {
            if (alloc.endpointId == activeConnectedEndpointId) continue
            if (needsDailyCycleRotation && candidateCursor < candidatePool.size) {
                // Rotate out old non-active endpoint in favor of fresh candidate
                val oldEp = invMap[alloc.endpointId]
                if (oldEp != null && oldEp.assignedUserId == uid) {
                    invMap[oldEp.endpointId] = oldEp.copy(
                        assignedUserId = "",
                        assignedSubscriptionId = "",
                        allocationExpiresAt = null,
                        updatedAt = nowTs
                    )
                }
                failedOrRemovedIds.add(alloc.endpointId)
                removalReasonByEp[alloc.endpointId] = "ROTATED_OUT"
                candidateCursor++
                replacedCount++
            } else {
                // Keep last valid allocation safely if no replacement or within cycle
                keptAfterRotation.add(alloc)
            }
        }

        // Assemble fresh allocations up to targetLimit using strictly unassigned or user-owned endpoints
        val newAllocVersion = checkedSub.currentAllocationVersion + 1
        val finalAllocations = keptAfterRotation.toMutableList()

        val alreadyKeptIds = finalAllocations.map { it.endpointId }.toSet()
        val prioritizedFreshCandidates = selectEligiblePool(
            endpoints = invMap.values.toList(),
            userId = uid,
            planPriority = planPriority,
            compatibleProtocols = compatibleProtocols,
            trustedServerNowMs = trustedServerNowMs
        ).filter { ep ->
            ep.endpointId !in alreadyKeptIds &&
                (ep.assignedUserId.isBlank() || ep.assignedUserId == uid)
        }.sortedBy { ep ->
            if (ep.endpointId in failedOrRemovedIds) 1 else 0
        }

        for (ep in prioritizedFreshCandidates) {
            if (finalAllocations.size >= targetLimit) break
            if (!ep.enabled || !ep.healthy) continue
            // Double-check exclusivity lock inside invMap before assignment
            val liveEp = invMap[ep.endpointId] ?: continue
            if (liveEp.assignedUserId.isNotBlank() && liveEp.assignedUserId != uid) continue

            invMap[ep.endpointId] = liveEp.copy(
                assignedUserId = uid,
                assignedSubscriptionId = checkedSub.subscriptionId,
                allocationExpiresAt = SubscriptionLifecycleEngine.timestampOf(allocationExpMs),
                updatedAt = nowTs
            )
            val record = buildAllocatedRecord(
                endpoint = liveEp,
                subscription = checkedSub,
                credentialToken = userCredentialToken,
                allocationVersion = newAllocVersion,
                cycleKey = cycleKey,
                allocatedAtMs = trustedServerNowMs,
                expiresAtMs = allocationExpMs
            )
            finalAllocations.add(record)
        }

        // Ensure invMap ownership strictly matches finalAllocations
        val finalIds = finalAllocations.map { it.endpointId }.toSet()
        finalAllocations.forEach { alloc ->
            val ep = invMap[alloc.endpointId]
            if (ep != null) {
                invMap[ep.endpointId] = ep.copy(
                    assignedUserId = uid,
                    assignedSubscriptionId = checkedSub.subscriptionId,
                    allocationExpiresAt = SubscriptionLifecycleEngine.timestampOf(allocationExpMs),
                    updatedAt = nowTs
                )
            }
        }

        val rotatedOut = currentActive
            .filter { it.endpointId !in finalIds }
            .map { old ->
                val ep = invMap[old.endpointId]
                val status = if (ep?.healthy == false || ep?.enabled == false) "FAILED" else "ROTATED_OUT"
                old.copy(
                    allocationStatus = status,
                    updatedAt = nowTs
                )
            }

        // Build immutable AllocationHistoryRecord trail for newly assigned & removed endpoints
        val historyEvents = mutableListOf<AllocationHistoryRecord>()
        rotatedOut.forEach { old ->
            val action = removalReasonByEp[old.endpointId] ?: if (old.allocationStatus == "FAILED") "REPLACED_FAILED" else "ROTATED_OUT"
            historyEvents.add(
                AllocationHistoryRecord(
                    historyId = "hist_${checkedSub.subscriptionId}_${old.endpointId}_v${newAllocVersion}_out",
                    userId = uid,
                    subscriptionId = checkedSub.subscriptionId,
                    planId = checkedSub.planId,
                    endpointId = old.endpointId,
                    endpointName = old.endpointName,
                    host = old.host,
                    port = old.port,
                    protocol = old.protocol,
                    action = action,
                    allocationVersion = newAllocVersion,
                    cycleKey = cycleKey,
                    reason = when (action) {
                        "REPLACED_FAILED" -> "Unhealthy or disabled endpoint replaced"
                        "COOLDOWN_EXCLUDED" -> "Temporarily excluded due to failure cooldown"
                        else -> "Rotated out for cycle $cycleKey"
                    },
                    actorId = actorId,
                    createdAt = nowTs
                )
            )
        }
        finalAllocations.forEach { alloc ->
            if (alloc.endpointId !in previouslyActiveIds) {
                historyEvents.add(
                    AllocationHistoryRecord(
                        historyId = "hist_${checkedSub.subscriptionId}_${alloc.endpointId}_v${newAllocVersion}_in",
                        userId = uid,
                        subscriptionId = checkedSub.subscriptionId,
                        planId = checkedSub.planId,
                        endpointId = alloc.endpointId,
                        endpointName = alloc.endpointName,
                        host = alloc.host,
                        port = alloc.port,
                        protocol = alloc.protocol,
                        action = "ALLOCATED",
                        allocationVersion = newAllocVersion,
                        cycleKey = cycleKey,
                        reason = "Dedicated endpoint allocated (${checkedSub.planEnum().titleEn})",
                        actorId = actorId,
                        createdAt = nowTs
                    )
                )
            }
        }

        val shortfall = (targetLimit - finalAllocations.size).coerceAtLeast(0)
        val shortageAlert = if (shortfall > 0) {
            InventoryShortageAlert(
                alertId = "shortage_${checkedSub.subscriptionId}_$cycleKey",
                userId = uid,
                subscriptionId = checkedSub.subscriptionId,
                planId = checkedSub.planId,
                requestedCount = targetLimit,
                allocatedCount = finalAllocations.size,
                shortfallCount = shortfall,
                cycleKey = cycleKey,
                reason = "Insufficient healthy unassigned endpoints for priority tier $planPriority ($shortfall needed)",
                resolved = false,
                createdAt = nowTs
            )
        } else {
            null
        }

        val updatedSub = checkedSub.copy(
            lastDailyRotation = nowTs,
            lastRotationCycleKey = cycleKey,
            currentAllocationVersion = newAllocVersion,
            updatedAt = nowTs
        )

        return UserRotationOutcome(
            updatedSubscription = updatedSub,
            updatedInventory = invMap.values.toList(),
            activeAllocations = finalAllocations,
            rotatedOutAllocations = rotatedOut,
            shortageAlert = shortageAlert,
            skippedIdempotent = false,
            replacedCount = replacedCount,
            allocationHistoryEvents = historyEvents
        )
    }

    /**
     * Batch / global daily rotation runner. Preserves priority order (Plan C -> Plan B -> Plan A).
     * Idempotent within the same UTC [cycleKey] unless [forceManual] is true.
     */
    fun runDailyRotationBatch(
        subscriptions: List<UserSubscription>,
        inventory: List<ManagedEndpoint>,
        existingAllocationsByUser: Map<String, List<AllocatedEndpointRecord>>,
        credentialsByUser: Map<String, String>,
        activeEndpointByUser: Map<String, String?> = emptyMap(),
        triggeredByUid: String,
        triggerMode: String,
        trustedServerNowMs: Long,
        forceManual: Boolean = false
    ): BatchRotationResult {
        val cycleKey = SubscriptionLifecycleEngine.utcCycleKey(trustedServerNowMs)
        val orderedSubs = subscriptions.sortedByDescending { it.planEnum().priorityLevel }

        var mutableInventory = inventory
        val updatedSubs = mutableListOf<UserSubscription>()
        val allocsMap = mutableMapOf<String, List<AllocatedEndpointRecord>>()
        val rotatedOutMap = mutableMapOf<String, List<AllocatedEndpointRecord>>()
        val historyMap = mutableMapOf<String, List<AllocationHistoryRecord>>()
        val shortages = mutableListOf<InventoryShortageAlert>()

        var rotatedUsers = 0
        var replacedTotal = 0

        for (sub in orderedSubs) {
            val uid = sub.userId
            val userCred = credentialsByUser[uid]?.takeIf { it.isNotBlank() }
                ?: UUID.nameUUIDFromBytes("tms_$uid".toByteArray()).toString()

            val outcome = allocateOrRotateForUser(
                subscription = sub,
                inventory = mutableInventory,
                existingAllocations = existingAllocationsByUser[uid].orEmpty(),
                userCredentialToken = userCred,
                activeConnectedEndpointId = activeEndpointByUser[uid],
                trustedServerNowMs = trustedServerNowMs,
                forceManualRotation = forceManual,
                actorId = triggeredByUid
            )
            mutableInventory = outcome.updatedInventory
            updatedSubs.add(outcome.updatedSubscription)
            allocsMap[uid] = outcome.activeAllocations
            rotatedOutMap[uid] = outcome.rotatedOutAllocations
            if (outcome.allocationHistoryEvents.isNotEmpty()) {
                historyMap[uid] = outcome.allocationHistoryEvents
            }
            if (outcome.shortageAlert != null) {
                shortages.add(outcome.shortageAlert)
            }
            if (!outcome.skippedIdempotent) {
                rotatedUsers++
                replacedTotal += outcome.replacedCount
            }
        }

        check(verifyZeroDoubleAssignment(allocsMap)) {
            "Invariant violated: duplicate dedicated endpoint assignment detected in batch"
        }

        val auditStatus = when {
            shortages.isNotEmpty() -> "PARTIAL_SHORTAGE"
            rotatedUsers == 0 && orderedSubs.isNotEmpty() -> "SKIPPED_IDEMPOTENT"
            else -> "SUCCESS"
        }

        val audit = RotationAuditLog(
            cycleKey = cycleKey,
            triggerMode = triggerMode,
            triggeredByUid = triggeredByUid,
            targetUserCount = orderedSubs.size,
            rotatedUsersCount = rotatedUsers,
            replacedEndpointsCount = replacedTotal,
            shortageCount = shortages.size,
            status = auditStatus,
            details = "Cycle $cycleKey: targets=${orderedSubs.size}, rotated=$rotatedUsers, replaced=$replacedTotal, shortages=${shortages.size}",
            createdAt = SubscriptionLifecycleEngine.timestampOf(trustedServerNowMs)
        )

        return BatchRotationResult(
            updatedSubscriptions = updatedSubs,
            updatedInventory = mutableInventory,
            allocationsByUser = allocsMap,
            rotatedOutByUser = rotatedOutMap,
            shortageAlerts = shortages,
            auditLog = audit,
            allocationHistoryByUser = historyMap
        )
    }

    /**
     * Verifies across all active allocations that no endpointId is ever assigned to
     * more than one user simultaneously.
     */
    fun verifyZeroDoubleAssignment(
        allocationsByUser: Map<String, List<AllocatedEndpointRecord>>
    ): Boolean {
        val ownerByEndpoint = mutableMapOf<String, String>()
        for ((uid, list) in allocationsByUser) {
            for (rec in list) {
                if (rec.allocationStatus != "ACTIVE") continue
                val existingOwner = ownerByEndpoint[rec.endpointId]
                if (existingOwner != null && existingOwner != uid) {
                    return false
                }
                ownerByEndpoint[rec.endpointId] = uid
            }
        }
        return true
    }

    /**
     * Excludes an endpoint temporarily after repeated runtime connection failures.
     */
    fun recordEndpointFailure(
        endpoint: ManagedEndpoint,
        trustedServerNowMs: Long,
        cooldownMs: Long = DEFAULT_COOLDOWN_MS
    ): ManagedEndpoint {
        val nextFailures = endpoint.failureCount + 1
        val shouldCooldown = nextFailures >= 2
        return endpoint.copy(
            healthy = if (nextFailures >= 3) false else endpoint.healthy,
            failureCount = nextFailures,
            cooldownUntil = if (shouldCooldown) {
                SubscriptionLifecycleEngine.timestampOf(trustedServerNowMs + cooldownMs)
            } else {
                endpoint.cooldownUntil
            },
            assignedUserId = if (shouldCooldown) "" else endpoint.assignedUserId,
            assignedSubscriptionId = if (shouldCooldown) "" else endpoint.assignedSubscriptionId,
            updatedAt = SubscriptionLifecycleEngine.timestampOf(trustedServerNowMs)
        )
    }

    fun selectEligiblePool(
        endpoints: List<ManagedEndpoint>,
        userId: String,
        planPriority: Int,
        compatibleProtocols: Set<String>,
        trustedServerNowMs: Long
    ): List<ManagedEndpoint> {
        val allowedProtocols = compatibleProtocols.map { it.lowercase() }.toSet()
        return endpoints.filter { ep ->
            val cooldownEndMs = ep.cooldownUntil?.toDate()?.time ?: 0L
            ep.enabled &&
                ep.healthy &&
                cooldownEndMs <= trustedServerNowMs &&
                ep.isUnassignedOrOwnedBy(userId) &&
                ep.protocol.lowercase() in allowedProtocols
        }.sortedWith(
            compareByDescending<ManagedEndpoint> { ep ->
                if (planPriority >= 3) ep.priorityTier else if (ep.priorityTier <= planPriority) ep.priorityTier else -1
            }
                .thenByDescending { it.stabilityScore }
                .thenBy { if (it.latencyMs > 0) it.latencyMs else Int.MAX_VALUE }
        )
    }

    fun buildAllocatedRecord(
        endpoint: ManagedEndpoint,
        subscription: UserSubscription,
        credentialToken: String,
        allocationVersion: Int,
        cycleKey: String,
        allocatedAtMs: Long,
        expiresAtMs: Long
    ): AllocatedEndpointRecord {
        val rawUri = buildProtocolUri(endpoint, credentialToken)
        return AllocatedEndpointRecord(
            allocationId = "${subscription.subscriptionId}_${endpoint.endpointId}_v$allocationVersion",
            userId = subscription.userId,
            subscriptionId = subscription.subscriptionId,
            endpointId = endpoint.endpointId,
            endpointName = endpoint.name.ifBlank { "${endpoint.countryCode} • ${endpoint.host}" },
            country = endpoint.country,
            countryCode = endpoint.countryCode,
            host = endpoint.host,
            port = endpoint.port,
            protocol = endpoint.protocol.lowercase(),
            transport = endpoint.transport,
            security = endpoint.security,
            sni = endpoint.sni,
            pathOrServiceName = endpoint.pathOrServiceName,
            credentialToken = credentialToken,
            rawConfig = rawUri,
            allocationVersion = allocationVersion,
            cycleKey = cycleKey,
            allocationStatus = "ACTIVE",
            healthy = endpoint.healthy,
            latencyMs = endpoint.latencyMs,
            allocatedAt = SubscriptionLifecycleEngine.timestampOf(allocatedAtMs),
            expiresAt = SubscriptionLifecycleEngine.timestampOf(expiresAtMs),
            updatedAt = SubscriptionLifecycleEngine.timestampOf(allocatedAtMs)
        )
    }

    fun buildProtocolUri(endpoint: ManagedEndpoint, credentialToken: String): String {
        val sniPart = if (endpoint.sni.isNotBlank()) "&sni=${endpoint.sni}" else ""
        val pathPart = if (endpoint.pathOrServiceName.isNotBlank()) "&path=${endpoint.pathOrServiceName}" else ""
        val tag = endpoint.name.ifBlank { endpoint.host }.replace(" ", "+")
        return when (endpoint.protocol.lowercase()) {
            "trojan" -> "trojan://$credentialToken@${endpoint.host}:${endpoint.port}?security=${endpoint.security}$sniPart&type=${endpoint.transport}$pathPart#$tag"
            "vmess" -> "vmess://$credentialToken@${endpoint.host}:${endpoint.port}?security=${endpoint.security}$sniPart&type=${endpoint.transport}$pathPart#$tag"
            "shadowsocks" -> "ss://$credentialToken@${endpoint.host}:${endpoint.port}#$tag"
            else -> "vless://$credentialToken@${endpoint.host}:${endpoint.port}?encryption=none&security=${endpoint.security}$sniPart&type=${endpoint.transport}$pathPart#$tag"
        }
    }
}
