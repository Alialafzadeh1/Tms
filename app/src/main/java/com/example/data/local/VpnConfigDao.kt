package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for [VpnConfig] entity in Room.
 */
@Dao
interface VpnConfigDao {

    @Query("SELECT * FROM vpn_configs ORDER BY isFavorite DESC, name ASC")
    fun getAllConfigs(): Flow<List<VpnConfig>>

    @Query("SELECT * FROM vpn_configs WHERE id = :id LIMIT 1")
    suspend fun getConfigById(id: String): VpnConfig?

    @Query("SELECT * FROM vpn_configs WHERE UPPER(protocol) = UPPER(:protocol) ORDER BY isFavorite DESC, name ASC")
    fun getConfigsByProtocol(protocol: String): Flow<List<VpnConfig>>

    @Query("SELECT * FROM vpn_configs WHERE isFavorite = 1 ORDER BY name ASC")
    fun getFavoriteConfigs(): Flow<List<VpnConfig>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: VpnConfig)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(configs: List<VpnConfig>)

    @Update
    suspend fun updateConfig(config: VpnConfig)

    @Query("UPDATE vpn_configs SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: String, isFavorite: Boolean)

    @Delete
    suspend fun deleteConfig(config: VpnConfig)

    @Query("DELETE FROM vpn_configs WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM vpn_configs")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM vpn_configs")
    fun getConfigCount(): Flow<Int>
}
