package com.example.data.local

import android.content.Context
import com.example.data.model.VpnConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Repository to manage locally stored VPN configurations via Room.
 * Implements the Repository Pattern, providing clean abstraction over the DAO
 * and handling threading via Coroutine Dispatchers.
 */
class LocalVpnConfigRepository(private val dao: LocalVpnConfigDao) {

    constructor(context: Context) : this(
        TmsVpnRoomDatabase.getDatabase(context).vpnConfigDao()
    )

    /**
     * Observes all local VPN configuration entities.
     */
    val allEntities: Flow<List<LocalVpnConfigEntity>> = dao.observeAll()

    /**
     * Observes all configurations mapped to domain [VpnConfig].
     */
    val allConfigs: Flow<List<VpnConfig>> = dao.observeAll().map { list ->
        list.map { it.toDomainModel() }
    }

    /**
     * Observes configurations for a specific user ID.
     */
    fun observeUserConfigs(userId: String): Flow<List<VpnConfig>> =
        dao.observeByUserId(userId).map { list -> list.map { it.toDomainModel() } }

    /**
     * Observes configurations filtered by protocol (VLESS, VMESS, TROJAN, SHADOWSOCKS).
     */
    fun observeByProtocol(protocol: String): Flow<List<VpnConfig>> =
        dao.observeByProtocol(protocol).map { list -> list.map { it.toDomainModel() } }

    /**
     * Observes favorite configurations.
     */
    fun observeFavorites(): Flow<List<VpnConfig>> =
        dao.observeFavorites().map { list -> list.map { it.toDomainModel() } }

    /**
     * Retrieves a single config by ID.
     */
    suspend fun getById(id: String): VpnConfig? = withContext(Dispatchers.IO) {
        dao.getById(id)?.toDomainModel()
    }

    /**
     * Inserts or replaces a VPN configuration in the local database.
     */
    suspend fun saveConfig(config: VpnConfig, notes: String = "") = withContext(Dispatchers.IO) {
        dao.insert(LocalVpnConfigEntity.fromDomainModel(config, notes))
    }

    /**
     * Batch saves or synchronizes multiple VPN configurations.
     */
    suspend fun saveAllConfigs(configs: List<VpnConfig>) = withContext(Dispatchers.IO) {
        dao.insertAll(configs.map { LocalVpnConfigEntity.fromDomainModel(it) })
    }

    /**
     * Updates an existing configuration.
     */
    suspend fun updateConfig(config: VpnConfig, notes: String = "") = withContext(Dispatchers.IO) {
        dao.update(LocalVpnConfigEntity.fromDomainModel(config, notes))
    }

    /**
     * Updates ping latency and online status in the local database.
     */
    suspend fun updatePingStatus(id: String, pingMs: Int, status: String) = withContext(Dispatchers.IO) {
        dao.updatePingStatus(id = id, pingMs = pingMs, status = status)
    }

    /**
     * Toggles favorite flag in the local database.
     */
    suspend fun updateFavorite(id: String, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        dao.updateFavorite(id = id, isFavorite = isFavorite)
    }

    /**
     * Deletes a single configuration by ID.
     */
    suspend fun deleteById(id: String) = withContext(Dispatchers.IO) {
        dao.deleteById(id)
    }

    /**
     * Clears all local configurations.
     */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        dao.clearAll()
    }
}
