package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.example.data.model.AllocatedEndpointRecord
import com.example.data.model.AllocationHistoryRecord
import com.example.data.model.ManagedEndpoint
import com.example.data.model.PaymentRequestRecord
import com.example.data.model.PaymentStatus
import com.example.data.model.SubscriptionHistoryEvent
import com.example.data.model.SubscriptionPlanId
import com.example.data.model.SubscriptionStatus
import com.example.data.model.UserProfile
import com.example.data.model.UserSubscription
import com.example.data.subscription.SubscriptionLifecycleEngine
import kotlinx.coroutines.flow.Flow

/**
 * Tombstone tracking deleted configuration IDs so deletions survive offline restarts
 * and never reappear when Firestore snapshots replay or default seeding runs.
 */
@Entity(
    tableName = "deleted_config_tombstones",
    indices = [Index(value = ["userId"]), Index(value = ["pendingRemoteDelete"])]
)
data class DeletedConfigTombstoneEntity(
    @PrimaryKey
    @ColumnInfo(name = "configId")
    val configId: String,

    @ColumnInfo(name = "userId")
    val userId: String,

    @ColumnInfo(name = "deletedAtMs")
    val deletedAtMs: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "pendingRemoteDelete")
    val pendingRemoteDelete: Boolean = true
)

/**
 * Read-only offline cache of the user's backend-verified subscription state.
 * Cannot be used to activate, renew, or extend entitlements offline.
 */
@Entity(
    tableName = "cached_subscriptions",
    indices = [Index(value = ["userId"], unique = true)]
)
data class CachedSubscriptionEntity(
    @PrimaryKey
    @ColumnInfo(name = "subscriptionId")
    val subscriptionId: String,

    @ColumnInfo(name = "userId")
    val userId: String,

    @ColumnInfo(name = "userEmail")
    val userEmail: String = "",

    @ColumnInfo(name = "planId")
    val planId: String = SubscriptionPlanId.ONE_MONTH.id,

    @ColumnInfo(name = "planDurationDays")
    val planDurationDays: Int = 30,

    @ColumnInfo(name = "endpointAllocationLimit")
    val endpointAllocationLimit: Int = 5,

    @ColumnInfo(name = "startDateMs")
    val startDateMs: Long = 0L,

    @ColumnInfo(name = "expirationDateMs")
    val expirationDateMs: Long = 0L,

    @ColumnInfo(name = "status")
    val status: String = SubscriptionStatus.PENDING_PAYMENT.name,

    @ColumnInfo(name = "paymentStatus")
    val paymentStatus: String = PaymentStatus.UNPAID.name,

    @ColumnInfo(name = "lastDailyRotationMs")
    val lastDailyRotationMs: Long = 0L,

    @ColumnInfo(name = "lastRotationCycleKey")
    val lastRotationCycleKey: String = "",

    @ColumnInfo(name = "currentAllocationVersion")
    val currentAllocationVersion: Int = 0,

    @ColumnInfo(name = "createdAtMs")
    val createdAtMs: Long = 0L,

    @ColumnInfo(name = "updatedAtMs")
    val updatedAtMs: Long = 0L,

    @ColumnInfo(name = "lastSyncedServerMs")
    val lastSyncedServerMs: Long = 0L
) {
    fun toDomainModel(): UserSubscription {
        return UserSubscription(
            subscriptionId = subscriptionId,
            userId = userId,
            userEmail = userEmail,
            planId = planId,
            planDurationDays = planDurationDays,
            endpointAllocationLimit = endpointAllocationLimit,
            startDate = if (startDateMs > 0L) SubscriptionLifecycleEngine.timestampOf(startDateMs) else null,
            expirationDate = if (expirationDateMs > 0L) SubscriptionLifecycleEngine.timestampOf(expirationDateMs) else null,
            status = status,
            paymentStatus = paymentStatus,
            lastDailyRotation = if (lastDailyRotationMs > 0L) SubscriptionLifecycleEngine.timestampOf(lastDailyRotationMs) else null,
            lastRotationCycleKey = lastRotationCycleKey,
            currentAllocationVersion = currentAllocationVersion,
            createdAt = if (createdAtMs > 0L) SubscriptionLifecycleEngine.timestampOf(createdAtMs) else null,
            updatedAt = if (updatedAtMs > 0L) SubscriptionLifecycleEngine.timestampOf(updatedAtMs) else null
        )
    }

    companion object {
        fun fromDomainModel(sub: UserSubscription, syncedServerNowMs: Long): CachedSubscriptionEntity {
            return CachedSubscriptionEntity(
                subscriptionId = sub.subscriptionId.ifBlank { "sub_${sub.userId}" },
                userId = sub.userId,
                userEmail = sub.userEmail,
                planId = sub.planId,
                planDurationDays = sub.planDurationDays,
                endpointAllocationLimit = sub.endpointAllocationLimit,
                startDateMs = sub.startDate?.toDate()?.time ?: 0L,
                expirationDateMs = sub.expirationDate?.toDate()?.time ?: 0L,
                status = sub.status,
                paymentStatus = sub.paymentStatus,
                lastDailyRotationMs = sub.lastDailyRotation?.toDate()?.time ?: 0L,
                lastRotationCycleKey = sub.lastRotationCycleKey,
                currentAllocationVersion = sub.currentAllocationVersion,
                createdAtMs = sub.createdAt?.toDate()?.time ?: 0L,
                updatedAtMs = sub.updatedAt?.toDate()?.time ?: 0L,
                lastSyncedServerMs = syncedServerNowMs
            )
        }
    }
}

