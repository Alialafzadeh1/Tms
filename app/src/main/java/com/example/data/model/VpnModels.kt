package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class AdminPermission(val key: String, val titleFa: String, val titleEn: String) {
    VIEW_OVERVIEW("view_overview", "مشاهده داشبورد و آمار", "View Overview & Telemetry"),
    VIEW_CONFIGS("view_configs", "مشاهده کانفیگ‌ها", "View Configurations"),
    MANAGE_CONFIGS("manage_configs", "افزودن، ویرایش و حذف کانفیگ", "Import, Edit & Delete Configs"),
    VIEW_SERVERS("view_servers", "مشاهده وضعیت نودها", "View Server Nodes"),
    MANAGE_SERVERS("manage_servers", "مدیریت، افزودن و حذف سرور", "Add, Ping & Delete Servers"),
    RUN_INTELLIGENCE("run_intelligence", "اجرای هوش مصنوعی و بهینه‌سازی", "Run AI Reasoning Engine"),
    MANAGE_FAILOVER("manage_failover", "مدیریت سیاست‌های جایگزینی", "Manage Failover Policies"),
    VIEW_USERS("view_users", "مشاهده لیست کاربران و دستگاه‌ها", "View Users & Devices"),
    MANAGE_USERS("manage_users", "تغییر سهمیه و لغو دسترسی دستگاه", "Manage User Quotas & Devices"),
    VIEW_SETTINGS("view_settings", "مشاهده تنظیمات سیستم", "View System Settings"),
    MANAGE_SETTINGS("manage_settings", "تغییر تنظیمات شبکه و امنیت", "Change Security & Network Settings")
}

enum class AdminRole(
    val id: String,
    val titleFa: String,
    val titleEn: String,
    val permissions: Set<AdminPermission>
) {
    SUPER_ADMIN(
        id = "SUPER_ADMIN",
        titleFa = "مدیر ارشد کل (Super Admin)",
        titleEn = "Super Admin (Full Access)",
        permissions = AdminPermission.entries.toSet()
    ),
    SERVER_MANAGER(
        id = "SERVER_MANAGER",
        titleFa = "مدیر زیرساخت و سرورها (Server Manager)",
        titleEn = "Server Manager",
        permissions = setOf(
            AdminPermission.VIEW_OVERVIEW,
            AdminPermission.VIEW_CONFIGS,
            AdminPermission.MANAGE_CONFIGS,
            AdminPermission.VIEW_SERVERS,
            AdminPermission.MANAGE_SERVERS,
            AdminPermission.RUN_INTELLIGENCE,
            AdminPermission.MANAGE_FAILOVER,
            AdminPermission.VIEW_SETTINGS
        )
    ),
    USER_ADMIN(
        id = "USER_ADMIN",
        titleFa = "مدیر کاربران و اکانت‌ها (User Administrator)",
        titleEn = "User Administrator",
        permissions = setOf(
            AdminPermission.VIEW_OVERVIEW,
            AdminPermission.VIEW_USERS,
            AdminPermission.MANAGE_USERS,
            AdminPermission.VIEW_SETTINGS
        )
    ),
    AUDITOR(
        id = "AUDITOR",
        titleFa = "ناظر و حسابرس سیستم (Auditor)",
        titleEn = "Auditor (Read-Only)",
        permissions = setOf(
            AdminPermission.VIEW_OVERVIEW,
            AdminPermission.VIEW_CONFIGS,
            AdminPermission.VIEW_SERVERS,
            AdminPermission.VIEW_USERS,
            AdminPermission.VIEW_SETTINGS
        )
    );

    fun hasPermission(permission: AdminPermission): Boolean = permissions.contains(permission)

    companion object {
        fun fromString(roleStr: String?): AdminRole {
            return when (roleStr?.uppercase()) {
                "SUPER_ADMIN", "ADMIN" -> SUPER_ADMIN
                "SERVER_MANAGER" -> SERVER_MANAGER
                "USER_ADMIN" -> USER_ADMIN
                "AUDITOR", "OPERATOR" -> AUDITOR
                else -> SUPER_ADMIN
            }
        }
    }
}

@IgnoreExtraProperties
data class AuthAuditLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val action: String = "",
    val role: String = "SUPER_ADMIN",
    val module: String = "Overview",
    val status: String = "SUCCESS", // SUCCESS, FAILED, LOCKED_OUT
    val ipOrDevice: String = "",
    val details: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class VpnConfig(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val protocol: String = "vless", // vless, vmess, trojan, shadowsocks
    val server: String = "",
    val port: Int = 443,
    val uuidOrPassword: String = "",
    val transport: String = "tcp", // tcp, ws, grpc, h2
    val security: String = "tls", // tls, reality, none
    val sni: String = "",
    val pathOrServiceName: String = "",
    val rawConfig: String = "",
    val pingMs: Int = -1, // -1 means offline/untested
    val status: String = "untested", // online, offline, untested
    val isFavorite: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class VpnServer(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val country: String = "",
    val countryCode: String = "", // DE, US, FI, NL, etc.
    val host: String = "",
    val port: Int = 443,
    val protocol: String = "VLESS Reality",
    val status: String = "online", // online, offline, maintenance
    val latencyMs: Int = -1,
    val uptimePercentage: Double = 99.9,
    val bandwidthUsageGb: Double = 0.0,
    val bandwidthLimitGb: Double = 1000.0,
    val activeUsers: Int = 0,
    val stabilityScore: Int = 90, // 0 - 100
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FailoverPolicy(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val primaryServerId: String = "",
    val backupServerId: String = "",
    val latencyThresholdMs: Int = 300,
    val autoSwitchEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val role: String = "SUPER_ADMIN", // SUPER_ADMIN, SERVER_MANAGER, USER_ADMIN, AUDITOR
    val deviceCount: Int = 1,
    val maxDevices: Int = 5,
    val dataUsageBytes: Double = 0.0,
    val dataLimitBytes: Double = 100.0 * 1024 * 1024 * 1024, // 100GB
    val subscriptionStatus: String = "active", // active, expired, trial
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class OverviewMetrics(
    val totalConfigs: Int = 0,
    val activeServers: Int = 0,
    val totalServers: Int = 0,
    val onlineUsers: Int = 0,
    val averageLatencyMs: Int = 0,
    val totalBandwidthGb: Double = 0.0,
    val systemHealthScore: Int = 0,
    val isRealBackendConnected: Boolean = true
)

enum class VpnConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    ERROR
}

data class VpnSessionStats(
    val state: VpnConnectionState = VpnConnectionState.DISCONNECTED,
    val activeConfig: VpnConfig? = null,
    val connectedDurationSeconds: Long = 0L,
    val downloadSpeedBytesPerSec: Long = 0L,
    val uploadSpeedBytesPerSec: Long = 0L,
    val totalBytesReceived: Long = 0L,
    val totalBytesSent: Long = 0L,
    val realTunnelLatencyMs: Int = -1,
    val stabilityScore: Int = 98,
    val errorMessage: String? = null,
    val engineMode: String = "Kotlin TUN Router",
    val xrayVersion: String = ""
)

