package com.example.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.CleanIpAddressType
import com.example.data.local.CleanIpEntity
import com.example.data.local.CleanIpHealthStatus
import com.example.data.local.CleanIpSeedData
import com.example.data.local.LocalVpnConfigRepository
import com.example.data.local.TmsVpnRoomDatabase
import com.example.data.model.AdminRole
import com.example.data.model.DefaultVpnConfigs
import com.example.data.model.OverviewMetrics
import com.example.data.model.SubscriptionPlanId
import com.example.data.model.UserProfile
import com.example.data.model.VpnConfig
import com.example.data.model.VpnConnectionState
import com.example.data.model.VpnSessionStats
import com.example.data.subscription.SubscriptionLifecycleEngine
import com.example.ui.screens.AdminSubscriptionScreen
import com.example.ui.screens.AntiFilterRadarScreen
import com.example.ui.screens.OverviewScreen
import com.example.ui.screens.UserManagementScreen
import com.example.ui.screens.UserServersScreen
import com.example.ui.theme.TmsVpnTheme
import com.example.vpn.engine.ParsedVpnConfig
import com.example.vpn.engine.XrayCoreEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VpnUpgradesAndComplianceTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun overviewScreenIsStrictlyManagementOnlyWithZeroConnectControls() {
        composeRule.setContent {
            TmsVpnTheme {
                OverviewScreen(
                    metrics = OverviewMetrics(
                        totalConfigs = 7,
                        activeServers = 6,
                        totalServers = 6,
                        registeredUsersCount = 12,
                        activeSubscriptionsCount = 5,
                        pendingPaymentsCount = 2,
                        healthyEndpointsCount = 75,
                        totalEndpointsCount = 75,
                        cleanIpHealthyCount = 120,
                        cleanIpTotalCount = 340
                    ),
                    configs = DefaultVpnConfigs.getDefaultConfigs(),
                    servers = emptyList(),
                    isPersian = true
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("admin_overview_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("admin_overview_screen")
            .performScrollToNode(hasTestTag("admin_overview_go_subscriptions"))
        composeRule.onNodeWithTag("admin_overview_go_subscriptions").assertIsDisplayed()
        composeRule.onNodeWithTag("admin_overview_screen")
            .performScrollToNode(hasTestTag("admin_overview_go_users"))
        composeRule.onNodeWithTag("admin_overview_go_users").assertIsDisplayed()

        assertEquals(0, composeRule.onAllNodesWithText("اتصال امن", substring = false).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("قطع اتصال", substring = false).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("DISCONNECT", substring = false).fetchSemanticsNodes().size)
    }

    @Test
    fun antiFilterRadarHidesConnectButtonsInAdminModeAndShowsThemInUserMode() {
        composeRule.setContent {
            TmsVpnTheme {
                AntiFilterRadarScreen(
                    vpnState = VpnConnectionState.DISCONNECTED,
                    sessionStats = VpnSessionStats(),
                    configs = DefaultVpnConfigs.getDefaultConfigs(),
                    isPersian = true,
                    onConnectVpn = { _, _ -> },
                    onDisconnectVpn = {},
                    isAdminMode = true
                )
            }
        }

        composeRule.waitForIdle()
        assertEquals(0, composeRule.onAllNodesWithText("اتصال امن سریع", substring = false).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("قطع اتصال", substring = false).fetchSemanticsNodes().size)
        composeRule.onNodeWithText("تست اتصال پلتفرم‌ها").assertIsDisplayed()
    }

    @Test
    fun userManagementScreenListsAllRegisteredUsersWithUidSearchAndSubscriptionAssign() {
        val users = listOf(
            UserProfile(userId = "intranet_u1", displayName = "Sara Ahmadi", email = "sara@tms.ir"),
            UserProfile(userId = "intranet_u2", displayName = "Reza Karimi", email = "reza@tms.ir")
        )
        var assignedUser: UserProfile? = null

        composeRule.setContent {
            TmsVpnTheme {
                UserManagementScreen(
                    profile = users.first(),
                    currentRole = AdminRole.SUPER_ADMIN,
                    auditLogs = emptyList(),
                    isPersian = true,
                    onSwitchRole = {},
                    onUpdateQuota = { _, _, _ -> },
                    registeredUsers = users,
                    subscriptions = emptyList(),
                    onAssignSubscriptionForUser = { assignedUser = it }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("admin_user_management_list")
            .performScrollToNode(hasTestTag("admin_registered_users_directory"))
        composeRule.onNodeWithTag("admin_registered_users_directory").assertIsDisplayed()
        composeRule.onNodeWithTag("admin_user_search_input").performScrollTo().performTextInput("Reza")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("admin_registered_user_row_intranet_u2").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("admin_assign_sub_intranet_u2").performScrollTo().performClick()
        assertEquals("intranet_u2", assignedUser?.userId)
    }

    @Test
    fun userServersScreenExposesCleanIpPoolTabAndAppliesCleanIp() {
        val samplePool = listOf(
            CleanIpEntity(
                host = "172.67.127.156",
                addressType = CleanIpAddressType.IPV4.name,
                healthStatus = CleanIpHealthStatus.HEALTHY.name,
                latencyMs = 42,
                isAvailable = true
            )
        )
        var appliedHost = ""
        var connectImmediate = false

        composeRule.setContent {
            TmsVpnTheme {
                UserServersScreen(
                    configs = DefaultVpnConfigs.getDefaultConfigs(),
                    servers = emptyList(),
                    selectedConfig = DefaultVpnConfigs.getDefaultConfigs().first(),
                    isPinging = false,
                    isPersian = true,
                    onSelectConfig = {},
                    onConnectSelected = {},
                    onPingConfig = {},
                    onToggleFavorite = {},
                    onAutoSelectBest = {},
                    cleanIpPool = samplePool,
                    onApplyCleanIp = { host, immediate ->
                        appliedHost = host
                        connectImmediate = immediate
                    }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("user_servers_clean_ip_tab").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("user_clean_ip_pool_section").assertIsDisplayed()
        composeRule.onNodeWithTag("apply_clean_ip_172.67.127.156").performClick()
        assertEquals("172.67.127.156", appliedHost)
        assertTrue(connectImmediate)
    }

    @Test
    fun cleanIpDefaultsExceed300HostsAndExpandCidr24Safely() {
        assertTrue("Expected >= 300 seed Clean Hosts", CleanIpSeedData.totalSeedCount() >= 300)
        val expanded = CleanIpSeedData.expandCidr("104.16.190.0/24")
        assertEquals(254, expanded.size)
        assertEquals("104.16.190.1", expanded.first())
        assertEquals("104.16.190.254", expanded.last())

        val bulk = CleanIpSeedData.parseBulkInput(
            "104.16.190.0/24\n172.67.127.156\n2606:4700:3037::6815:2826\ncdn.tmsvpn.ir"
        )
        assertTrue(bulk.any { it.host == "172.67.127.156" && it.addressType == CleanIpAddressType.IPV4.name })
        assertTrue(bulk.any { it.host == "2606:4700:3037::6815:2826" && it.addressType == CleanIpAddressType.IPV6.name })
        assertTrue(bulk.any { it.host == "cdn.tmsvpn.ir" && it.addressType == CleanIpAddressType.DOMAIN.name })
        assertEquals(257, bulk.size)
    }

    @Test
    fun defaultUserQuotasAre500GbAnd10DevicesAndDeTrNodesExist() {
        val profile = UserProfile(userId = "u1")
        assertEquals(10, profile.maxDevices)
        assertEquals(500.0 * 1024 * 1024 * 1024, profile.dataLimitBytes, 1.0)
        assertTrue(DefaultVpnConfigs.getDefaultConfigs().any { DefaultVpnConfigs.isDeOrTrNode(it) })
    }

    @Test
    fun xrayConfigPreservesSniAndRoutesDnsOverPort53WithCleanIpOverride() {
        val rawUri = "vless://11111111-1111-1111-1111-111111111111@de-edge.tmsvpn.example:443?type=ws&security=tls&sni=cdn-sni.tmsvpn.example&host=ws-host.tmsvpn.example#DE"
        val config = VpnConfig(
            id = "de_ws_test",
            name = "DE Frankfurt Edge",
            protocol = "vless",
            server = "de-edge.tmsvpn.example",
            port = 443,
            rawConfig = rawUri
        )
        val parsed = ParsedVpnConfig.from(rawUri)
        val json = XrayCoreEngine.buildXrayJson(
            parsed = parsed,
            rawConfig = config,
            enableFragment = true,
            socksPort = 10808,
            cleanIpOverride = "172.67.127.156"
        )
        assertTrue(json.contains("\"address\":\"172.67.127.156\""))
        assertTrue(json.contains("\"serverName\":\"cdn-sni.tmsvpn.example\""))
        assertTrue(json.contains("\"Host\":\"ws-host.tmsvpn.example\""))
        assertTrue(json.contains("\"dialerProxy\":\"fragment-out\""))
        assertTrue(json.contains("\"port\":\"53\""))
    }

    @Test
    fun localRepoSupports75EndpointPoolAnd5_15_50PlanAllocationsWithoutDoubleAssignment() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(
            app,
            TmsVpnRoomDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val repo = LocalVpnConfigRepository(
                dao = db.vpnConfigDao(),
                tombstoneDao = db.tombstoneDao(),
                subscriptionDao = db.cachedSubscriptionDao(),
                cleanIpDao = db.cleanIpDao(),
                registeredUserDao = db.registeredUserDao(),
                paymentRequestDao = db.localPaymentRequestDao(),
                managedEndpointDao = db.localManagedEndpointDao()
            )
            repo.prepopulateEndpointInventoryIfNeeded()
            val now = System.currentTimeMillis()

        val (sub1, _) = SubscriptionLifecycleEngine.activateAfterPaymentConfirmation(
            subscriptionId = "sub_u1",
            userId = "user_1m",
            userEmail = "u1@tms.ir",
            plan = SubscriptionPlanId.ONE_MONTH,
            actorId = "admin",
            trustedServerNowMs = now
        )
        val (sub2, _) = SubscriptionLifecycleEngine.activateAfterPaymentConfirmation(
            subscriptionId = "sub_u2",
            userId = "user_3m",
            userEmail = "u2@tms.ir",
            plan = SubscriptionPlanId.THREE_MONTHS,
            actorId = "admin",
            trustedServerNowMs = now
        )
        val (sub3, _) = SubscriptionLifecycleEngine.activateAfterPaymentConfirmation(
            subscriptionId = "sub_u3",
            userId = "user_1y",
            userEmail = "u3@tms.ir",
            plan = SubscriptionPlanId.ONE_YEAR,
            actorId = "admin",
            trustedServerNowMs = now
        )

        val batch1 = repo.executeLocalAllocationOrRotation(
            targetSubscriptions = listOf(sub1),
            activeConnectedEndpointByUser = emptyMap(),
            actorUid = "admin",
            triggerMode = "MANUAL",
            nowMs = now
        )
        val batch2 = repo.executeLocalAllocationOrRotation(
            targetSubscriptions = listOf(sub2),
            activeConnectedEndpointByUser = emptyMap(),
            actorUid = "admin",
            triggerMode = "MANUAL",
            nowMs = now
        )
        val batch3 = repo.executeLocalAllocationOrRotation(
            targetSubscriptions = listOf(sub3),
            activeConnectedEndpointByUser = emptyMap(),
            actorUid = "admin",
            triggerMode = "MANUAL",
            nowMs = now
        )

            val ep1 = batch1.allocationsByUser["user_1m"].orEmpty()
            val ep2 = batch2.allocationsByUser["user_3m"].orEmpty()
            val ep3 = batch3.allocationsByUser["user_1y"].orEmpty()

            assertEquals(5, ep1.size)
            assertEquals(15, ep2.size)
            assertEquals(50, ep3.size)
            val totalIds = (ep1 + ep2 + ep3).map { it.endpointId }.toSet()
            assertEquals(70, totalIds.size)
        } finally {
            db.close()
        }
    }

    @Test
    fun all300NewCleanIpsSeededIntoFreshAndUpgradedPool() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(
            app,
            TmsVpnRoomDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val cleanIpDao = db.cleanIpDao()
            // Simulate an upgraded install that already had one legacy Clean IP
            cleanIpDao.insertIgnoreAll(
                listOf(
                    com.example.data.local.CleanIpEntity(
                        host = "104.16.190.196",
                        addressType = com.example.data.local.CleanIpAddressType.IPV4.name,
                        sourceTag = "LEGACY"
                    )
                )
            )
            val repo = LocalVpnConfigRepository(
                dao = db.vpnConfigDao(),
                tombstoneDao = db.tombstoneDao(),
                subscriptionDao = db.cachedSubscriptionDao(),
                cleanIpDao = cleanIpDao,
                registeredUserDao = db.registeredUserDao(),
                paymentRequestDao = db.localPaymentRequestDao(),
                managedEndpointDao = db.localManagedEndpointDao()
            )
            repo.seedCleanIpPoolIfNeeded()
            val hosts = cleanIpDao.getAllHostsOnce().toSet()

            assertEquals(com.example.data.local.CleanIpSeedData.totalSeedCount(), hosts.size)
            assertTrue(hosts.size >= 400)
            // Spot-check newly supplied IPv4, IPv6, and Domain batches
            assertTrue("172.66.161.168" in hosts)
            assertTrue("104.20.9.230" in hosts)
            assertTrue("64.69.24.132" in hosts)
            assertTrue("2606:4700:3031::6815:fb6" in hosts)
            assertTrue("2606:4700:3037::6815:33dc" in hosts)
            assertTrue("bahmannews.ir" in hosts)
            assertTrue("20tadris.com" in hosts)
        } finally {
            db.close()
        }
    }

    @Test
    fun ipOnlyEndpointEntryPopulatesBothCleanPoolAndPort443ManagedEndpoints() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(
            app,
            TmsVpnRoomDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val repo = LocalVpnConfigRepository(
                dao = db.vpnConfigDao(),
                tombstoneDao = db.tombstoneDao(),
                subscriptionDao = db.cachedSubscriptionDao(),
                cleanIpDao = db.cleanIpDao(),
                registeredUserDao = db.registeredUserDao(),
                paymentRequestDao = db.localPaymentRequestDao(),
                managedEndpointDao = db.localManagedEndpointDao()
            )
            val (cleanCount, endpointCount) = repo.addIpOnlyEndpoints(
                rawText = "172.66.161.168\n2606:4700:3031::6815:fb6\nbahmannews.ir",
                sourceTag = "ADMIN_IP_ONLY"
            )
            assertEquals(3, cleanCount)
            assertEquals(3, endpointCount)

            val endpoints = db.localManagedEndpointDao().getAllOnce()
            assertEquals(3, endpoints.size)
            assertTrue(endpoints.all { it.port == 443 && it.protocol == "vless" && it.healthy })
            assertTrue(endpoints.any { it.host == "172.66.161.168" })
            assertTrue(endpoints.any { it.host == "2606:4700:3031::6815:fb6" })
            assertTrue(endpoints.any { it.host == "bahmannews.ir" })
        } finally {
            db.close()
        }
    }

    @Test
    fun perUserBandwidthDeltaAccumulatesInRoomRegisteredUserProfile() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(
            app,
            TmsVpnRoomDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val regDao = db.registeredUserDao()
            val repo = LocalVpnConfigRepository(
                dao = db.vpnConfigDao(),
                tombstoneDao = db.tombstoneDao(),
                subscriptionDao = db.cachedSubscriptionDao(),
                cleanIpDao = db.cleanIpDao(),
                registeredUserDao = regDao,
                paymentRequestDao = db.localPaymentRequestDao(),
                managedEndpointDao = db.localManagedEndpointDao()
            )
            repo.upsertRegisteredUser(
                com.example.data.local.RegisteredUserEntity(
                    userId = "bw_user",
                    displayName = "Bandwidth Tester",
                    email = "bw@tms.ir",
                    dataUsageBytes = 1024.0
                )
            )
            repo.recordTrafficUsageDelta("bw_user", 500_000L)
            repo.recordTrafficUsageDelta("bw_user", 250_000L)

            val updated = regDao.getAllOnce().first { it.userId == "bw_user" }
            assertEquals(751_024.0, updated.dataUsageBytes, 1.0)
        } finally {
            db.close()
        }
    }

    @Test
    fun ispUtlsFingerprintRotationUpdatesXrayConfigOnRetry() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val step1 = com.example.vpn.engine.IspAutoTuner.rotateFragmentAndFingerprintForRetry(app, 1)
        val step2 = com.example.vpn.engine.IspAutoTuner.rotateFragmentAndFingerprintForRetry(app, 2)
        assertNotEquals(step1.fingerprint, step2.fingerprint)
        assertTrue(step1.fingerprint in com.example.vpn.engine.IspAutoTuner.FINGERPRINT_ROTATION)
        assertTrue(step2.fingerprint in com.example.vpn.engine.IspAutoTuner.FINGERPRINT_ROTATION)

        val rawUri = "vless://11111111-1111-1111-1111-111111111111@de-edge.tmsvpn.example:443?type=ws&security=tls&sni=cdn-sni.tmsvpn.example&fp=chrome#DE"
        val parsed = ParsedVpnConfig.from(rawUri)
        val json = XrayCoreEngine.buildXrayJson(
            parsed = parsed,
            enableFragment = true,
            fragmentProfile = step2
        )
        assertTrue(json.contains("\"fingerprint\":\"${step2.fingerprint}\""))
    }

    @Test
    fun subscriptionActivationLinkAndQrMatrixAreValidAndParseable() {
        val link = com.example.data.subscription.SubscriptionActivationLinkEngine.buildActivationUrl(
            planId = "three_months",
            targetUserId = "uid_900",
            referenceCode = "TMS-123456",
            actorId = "admin_1"
        )
        assertTrue(link.startsWith("tmsvpn://activate?"))
        val parsed = com.example.data.subscription.SubscriptionActivationLinkEngine.parseActivationUrl(link)
        assertNotNull(parsed)
        assertEquals("three_months", parsed?.planId)
        assertEquals("uid_900", parsed?.targetUserId)
        assertEquals("TMS-123456", parsed?.referenceCode)
        assertEquals("admin_1", parsed?.actorId)

        val qrMatrix = com.example.data.subscription.QrMatrixEncoder.encode(link)
        assertTrue(qrMatrix.size >= 21)
        assertEquals(qrMatrix.size, qrMatrix[0].size)
        // Top-left 7x7 finder pattern corners & center must be dark
        assertTrue(qrMatrix[0][0])
        assertTrue(qrMatrix[0][6])
        assertTrue(qrMatrix[6][0])
        assertTrue(qrMatrix[3][3])
    }

    @Test
    fun adminIpOnlyModalAcceptsIpWithoutPort() {
        var capturedIpOnly = ""

        composeRule.setContent {
            AdminSubscriptionScreen(
                subscriptions = emptyList(),
                endpointInventory = emptyList(),
                paymentRequests = emptyList(),
                shortageAlerts = emptyList(),
                rotationLogs = emptyList(),
                selectedRotationUserIds = emptySet(),
                trustedNowMs = System.currentTimeMillis(),
                isBusy = false,
                isPersian = true,
                onToggleSelectUser = {},
                onSelectAllUsers = {},
                onClearUserSelection = {},
                onActivateSubscription = { _, _, _, _ -> },
                onRejectPayment = { _, _ -> },
                onRenewSubscription = {},
                onChangePlan = { _, _ -> },
                onUpdateStatus = { _, _, _ -> },
                onUpsertEndpoint = {},
                onImportFromNodes = {},
                onToggleEndpointHealth = { _, _, _ -> },
                onResolveShortage = {},
                onRotateSingleUser = {},
                onRotateSelectedUsers = {},
                onRunDailyRotationAll = {},
                onAddIpOnlyEndpoints = { capturedIpOnly = it }
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("admin_sub_tab_INVENTORY").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("admin_add_endpoint_btn").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("admin_add_ip_only_input").performTextInput("172.66.161.168")
        composeRule.onNodeWithTag("admin_add_ip_only_confirm_btn").performClick()
        composeRule.waitForIdle()
        assertEquals("172.66.161.168", capturedIpOnly)
    }

    @Test
    fun userSubscriptionShowsLinkDialogAndSubmitsDeepLink() {
        var capturedLink = ""

        composeRule.setContent {
            com.example.ui.screens.UserSubscriptionScreen(
                userProfile = UserProfile(userId = "u_test"),
                isPersian = true,
                onActivateByLink = { capturedLink = it }
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("user_activate_by_link_btn").performScrollTo().performClick()
        composeRule.waitForIdle()
        val testUrl = "tmsvpn://activate?plan=1_month&ref=TMS-999999&actor=admin"
        composeRule.onNodeWithTag("user_activate_link_input").performTextInput(testUrl)
        composeRule.onNodeWithTag("user_submit_activation_link_btn").performClick()
        composeRule.waitForIdle()
        assertEquals(testUrl, capturedLink)
    }
}