@Entity(
    tableName = "cached_allocated_endpoints",
    indices = [Index(value = ["userId"]), Index(value = ["subscriptionId"])]
)
data class CachedAllocatedEndpointEntity(
    @PrimaryKey
    @ColumnInfo(name = "allocationId")
    val allocationId: String,
    @ColumnInfo(name = "userId")
    val userId: String,
    @ColumnInfo(name = "subscriptionId")
    val subscriptionId: String,
    @ColumnInfo(name = "endpointId")
    val endpointId: String,
    @ColumnInfo(name = "endpointName")
    val endpointName: String,
    @ColumnInfo(name = "country")
    val country: String,
    @ColumnInfo(name = "countryCode")
    val countryCode: String,
    @ColumnInfo(name = "host")
    val host: String,
    @ColumnInfo(name = "port")
    val port: Int,
    @ColumnInfo(name = "protocol")
    val protocol: String,
    @ColumnInfo(name = "transport")
    val transport: String,
    @ColumnInfo(name = "security")
    val security: String,
    @ColumnInfo(name = "sni")
    val sni: String,
    @ColumnInfo(name = "pathOrServiceName")
    val pathOrServiceName: String,
    @ColumnInfo(name = "credentialToken")
    val credentialToken: String,
    @ColumnInfo(name = "rawConfig")
    val rawConfig: String,
    @ColumnInfo(name = "allocationVersion")
    val allocationVersion: Int,
    @ColumnInfo(name = "cycleKey")
    val cycleKey: String,
    @ColumnInfo(name = "allocationStatus")
    val allocationStatus: String,
    @ColumnInfo(name = "healthy")
    val healthy: Boolean,
    @ColumnInfo(name = "latencyMs")
    val latencyMs: Int,
    @ColumnInfo(name = "allocatedAtMs")
    val allocatedAtMs: Long,
    @ColumnInfo(name = "expiresAtMs")
    val expiresAtMs: Long
) {
    fun toDomainModel(): AllocatedEndpointRecord {
        return AllocatedEndpointRecord(
            allocationId = allocationId,
            userId = userId,
            subscriptionId = subscriptionId,
            endpointId = endpointId,
            endpointName = endpointName,
            country = country,
            countryCode = countryCode,
            host = host,
            port = port,
            protocol = protocol,
            transport = transport,
            security = security,
            sni = sni,
            pathOrServiceName = pathOrServiceName,
            credentialToken = credentialToken,
            rawConfig = rawConfig,
            allocationVersion = allocationVersion,
            cycleKey = cycleKey,
            allocationStatus = allocationStatus,
            healthy = healthy,
            latencyMs = latencyMs,
            allocatedAt = if (allocatedAtMs > 0L) SubscriptionLifecycleEngine.timestampOf(allocatedAtMs) else null,
            expiresAt = if (expiresAtMs > 0L) SubscriptionLifecycleEngine.timestampOf(expiresAtMs) else null
        )
    }

    companion object {
        fun fromDomainModel(rec: AllocatedEndpointRecord): CachedAllocatedEndpointEntity {
            return CachedAllocatedEndpointEntity(
                allocationId = rec.allocationId.ifBlank { "${rec.subscriptionId}_${rec.endpointId}_v${rec.allocationVersion}" },
                userId = rec.userId,
                subscriptionId = rec.subscriptionId,
                endpointId = rec.endpointId,
                endpointName = rec.endpointName,
                country = rec.country,
                countryCode = rec.countryCode,
                host = rec.host,
                port = rec.port,
                protocol = rec.protocol,
                transport = rec.transport,
                security = rec.security,
                sni = rec.sni,
                pathOrServiceName = rec.pathOrServiceName,
                credentialToken = rec.credentialToken,
                rawConfig = rec.rawConfig,
                allocationVersion = rec.allocationVersion,
                cycleKey = rec.cycleKey,
                allocationStatus = rec.allocationStatus,
                healthy = rec.healthy,
                latencyMs = rec.latencyMs,
                allocatedAtMs = rec.allocatedAt?.toDate()?.time ?: 0L,
                expiresAtMs = rec.expiresAt?.toDate()?.time ?: 0L
            )
        }
    }
}

