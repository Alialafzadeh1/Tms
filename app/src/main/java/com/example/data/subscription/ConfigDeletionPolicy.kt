package com.example.data.subscription

import com.example.data.model.ConfigOrigin
import com.example.data.model.VpnConfig

enum class DeletionDenyReason {
    NOT_OWNER,
    SYSTEM_MANAGED_PROTECTED,
    SUBSCRIPTION_REQUIRES_ADMIN_OR_BACKEND,
    MISSING_ADMIN_PERMISSION,
    INVALID_ID
}

data class ConfigDeletionDecision(
    val configId: String,
    val allowed: Boolean,
    val origin: ConfigOrigin,
    val denyReason: DeletionDenyReason? = null,
    val isActiveTunnel: Boolean = false
)

data class BulkDeletionPlan(
    val requestedIds: Set<String>,
    val deletableConfigs: List<VpnConfig>,
    val blockedSystemConfigs: List<VpnConfig>,
    val blockedSubscriptionConfigs: List<VpnConfig>,
    val blockedUnauthorizedConfigs: List<VpnConfig>,
    val includesActiveTunnel: Boolean
) {
    val deletableIds: List<String> get() = deletableConfigs.map { it.id }
    val blockedCount: Int
        get() = blockedSystemConfigs.size + blockedSubscriptionConfigs.size + blockedUnauthorizedConfigs.size
}

data class ConfigDeletionContext(
    val actorUserId: String,
    val isAdminActor: Boolean,
    val hasAdminManageConfigsPermission: Boolean,
    val activeTunnelConfigId: String? = null
)

/**
 * Enforces explicit deletion permissions across:
 * 1) USER_IMPORTED
 * 2) SUBSCRIPTION_ASSIGNED
 * 3) SYSTEM_MANAGED
 */
object ConfigDeletionPolicy {

    fun evaluateSingle(
        config: VpnConfig,
        context: ConfigDeletionContext
    ): ConfigDeletionDecision {
        val origin = config.originEnum()
        if (config.id.isBlank()) {
            return ConfigDeletionDecision(
                configId = config.id,
                allowed = false,
                origin = origin,
                denyReason = DeletionDenyReason.INVALID_ID
            )
        }

        val isOwner = config.userId.isBlank() ||
            config.userId == "local_user" ||
            config.userId == context.actorUserId

        val isActive = context.activeTunnelConfigId != null && config.id == context.activeTunnelConfigId

        return when (origin) {
            ConfigOrigin.SYSTEM_MANAGED -> {
                ConfigDeletionDecision(
                    configId = config.id,
                    allowed = false,
                    origin = origin,
                    denyReason = DeletionDenyReason.SYSTEM_MANAGED_PROTECTED,
                    isActiveTunnel = isActive
                )
            }
            ConfigOrigin.SUBSCRIPTION_ASSIGNED -> {
                val allowed = context.isAdminActor && context.hasAdminManageConfigsPermission
                ConfigDeletionDecision(
                    configId = config.id,
                    allowed = allowed,
                    origin = origin,
                    denyReason = if (allowed) null else DeletionDenyReason.SUBSCRIPTION_REQUIRES_ADMIN_OR_BACKEND,
                    isActiveTunnel = isActive
                )
            }
            ConfigOrigin.USER_IMPORTED -> {
                val allowed = if (context.isAdminActor) {
                    context.hasAdminManageConfigsPermission && isOwner
                } else {
                    isOwner
                }
                val reason = when {
                    allowed -> null
                    !isOwner -> DeletionDenyReason.NOT_OWNER
                    else -> DeletionDenyReason.MISSING_ADMIN_PERMISSION
                }
                ConfigDeletionDecision(
                    configId = config.id,
                    allowed = allowed,
                    origin = origin,
                    denyReason = reason,
                    isActiveTunnel = isActive
                )
            }
        }
    }

    fun planBulkDeletion(
        allConfigs: List<VpnConfig>,
        targetIds: Set<String>,
        context: ConfigDeletionContext
    ): BulkDeletionPlan {
        val uniqueTargets = allConfigs
            .filter { it.id.isNotBlank() && it.id in targetIds }
            .distinctBy { it.id }

        val deletable = mutableListOf<VpnConfig>()
        val blockedSystem = mutableListOf<VpnConfig>()
        val blockedSub = mutableListOf<VpnConfig>()
        val blockedUnauthorized = mutableListOf<VpnConfig>()
        var touchesActiveTunnel = false

        for (cfg in uniqueTargets) {
            val decision = evaluateSingle(cfg, context)
            if (decision.allowed) {
                deletable.add(cfg)
                if (decision.isActiveTunnel) touchesActiveTunnel = true
            } else {
                when (decision.denyReason) {
                    DeletionDenyReason.SYSTEM_MANAGED_PROTECTED -> blockedSystem.add(cfg)
                    DeletionDenyReason.SUBSCRIPTION_REQUIRES_ADMIN_OR_BACKEND -> blockedSub.add(cfg)
                    else -> blockedUnauthorized.add(cfg)
                }
            }
        }

        return BulkDeletionPlan(
            requestedIds = targetIds,
            deletableConfigs = deletable,
            blockedSystemConfigs = blockedSystem,
            blockedSubscriptionConfigs = blockedSub,
            blockedUnauthorizedConfigs = blockedUnauthorized,
            includesActiveTunnel = touchesActiveTunnel
        )
    }

    fun filterConfigs(
        configs: List<VpnConfig>,
        query: String,
        statusFilter: String,
        protocolOrOriginFilter: String
    ): List<VpnConfig> {
        val q = query.trim()
        return configs.filter { cfg ->
            val matchesQuery = q.isBlank() ||
                cfg.name.contains(q, ignoreCase = true) ||
                cfg.server.contains(q, ignoreCase = true) ||
                cfg.protocol.contains(q, ignoreCase = true) ||
                cfg.sni.contains(q, ignoreCase = true) ||
                cfg.port.toString().contains(q)

            val matchesStatus = when (statusFilter.uppercase()) {
                "ONLINE" -> cfg.status.equals("online", ignoreCase = true)
                "OFFLINE" -> cfg.status.equals("offline", ignoreCase = true)
                "UNTESTED" -> cfg.status.equals("untested", ignoreCase = true) || cfg.status.isBlank()
                else -> true
            }

            val matchesCategory = when (protocolOrOriginFilter.uppercase()) {
                "FAVORITES" -> cfg.isFavorite
                "USER_IMPORTED" -> cfg.originEnum() == ConfigOrigin.USER_IMPORTED
                "SUBSCRIPTION_ASSIGNED" -> cfg.originEnum() == ConfigOrigin.SUBSCRIPTION_ASSIGNED
                "SYSTEM_MANAGED" -> cfg.originEnum() == ConfigOrigin.SYSTEM_MANAGED
                "VLESS" -> cfg.protocol.equals("vless", ignoreCase = true)
                "VMESS" -> cfg.protocol.equals("vmess", ignoreCase = true)
                "TROJAN" -> cfg.protocol.equals("trojan", ignoreCase = true)
                "SHADOWSOCKS" -> cfg.protocol.equals("shadowsocks", ignoreCase = true)
                else -> true
            }

            matchesQuery && matchesStatus && matchesCategory
        }
    }
}
