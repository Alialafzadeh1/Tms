package com.example.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.DefaultVpnConfigs
import com.example.data.model.SubscriptionPlanId
import com.example.data.model.UserProfile
import com.example.data.model.VpnConnectionState
import com.example.data.model.VpnSessionStats
import com.example.data.subscription.SubscriptionLifecycleEngine
import com.example.ui.screens.UserHomeScreen
import com.example.ui.screens.UserSubscriptionScreen
import com.example.ui.theme.TmsVpnTheme
import com.example.vpn.VpnController
import com.example.vpn.engine.AntiDpiComboEngine
import com.example.vpn.engine.AntiDpiFailureReason
import com.example.vpn.engine.ParsedVpnConfig
import com.example.vpn.engine.TlsHandshakeProbeResult
import com.example.vpn.engine.XrayCoreEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.SocketException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLHandshakeException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AntiDpiComboAndSubscriptionUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun xrayComboConfigEnablesDoHFakeDnsFragmentAlpnAndUtls() {
        val rawUri = "vless://11111111-1111-1111-1111-111111111111@de-edge.tmsvpn.example:443?type=ws&security=tls&sni=cdn.example.com#DE"
        val parsed = ParsedVpnConfig.from(rawUri)
        val preset = AntiDpiComboEngine.PRESETS[1] // Micro-Split Safari + http/1.1
        val json = XrayCoreEngine.buildXrayJson(
            parsed = parsed,
            enableFragment = true,
            fragmentProfile = preset.toFragmentProfile(),
            cleanIpOverride = "172.66.161.168",
            enableDoH = preset.enableDoH,
            enableFakeDns = preset.enableFakeDns,
            alpnOverride = preset.alpn
        )

        val normalizedJson = json.replace("\\/", "/")
        assertTrue(normalizedJson.contains("\"fakedns\""))
        assertTrue(normalizedJson.contains("198.18.0.0/15"))
        assertTrue(normalizedJson.contains("https+local://1.1.1.1/dns-query"))
        assertTrue(normalizedJson.contains("\"fingerprint\":\"safari\""))
        assertTrue(normalizedJson.contains("\"http/1.1\""))
        assertTrue(normalizedJson.contains("\"dialerProxy\":\"fragment-out\""))
        assertTrue(normalizedJson.contains("\"address\":\"172.66.161.168\""))
    }

    @Test
    fun antiDpiComboEngineClassifiesRstVsTimeoutAndUsesIspMemory() = runBlocking {
        assertEquals(AntiDpiFailureReason.DPI_RESET, AntiDpiComboEngine.classifyThrowable(SSLHandshakeException("handshake aborted")))
        assertEquals(AntiDpiFailureReason.DPI_RESET, AntiDpiComboEngine.classifyThrowable(SocketException("Connection reset by peer")))
        assertEquals(AntiDpiFailureReason.TIMEOUT, AntiDpiComboEngine.classifyThrowable(SocketTimeoutException("connect timed out")))

        val app = ApplicationProvider.getApplicationContext<Application>()
        val configs = DefaultVpnConfigs.getDefaultConfigs("combo_user")
        val targetNode = configs.first { DefaultVpnConfigs.isDeOrTrNode(it) }

        val firstRun = AntiDpiComboEngine.runSmartComboBypass(
            context = app,
            configs = configs,
            cleanIpCandidates = listOf("172.66.161.168", "104.20.9.230"),
            forceNextPreset = false
        ) { cfg, ip, preset ->
            if (cfg.id == targetNode.id && ip == "172.66.161.168" && preset.index == 1) {
                TlsHandshakeProbeResult(host = ip, isReachable = true, latencyMs = 58, failureReason = AntiDpiFailureReason.NONE)
            } else {
                TlsHandshakeProbeResult(host = ip ?: cfg.server, isReachable = false, latencyMs = -1, failureReason = AntiDpiFailureReason.DPI_RESET)
            }
        }
        assertNotNull(firstRun.winnerConfig)
        assertEquals("172.66.161.168", firstRun.winnerCleanIp)
        assertEquals(1, firstRun.preset.index)
        assertFalse(firstRun.usedMemoryHit)

        // Second run should hit ISP Combo Memory on the very first probe
        val secondRun = AntiDpiComboEngine.runSmartComboBypass(
            context = app,
            configs = configs,
            cleanIpCandidates = listOf("172.66.161.168", "104.20.9.230"),
            forceNextPreset = false
        ) { cfg, ip, preset ->
            TlsHandshakeProbeResult(host = ip ?: cfg.server, isReachable = true, latencyMs = 45, failureReason = AntiDpiFailureReason.NONE)
        }
        assertTrue(secondRun.usedMemoryHit)
        assertEquals("172.66.161.168", secondRun.winnerCleanIp)
        assertEquals(1, secondRun.testedCombosCount)
    }

    @Test
    fun vpnControllerEnforcesSubscriberOnlyGatingForComboShield() {
        val app = ApplicationProvider.getApplicationContext<Application>()

        val denied = VpnController.setComboShieldEnabled(app, enabled = true, isEntitled = false)
        assertFalse(denied)
        assertFalse(VpnController.comboShieldEnabled.value)

        val enabled = VpnController.setComboShieldEnabled(app, enabled = true, isEntitled = true)
        assertTrue(enabled)
        assertTrue(VpnController.comboShieldEnabled.value)

        val disabled = VpnController.setComboShieldEnabled(app, enabled = false, isEntitled = true)
        assertTrue(disabled)
        assertFalse(VpnController.comboShieldEnabled.value)
    }

    @Test
    fun nonSubscriberHomeComboSwitchOpensThreeWayPurchaseDialog() {
        var openedSubscriptionTab = false
        composeRule.setContent {
            TmsVpnTheme {
                UserHomeScreen(
                    vpnState = VpnConnectionState.DISCONNECTED,
                    sessionStats = VpnSessionStats(),
                    configs = DefaultVpnConfigs.getDefaultConfigs(),
                    servers = emptyList(),
                    selectedConfig = DefaultVpnConfigs.getDefaultConfigs().first(),
                    isPersian = true,
                    onConnectVpn = {},
                    onDisconnectVpn = {},
                    onSelectConfig = {},
                    onImportConfig = {},
                    onImportBatch = {},
                    onOpenServersTab = {},
                    onOpenRadar = {},
                    userSubscription = null,
                    isComboShieldEnabled = false,
                    onOpenSubscriptionTab = { openedSubscriptionTab = true }
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_combo_shield_switch").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("home_combo_purchase_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("home_combo_buy_rubika_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("home_combo_buy_telegram_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("home_combo_buy_in_app_btn").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertTrue(openedSubscriptionTab)
    }

    @Test
    fun subscriberHomeComboSwitchTogglesOnAndOffAndShowsReroll() {
        val now = System.currentTimeMillis()
        val (activeSub, _) = SubscriptionLifecycleEngine.activateAfterPaymentConfirmation(
            subscriptionId = "sub_vip",
            userId = "vip_user",
            userEmail = "vip@tms.ir",
            plan = SubscriptionPlanId.THREE_MONTHS,
            actorId = "admin",
            trustedServerNowMs = now
        )
        var toggledValues = mutableListOf<Boolean>()
        var rerolled = false

        composeRule.setContent {
            TmsVpnTheme {
                UserHomeScreen(
                    vpnState = VpnConnectionState.DISCONNECTED,
                    sessionStats = VpnSessionStats(),
                    configs = DefaultVpnConfigs.getDefaultConfigs(),
                    servers = emptyList(),
                    selectedConfig = DefaultVpnConfigs.getDefaultConfigs().first(),
                    isPersian = true,
                    onConnectVpn = {},
                    onDisconnectVpn = {},
                    onSelectConfig = {},
                    onImportConfig = {},
                    onImportBatch = {},
                    onOpenServersTab = {},
                    onOpenRadar = {},
                    userSubscription = activeSub,
                    trustedNowMs = now,
                    isComboShieldEnabled = true,
                    activeComboSummary = "DoH+FakeDNS • Frag 10-22/8-16 • uTLS:chrome",
                    onToggleComboShield = { toggledValues.add(it) },
                    onRerollComboShield = { rerolled = true }
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_combo_reroll_button").performScrollTo().assertIsDisplayed().performClick()
        assertTrue(rerolled)

        composeRule.onNodeWithTag("home_combo_shield_switch").performScrollTo().performClick()
        assertEquals(listOf(false), toggledValues)
    }

    @Test
    fun userSubscriptionScreenShowsPurchaseHubWithInAppRubikaAndTelegramButtons() {
        composeRule.setContent {
            TmsVpnTheme {
                UserSubscriptionScreen(
                    userProfile = UserProfile(userId = "u_sub_test"),
                    isPersian = true
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("subscription_purchase_hub_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("sub_buy_rubika_btn").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("sub_buy_telegram_btn").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("sub_buy_in_app_btn").performScrollTo().assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("payment_request_dialog").assertIsDisplayed()
    }
}
