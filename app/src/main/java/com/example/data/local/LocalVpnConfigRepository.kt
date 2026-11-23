package com.example.data.local

import android.content.Context
import com.example.data.model.AllocatedEndpointRecord
import com.example.data.model.AllocationHistoryRecord
import com.example.data.model.ConfigOrigin
import com.example.data.model.DefaultVpnConfigs
import com.example.data.model.ManagedEndpoint
import com.example.data.model.PaymentRequestRecord
import com.example.data.model.SubscriptionHistoryEvent
import com.example.data.model.UserProfile
import com.example.data.model.UserSubscription
import com.example.data.model.VpnConfig
import com.example.data.subscription.BatchRotationResult
import com.example.data.subscription.DedicatedIpAllocationEngine
import com.example.data.subscription.SubscriptionLifecycleEngine
import com.example.data.subscription.UserRotationOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Repository to manage locally stored VPN configurations, deletion tombstones,
 * Clean IP pool, registered users, local payments, managed endpoint inventory,
 * and subscription allocation caches via Room.
 */
class LocalVpnConfigRepository(
    private val dao: LocalVpnConfigDao,
    private val tombstoneDao: DeletedConfigTombstoneDao? = null,
    private val subscriptionDao: CachedSubscriptionDao? = null,
    private val cleanIpDao: CleanIpDao? = null,
    private val registeredUserDao: RegisteredUserDao? = null,
    private val paymentRequestDao: LocalPaymentRequestDao? = null,
    private val managedEndpointDao: LocalManagedEndpointDao? = null
) {

    constructor(context: Context) : this(
        dao = TmsVpnRoomDatabase.getDatabase(context).vpnConfigDao(),
        tombstoneDao = TmsVpnRoomDatabase.getDatabase(context).tombstoneDao(),
        subscriptionDao = TmsVpnRoomDatabase.getDatabase(context).cachedSubscriptionDao(),
        cleanIpDao = TmsVpnRoomDatabase.getDatabase(context).cleanIpDao(),
        registeredUserDao = TmsVpnRoomDatabase.getDatabase(context).registeredUserDao(),
        paymentRequestDao = TmsVpnRoomDatabase.getDatabase(context).localPaymentRequestDao(),
        managedEndpointDao = TmsVpnRoomDatabase.getDatabase(context).localManagedEndpointDao()
    )

    val allEntities: Flow<List<LocalVpnConfigEntity>> = dao.observeAll()

    val allConfigs: Flow<List<VpnConfig>> = dao.observeAll().map { list ->
        list.map { it.toDomainModel() }
    }

    fun observeUserConfigs(userId: String): Flow<List<VpnConfig>> =
        dao.observeByUserId(userId).map { list -> list.map { it.toDomainModel() } }

    fun observeByProtocol(protocol: String): Flow<List<VpnConfig>> =
        dao.observeByProtocol(protocol).map { list -> list.map { it.toDomainModel() } }

    fun observeFavorites(): Flow<List<VpnConfig>> =
        dao.observeFavorites().map { list -> list.map { it.toDomainModel() } }

    suspend fun getById(id: String): VpnConfig? = withContext(Dispatchers.IO) {
        dao.getById(id)?.toDomainModel()
    }

    suspend fun getAllOnce(): List<VpnConfig> = withContext(Dispatchers.IO) {
        dao.getAllOnce().map { it.toDomainModel() }
    }

    suspend fun saveConfig(config: VpnConfig, notes: String = "") = withContext(Dispatchers.IO) {
        if (config.id.isNotBlank()) {
            tombstoneDao?.removeTombstones(listOf(config.id))
        }
        dao.insert(LocalVpnConfigEntity.fromDomainModel(config, notes))
    }

    suspend fun saveAllConfigs(configs: List<VpnConfig>, activeUserId: String = "") = withContext(Dispatchers.IO) {
        val tombstonedIds = if (activeUserId.isNotBlank()) {
            tombstoneDao?.getTombstonedIds(activeUserId)?.toSet().orEmpty()
        } else {
            emptySet()
        }
        val safeList = configs.filter { it.id !in tombstonedIds }
        if (safeList.isNotEmpty()) {
            dao.insertAll(safeList.map { LocalVpnConfigEntity.fromDomainModel(it) })
        }
    }

    suspend fun updateConfig(config: VpnConfig, notes: String = "") = withContext(Dispatchers.IO) {
        dao.update(LocalVpnConfigEntity.fromDomainModel(config, notes))
    }

    suspend fun updatePingStatus(id: String, pingMs: Int, status: String) = withContext(Dispatchers.IO) {
        dao.updatePingStatus(id = id, pingMs = pingMs, status = status)
    }

    suspend fun updateFavorite(id: String, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        dao.updateFavorite(id = id, isFavorite = isFavorite)
    }

    suspend fun deleteById(id: String) = withContext(Dispatchers.IO) {
        dao.deleteById(id)
    }

    suspend fun deleteWithTombstone(
        configId: String,
        userId: String,
        pendingRemoteDelete: Boolean = true
    ) = withContext(Dispatchers.IO) {
        tombstoneDao?.upsertAll(
            listOf(
                DeletedConfigTombstoneEntity(
                    configId = configId,
                    userId = userId,
                    deletedAtMs = System.currentTimeMillis(),
                    pendingRemoteDelete = pendingRemoteDelete
                )
            )
        )
        dao.deleteById(configId)
    }

    suspend fun deleteBatchWithTombstones(
        configIds: List<String>,
        userId: String,
        pendingRemoteDelete: Boolean = true
    ) = withContext(Dispatchers.IO) {
        val uniqueIds = configIds.filter { it.isNotBlank() }.distinct()
        if (uniqueIds.isEmpty()) return@withContext
        val now = System.currentTimeMillis()
        tombstoneDao?.upsertAll(
            uniqueIds.map { id ->
                DeletedConfigTombstoneEntity(
                    configId = id,
                    userId = userId,
                    deletedAtMs = now,
                    pendingRemoteDelete = pendingRemoteDelete
                )
            }
        )
        uniqueIds.chunked(200).forEach { chunk ->
            dao.deleteByIds(chunk)
        }
    }

    suspend fun getTombstonedIds(userId: String): Set<String> = withContext(Dispatchers.IO) {
        tombstoneDao?.getTombstonedIds(userId)?.toSet().orEmpty()
    }

    suspend fun getPendingRemoteDeletes(userId: String): List<String> = withContext(Dispatchers.IO) {
        tombstoneDao?.getPendingRemoteDeletes(userId)?.map { it.configId }.orEmpty()
    }

    suspend fun markRemoteDeletesSynced(configIds: List<String>) = withContext(Dispatchers.IO) {
        if (configIds.isNotEmpty()) {
            tombstoneDao?.markRemoteSynced(configIds)
        }
    }

    suspend fun replaceSubscriptionConfigs(
        userId: String,
        allocations: List<AllocatedEndpointRecord>
    ) = withContext(Dispatchers.IO) {
        dao.deleteByOrigin(ConfigOrigin.SUBSCRIPTION_ASSIGNED.key)
        val mapped = allocations
            .filter { it.allocationStatus == "ACTIVE" && it.rawConfig.isNotBlank() }
            .map { alloc ->
                LocalVpnConfigEntity.fromDomainModel(alloc.toVpnConfig().copy(userId = userId))
            }
        if (mapped.isNotEmpty()) {
            dao.insertAll(mapped)
        }
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        dao.clearAll()
    }

    suspend fun prepopulateDefaultsIfEmpty(userId: String = "local_user") = withContext(Dispatchers.IO) {
        val existingIds = dao.getAllOnce().map { it.id }.toSet()
        val tombstonedIds = tombstoneDao?.getTombstonedIds(userId)?.toSet().orEmpty()
        val defaults = DefaultVpnConfigs.getDefaultConfigs(userId)
            .filter { it.id !in existingIds && it.id !in tombstonedIds }
        if (defaults.isNotEmpty()) {
            dao.insertAll(defaults.map { LocalVpnConfigEntity.fromDomainModel(it) })
        }
    }

    // --- REGISTERED USERS DIRECTORY ---

    fun observeRegisteredUsers(): Flow<List<UserProfile>>? =
        registeredUserDao?.observeAll()?.map { list -> list.map { it.toUserProfile() } }

    suspend fun getRegisteredUsersOnce(): List<UserProfile> = withContext(Dispatchers.IO) {
        registeredUserDao?.getAllOnce()?.map { it.toUserProfile() }.orEmpty()
    }

    suspend fun upsertRegisteredUser(entity: RegisteredUserEntity) = withContext(Dispatchers.IO) {
        val daoRef = registeredUserDao ?: return@withContext
        val existing = daoRef.getByEmail(entity.email)
        if (existing != null) {
            daoRef.upsert(
                existing.copy(
                    userId = entity.userId,
                    displayName = entity.displayName.ifBlank { existing.displayName },
                    role = entity.role.ifBlank { existing.role },
                    maxDevices = maxOf(existing.maxDevices, entity.maxDevices),
                    dataLimitBytes = maxOf(existing.dataLimitBytes, entity.dataLimitBytes),
                    updatedAtMs = entity.updatedAtMs
                )
            )
        } else {
            daoRef.upsert(entity)
        }
    }

    suspend fun upsertRegisteredUsers(entities: List<RegisteredUserEntity>) = withContext(Dispatchers.IO) {
        entities.forEach { upsertRegisteredUser(it) }
    }

    suspend fun syncRegisteredUserSubscriptionStatus(userId: String, status: String) = withContext(Dispatchers.IO) {
        registeredUserDao?.updateSubscriptionStatus(userId, status, System.currentTimeMillis())
    }

    suspend fun updateRegisteredUserQuota(userId: String, maxDevices: Int, dataLimitBytes: Double) = withContext(Dispatchers.IO) {
        registeredUserDao?.updateQuota(userId, maxDevices, dataLimitBytes, System.currentTimeMillis())
    }

    suspend fun recordTrafficUsageDelta(
        userId: String,
        deltaBytes: Long,
        updatedAtMs: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        if (userId.isBlank() || deltaBytes <= 0L) return@withContext
        registeredUserDao?.incrementDataUsage(
            userId = userId,
            deltaBytes = deltaBytes.toDouble(),
            updatedAtMs = updatedAtMs
        )
    }

    // --- SUBSCRIPTION & ALLOCATION CACHES (USER + ADMIN) ---

    fun observeCachedSubscription(userId: String): Flow<UserSubscription?>? =
        subscriptionDao?.observeSubscription(userId)?.map { it?.toDomainModel() }

    fun observeAllCachedSubscriptions(): Flow<List<UserSubscription>>? =
        subscriptionDao?.observeAllSubscriptions()?.map { list -> list.map { it.toDomainModel() } }

    suspend fun cacheSubscription(sub: UserSubscription, syncedServerNowMs: Long) = withContext(Dispatchers.IO) {
        subscriptionDao?.upsertSubscription(CachedSubscriptionEntity.fromDomainModel(sub, syncedServerNowMs))
    }

    fun observeCachedAllocatedEndpoints(userId: String): Flow<List<AllocatedEndpointRecord>>? =
        subscriptionDao?.observeActiveEndpoints(userId)?.map { list -> list.map { it.toDomainModel() } }

    suspend fun cacheAllocatedEndpoints(userId: String, records: List<AllocatedEndpointRecord>) = withContext(Dispatchers.IO) {
        subscriptionDao?.clearEndpointsForUser(userId)
        if (records.isNotEmpty()) {
            subscriptionDao?.upsertEndpoints(records.map { CachedAllocatedEndpointEntity.fromDomainModel(it) })
        }
    }

    fun observeCachedHistory(userId: String): Flow<List<SubscriptionHistoryEvent>>? =
        subscriptionDao?.observeHistory(userId)?.map { list -> list.map { it.toDomainModel() } }

    suspend fun cacheSubscriptionHistory(events: List<SubscriptionHistoryEvent>) = withContext(Dispatchers.IO) {
        if (events.isNotEmpty()) {
            subscriptionDao?.upsertHistory(events.map { CachedSubscriptionHistoryEntity.fromDomainModel(it) })
        }
    }

    fun observeCachedAllocationHistory(userId: String): Flow<List<AllocationHistoryRecord>>? =
        subscriptionDao?.observeAllocationHistory(userId)?.map { list -> list.map { it.toDomainModel() } }

    fun observeAllCachedAllocationHistory(): Flow<List<AllocationHistoryRecord>>? =
        subscriptionDao?.observeAllAllocationHistory()?.map { list -> list.map { it.toDomainModel() } }

    suspend fun cacheAllocationHistory(records: List<AllocationHistoryRecord>) = withContext(Dispatchers.IO) {
        if (records.isNotEmpty()) {
            subscriptionDao?.upsertAllocationHistory(records.map { CachedAllocationHistoryEntity.fromDomainModel(it) })
        }
    }

    // --- LOCAL PAYMENT REQUESTS ---

    fun observeLocalPaymentRequestsForUser(userId: String): Flow<List<PaymentRequestRecord>>? =
        paymentRequestDao?.observeByUser(userId)?.map { list -> list.map { it.toDomainModel() } }

    fun observeAllLocalPaymentRequests(): Flow<List<PaymentRequestRecord>>? =
        paymentRequestDao?.observeAll()?.map { list -> list.map { it.toDomainModel() } }

    suspend fun saveLocalPaymentRequest(record: PaymentRequestRecord) = withContext(Dispatchers.IO) {
        paymentRequestDao?.upsert(LocalPaymentRequestEntity.fromDomainModel(record))
    }

    suspend fun cachePaymentRequests(records: List<PaymentRequestRecord>) = withContext(Dispatchers.IO) {
        if (records.isNotEmpty()) {
            paymentRequestDao?.upsertAll(records.map { LocalPaymentRequestEntity.fromDomainModel(it) })
        }
    }

    suspend fun markLocalPaymentStatus(
        paymentId: String,
        status: String,
        verifiedBy: String,
        note: String,
        verifiedAtMs: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        paymentRequestDao?.markVerified(paymentId, status, verifiedBy, note, verifiedAtMs)
    }

    // --- MANAGED ENDPOINT INVENTORY & LOCAL ATOMIC ALLOCATION ENGINE ---

    fun observeManagedEndpoints(): Flow<List<ManagedEndpoint>>? =
        managedEndpointDao?.observeAll()?.map { list -> list.map { it.toDomainModel() } }

    suspend fun getManagedEndpointsOnce(): List<ManagedEndpoint> = withContext(Dispatchers.IO) {
        managedEndpointDao?.getAllOnce()?.map { it.toDomainModel() }.orEmpty()
    }

    suspend fun upsertLocalManagedEndpoint(endpoint: ManagedEndpoint) = withContext(Dispatchers.IO) {
        managedEndpointDao?.upsert(LocalManagedEndpointEntity.fromDomainModel(endpoint))
    }

    suspend fun upsertLocalManagedEndpoints(endpoints: List<ManagedEndpoint>) = withContext(Dispatchers.IO) {
        if (endpoints.isNotEmpty()) {
            managedEndpointDao?.upsertAll(endpoints.map { LocalManagedEndpointEntity.fromDomainModel(it) })
        }
    }

    suspend fun updateLocalManagedEndpointHealth(
        endpointId: String,
        enabled: Boolean,
        healthy: Boolean
    ) = withContext(Dispatchers.IO) {
        managedEndpointDao?.updateHealth(endpointId, enabled, healthy, System.currentTimeMillis())
    }

    /**
     * Ensures 75+ dedicated endpoints (DE/TR/FI/NL/US + Clean IPv4 edge candidates)
     * exist in the inventory so 5 / 15 / 50 endpoint plans allocate cleanly without shortage.
     */
    suspend fun prepopulateEndpointInventoryIfNeeded(nowMs: Long = System.currentTimeMillis()): Int = withContext(Dispatchers.IO) {
        val epDao = managedEndpointDao ?: return@withContext 0
        val existingCount = epDao.getCount()
        val targetPoolSize = 75
        if (existingCount >= targetPoolSize) return@withContext 0

        val regionalSpecs = listOf(
            Triple("Germany", "DE", "speed.cloudflare.com"),
            Triple("Turkey", "TR", "cdnjs.cloudflare.com"),
            Triple("Germany", "DE", "cdn.jsdelivr.net"),
            Triple("Turkey", "TR", "speedtest.net"),
            Triple("Finland", "FI", "cloudflare.com"),
            Triple("Netherlands", "NL", "pages.dev")
        )
        val protocols = listOf("vless", "trojan", "vmess")
        val candidateIps = CleanIpSeedData.IPV4_LIST.take(targetPoolSize)
        val entities = candidateIps.mapIndexed { index, ip ->
            val (country, code, sni) = regionalSpecs[index % regionalSpecs.size]
            val proto = protocols[index % protocols.size]
            val priority = when {
                index % 3 == 0 -> 3
                index % 2 == 0 -> 2
                else -> 1
            }
            val safeId = "ep_${code.lowercase()}_${ip.replace(".", "_")}_$proto"
            LocalManagedEndpointEntity(
                endpointId = safeId,
                name = "$code Dedicated #${index + 1} ($proto)",
                country = country,
                countryCode = code,
                host = ip,
                port = 443,
                protocol = proto,
                transport = "ws",
                security = "tls",
                sni = sni,
                pathOrServiceName = "/tms-${code.lowercase()}-$proto",
                enabled = true,
                healthy = true,
                priorityTier = priority,
                latencyMs = -1,
                stabilityScore = 95,
                assignedUserId = "",
                assignedSubscriptionId = "",
                allocationExpiresAtMs = 0L,
                failureCount = 0,
                cooldownUntilMs = 0L,
                createdAtMs = nowMs,
                updatedAtMs = nowMs
            )
        }
        val before = epDao.getCount()
        epDao.insertIgnoreAll(entities)
        (epDao.getCount() - before).coerceAtLeast(0)
    }

    suspend fun executeLocalAllocationOrRotation(
        targetSubscriptions: List<UserSubscription>,
        activeConnectedEndpointByUser: Map<String, String?>,
        actorUid: String,
        triggerMode: String,
        nowMs: Long = System.currentTimeMillis(),
        forceManual: Boolean = false
    ): BatchRotationResult = withContext(Dispatchers.IO) {
        val epDao = managedEndpointDao
        val subDao = subscriptionDao
        prepopulateEndpointInventoryIfNeeded(nowMs)

        val inventory = epDao?.getAllOnce()?.map { it.toDomainModel() }.orEmpty()
        val existingAllocs = subDao?.getAllActiveEndpointsOnce()?.map { it.toDomainModel() }.orEmpty()
        val existingByUser = existingAllocs.groupBy { it.userId }
        val credentialsByUser = targetSubscriptions.associate { sub ->
            val existingCred = existingByUser[sub.userId]?.firstOrNull { it.credentialToken.isNotBlank() }?.credentialToken
            sub.userId to (existingCred ?: UUID.nameUUIDFromBytes("tms_${sub.userId}".toByteArray()).toString())
        }

        val batch = DedicatedIpAllocationEngine.runDailyRotationBatch(
            subscriptions = targetSubscriptions,
            inventory = inventory,
            existingAllocationsByUser = existingByUser,
            credentialsByUser = credentialsByUser,
            activeEndpointByUser = activeConnectedEndpointByUser,
            triggeredByUid = actorUid,
            triggerMode = triggerMode,
            trustedServerNowMs = nowMs,
            forceManual = forceManual
        )

        if (batch.updatedInventory.isNotEmpty()) {
            epDao?.upsertAll(batch.updatedInventory.map { LocalManagedEndpointEntity.fromDomainModel(it, nowMs) })
        }
        batch.updatedSubscriptions.forEach { sub ->
            subDao?.upsertSubscription(CachedSubscriptionEntity.fromDomainModel(sub, nowMs))
            syncRegisteredUserSubscriptionStatus(
                userId = sub.userId,
                status = if (sub.status.equals("ACTIVE", ignoreCase = true)) "active" else sub.status.lowercase()
            )
        }
        batch.allocationsByUser.forEach { (uid, allocs) ->
            subDao?.clearEndpointsForUser(uid)
            if (allocs.isNotEmpty()) {
                subDao?.upsertEndpoints(allocs.map { CachedAllocatedEndpointEntity.fromDomainModel(it) })
            }
            replaceSubscriptionConfigs(uid, allocs)
        }
        val allHistories = batch.allocationHistoryByUser.values.flatten()
        if (allHistories.isNotEmpty()) {
            subDao?.upsertAllocationHistory(allHistories.map { CachedAllocationHistoryEntity.fromDomainModel(it) })
        }
        batch
    }

    suspend fun recordLocalEndpointFailureAndFailover(
        endpointId: String,
        targetUserId: String,
        subscription: UserSubscription?,
        activeConnectedEndpointId: String?,
        actorUid: String,
        nowMs: Long = System.currentTimeMillis()
    ): UserRotationOutcome? = withContext(Dispatchers.IO) {
        val epDao = managedEndpointDao ?: return@withContext null
        val subDao = subscriptionDao
        val current = epDao.getAllOnce().firstOrNull { it.endpointId == endpointId }?.toDomainModel()
            ?: return@withContext null
        val next = DedicatedIpAllocationEngine.recordEndpointFailure(current, nowMs)
        epDao.upsert(LocalManagedEndpointEntity.fromDomainModel(next, nowMs))

        val hist = AllocationHistoryRecord(
            historyId = "hist_fail_${endpointId}_$nowMs",
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
            cycleKey = SubscriptionLifecycleEngine.utcCycleKey(nowMs),
            reason = "Failure #${next.failureCount} recorded; cooldown applied",
            actorId = actorUid,
            createdAt = SubscriptionLifecycleEngine.timestampOf(nowMs)
        )
        subDao?.upsertAllocationHistory(listOf(CachedAllocationHistoryEntity.fromDomainModel(hist)))

        if (subscription != null && (!next.healthy || next.cooldownUntil != null)) {
            val res = executeLocalAllocationOrRotation(
                targetSubscriptions = listOf(subscription),
                activeConnectedEndpointByUser = mapOf(targetUserId to activeConnectedEndpointId),
                actorUid = actorUid,
                triggerMode = "FAILOVER_REPLACE",
                nowMs = nowMs,
                forceManual = false
            )
            UserRotationOutcome(
                updatedSubscription = res.updatedSubscriptions.firstOrNull() ?: subscription,
                updatedInventory = res.updatedInventory,
                activeAllocations = res.allocationsByUser[targetUserId].orEmpty(),
                rotatedOutAllocations = res.rotatedOutByUser[targetUserId].orEmpty(),
                shortageAlert = res.shortageAlerts.firstOrNull(),
                skippedIdempotent = res.auditLog.status == "SKIPPED_IDEMPOTENT",
                replacedCount = res.auditLog.replacedEndpointsCount,
                allocationHistoryEvents = res.allocationHistoryByUser[targetUserId].orEmpty()
            )
        } else {
            null
        }
    }

    // --- CLEAN IP & EDGE DOMAIN POOL MANAGEMENT ---

    fun observeCleanIpPool(): Flow<List<CleanIpEntity>>? = cleanIpDao?.observeAll()

    fun observeHealthyAvailableCleanIps(): Flow<List<CleanIpEntity>>? = cleanIpDao?.observeHealthyAvailable()

    suspend fun getCleanIpPoolOnce(): List<CleanIpEntity> = withContext(Dispatchers.IO) {
        cleanIpDao?.getAllOnce().orEmpty()
    }

    suspend fun seedCleanIpPoolIfNeeded(nowMs: Long = System.currentTimeMillis()): Int = withContext(Dispatchers.IO) {
        val daoRef = cleanIpDao ?: return@withContext 0
        val seeds = CleanIpSeedData.buildSeedEntities(nowMs)
        val existingHosts = daoRef.getAllHostsOnce().toHashSet()
        val missing = seeds.filter { it.host !in existingHosts }
        if (missing.isEmpty()) return@withContext 0
        val beforeCount = daoRef.getCount()
        daoRef.insertIgnoreAll(missing)
        val afterCount = daoRef.getCount()
        (afterCount - beforeCount).coerceAtLeast(0)
    }

    suspend fun importBulkCleanIps(
        rawText: String,
        sourceTag: String = "BULK_CIDR_IMPORT"
    ): Int = withContext(Dispatchers.IO) {
        val daoRef = cleanIpDao ?: return@withContext 0
        val parsed = CleanIpSeedData.parseBulkInput(rawText).map { entity ->
            entity.copy(sourceTag = sourceTag)
        }
        if (parsed.isEmpty()) return@withContext 0
        val before = daoRef.getCount()
        daoRef.insertIgnoreAll(parsed)
        (daoRef.getCount() - before).coerceAtLeast(0)
    }

    /**
     * Accepts IP-only / host-only / CIDR lines (without requiring port, country, or protocol)
     * and inserts them simultaneously into the Clean IP pool AND Managed Endpoint inventory (defaults: 443, VLESS/WS/TLS).
     */
    suspend fun addIpOnlyEndpoints(
        rawText: String,
        sourceTag: String = "ADMIN_IP_ONLY",
        nowMs: Long = System.currentTimeMillis()
    ): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val parsedIps = CleanIpSeedData.parseBulkInput(rawText).map {
            it.copy(sourceTag = sourceTag, createdAtMs = nowMs, updatedAtMs = nowMs)
        }
        if (parsedIps.isEmpty()) return@withContext (0 to 0)

        val cleanInserted = cleanIpDao?.let { daoRef ->
            val before = daoRef.getCount()
            daoRef.insertIgnoreAll(parsedIps)
            (daoRef.getCount() - before).coerceAtLeast(0)
        } ?: 0

        val epInserted = managedEndpointDao?.let { epDao ->
            val regionalSpecs = listOf(
                Triple("Germany", "DE", "speed.cloudflare.com"),
                Triple("Turkey", "TR", "cdnjs.cloudflare.com")
            )
            val endpointEntities = parsedIps.mapIndexed { idx, ipEntity ->
                val (country, code, sni) = regionalSpecs[idx % regionalSpecs.size]
                val safeId = "ep_ip_${ipEntity.host.replace(":", "_").replace(".", "_")}"
                LocalManagedEndpointEntity(
                    endpointId = safeId,
                    name = "$code Edge (${ipEntity.host})",
                    country = country,
                    countryCode = code,
                    host = ipEntity.host,
                    port = 443,
                    protocol = "vless",
                    transport = "ws",
                    security = "tls",
                    sni = sni,
                    pathOrServiceName = "/tms-edge",
                    enabled = true,
                    healthy = true,
                    priorityTier = 3,
                    latencyMs = -1,
                    stabilityScore = 95,
                    assignedUserId = "",
                    assignedSubscriptionId = "",
                    allocationExpiresAtMs = 0L,
                    failureCount = 0,
                    cooldownUntilMs = 0L,
                    createdAtMs = nowMs,
                    updatedAtMs = nowMs
                )
            }
            val beforeEp = epDao.getCount()
            epDao.insertIgnoreAll(endpointEntities)
            (epDao.getCount() - beforeEp).coerceAtLeast(0)
        } ?: 0

        cleanInserted to epInserted
    }

    suspend fun addCustomCleanIps(
        rawHosts: List<String>,
        sourceTag: String = "ADMIN_CUSTOM",
        nowMs: Long = System.currentTimeMillis()
    ): Int = withContext(Dispatchers.IO) {
        val daoRef = cleanIpDao ?: return@withContext 0
        val parsed = CleanIpSeedData.parseBulkInput(rawHosts.joinToString("\n"))
            .map { it.copy(sourceTag = sourceTag, createdAtMs = nowMs, updatedAtMs = nowMs) }
        if (parsed.isEmpty()) return@withContext 0
        val before = daoRef.getCount()
        daoRef.insertIgnoreAll(parsed)
        (daoRef.getCount() - before).coerceAtLeast(0)
    }

    suspend fun recordCleanIpHealth(
        host: String,
        isReachable: Boolean,
        latencyMs: Int,
        checkedAtMs: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        val daoRef = cleanIpDao ?: return@withContext
        val current = daoRef.getByHost(host) ?: return@withContext
        val nextFailures = if (isReachable) 0 else current.failureCount + 1
        val nextStatus = when {
            isReachable && latencyMs in 1..900 -> CleanIpHealthStatus.HEALTHY.name
            isReachable -> CleanIpHealthStatus.DEGRADED.name
            nextFailures >= 3 -> CleanIpHealthStatus.QUARANTINED.name
            else -> CleanIpHealthStatus.UNHEALTHY.name
        }
        val score = when {
            !isReachable -> 0
            latencyMs < 100 -> 98
            latencyMs < 220 -> 90
            latencyMs < 450 -> 78
            else -> 62
        }
        daoRef.recordHealthProbe(
            host = host,
            healthStatus = nextStatus,
            latencyMs = if (isReachable) latencyMs else -1,
            stabilityScore = score,
            isSuccess = isReachable,
            checkedAtMs = checkedAtMs
        )
        if (nextStatus == CleanIpHealthStatus.QUARANTINED.name) {
            daoRef.updateHealthAndAvailability(
                host = host,
                healthStatus = nextStatus,
                isAvailable = false,
                updatedAtMs = checkedAtMs
            )
        }
    }

    suspend fun updateCleanIpAvailability(
        host: String,
        isAvailable: Boolean,
        userId: String = "",
        subscriptionId: String = "",
        updatedAtMs: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        cleanIpDao?.updateAvailability(
            host = host,
            isAvailable = isAvailable,
            assignedUserId = userId,
            assignedSubscriptionId = subscriptionId,
            updatedAtMs = updatedAtMs
        )
    }

    suspend fun deleteCleanIp(host: String) = withContext(Dispatchers.IO) {
        cleanIpDao?.deleteByHost(host)
    }
}

