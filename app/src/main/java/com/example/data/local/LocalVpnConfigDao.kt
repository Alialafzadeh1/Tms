package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for local VPN configurations stored in Room.
 */
@Dao
interface LocalVpnConfigDao {

    /**
     * Observes all saved VPN configurations, favorites first, ordered by recent update.
     */
    @Query("SELECT * FROM local_vpn_configs ORDER BY isFavorite DESC, updatedAtMs DESC")
    fun observeAll(): Flow<List<LocalVpnConfigEntity>>

    /**
     * Observes configurations belonging to a specific user ID.
     */
    @Query("SELECT * FROM local_vpn_configs WHERE userId = :userId ORDER BY isFavorite DESC, updatedAtMs DESC")
    fun observeByUserId(userId: String): Flow<List<LocalVpnConfigEntity>>

    /**
     * Observes configurations filtered by protocol (e.g., VLESS, VMESS, TROJAN, SHADOWSOCKS).
     */
    @Query("SELECT * FROM local_vpn_configs WHERE UPPER(protocol) = UPPER(:protocol) ORDER BY isFavorite DESC, updatedAtMs DESC")
    fun observeByProtocol(protocol: String): Flow<List<LocalVpnConfigEntity>>

    /**
     * Observes favorite configurations.
     */
    @Query("SELECT * FROM local_vpn_configs WHERE isFavorite = 1 ORDER BY updatedAtMs DESC")
    fun observeFavorites(): Flow<List<LocalVpnConfigEntity>>

    /**
     * Retrieves a single configuration by its unique ID.
     */
    @Query("SELECT * FROM local_vpn_configs WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): LocalVpnConfigEntity?

    /**
     * Inserts or updates a single configuration.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: LocalVpnConfigEntity)

    /**
     * Batch inserts or updates multiple configurations.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(configs: List<LocalVpnConfigEntity>)

    /**
     * Updates an existing configuration.
     */
    @Update
    suspend fun update(config: LocalVpnConfigEntity)

    /**
     * Updates ping latency and online status for a specific configuration.
     */
    @Query("UPDATE local_vpn_configs SET pingMs = :pingMs, status = :status, updatedAtMs = :updatedAtMs WHERE id = :id")
    suspend fun updatePingStatus(
        id: String,
        pingMs: Int,
        status: String,
        updatedAtMs: Long = System.currentTimeMillis()
    )

    /**
     * Toggles favorite state.
     */
    @Query("UPDATE local_vpn_configs SET isFavorite = :isFavorite, updatedAtMs = :updatedAtMs WHERE id = :id")
    suspend fun updateFavorite(
        id: String,
        isFavorite: Boolean,
        updatedAtMs: Long = System.currentTimeMillis()
    )

    /**
     * Deletes a single configuration by its unique ID.
     */
    @Query("DELETE FROM local_vpn_configs WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM local_vpn_configs WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM local_vpn_configs WHERE origin = :origin")
    suspend fun deleteByOrigin(origin: String)

    @Query("SELECT * FROM local_vpn_configs")
    suspend fun getAllOnce(): List<LocalVpnConfigEntity>

    /**
     * Deletes a configuration entity.
     */
    @Delete
    suspend fun delete(config: LocalVpnConfigEntity)

    /**
     * Deletes all configurations belonging to a specific user.
     */
    @Query("DELETE FROM local_vpn_configs WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)

    /**
     * Clears all local VPN configurations.
     */
    @Query("DELETE FROM local_vpn_configs")
    suspend fun clearAll()

    /**
     * Returns total count of saved configurations as Flow.
     */
    @Query("SELECT COUNT(*) FROM local_vpn_configs")
    fun count(): Flow<Int>
}
