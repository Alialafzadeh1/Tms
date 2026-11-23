package com.example.data.subscription

import com.example.data.model.ConfigOrigin
import com.example.data.model.VpnConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigDeletionPolicyTest {

    private fun sampleConfig(
        id: String,
        userId: String = "alice",
        origin: ConfigOrigin = ConfigOrigin.USER_IMPORTED,
        status: String = "online",
        protocol: String = "vless",
        name: String = "Node $id"
    ) = VpnConfig(
        id = id,
        userId = userId,
        name = name,
        protocol = protocol,
        server = "$id.tmsvpn.net",
        port = 443,
        status = status,
        origin = origin.key
    )

    @Test
    fun separateDeletionPermissionsAreEnforcedForNormalUserAndAdmin() {
        val userCfg = sampleConfig("u1", origin = ConfigOrigin.USER_IMPORTED)
        val otherUserCfg = sampleConfig("u2", userId = "bob", origin = ConfigOrigin.USER_IMPORTED)
        val subCfg = sampleConfig("s1", origin = ConfigOrigin.SUBSCRIPTION_ASSIGNED)
        val sysCfg = sampleConfig("sys1", origin = ConfigOrigin.SYSTEM_MANAGED)

        val userCtx = ConfigDeletionContext(
            actorUserId = "alice",
            isAdminActor = false,
            hasAdminManageConfigsPermission = false,
            activeTunnelConfigId = "u1"
        )

        assertTrue(ConfigDeletionPolicy.evaluateSingle(userCfg, userCtx).allowed)
        assertTrue(ConfigDeletionPolicy.evaluateSingle(userCfg, userCtx).isActiveTunnel)
        assertEquals(
            DeletionDenyReason.NOT_OWNER,
            ConfigDeletionPolicy.evaluateSingle(otherUserCfg, userCtx).denyReason
        )
        assertEquals(
            DeletionDenyReason.SUBSCRIPTION_REQUIRES_ADMIN_OR_BACKEND,
            ConfigDeletionPolicy.evaluateSingle(subCfg, userCtx).denyReason
        )
        assertEquals(
            DeletionDenyReason.SYSTEM_MANAGED_PROTECTED,
            ConfigDeletionPolicy.evaluateSingle(sysCfg, userCtx).denyReason
        )

        val adminCtx = ConfigDeletionContext(
            actorUserId = "alice",
            isAdminActor = true,
            hasAdminManageConfigsPermission = true
        )
        assertTrue(ConfigDeletionPolicy.evaluateSingle(userCfg, adminCtx).allowed)
        assertTrue(ConfigDeletionPolicy.evaluateSingle(subCfg, adminCtx).allowed)
        // Even Admin cannot delete SYSTEM_MANAGED configurations in bulk or single delete
        assertFalse(ConfigDeletionPolicy.evaluateSingle(sysCfg, adminCtx).allowed)
        assertEquals(
            DeletionDenyReason.SYSTEM_MANAGED_PROTECTED,
            ConfigDeletionPolicy.evaluateSingle(sysCfg, adminCtx).denyReason
        )
    }

    @Test
    fun planBulkDeletionSeparatesDeletableAndProtectedCategories() {
        val all = listOf(
            sampleConfig("u1", origin = ConfigOrigin.USER_IMPORTED),
            sampleConfig("u2", origin = ConfigOrigin.USER_IMPORTED),
            sampleConfig("sub1", origin = ConfigOrigin.SUBSCRIPTION_ASSIGNED),
            sampleConfig("sys1", origin = ConfigOrigin.SYSTEM_MANAGED),
            sampleConfig("bob1", userId = "bob", origin = ConfigOrigin.USER_IMPORTED)
        )
        val userCtx = ConfigDeletionContext(
            actorUserId = "alice",
            isAdminActor = false,
            hasAdminManageConfigsPermission = false,
            activeTunnelConfigId = "u2"
        )

        val plan = ConfigDeletionPolicy.planBulkDeletion(
            allConfigs = all,
            targetIds = all.map { it.id }.toSet(),
            context = userCtx
        )

        assertEquals(listOf("u1", "u2"), plan.deletableIds)
        assertEquals(listOf("sys1"), plan.blockedSystemConfigs.map { it.id })
        assertEquals(listOf("sub1"), plan.blockedSubscriptionConfigs.map { it.id })
        assertEquals(listOf("bob1"), plan.blockedUnauthorizedConfigs.map { it.id })
        assertEquals(3, plan.blockedCount)
        assertTrue(plan.includesActiveTunnel)
    }

    @Test
    fun searchAndStatusAndOriginFiltersMatchExpectedRecords() {
        val list = listOf(
            sampleConfig("1", name = "Frankfurt Edge", origin = ConfigOrigin.USER_IMPORTED, status = "online", protocol = "vless"),
            sampleConfig("2", name = "Helsinki Dedicated", origin = ConfigOrigin.SUBSCRIPTION_ASSIGNED, status = "offline", protocol = "trojan"),
            sampleConfig("3", name = "Core Default", origin = ConfigOrigin.SYSTEM_MANAGED, status = "untested", protocol = "vmess")
        )

        assertEquals(
            listOf("1"),
            ConfigDeletionPolicy.filterConfigs(list, "frankfurt", "ONLINE", "ALL").map { it.id }
        )
        assertEquals(
            listOf("2"),
            ConfigDeletionPolicy.filterConfigs(list, "", "OFFLINE", "SUBSCRIPTION_ASSIGNED").map { it.id }
        )
        assertEquals(
            listOf("3"),
            ConfigDeletionPolicy.filterConfigs(list, "Core", "UNTESTED", "SYSTEM_MANAGED").map { it.id }
        )
    }
}