@Entity(
    tableName = "cached_subscription_history",
    indices = [Index(value = ["userId"])]
)
data class CachedSubscriptionHistoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "eventId")
    val eventId: String,
    @ColumnInfo(name = "subscriptionId")
    val subscriptionId: String,
    @ColumnInfo(name = "userId")
    val userId: String,
    @ColumnInfo(name = "action")
    val action: String,
    @ColumnInfo(name = "fromPlanId")
    val fromPlanId: String,
    @ColumnInfo(name = "toPlanId")
    val toPlanId: String,
    @ColumnInfo(name = "statusAfter")
    val statusAfter: String,
    @ColumnInfo(name = "actorId")
    val actorId: String,
    @ColumnInfo(name = "details")
    val details: String,
    @ColumnInfo(name = "createdAtMs")
    val createdAtMs: Long
) {
    fun toDomainModel(): SubscriptionHistoryEvent {
        return SubscriptionHistoryEvent(
            eventId = eventId,
            subscriptionId = subscriptionId,
            userId = userId,
            action = action,
            fromPlanId = fromPlanId,
            toPlanId = toPlanId,
            statusAfter = statusAfter,
            actorId = actorId,
            details = details,
            createdAt = if (createdAtMs > 0L) SubscriptionLifecycleEngine.timestampOf(createdAtMs) else null
        )
    }

    companion object {
        fun fromDomainModel(ev: SubscriptionHistoryEvent): CachedSubscriptionHistoryEntity {
            return CachedSubscriptionHistoryEntity(
                eventId = ev.eventId.ifBlank { "${ev.subscriptionId}_${ev.action}_${ev.createdAt?.toDate()?.time ?: System.nanoTime()}" },
                subscriptionId = ev.subscriptionId,
                userId = ev.userId,
                action = ev.action,
                fromPlanId = ev.fromPlanId,
                toPlanId = ev.toPlanId,
                statusAfter = ev.statusAfter,
                actorId = ev.actorId,
                details = ev.details,
                createdAtMs = ev.createdAt?.toDate()?.time ?: 0L
            )
        }
    }
}

