package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.model.VpnConfig
import java.util.UUID

/**
 * Room entity representing a locally stored VPN configuration.
 *
 * Stores connection details:
 * - Address (host / IP / domain)
 * - Port (e.g., 443, 80, 8443)
 * - Protocol (VLESS, VMess, Trojan, Shadowsocks)
 * - User-specific metadata (userId, alias/name, UUID/password, transport, security/TLS, SNI, path, notes, timestamps)
 */
@Entity(
    tableName = "local_vpn_configs",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["protocol"]),
        Index(value = ["isFavorite"])
    ]
)
data class LocalVpnConfigEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "userId")
    val userId: String = "",

    @ColumnInfo(name = "name")
    val name: String = "",

    /**
     * Supported VPN protocols:
     * - VLESS
     * - VMess
     * - Trojan
     * - Shadowsocks
     */
    @ColumnInfo(name = "protocol")
    val protocol: String = "VLESS",

    /**
     * Remote server host, IP address, or domain name.
     */
    @ColumnInfo(name = "address")
    val address: String = "",

    /**
     * Remote server port.
     */
    @ColumnInfo(name = "port")
    val port: Int = 443,

    /**
     * User authentication credential:
     * - UUID for VLESS / VMess
     * - Password for Trojan / Shadowsocks
     */
    @ColumnInfo(name = "uuidOrPassword")
    val uuidOrPassword: String = "",

    /**
     * Transport protocol: tcp, ws, grpc, h2, httpupgrade
     */
    @ColumnInfo(name = "transport")
    val transport: String = "tcp",

    /**
     * Security type: tls, reality, none
     */
    @ColumnInfo(name = "security")
    val security: String = "tls",

    /**
     * Server Name Indication (SNI) or TLS host header.
     */
    @ColumnInfo(name = "sni")
    val sni: String = "",

    /**
     * WebSocket path or gRPC service name.
     */
    @ColumnInfo(name = "pathOrServiceName")
    val pathOrServiceName: String = "",

    /**
     * Full raw configuration URI or export string (e.g. vless://..., vmess://...).
     */
    @ColumnInfo(name = "rawConfig")
    val rawConfig: String = "",

    /**
     * Latency measurement in milliseconds (-1 = untested or offline).
     */
    @ColumnInfo(name = "pingMs")
    val pingMs: Int = -1,

    /**
     * Status indicator: "online", "offline", "untested".
     */
    @ColumnInfo(name = "status")
    val status: String = "untested",

    /**
     * User favorite flag for quick access.
     */
    @ColumnInfo(name = "isFavorite")
    val isFavorite: Boolean = false,

    /**
     * User-defined custom notes or tags.
     */
    @ColumnInfo(name = "notes")
    val notes: String = "",

    /**
     * Creation timestamp in epoch milliseconds.
     */
    @ColumnInfo(name = "createdAtMs")
    val createdAtMs: Long = System.currentTimeMillis(),

    /**
     * Last update timestamp in epoch milliseconds.
     */
    @ColumnInfo(name = "updatedAtMs")
    val updatedAtMs: Long = System.currentTimeMillis()
) {
    /**
     * Maps local Room entity to domain [VpnConfig].
     */
    fun toDomainModel(): VpnConfig {
        return VpnConfig(
            id = id,
            userId = userId,
            name = name,
            protocol = protocol.lowercase(),
            server = address,
            port = port,
            uuidOrPassword = uuidOrPassword,
            transport = transport,
            security = security,
            sni = sni,
            pathOrServiceName = pathOrServiceName,
            rawConfig = rawConfig,
            pingMs = pingMs,
            status = status,
            isFavorite = isFavorite,
            createdAt = null,
            updatedAt = null
        )
    }

    companion object {
        /**
         * Maps domain [VpnConfig] to local [LocalVpnConfigEntity].
         */
        fun fromDomainModel(
            config: VpnConfig,
            notes: String = ""
        ): LocalVpnConfigEntity {
            val now = System.currentTimeMillis()
            return LocalVpnConfigEntity(
                id = config.id.ifBlank { UUID.randomUUID().toString() },
                userId = config.userId,
                name = config.name,
                protocol = config.protocol.uppercase(),
                address = config.server,
                port = if (config.port > 0) config.port else 443,
                uuidOrPassword = config.uuidOrPassword,
                transport = config.transport.ifBlank { "tcp" },
                security = config.security.ifBlank { "tls" },
                sni = config.sni,
                pathOrServiceName = config.pathOrServiceName,
                rawConfig = config.rawConfig,
                pingMs = config.pingMs,
                status = config.status,
                isFavorite = config.isFavorite,
                notes = notes,
                createdAtMs = config.createdAt?.toDate()?.time ?: now,
                updatedAtMs = config.updatedAt?.toDate()?.time ?: now
            )
        }
    }
}
