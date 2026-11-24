package com.example.data.local

import android.content.Context
import android.util.Log
import com.example.data.security.LocalUserAuthManager

private const val MIGRATION_PREFS = "tms_legacy_account_migration"
private const val TAG = "LegacyMigration"

/**
 * Safely preserves and copies legacy offline Room records (`intranet_*` or `local_user`)
 * into the user's canonical Firebase UID on sign-in, without deleting original records.
 */
object LegacyAccountMigrationHelper {

    suspend fun migrateLegacyLocalDataIfNeeded(
        context: Context,
        localRepo: LocalVpnConfigRepository,
        firebaseUid: String,
        email: String
    ): Int {
        if (firebaseUid.isBlank()) return 0
        val prefs = context.applicationContext.getSharedPreferences(MIGRATION_PREFS, Context.MODE_PRIVATE)
        val migrationKey = "migrated_to_$firebaseUid"
        if (prefs.getBoolean(migrationKey, false)) {
            return 0
        }

        val legacyCandidates = buildSet {
            val intranetId = LocalUserAuthManager.computeLegacyIntranetUid(email)
            if (intranetId.isNotBlank() && intranetId != firebaseUid) {
                add(intranetId)
            }
            add("local_user")
        }

        var migratedCount = 0
        runCatching {
            val allEntities = localRepo.getAllConfigEntitiesOnce()
            val toCopy = allEntities.filter { it.userId in legacyCandidates }
            val existingForUidIds = allEntities.filter { it.userId == firebaseUid }.map { it.id }.toSet()

            for (legacy in toCopy) {
                if (legacy.id !in existingForUidIds) {
                    val copied = legacy.copy(
                        id = if (allEntities.any { it.id == legacy.id && it.userId != firebaseUid }) {
                            "${legacy.id}_$firebaseUid"
                        } else {
                            legacy.id
                        },
                        userId = firebaseUid,
                        updatedAtMs = System.currentTimeMillis()
                    )
                    localRepo.saveConfigEntity(copied)
                    migratedCount++
                }
            }

            // Copy cached subscription if none exists yet under canonical Firebase UID
            if (localRepo.getCachedSubscriptionOnce(firebaseUid) == null) {
                for (legacyId in legacyCandidates) {
                    val legacySub = localRepo.getCachedSubscriptionOnce(legacyId)
                    if (legacySub != null) {
                        localRepo.cacheSubscription(
                            sub = legacySub.copy(
                                subscriptionId = "sub_$firebaseUid",
                                userId = firebaseUid,
                                userEmail = email.ifBlank { legacySub.userEmail }
                            ),
                            syncedServerNowMs = System.currentTimeMillis()
                        )
                        migratedCount++
                        break
                    }
                }
            }
        }.onFailure { err ->
            Log.w(TAG, "Legacy Room data migration warning: ${err.message}")
        }

        prefs.edit().putBoolean(migrationKey, true).apply()
        return migratedCount
    }
}