@Entity(
    tableName = "cached_allocation_history",
    indices = [Index(value = ["userId"]), Index(value = ["endpointId"])]
)
data class CachedAllocationHistoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "historyId")
    val historyId: String,
    @ColumnInfo(name = "userId")
    val userId: String,
    @ColumnInfo(name = "subscriptionId")
    val subscriptionId: String,
    @ColumnInfo(name = "planId")
    val planId: String,
    @ColumnInfo(name = "endpointId")
    val endpointId: String,
    @ColumnInfo(name = "endpointName")
    val endpointName: String,
    @ColumnInfo(name = "host")
    val host: String,
    @ColumnInfo(name = "port")
    val port: Int,
    @ColumnInfo(name = "protocol")
    val protocol: String,
    @ColumnInfo(name = "action")
    val action: String,
    @ColumnInfo(name = "allocationVersion")
    val allocationVersion: Int,
    @ColumnInfo(name = "cycleKey")
    val cycleKey: String,
    @ColumnInfo(name = "reason")
    val reason: String,
    @ColumnInfo(name = "actorId")
    val actorId: String,
    @ColumnInfo(name = "createdAtMs")
    val createdAtMs: Long
) {
    fun toDomainModel(): AllocationHistoryRecord {
        return AllocationHistoryRecord(
            historyId = historyId,
            userId = userId,
            subscriptionId = subscriptionId,
            planId = planId,
            endpointId = endpointId,
            endpointName = endpointName,
            host = host,
            port = port,
            protocol = protocol,
            action = action,
            allocationVersion = allocationVersion,
            cycleKey = cycleKey,
            reason = reason,
            actorId = actorId,
            createdAt = if (createdAtMs > 0L) SubscriptionLifecycleEngine.timestampOf(createdAtMs) else null
        )
    }

    companion object {
        fun fromDomainModel(rec: AllocationHistoryRecord): CachedAllocationHistoryEntity {
            return CachedAllocationHistoryEntity(
                historyId = rec.historyId.ifBlank { "${rec.subscriptionId}_${rec.endpointId}_${rec.action}_${rec.createdAt?.toDate()?.time ?: System.nanoTime()}" },
                userId = rec.userId,
                subscriptionId = rec.subscriptionId,
                planId = rec.planId,
                endpointId = rec.endpointId,
                endpointName = rec.endpointName,
                host = rec.host,
                port = rec.port,
                protocol = rec.protocol,
                action = rec.action,
                allocationVersion = rec.allocationVersion,
                cycleKey = rec.cycleKey,
                reason = rec.reason,
                actorId = rec.actorId,
                createdAtMs = rec.createdAt?.toDate()?.time ?: 0L
            )
        }
    }
}

@Dao
interface DeletedConfigTombstoneDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<DeletedConfigTombstoneEntity>)

    @Query("SELECT configId FROM deleted_config_tombstones WHERE userId = :userId OR userId = ''")
    suspend fun getTombstonedIds(userId: String): List<String>

    @Query("SELECT * FROM deleted_config_tombstones WHERE pendingRemoteDelete = 1 AND (userId = :userId OR userId = '')")
    suspend fun getPendingRemoteDeletes(userId: String): List<DeletedConfigTombstoneEntity>

    @Query("UPDATE deleted_config_tombstones SET pendingRemoteDelete = 0 WHERE configId IN (:configIds)")
    suspend fun markRemoteSynced(configIds: List<String>)

    @Query("DELETE FROM deleted_config_tombstones WHERE configId IN (:configIds)")
    suspend fun removeTombstones(configIds: List<String>)
}

@Dao
interface CachedSubscriptionDao {
    @Query("SELECT * FROM cached_subscriptions WHERE userId = :userId LIMIT 1")
    fun observeSubscription(userId: String): Flow<CachedSubscriptionEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSubscription(entity: CachedSubscriptionEntity)

    @Query("SELECT * FROM cached_subscriptions ORDER BY updatedAtMs DESC")
    fun observeAllSubscriptions(): Flow<List<CachedSubscriptionEntity>>

    @Query("SELECT * FROM cached_subscriptions ORDER BY updatedAtMs DESC")
    suspend fun getAllSubscriptionsOnce(): List<CachedSubscriptionEntity>

    @Query("SELECT * FROM cached_allocated_endpoints WHERE userId = :userId AND allocationStatus = 'ACTIVE'")
    fun observeActiveEndpoints(userId: String): Flow<List<CachedAllocatedEndpointEntity>>

    @Query("SELECT * FROM cached_allocated_endpoints WHERE userId = :userId AND allocationStatus = 'ACTIVE'")
    suspend fun getActiveEndpointsOnce(userId: String): List<CachedAllocatedEndpointEntity>

    @Query("SELECT * FROM cached_allocated_endpoints WHERE allocationStatus = 'ACTIVE'")
    suspend fun getAllActiveEndpointsOnce(): List<CachedAllocatedEndpointEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEndpoints(entities: List<CachedAllocatedEndpointEntity>)

    @Query("DELETE FROM cached_allocated_endpoints WHERE userId = :userId")
    suspend fun clearEndpointsForUser(userId: String)

    @Query("SELECT * FROM cached_subscription_history WHERE userId = :userId ORDER BY createdAtMs DESC")
    fun observeHistory(userId: String): Flow<List<CachedSubscriptionHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(entities: List<CachedSubscriptionHistoryEntity>)

    @Query("SELECT * FROM cached_allocation_history WHERE userId = :userId ORDER BY createdAtMs DESC LIMIT 100")
    fun observeAllocationHistory(userId: String): Flow<List<CachedAllocationHistoryEntity>>

