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
import com.example.ui.screens.UserHomeScreen
import com.example.ui.screens.UserServersScreen
import com.example.ui.theme.TmsVpnTheme
import com.example.vpn.engine.DynamicIpDnsRotator
import com.example.vpn.engine.FragmentProfile
import com.example.vpn.engine.IspAutoTuner
import com.example.vpn.engine.IspNetworkType
import com.example.vpn.engine.IspTuningReport
import com.example.vpn.engine.LeakCheckEngine
import com.example.vpn.engine.LeakCheckReport
import com.example.vpn.engine.ParsedVpnConfig
import com.example.vpn.engine.SmartRacingSelector
import com.example.vpn.engine.XrayCoreEngine
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SmartRacingAndIspTunerTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun ispAutoTunerClassifiesIranOperatorsAndPersistsCleanIpAndFragment() {
        val context = ApplicationProvider.getApplicationContext<Application>()

        assertEquals(IspNetworkType.WIFI, IspAutoTuner.classifyNetwork("wifi", ""))
        assertEquals(IspNetworkType.MCI, IspAutoTuner.classifyNetwork("cellular", "IR-MCI"))
        assertEquals(IspNetworkType.IRANCELL, IspAutoTuner.classifyNetwork("cellular", "MTN Irancell"))
        assertEquals(IspNetworkType.RIGHTEL, IspAutoTuner.classifyNetwork("cellular", "Rightel"))
        assertEquals(IspNetworkType.MOBILE_OTHER, IspAutoTuner.classifyNetwork("cellular", "CustomCarrier"))

        val mciFrag = IspAutoTuner.recommendFragmentForNetwork(IspNetworkType.MCI)
        assertEquals("10-20", mciFrag.length)
        assertEquals("10-15", mciFrag.interval)

        IspAutoTuner.persistTuningForNetwork(
            context = context,
            networkType = IspNetworkType.WIFI,
            cleanIp = "104.16.132.229",
            fragmentProfile = FragmentProfile("tlshello", "15-25", "10-15")
        )
        assertEquals("104.16.132.229", IspAutoTuner.getCachedCleanIp(context, IspNetworkType.WIFI))

        DynamicIpDnsRotator.promoteCleanIp("104.16.132.229")
        assertEquals("104.16.132.229", DynamicIpDnsRotator.currentCleanIp.value)
    }

    @Test
    fun xrayCoreEngineAppliesCustomIspFragmentProfile() {
        val tlsUri = "vless://22222222-2222-2222-2222-222222222222@ws.tmsvpn.net:443" +
            "?security=tls&sni=cdn.tmsvpn.net&type=ws&path=/ws#CustomFrag"
        val parsed = ParsedVpnConfig.from(tlsUri, "ws.tmsvpn.net", 443, "vless")
        val customProfile = FragmentProfile(packets = "tlshello", length = "5-15", interval = "5-12")

        val root = JSONObject(
            XrayCoreEngine.buildXrayJson(
                parsed = parsed,
                rawConfig = null,
                tunFd = -1,
                enableFragment = true,
                fragmentProfile = customProfile
            )
        )
        val outbounds = root.getJSONArray("outbounds")
        val fragmentObj = (0 until outbounds.length())
            .map { outbounds.getJSONObject(it) }
            .first { it.getString("tag") == "fragment-out" }
            .getJSONObject("settings")
            .getJSONObject("fragment")

        assertEquals("tlshello", fragmentObj.getString("packets"))
        assertEquals("5-15", fragmentObj.getString("length"))
        assertEquals("5-12", fragmentObj.getString("interval"))
    }

    @Test
    fun leakCheckEngineParsesCloudflareTraceAndIpWhoPayloads() {
        val trace = """
            fl=21f402
            h=1.1.1.1
            ip=185.199.108.153
            ts=1731835200.123
            loc=DE
        """.trimIndent()
        val (ip, loc) = LeakCheckEngine.parseCloudflareTrace(trace)
        assertEquals("185.199.108.153", ip)
        assertEquals("DE", loc)

        val ipWhoJson = """
            {
              "success": true,
              "ip": "91.108.56.130",
              "country": "Netherlands",
              "country_code": "NL",
              "connection": { "isp": "Hetzner Online GmbH", "org": "Hetzner" }
            }
        """.trimIndent()
        val parsed = LeakCheckEngine.parseIpWhoJson(ipWhoJson)
        assertEquals("91.108.56.130", parsed.exitIp)
        assertEquals("NL", parsed.countryCode)
        assertEquals("Netherlands", parsed.countryName)
        assertEquals("Hetzner Online GmbH", parsed.ispOrOrg)
        assertNull(parsed.errorMessage)
    }

    @Test
    fun smartRacingSelectorRanksFastestReachableNodeAndHandlesOffline() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val n1 = VpnConfig(id = "n1", name = "Slow Node", server = "slow.example.com", port = 443)
        val n2 = VpnConfig(id = "n2", name = "Fastest Node", server = "fast.example.com", port = 443)
        val n3 = VpnConfig(id = "n3", name = "Dead Node", server = "dead.example.com", port = 443)

        val summary = SmartRacingSelector.raceConfigs(
            context = context,
            configs = listOf(n1, n2, n3),
            maxParallel = 3
        ) { cfg ->
            when (cfg.id) {
                "n1" -> 210
                "n2" -> 48
                else -> -1
            }
        }

        assertEquals(3, summary.testedCount)
        assertEquals("n2", summary.winner?.id)
        assertEquals(48, summary.winnerDelayMs)
        assertEquals(listOf("n2", "n1", "n3"), summary.results.map { it.config.id })

        val allDead = SmartRacingSelector.raceConfigs(
            context = context,
            configs = listOf(n1, n3)
        ) { -1 }
        assertNull(allDead.winner)
        assertEquals(-1, allDead.winnerDelayMs)

        val pingPayload = JSONObject().put("success", true).put("data", 132).toString()
        val encoded = Base64.encodeToString(pingPayload.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        assertEquals(132, SmartRacingSelector.parseLibXrayPingResponse(encoded))
    }

    @Test
    fun homeAndServersScreensDisplayRacingTunerAndLeakGuardControls() {
        val cfg = VpnConfig(
            id = "n1",
            name = "Frankfurt Core",
            protocol = "vless",
            server = "de.tmsvpn.net",
            port = 443,
            rawConfig = "vless://11111111-1111-1111-1111-111111111111@de.tmsvpn.net:443#Frankfurt"
        )
        val ispReport = IspTuningReport(
            networkType = IspNetworkType.MCI,
            operatorLabel = "IR-MCI",
            transportLabel = "همراه اول (MCI)",
            bestCleanIp = "104.16.132.229",
            bestCleanIpLatencyMs = 64L,
            fragmentProfile = FragmentProfile("tlshello", "10-20", "10-15"),
            probedIpsCount = 8,
            reachableIpsCount = 5
        )
        val leakReport = LeakCheckReport(
            exitIp = "138.201.44.12",
            countryCode = "DE",
            countryName = "Germany",
            ispOrOrg = "Hetzner Online GmbH",
            routedViaVpnProxy = true,
            dnsStatusLabel = "DoH / SOCKS5 Tunnel Protected",
            isLeakProtected = true
        )

        composeRule.setContent {
            TmsVpnTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    UserHomeScreen(
                        vpnState = VpnConnectionState.CONNECTED,
                        sessionStats = VpnSessionStats(
                            state = VpnConnectionState.CONNECTED,
                            activeConfig = cfg
                        ),
                        configs = listOf(cfg),
                        servers = emptyList(),
                        selectedConfig = cfg,
                        isPersian = true,
                        onConnectVpn = {},
                        onDisconnectVpn = {},
                        onSelectConfig = {},
                        onImportConfig = {},
                        onImportBatch = {},
                        onOpenServersTab = {},
                        onOpenRadar = {},
                        ispTuningReport = ispReport,
                        leakCheckReport = leakReport
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_racing_connect_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("home_isp_tune_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("home_isp_report_badge").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("home_leak_check_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("home_leak_report_panel").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("138.201.44.12", substring = true).assertIsDisplayed()
        assertTrue(ispReport.reachableIpsCount == 5)
    }

    @Test
    fun serversScreenDisplaysParallelRacingButton() {
        val cfg = VpnConfig(
            id = "srv1",
            name = "Finland Edge",
            protocol = "vless",
            server = "fi.tmsvpn.net",
            port = 443,
            pingMs = 72,
            status = "online"
        )
        composeRule.setContent {
            TmsVpnTheme {
                UserServersScreen(
                    configs = listOf(cfg),
                    servers = emptyList(),
                    selectedConfig = cfg,
                    isPinging = false,
                    isPersian = true,
                    onSelectConfig = {},
                    onConnectSelected = {},
                    onPingConfig = {},
                    onToggleFavorite = {},
                    onAutoSelectBest = {},
                    onRacingConnectAll = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("servers_racing_all_button").assertIsDisplayed()
    }
}
