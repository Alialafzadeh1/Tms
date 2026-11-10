package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Room entity representing a VPN Configuration.
 *
 * Required fields:
 * - id (PK)
 * - name
 * - protocol (VLESS / VMess / Trojan / Shadowsocks)
 * - serverAddress
 * - port
 * - configJson
 * - isFavorite
 */
@Entity(tableName = "vpn_configs")
data class VpnConfig(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "name")
    val name: String = "",

    /**
     * Supported protocols: VLESS, VMess, Trojan, Shadowsocks
     */
    @ColumnInfo(name = "protocol")
    val protocol: String = "VLESS",

    @ColumnInfo(name = "serverAddress")
    val serverAddress: String = "",

    @ColumnInfo(name = "port")
    val port: Int = 443,

    /**
     * Complete raw JSON or URI configuration payload
     */
    @ColumnInfo(name = "configJson")
    val configJson: String = "",

    @ColumnInfo(name = "isFavorite")
    val isFavorite: Boolean = false
) {
    /**
     * Converts to domain model [com.example.data.model.VpnConfig]
     */
    fun toDomainModel(): com.example.data.model.VpnConfig {
        return com.example.data.model.VpnConfig(
            id = id,
            name = name,
            protocol = protocol.lowercase(),
            server = serverAddress,
            port = port,
            rawConfig = configJson,
            isFavorite = isFavorite,
            status = "untested"
        )
    }

    companion object {
        fun fromDomainModel(domain: com.example.data.model.VpnConfig): VpnConfig {
            return VpnConfig(
                id = domain.id.ifBlank { UUID.randomUUID().toString() },
                name = domain.name,
                protocol = domain.protocol.uppercase(),
                serverAddress = domain.server,
                port = if (domain.port > 0) domain.port else 443,
                configJson = domain.rawConfig.ifBlank { "{}" },
                isFavorite = domain.isFavorite
            )
        }
    }
}