    @Query("SELECT * FROM cached_allocation_history ORDER BY createdAtMs DESC LIMIT 200")
    fun observeAllAllocationHistory(): Flow<List<CachedAllocationHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAllocationHistory(entities: List<CachedAllocationHistoryEntity>)
}

/**
 * Persists registered users (email, displayName, userId, role, quota, subscription status)
 * so the Admin Panel always has full visibility over all registered accounts.
 */
@Entity(
    tableName = "registered_users",
    indices = [Index(value = ["email"], unique = true)]
)
data class RegisteredUserEntity(
    @PrimaryKey
    @ColumnInfo(name = "userId")
    val userId: String,
    @ColumnInfo(name = "displayName")
    val displayName: String,
    @ColumnInfo(name = "email")
    val email: String,
    @ColumnInfo(name = "role")
    val role: String = "USER",
    @ColumnInfo(name = "deviceCount")
    val deviceCount: Int = 1,
    @ColumnInfo(name = "maxDevices")
    val maxDevices: Int = 10,
    @ColumnInfo(name = "dataUsageBytes")
    val dataUsageBytes: Double = 0.0,
    @ColumnInfo(name = "dataLimitBytes")
    val dataLimitBytes: Double = 500.0 * 1024 * 1024 * 1024,
    @ColumnInfo(name = "subscriptionStatus")
    val subscriptionStatus: String = "expired",
    @ColumnInfo(name = "createdAtMs")
    val createdAtMs: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updatedAtMs")
    val updatedAtMs: Long = System.currentTimeMillis()
) {
    fun toUserProfile(): UserProfile {
        return UserProfile(
            id = userId,
            userId = userId,
            displayName = displayName.ifBlank { email.substringBefore("@").ifBlank { "TMS User" } },
            email = email,
            role = role,
            deviceCount = deviceCount,
            maxDevices = maxDevices,
            dataUsageBytes = dataUsageBytes,
            dataLimitBytes = dataLimitBytes,
            subscriptionStatus = subscriptionStatus,
            createdAt = if (createdAtMs > 0L) SubscriptionLifecycleEngine.timestampOf(createdAtMs) else null,
            updatedAt = if (updatedAtMs > 0L) SubscriptionLifecycleEngine.timestampOf(updatedAtMs) else null
        )
    }
}

@Dao
interface RegisteredUserDao {
    @Query("SELECT * FROM registered_users ORDER BY updatedAtMs DESC")
    fun observeAll(): Flow<List<RegisteredUserEntity>>

    @Query("SELECT * FROM registered_users ORDER BY updatedAtMs DESC")
    suspend fun getAllOnce(): List<RegisteredUserEntity>

    @Query("SELECT * FROM registered_users WHERE userId = :userId LIMIT 1")
    suspend fun getByUserId(userId: String): RegisteredUserEntity?

    @Query("SELECT * FROM registered_users WHERE email = :email LIMIT 1")
    suspend fun getByEmail(email: String): RegisteredUserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RegisteredUserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<RegisteredUserEntity>)

    @Query("UPDATE registered_users SET subscriptionStatus = :status, updatedAtMs = :updatedAtMs WHERE userId = :userId")
    suspend fun updateSubscriptionStatus(userId: String, status: String, updatedAtMs: Long)

    @Query("UPDATE registered_users SET maxDevices = :maxDevices, dataLimitBytes = :dataLimitBytes, updatedAtMs = :updatedAtMs WHERE userId = :userId")
    suspend fun updateQuota(userId: String, maxDevices: Int, dataLimitBytes: Double, updatedAtMs: Long)

    @Query("UPDATE registered_users SET dataUsageBytes = dataUsageBytes + :deltaBytes, updatedAtMs = :updatedAtMs WHERE userId = :userId")
    suspend fun incrementDataUsage(userId: String, deltaBytes: Double, updatedAtMs: Long)
}

/**
 * Persists user payment & plan activation requests locally so the subscription workflow
 * works seamlessly across Email+Password accounts and syncs cleanly with Firestore when available.
 */
