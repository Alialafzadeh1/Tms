package com.example.vpn

import android.app.Application
import android.util.Base64
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.VpnConfig
import com.example.data.model.VpnConnectionState
import com.example.data.model.VpnSessionStats
import com.example.data.network.SubscriptionImporter
import com.example.ui.screens.UserHomeScreen
import com.example.ui.theme.TmsVpnTheme
import com.example.vpn.engine.ParsedVpnConfig
import com.example.vpn.engine.TunnelHealthReport
import com.example.vpn.engine.XrayCoreEngine
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TmsProductionUpgradesTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun xrayCoreEngineBuildsValidVlessRealityAndTlsFragmentJson() {
        val rawUri = "vless://11111111-1111-1111-1111-111111111111@de.tmsvpn.net:443" +
            "?security=reality&sni=www.google.com&fp=chrome&pbk=TEST_PBK&sid=ab12&type=tcp&flow=xtls-rprx-vision#Germany-Reality"
        val parsed = ParsedVpnConfig.from(rawUri, "de.tmsvpn.net", 443, "vless")
        val vpnCfg = VpnConfig(
            id = "reality-1",
            name = "Germany-Reality",
            protocol = "vless",
            server = "de.tmsvpn.net",
            port = 443,
            uuidOrPassword = "11111111-1111-1111-1111-111111111111",
            transport = "tcp",
            security = "reality",
            sni = "www.google.com",
            rawConfig = rawUri
        )

        val jsonStr = XrayCoreEngine.buildXrayJson(
            parsed = parsed,
            rawConfig = vpnCfg,
            tunFd = 42,
            enableFragment = true
        )
        val root = JSONObject(jsonStr)
        assertEquals("42", root.getJSONObject("env").getString("xray.tun.fd"))

        val inbounds = root.getJSONArray("inbounds")
        assertEquals("tun", inbounds.getJSONObject(0).getString("protocol"))
        assertEquals("socks", inbounds.getJSONObject(1).getString("protocol"))

        val proxy = root.getJSONArray("outbounds").getJSONObject(0)
        assertEquals("vless", proxy.getString("protocol"))
        val user = proxy.getJSONObject("settings").getJSONArray("vnext").getJSONObject(0)
            .getJSONArray("users").getJSONObject(0)
        assertEquals("xtls-rprx-vision", user.getString("flow"))

        val reality = proxy.getJSONObject("streamSettings").getJSONObject("realitySettings")
        assertEquals("TEST_PBK", reality.getString("publicKey"))
        assertEquals("ab12", reality.getString("shortId"))

        // Also check TLS + WS uses fragment-out dialerProxy
        val tlsWsUri = "vless://22222222-2222-2222-2222-222222222222@ws.tmsvpn.net:443" +
            "?security=tls&sni=cdn.tmsvpn.net&type=ws&path=/ws-tunnel#WS-TLS"
        val tlsParsed = ParsedVpnConfig.from(tlsWsUri, "ws.tmsvpn.net", 443, "vless")
        val tlsJson = JSONObject(
            XrayCoreEngine.buildXrayJson(
                parsed = tlsParsed,
                rawConfig = VpnConfig(
                    protocol = "vless",
                    server = "ws.tmsvpn.net",
                    port = 443,
                    uuidOrPassword = "22222222-2222-2222-2222-222222222222",
                    transport = "ws",
                    security = "tls",
                    sni = "cdn.tmsvpn.net",
                    pathOrServiceName = "/ws-tunnel",
                    rawConfig = tlsWsUri
                ),
                tunFd = 99,
                enableFragment = true
            )
        )
        val tlsProxy = tlsJson.getJSONArray("outbounds").getJSONObject(0)
        assertEquals(
            "fragment-out",
            tlsProxy.getJSONObject("streamSettings").getJSONObject("sockopt").getString("dialerProxy")
        )
    }

    @Test
    fun splitTunnelManagerClassifiesIranianBankingAppsAndPersistsToggles() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        SplitTunnelManager.init(context)

        assertTrue(SplitTunnelManager.isIranianOrBankingPackage("ir.resaneh1.iptv"))
        assertTrue(SplitTunnelManager.isIranianOrBankingPackage("com.bmi.mobilebank"))
        assertTrue(SplitTunnelManager.isIranianOrBankingPackage("cab.snapp.passenger"))
        assertFalse(SplitTunnelManager.isIranianOrBankingPackage("org.telegram.messenger"))

        SplitTunnelManager.setSplitTunnelEnabled(context, true)
        SplitTunnelManager.setBypassIranAppsEnabled(context, true)
        SplitTunnelManager.setAutoReconnectEnabled(context, true)
        SplitTunnelManager.setKillSwitchEnabled(context, true)
        SplitTunnelManager.togglePackageBypass(context, "com.example.custombypass")

        val disallowed = SplitTunnelManager.getEffectiveDisallowedPackages(context)
        assertTrue(disallowed.contains(context.packageName))
        assertTrue(disallowed.contains("ir.resaneh1.iptv"))
        assertTrue(disallowed.contains("com.example.custombypass"))
        assertTrue(SplitTunnelManager.isKillSwitchEnabled(context))
        assertTrue(SplitTunnelManager.isAutoReconnectEnabled(context))
    }

    @Test
    fun subscriptionImporterDecodesBase64AndPlainMultiLinePayloads() = runBlocking {
        val vlessLine = "vless://11111111-1111-1111-1111-111111111111@de.tmsvpn.net:443?security=tls&type=ws#SubNode1"
        val trojanLine = "trojan://secretpass99@nl.tmsvpn.net:443?security=tls&sni=nl.tmsvpn.net#SubNode2"
        val combined = "$vlessLine\n$trojanLine"
        val b64 = Base64.encodeToString(combined.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

        val decodedConfigs = SubscriptionImporter.parseSubscriptionPayload(b64, "user-123")
        assertEquals(2, decodedConfigs.size)
        assertEquals("vless", decodedConfigs[0].protocol)
        assertEquals("trojan", decodedConfigs[1].protocol)

        val plainConfigs = SubscriptionImporter.parseSubscriptionPayload(combined, "user-123")
        assertEquals(2, plainConfigs.size)

        val invalidScheme = SubscriptionImporter.fetchSubscription("ftp://invalid.example.com/sub", "user-123")
        assertFalse(invalidScheme.isSuccess)
    }

    @Test
    fun userHomeScreenDisplaysEngineBadgeHealthProbeAndEmergencyChannels() {
        val cfg1 = VpnConfig(
            id = "n1",
            name = "Germany Reality",
            protocol = "vless",
            server = "de.tmsvpn.net",
            port = 443,
            rawConfig = "vless://11111111-1111-1111-1111-111111111111@de.tmsvpn.net:443#Germany"
        )
        val cfg2 = cfg1.copy(id = "n2", name = "Finland Backup", server = "fi.tmsvpn.net")
        val report = TunnelHealthReport(
            global204Reachable = false,
            global204LatencyMs = -1L,
            telegramReachable = false,
            telegramLatencyMs = -1L,
            instagramReachable = false,
            instagramLatencyMs = -1L,
            isHealthy = false
        )

        composeRule.setContent {
            TmsVpnTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    UserHomeScreen(
                        vpnState = VpnConnectionState.CONNECTED,
                        sessionStats = VpnSessionStats(
                            state = VpnConnectionState.CONNECTED,
                            activeConfig = cfg1,
                            connectedDurationSeconds = 12L,
                            engineMode = "Xray Core Native (TCP + UDP)",
                            xrayVersion = "26.6.27"
                        ),
                        configs = listOf(cfg1, cfg2),
                        servers = emptyList(),
                        selectedConfig = cfg1,
                        isPersian = true,
                        onConnectVpn = {},
                        onDisconnectVpn = {},
                        onSelectConfig = {},
                        onImportConfig = {},
                        onImportBatch = {},
                        onOpenServersTab = {},
                        onOpenRadar = {},
                        healthReport = report
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_engine_mode_badge").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("home_tunnel_health_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("home_failover_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("home_emergency_channels_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("home_channel_rubika_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("home_channel_telegram_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Global 204").assertIsDisplayed()
    }
}
