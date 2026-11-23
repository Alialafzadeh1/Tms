package com.example.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.AllocatedEndpointRecord
import com.example.data.model.AllocationHistoryRecord
import com.example.data.model.ConfigOrigin
import com.example.data.model.SubscriptionPlanId
import com.example.data.model.SubscriptionStatus
import com.example.data.model.VpnConfig
import com.example.data.subscription.SubscriptionLifecycleEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoomTombstoneAndMigrationTest {

    private lateinit var context: Context
    private lateinit var database: TmsVpnRoomDatabase
    private lateinit var repo: LocalVpnConfigRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(
            context,
            TmsVpnRoomDatabase::class.java
        ).allowMainThreadQueries().build()
        repo = LocalVpnConfigRepository(
            dao = database.vpnConfigDao(),
            tombstoneDao = database.tombstoneDao(),
            subscriptionDao = database.cachedSubscriptionDao(),
            cleanIpDao = database.cleanIpDao()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun deletingConfigsWritesTombstonesAndPreventsRemoteResurrection() = runBlocking {
        val userCfg1 = VpnConfig(
            id = "u_cfg_1",
            userId = "alice",
            name = "User Config 1",
            protocol = "vless",
            server = "de1.tmsvpn.net",
            port = 443,
            origin = ConfigOrigin.USER_IMPORTED.key
        )
        val userCfg2 = VpnConfig(
            id = "u_cfg_2",
            userId = "alice",
            name = "User Config 2",
            protocol = "trojan",
            server = "de2.tmsvpn.net",
            port = 443,
            origin = ConfigOrigin.USER_IMPORTED.key
        )
        val sysCfg = VpnConfig(
            id = "sys_core_1",
            userId = "alice",
            name = "System Core",
            protocol = "vless",
            server = "core.tmsvpn.net",
            port = 443,
            origin = ConfigOrigin.SYSTEM_MANAGED.key
        )

        repo.saveAllConfigs(listOf(userCfg1, userCfg2, sysCfg), activeUserId = "alice")

        // Delete userCfg1 + userCfg2 offline with tombstones
        repo.deleteBatchWithTombstones(
            configIds = listOf(userCfg1.id, userCfg2.id),
            userId = "alice",
            pendingRemoteDelete = true
        )

        val remaining = repo.allConfigs.first()
        assertEquals(listOf("sys_core_1"), remaining.map { it.id })

        val pendingSync = repo.getPendingRemoteDeletes("alice")
        assertEquals(setOf("u_cfg_1", "u_cfg_2"), pendingSync.toSet())

        // Simulate stale cloud snapshot still returning u_cfg_1 and u_cfg_2: MUST NOT resurrect!
        repo.saveAllConfigs(listOf(userCfg1, userCfg2, sysCfg), activeUserId = "alice")
        val afterSync = repo.allConfigs.first()
        assertEquals(listOf("sys_core_1"), afterSync.map { it.id })

        // Mark remote deletes synced
        repo.markRemoteDeletesSynced(listOf("u_cfg_1", "u_cfg_2"))
        assertTrue(repo.getPendingRemoteDeletes("alice").isEmpty())
        assertEquals(setOf("u_cfg_1", "u_cfg_2"), repo.getTombstonedIds("alice"))
    }

    @Test
    fun offlineSubscriptionAndAllocatedEndpointsCacheRoundTripsCleanly() = runBlocking {
        val now = 1_795_000_000_000L
        val (sub, history) = SubscriptionLifecycleEngine.activateAfterPaymentConfirmation(
            subscriptionId = "sub_alice",
            userId = "alice",
            userEmail = "alice@example.com",
            plan = SubscriptionPlanId.THREE_MONTHS,
            actorId = "admin",
            trustedServerNowMs = now
        )
        val endpoints = listOf(
            AllocatedEndpointRecord(
                allocationId = "alloc_1",
                userId = "alice",
                subscriptionId = "sub_alice",
                endpointId = "ep_1",
                endpointName = "Germany #1",
                country = "Germany",
                countryCode = "DE",
                host = "198.51.100.1",
                port = 443,
                protocol = "vless",
                credentialToken = "priv-token-alice",
                rawConfig = "vless://priv-token-alice@198.51.100.1:443#Germany1",
                allocationVersion = 1,
                cycleKey = "2026-11-18",
                allocationStatus = "ACTIVE",
                healthy = true,
                latencyMs = 42,
                allocatedAt = SubscriptionLifecycleEngine.timestampOf(now),
                expiresAt = SubscriptionLifecycleEngine.timestampOf(now + 86_400_000L)
            )
        )

        repo.cacheSubscription(sub, now)
        repo.cacheAllocatedEndpoints("alice", endpoints)
        repo.cacheSubscriptionHistory(listOf(history))

        val cachedSub = repo.observeCachedSubscription("alice")?.first()
        assertNotNull(cachedSub)
        assertEquals(SubscriptionStatus.ACTIVE.name, cachedSub?.status)
        assertEquals(15, cachedSub?.endpointAllocationLimit)

        val cachedEps = repo.observeCachedAllocatedEndpoints("alice")?.first().orEmpty()
        assertEquals(1, cachedEps.size)
        assertEquals("priv-token-alice", cachedEps.first().credentialToken)

        val cachedHist = repo.observeCachedHistory("alice")?.first().orEmpty()
        assertEquals(1, cachedHist.size)
        assertEquals("ACTIVATED", cachedHist.first().action)

        val allocHist = AllocationHistoryRecord(
            historyId = "hist_1",
            userId = "alice",
            subscriptionId = "sub_alice",
            planId = SubscriptionPlanId.THREE_MONTHS.id,
            endpointId = "ep_1",
            endpointName = "Germany #1",
            host = "198.51.100.1",
            port = 443,
            protocol = "vless",
            action = "ALLOCATED",
            allocationVersion = 1,
            cycleKey = "2026-11-18",
            reason = "Initial dedicated allocation",
            actorId = "admin",
            createdAt = SubscriptionLifecycleEngine.timestampOf(now)
        )
        repo.cacheAllocationHistory(listOf(allocHist))
        val cachedAllocHist = repo.observeCachedAllocationHistory("alice")?.first().orEmpty()
        assertEquals(1, cachedAllocHist.size)
        assertEquals("ALLOCATED", cachedAllocHist.first().action)
        assertEquals("Germany #1", cachedAllocHist.first().endpointName)
    }

    @Test
    fun sqlMigrationFromV1ToV2PreservesConfigsAndSetsUserImportedOrigin(): Unit = runBlocking {
        val dbName = "migration_test_v1_v2.db"
        context.deleteDatabase(dbName)

        // Create authentic v1 schema database directly via FrameworkSQLiteOpenHelperFactory
        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `local_vpn_configs` (
                        `id` TEXT NOT NULL,
                        `userId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `protocol` TEXT NOT NULL,
                        `address` TEXT NOT NULL,
                        `port` INTEGER NOT NULL,
                        `uuidOrPassword` TEXT NOT NULL,
                        `transport` TEXT NOT NULL,
                        `security` TEXT NOT NULL,
                        `sni` TEXT NOT NULL,
                        `pathOrServiceName` TEXT NOT NULL,
                        `rawConfig` TEXT NOT NULL,
                        `pingMs` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `isFavorite` INTEGER NOT NULL,
                        `notes` TEXT NOT NULL,
                        `createdAtMs` INTEGER NOT NULL,
                        `updatedAtMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_local_vpn_configs_userId` ON `local_vpn_configs` (`userId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_local_vpn_configs_protocol` ON `local_vpn_configs` (`protocol`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_local_vpn_configs_isFavorite` ON `local_vpn_configs` (`isFavorite`)")
                db.execSQL(
                    """
                    INSERT INTO local_vpn_configs (id, userId, name, protocol, address, port, uuidOrPassword, transport, security, sni, pathOrServiceName, rawConfig, pingMs, status, isFavorite, notes, createdAtMs, updatedAtMs)
                    VALUES ('legacy_1', 'alice', 'Legacy Server', 'vless', 'legacy.tmsvpn.net', 443, 'uuid-1', 'ws', 'tls', 'legacy.tmsvpn.net', '/ws', 'vless://uuid-1@legacy.tmsvpn.net:443', 55, 'online', 1, '', 1000, 1000)
                    """.trimIndent()
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
        }

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(callback)
                .build()
        )
        helper.writableDatabase.close()

        // Open v6 Room database with MIGRATION_1_2 .. MIGRATION_5_6
        val migratedDb = Room.databaseBuilder(
            context,
            TmsVpnRoomDatabase::class.java,
            dbName
        ).addMigrations(
            TmsVpnRoomDatabase.MIGRATION_1_2,
            TmsVpnRoomDatabase.MIGRATION_2_3,
            TmsVpnRoomDatabase.MIGRATION_3_4,
            TmsVpnRoomDatabase.MIGRATION_4_5,
            TmsVpnRoomDatabase.MIGRATION_5_6
        )
            .allowMainThreadQueries()
            .build()

        val migratedEntity = migratedDb.vpnConfigDao().getById("legacy_1")
        assertNotNull(migratedEntity)
        assertEquals("Legacy Server", migratedEntity?.name)
        assertEquals(ConfigOrigin.USER_IMPORTED.key, migratedEntity?.origin)
        assertFalse("legacy_1" in migratedDb.tombstoneDao().getTombstonedIds("alice"))

        migratedDb.cachedSubscriptionDao().upsertAllocationHistory(
            listOf(
                CachedAllocationHistoryEntity(
                    historyId = "mig_h1",
                    userId = "alice",
                    subscriptionId = "sub_alice",
                    planId = SubscriptionPlanId.ONE_MONTH.id,
                    endpointId = "ep_9",
                    endpointName = "Finland VIP",
                    host = "198.51.100.9",
                    port = 443,
                    protocol = "vless",
                    action = "ALLOCATED",
                    allocationVersion = 1,
                    cycleKey = "2026-11-18",
                    reason = "Post-migration v3 check",
                    actorId = "admin",
                    createdAtMs = 1_795_000_000_000L
                )
            )
        )
        val postMigHistory = migratedDb.cachedSubscriptionDao().observeAllocationHistory("alice").first()
        assertEquals(1, postMigHistory.size)
        assertEquals("Finland VIP", postMigHistory.first().endpointName)

        // Verify v4+ clean_ip_pool table works right after v1->v5 migration
        val expectedSeedCount = CleanIpSeedData.totalSeedCount()
        migratedDb.cleanIpDao().insertIgnoreAll(CleanIpSeedData.buildSeedEntities(1_795_000_000_000L))
        assertEquals(expectedSeedCount, migratedDb.cleanIpDao().getCount())

        migratedDb.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun cleanIpPoolSeedsAll150HostsAndTracksHealthAndAvailability() = runBlocking {
        val expectedTotal = CleanIpSeedData.totalSeedCount()
        assertTrue("Expected expanded Clean IP pool >= 300 hosts", expectedTotal >= 300)
        val inserted = repo.seedCleanIpPoolIfNeeded(nowMs = 1_795_000_000_000L)
        assertEquals(expectedTotal, inserted)
        // Re-seeding must be idempotent
        assertEquals(0, repo.seedCleanIpPoolIfNeeded(nowMs = 1_795_000_001_000L))

        val pool = repo.observeCleanIpPool()?.first().orEmpty()
        assertEquals(expectedTotal, pool.size)
        assertEquals(CleanIpSeedData.IPV4_LIST.size, pool.count { it.addressType == CleanIpAddressType.IPV4.name })
        assertEquals(CleanIpSeedData.IPV6_LIST.size, pool.count { it.addressType == CleanIpAddressType.IPV6.name })
        assertEquals(CleanIpSeedData.DOMAIN_LIST.size, pool.count { it.addressType == CleanIpAddressType.DOMAIN.name })
        assertTrue(pool.all { it.isAvailable && it.healthStatus == CleanIpHealthStatus.UNTESTED.name })

        // Verify a few spot checks across all 3 categories
        val sampleIpv4 = database.cleanIpDao().getByHost("69.48.218.1")
        val sampleIpv6 = database.cleanIpDao().getByHost("2606:4700:3037::6815:2826")
        val sampleDomain = database.cleanIpDao().getByHost("zagrox.com")
        assertNotNull(sampleIpv4)
        assertNotNull(sampleIpv6)
        assertNotNull(sampleDomain)

        // Successful health probe transitions host to HEALTHY
        repo.recordCleanIpHealth(host = "69.48.218.1", isReachable = true, latencyMs = 42, checkedAtMs = 1_795_000_100_000L)
        val healthyItem = database.cleanIpDao().getByHost("69.48.218.1")!!
        assertEquals(CleanIpHealthStatus.HEALTHY.name, healthyItem.healthStatus)
        assertEquals(42, healthyItem.latencyMs)
        assertEquals(98, healthyItem.stabilityScore)
        assertEquals(0, healthyItem.failureCount)

        // Repeated failures escalate to QUARANTINED and mark unavailable
        repo.recordCleanIpHealth(host = "zagrox.com", isReachable = false, latencyMs = -1, checkedAtMs = 1_795_000_200_000L)
        repo.recordCleanIpHealth(host = "zagrox.com", isReachable = false, latencyMs = -1, checkedAtMs = 1_795_000_201_000L)
        repo.recordCleanIpHealth(host = "zagrox.com", isReachable = false, latencyMs = -1, checkedAtMs = 1_795_000_202_000L)
        val quarantined = database.cleanIpDao().getByHost("zagrox.com")!!
        assertEquals(CleanIpHealthStatus.QUARANTINED.name, quarantined.healthStatus)
        assertEquals(3, quarantined.failureCount)
        assertFalse(quarantined.isAvailable)

        // Toggle availability & user assignment tracking
        repo.updateCleanIpAvailability(
            host = "104.16.190.196",
            isAvailable = false,
            userId = "alice",
            subscriptionId = "sub_alice",
            updatedAtMs = 1_795_000_300_000L
        )
        val assigned = database.cleanIpDao().getByHost("104.16.190.196")!!
        assertFalse(assigned.isAvailable)
        assertEquals("alice", assigned.assignedUserId)
        assertEquals("sub_alice", assigned.assignedSubscriptionId)
    }
}