@Entity(
    tableName = "local_payment_requests",
    indices = [Index(value = ["userId"]), Index(value = ["status"])]
)
data class LocalPaymentRequestEntity(
    @PrimaryKey
    @ColumnInfo(name = "paymentId")
    val paymentId: String,
    @ColumnInfo(name = "userId")
    val userId: String,
    @ColumnInfo(name = "userEmail")
    val userEmail: String,
    @ColumnInfo(name = "subscriptionId")
    val subscriptionId: String,
    @ColumnInfo(name = "planId")
    val planId: String,
    @ColumnInfo(name = "actionType")
    val actionType: String,
    @ColumnInfo(name = "provider")
    val provider: String,
    @ColumnInfo(name = "referenceCode")
    val referenceCode: String,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "note")
    val note: String,
    @ColumnInfo(name = "createdAtMs")
    val createdAtMs: Long,
    @ColumnInfo(name = "verifiedAtMs")
    val verifiedAtMs: Long = 0L,
    @ColumnInfo(name = "verifiedBy")
    val verifiedBy: String = ""
) {
    fun toDomainModel(): PaymentRequestRecord {
        return PaymentRequestRecord(
            paymentId = paymentId,
            userId = userId,
            userEmail = userEmail,
            subscriptionId = subscriptionId,
            planId = planId,
            actionType = actionType,
            provider = provider,
            referenceCode = referenceCode,
            status = status,
            note = note,
            createdAt = if (createdAtMs > 0L) SubscriptionLifecycleEngine.timestampOf(createdAtMs) else null,
            verifiedAt = if (verifiedAtMs > 0L) SubscriptionLifecycleEngine.timestampOf(verifiedAtMs) else null,
            verifiedBy = verifiedBy
        )
    }

    companion object {
        fun fromDomainModel(rec: PaymentRequestRecord, fallbackNowMs: Long = System.currentTimeMillis()): LocalPaymentRequestEntity {
            return LocalPaymentRequestEntity(
                paymentId = rec.paymentId.ifBlank { "pay_${rec.userId}_$fallbackNowMs" },
                userId = rec.userId,
                userEmail = rec.userEmail,
                subscriptionId = rec.subscriptionId,
                planId = rec.planId,
                actionType = rec.actionType,
                provider = rec.provider,
                referenceCode = rec.referenceCode,
                status = rec.status,
                note = rec.note,
                createdAtMs = rec.createdAt?.toDate()?.time ?: fallbackNowMs,
                verifiedAtMs = rec.verifiedAt?.toDate()?.time ?: 0L,
                verifiedBy = rec.verifiedBy
            )
        }
    }
}

@Dao
interface LocalPaymentRequestDao {
    @Query("SELECT * FROM local_payment_requests WHERE userId = :userId ORDER BY createdAtMs DESC")
    fun observeByUser(userId: String): Flow<List<LocalPaymentRequestEntity>>

    @Query("SELECT * FROM local_payment_requests ORDER BY createdAtMs DESC")
    fun observeAll(): Flow<List<LocalPaymentRequestEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LocalPaymentRequestEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<LocalPaymentRequestEntity>)

    @Query(
        """
        UPDATE local_payment_requests
        SET status = :status, verifiedBy = :verifiedBy, note = :note, verifiedAtMs = :verifiedAtMs
        WHERE paymentId = :paymentId
        """
    )
    suspend fun markVerified(
        paymentId: String,
        status: String,
        verifiedBy: String,
        note: String,
        verifiedAtMs: Long
    )
}

/**
 * Local persistent inventory for dedicated ManagedEndpoint nodes so atomic IP allocation
 * (5 / 15 / 50 endpoints) and daily rotation run reliably for every Email+Password account
 * and mirror Firestore when online.
 */
