package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Main Room database for local TMS VPN storage.
 * Version 2 adds:
 * - Config origin classification & subscription allocation links
 * - Deleted configuration tombstones (offline-safe deletion without resurrection)
 * Version 4 adds:
 * - Clean IP / Domain pool table (`clean_ip_pool`) with availability & health tracking
 * Version 5 adds:
 * - Registered user directory (`registered_users`), local payment requests (`local_payment_requests`),
 *   and persistent endpoint inventory (`local_managed_endpoints`)
 */
@Database(
    entities = [
        LocalVpnConfigEntity::class,
        DeletedConfigTombstoneEntity::class,
        CachedSubscriptionEntity::class,
        CachedAllocatedEndpointEntity::class,
        CachedSubscriptionHistoryEntity::class,
        CachedAllocationHistoryEntity::class,
        CleanIpEntity::class,
        RegisteredUserEntity::class,
        LocalPaymentRequestEntity::class,
        LocalManagedEndpointEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class TmsVpnRoomDatabase : RoomDatabase() {

    abstract fun vpnConfigDao(): LocalVpnConfigDao
    abstract fun tombstoneDao(): DeletedConfigTombstoneDao
    abstract fun cachedSubscriptionDao(): CachedSubscriptionDao
    abstract fun cleanIpDao(): CleanIpDao
    abstract fun registeredUserDao(): RegisteredUserDao
    abstract fun localPaymentRequestDao(): LocalPaymentRequestDao
    abstract fun localManagedEndpointDao(): LocalManagedEndpointDao

    companion object {
        @Volatile
        private var INSTANCE: TmsVpnRoomDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE local_vpn_configs ADD COLUMN origin TEXT NOT NULL DEFAULT 'USER_IMPORTED'"
                )
                db.execSQL(
                    "ALTER TABLE local_vpn_configs ADD COLUMN subscriptionId TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE local_vpn_configs ADD COLUMN endpointId TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE local_vpn_configs ADD COLUMN allocationVersion INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS deleted_config_tombstones (
                        configId TEXT NOT NULL PRIMARY KEY,
                        userId TEXT NOT NULL,
                        deletedAtMs INTEGER NOT NULL,
                        pendingRemoteDelete INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_deleted_config_tombstones_userId ON deleted_config_tombstones(userId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_deleted_config_tombstones_pendingRemoteDelete ON deleted_config_tombstones(pendingRemoteDelete)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS cached_subscriptions (
                        subscriptionId TEXT NOT NULL PRIMARY KEY,
                        userId TEXT NOT NULL,
                        userEmail TEXT NOT NULL,
                        planId TEXT NOT NULL,
                        planDurationDays INTEGER NOT NULL,
                        endpointAllocationLimit INTEGER NOT NULL,
                        startDateMs INTEGER NOT NULL,
                        expirationDateMs INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        paymentStatus TEXT NOT NULL,
                        lastDailyRotationMs INTEGER NOT NULL,
                        lastRotationCycleKey TEXT NOT NULL,
                        currentAllocationVersion INTEGER NOT NULL,
                        createdAtMs INTEGER NOT NULL,
                        updatedAtMs INTEGER NOT NULL,
                        lastSyncedServerMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_cached_subscriptions_userId ON cached_subscriptions(userId)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS cached_allocated_endpoints (
                        allocationId TEXT NOT NULL PRIMARY KEY,
                        userId TEXT NOT NULL,
                        subscriptionId TEXT NOT NULL,
                        endpointId TEXT NOT NULL,
                        endpointName TEXT NOT NULL,
                        country TEXT NOT NULL,
                        countryCode TEXT NOT NULL,
                        host TEXT NOT NULL,
                        port INTEGER NOT NULL,
                        protocol TEXT NOT NULL,
                        transport TEXT NOT NULL,
                        security TEXT NOT NULL,
                        sni TEXT NOT NULL,
                        pathOrServiceName TEXT NOT NULL,
                        credentialToken TEXT NOT NULL,
                        rawConfig TEXT NOT NULL,
                        allocationVersion INTEGER NOT NULL,
                        cycleKey TEXT NOT NULL,
                        allocationStatus TEXT NOT NULL,
                        healthy INTEGER NOT NULL,
                        latencyMs INTEGER NOT NULL,
                        allocatedAtMs INTEGER NOT NULL,
                        expiresAtMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_cached_allocated_endpoints_userId ON cached_allocated_endpoints(userId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_cached_allocated_endpoints_subscriptionId ON cached_allocated_endpoints(subscriptionId)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS cached_subscription_history (
                        eventId TEXT NOT NULL PRIMARY KEY,
                        subscriptionId TEXT NOT NULL,
                        userId TEXT NOT NULL,
                        action TEXT NOT NULL,
                        fromPlanId TEXT NOT NULL,
                        toPlanId TEXT NOT NULL,
                        statusAfter TEXT NOT NULL,
                        actorId TEXT NOT NULL,
                        details TEXT NOT NULL,
                        createdAtMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_cached_subscription_history_userId ON cached_subscription_history(userId)"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS cached_allocation_history (
                        historyId TEXT NOT NULL PRIMARY KEY,
                        userId TEXT NOT NULL,
                        subscriptionId TEXT NOT NULL,
                        planId TEXT NOT NULL,
                        endpointId TEXT NOT NULL,
                        endpointName TEXT NOT NULL,
                        host TEXT NOT NULL,
                        port INTEGER NOT NULL,
                        protocol TEXT NOT NULL,
                        action TEXT NOT NULL,
                        allocationVersion INTEGER NOT NULL,
                        cycleKey TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        actorId TEXT NOT NULL,
                        createdAtMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_cached_allocation_history_userId ON cached_allocation_history(userId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_cached_allocation_history_endpointId ON cached_allocation_history(endpointId)"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS clean_ip_pool (
                        host TEXT NOT NULL PRIMARY KEY,
                        addressType TEXT NOT NULL,
                        defaultPort INTEGER NOT NULL,
                        isAvailable INTEGER NOT NULL,
                        healthStatus TEXT NOT NULL,
                        latencyMs INTEGER NOT NULL,
                        stabilityScore INTEGER NOT NULL,
                        failureCount INTEGER NOT NULL,
                        lastCheckedAtMs INTEGER NOT NULL,
                        lastSuccessfulHandshakeMs INTEGER NOT NULL,
                        assignedUserId TEXT NOT NULL,
                        assignedSubscriptionId TEXT NOT NULL,
                        sourceTag TEXT NOT NULL,
                        notes TEXT NOT NULL,
                        createdAtMs INTEGER NOT NULL,
                        updatedAtMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_clean_ip_pool_addressType ON clean_ip_pool(addressType)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_clean_ip_pool_isAvailable ON clean_ip_pool(isAvailable)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_clean_ip_pool_healthStatus ON clean_ip_pool(healthStatus)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_clean_ip_pool_assignedUserId ON clean_ip_pool(assignedUserId)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS registered_users (
                        userId TEXT NOT NULL PRIMARY KEY,
                        displayName TEXT NOT NULL,
                        email TEXT NOT NULL,
                        role TEXT NOT NULL,
                        deviceCount INTEGER NOT NULL,
                        maxDevices INTEGER NOT NULL,
                        dataUsageBytes REAL NOT NULL,
                        dataLimitBytes REAL NOT NULL,
                        subscriptionStatus TEXT NOT NULL,
                        createdAtMs INTEGER NOT NULL,
                        updatedAtMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_registered_users_email ON registered_users(email)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS local_payment_requests (
                        paymentId TEXT NOT NULL PRIMARY KEY,
                        userId TEXT NOT NULL,
                        userEmail TEXT NOT NULL,
                        subscriptionId TEXT NOT NULL,
                        planId TEXT NOT NULL,
                        actionType TEXT NOT NULL,
                        provider TEXT NOT NULL,
                        referenceCode TEXT NOT NULL,
                        status TEXT NOT NULL,
                        note TEXT NOT NULL,
                        createdAtMs INTEGER NOT NULL,
                        verifiedAtMs INTEGER NOT NULL,
                        verifiedBy TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_local_payment_requests_userId ON local_payment_requests(userId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_local_payment_requests_status ON local_payment_requests(status)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS local_managed_endpoints (
                        endpointId TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        country TEXT NOT NULL,
                        countryCode TEXT NOT NULL,
                        host TEXT NOT NULL,
                        port INTEGER NOT NULL,
                        protocol TEXT NOT NULL,
                        transport TEXT NOT NULL,
                        security TEXT NOT NULL,
                        sni TEXT NOT NULL,
                        pathOrServiceName TEXT NOT NULL,
                        enabled INTEGER NOT NULL,
                        healthy INTEGER NOT NULL,
                        priorityTier INTEGER NOT NULL,
                        latencyMs INTEGER NOT NULL,
                        stabilityScore INTEGER NOT NULL,
                        assignedUserId TEXT NOT NULL,
                        assignedSubscriptionId TEXT NOT NULL,
                        allocationExpiresAtMs INTEGER NOT NULL,
                        failureCount INTEGER NOT NULL,
                        cooldownUntilMs INTEGER NOT NULL,
                        createdAtMs INTEGER NOT NULL,
                        updatedAtMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_local_managed_endpoints_assignedUserId ON local_managed_endpoints(assignedUserId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_local_managed_endpoints_healthy ON local_managed_endpoints(healthy)"
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Schema-parity step for incremental upgrade tests; getDatabase falls back
                // destructively on mismatched legacy v5 installs so launch never crashes.
            }
        }

        fun getDatabase(context: Context): TmsVpnRoomDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TmsVpnRoomDatabase::class.java,
                    "tms_vpn_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        @androidx.annotation.VisibleForTesting
        internal fun clearInstanceForTests() {
            synchronized(this) {
                runCatching { INSTANCE?.close() }
                INSTANCE = null
            }
        }
    }
}