@Entity(
    tableName = "local_managed_endpoints",
    indices = [Index(value = ["assignedUserId"]), Index(value = ["healthy"])]
)
data class LocalManagedEndpointEntity(
    @PrimaryKey
    @ColumnInfo(name = "endpointId")
    val endpointId: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "country")
    val country: String,
    @ColumnInfo(name = "countryCode")
    val countryCode: String,
    @ColumnInfo(name = "host")
    val host: String,
    @ColumnInfo(name = "port")
    val port: Int,
    @ColumnInfo(name = "protocol")
    val protocol: String,
    @ColumnInfo(name = "transport")
    val transport: String,
    @ColumnInfo(name = "security")
    val security: String,
    @ColumnInfo(name = "sni")
    val sni: String,
    @ColumnInfo(name = "pathOrServiceName")
    val pathOrServiceName: String,
    @ColumnInfo(name = "enabled")
    val enabled: Boolean,
    @ColumnInfo(name = "healthy")
    val healthy: Boolean,
    @ColumnInfo(name = "priorityTier")
    val priorityTier: Int,
    @ColumnInfo(name = "latencyMs")
    val latencyMs: Int,
    @ColumnInfo(name = "stabilityScore")
    val stabilityScore: Int,
    @ColumnInfo(name = "assignedUserId")
    val assignedUserId: String,
    @ColumnInfo(name = "assignedSubscriptionId")
    val assignedSubscriptionId: String,
    @ColumnInfo(name = "allocationExpiresAtMs")
    val allocationExpiresAtMs: Long,
    @ColumnInfo(name = "failureCount")
    val failureCount: Int,
    @ColumnInfo(name = "cooldownUntilMs")
    val cooldownUntilMs: Long,
    @ColumnInfo(name = "createdAtMs")
    val createdAtMs: Long,
    @ColumnInfo(name = "updatedAtMs")
    val updatedAtMs: Long
) {
    fun toDomainModel(): ManagedEndpoint {
        return ManagedEndpoint(
            endpointId = endpointId,
            name = name,
            country = country,
            countryCode = countryCode,
            host = host,
            port = port,
            protocol = protocol,
            transport = transport,
            security = security,
            sni = sni,
            pathOrServiceName = pathOrServiceName,
            enabled = enabled,
            healthy = healthy,
            priorityTier = priorityTier,
            latencyMs = latencyMs,
            stabilityScore = stabilityScore,
            assignedUserId = assignedUserId,
            assignedSubscriptionId = assignedSubscriptionId,
            allocationExpiresAt = if (allocationExpiresAtMs > 0L) SubscriptionLifecycleEngine.timestampOf(allocationExpiresAtMs) else null,
            failureCount = failureCount,
            cooldownUntil = if (cooldownUntilMs > 0L) SubscriptionLifecycleEngine.timestampOf(cooldownUntilMs) else null,
            createdAt = if (createdAtMs > 0L) SubscriptionLifecycleEngine.timestampOf(createdAtMs) else null,
            updatedAt = if (updatedAtMs > 0L) SubscriptionLifecycleEngine.timestampOf(updatedAtMs) else null
        )
    }

    companion object {
        fun fromDomainModel(ep: ManagedEndpoint, nowMs: Long = System.currentTimeMillis()): LocalManagedEndpointEntity {
            return LocalManagedEndpointEntity(
                endpointId = ep.endpointId.ifBlank { "ep_${ep.host.replace(".", "_").replace(":", "_")}_${ep.port}" },
                name = ep.name,
                country = ep.country,
                countryCode = ep.countryCode,
                host = ep.host,
                port = ep.port,
                protocol = ep.protocol,
                transport = ep.transport,
                security = ep.security,
                sni = ep.sni,
                pathOrServiceName = ep.pathOrServiceName,
                enabled = ep.enabled,
                healthy = ep.healthy,
                priorityTier = ep.priorityTier,
                latencyMs = ep.latencyMs,
                stabilityScore = ep.stabilityScore,
                assignedUserId = ep.assignedUserId,
                assignedSubscriptionId = ep.assignedSubscriptionId,
                allocationExpiresAtMs = ep.allocationExpiresAt?.toDate()?.time ?: 0L,
                failureCount = ep.failureCount,
                cooldownUntilMs = ep.cooldownUntil?.toDate()?.time ?: 0L,
                createdAtMs = ep.createdAt?.toDate()?.time ?: nowMs,
                updatedAtMs = ep.updatedAt?.toDate()?.time ?: nowMs
            )
        }
    }
}

@Dao
interface LocalManagedEndpointDao {
    @Query("SELECT * FROM local_managed_endpoints ORDER BY priorityTier DESC, stabilityScore DESC")
    fun observeAll(): Flow<List<LocalManagedEndpointEntity>>

    @Query("SELECT * FROM local_managed_endpoints ORDER BY priorityTier DESC, stabilityScore DESC")
    suspend fun getAllOnce(): List<LocalManagedEndpointEntity>

    @Query("SELECT COUNT(*) FROM local_managed_endpoints")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoreAll(items: List<LocalManagedEndpointEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: LocalManagedEndpointEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<LocalManagedEndpointEntity>)

    @Query("UPDATE local_managed_endpoints SET enabled = :enabled, healthy = :healthy, updatedAtMs = :updatedAtMs WHERE endpointId = :endpointId")
    suspend fun updateHealth(endpointId: String, enabled: Boolean, healthy: Boolean, updatedAtMs: Long)
}
