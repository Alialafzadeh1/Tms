package com.example.vpn

import android.app.Application
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.VpnConfig
import com.example.data.model.VpnServer
import com.example.ui.screens.UserServersScreen
import com.example.ui.theme.TmsVpnTheme
import com.example.ui.viewmodel.TmsVpnViewModel
import com.example.vpn.engine.LatencyFilterTier
import com.example.vpn.engine.ServerLocationFilterEngine
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ServerLocationFilterAndLatencyUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Application

    private val germanyFast = VpnConfig(
        id = "loc_de",
        name = "Frankfurt Core",
        protocol = "vless",
        server = "de.tmsvpn.net",
        port = 443,
        pingMs = 64,
        status = "online"
    )
    private val finlandUltra = VpnConfig(
        id = "loc_fi",
        name = "Helsinki Edge",
        protocol = "trojan",
        server = "fi.tmsvpn.net",
        port = 443,
        pingMs = 38,
        status = "online"
    )
    private val netherlandsMedium = VpnConfig(
        id = "loc_nl",
        name = "Amsterdam Relay",
        protocol = "vmess",
        server = "nl.tmsvpn.net",
        port = 443,
        pingMs = 190,
        status = "online"
    )
    private val usOffline = VpnConfig(
        id = "loc_us",
        name = "New York Node",
        protocol = "shadowsocks",
        server = "us.tmsvpn.net",
        port = 443,
        pingMs = -1,
        status = "offline"
    )

    private val sampleConfigs = listOf(netherlandsMedium, usOffline, germanyFast, finlandUltra)

    private val sampleServers = listOf(
        VpnServer(id = "s_de", name = "Frankfurt", country = "Germany", countryCode = "DE", host = "de.tmsvpn.net", port = 443),
        VpnServer(id = "s_fi", name = "Helsinki", country = "Finland", countryCode = "FI", host = "fi.tmsvpn.net", port = 443),
        VpnServer(id = "s_nl", name = "Amsterdam", country = "Netherlands", countryCode = "NL", host = "nl.tmsvpn.net", port = 443),
        VpnServer(id = "s_us", name = "New York", country = "United States", countryCode = "US", host = "us.tmsvpn.net", port = 443)
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId("1:123456789012:android:abcdef1234567890")
                    .setApiKey("AIzaSyDummyTestApiKeyForUnitTestsOnly")
                    .setProjectId("tms-vpn-test")
                    .build()
            )
        }
    }

    @Test
    fun filterEngineFiltersByLatencyTiersCountryAndSortsFastestFirst() {
        val sortedAll = ServerLocationFilterEngine.filterAndSortLocations(
            configs = sampleConfigs,
            servers = sampleServers,
            liveLatencies = emptyMap(),
            latencyTier = LatencyFilterTier.ALL,
            sortByLatency = true
        )
        assertEquals(listOf("loc_fi", "loc_de", "loc_nl", "loc_us"), sortedAll.map { it.id })

        val ultraFast = ServerLocationFilterEngine.filterAndSortLocations(
            configs = sampleConfigs,
            servers = sampleServers,
            liveLatencies = emptyMap(),
            latencyTier = LatencyFilterTier.ULTRA_FAST
        )
        assertEquals(listOf("loc_fi", "loc_de"), ultraFast.map { it.id })

        // Live coroutine latency overrides stale config pingMs
        val updatedLive = mapOf("loc_nl" to 49, "loc_de" to 210)
        val liveUltra = ServerLocationFilterEngine.filterAndSortLocations(
            configs = sampleConfigs,
            servers = sampleServers,
            liveLatencies = updatedLive,
            latencyTier = LatencyFilterTier.ULTRA_FAST
        )
        assertEquals(listOf("loc_fi", "loc_nl"), liveUltra.map { it.id })

        val onlineGermany = ServerLocationFilterEngine.filterAndSortLocations(
            configs = sampleConfigs,
            servers = sampleServers,
            liveLatencies = updatedLive,
            countryCodeFilter = "DE",
            latencyTier = LatencyFilterTier.MODERATE
        )
        assertEquals(listOf("loc_de"), onlineGermany.map { it.id })
    }

    @Test
    fun viewModelRefreshesServerLatenciesInBackgroundCoroutine() {
        val viewModel = TmsVpnViewModel(context)

        val deadlineMs = System.currentTimeMillis() + 5000L
        while (viewModel.configs.value.isEmpty() && System.currentTimeMillis() < deadlineMs) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }

        viewModel.refreshAllServerLatencies(
            context = context,
            force = true,
            maxParallel = 4
        ) { cfg ->
            delay(15)
            when {
                cfg.id.contains("fi") -> 31
                cfg.id.contains("de") -> 58
                cfg.id.contains("nl") -> 142
                else -> -1
            }
        }

        val scanDeadline = System.currentTimeMillis() + 5000L
        while ((viewModel.isRefreshingLatencies.value || viewModel.liveLatenciesMs.value.isEmpty()) &&
            System.currentTimeMillis() < scanDeadline
        ) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }

        val liveMap = viewModel.liveLatenciesMs.value
        assertFalse(viewModel.isRefreshingLatencies.value)
        assertTrue("Expected live coroutine latency entries", liveMap.isNotEmpty())
        val (done, total) = viewModel.latencyScanProgress.value
        assertTrue("Expected scan progress to complete", total > 0 && done == total)

        viewModel.setLatencyFilterTier(LatencyFilterTier.ULTRA_FAST)
        assertEquals(LatencyFilterTier.ULTRA_FAST, viewModel.selectedLatencyTier.value)

        viewModel.toggleSortLocationsByLatency()
        assertFalse(viewModel.sortLocationsByLatency.value)
    }

    @Test
    fun serversScreenFiltersByLatencyAndSupportsSelectionAndRefresh() {
        var selected by mutableStateOf<VpnConfig?>(null)
        var connectedLocationId by mutableStateOf<String?>(null)
        var refreshTriggered by mutableStateOf(false)
        var tier by mutableStateOf(LatencyFilterTier.ALL)

        composeRule.setContent {
            TmsVpnTheme {
                UserServersScreen(
                    configs = sampleConfigs,
                    servers = sampleServers,
                    selectedConfig = selected,
                    isPinging = false,
                    isPersian = false,
                    onSelectConfig = { selected = it },
                    onConnectSelected = { connectedLocationId = it.id },
                    onPingConfig = {},
                    onToggleFavorite = {},
                    onAutoSelectBest = { selected = finlandUltra },
                    liveLatencies = mapOf(
                        "loc_fi" to 38,
                        "loc_de" to 64,
                        "loc_nl" to 190,
                        "loc_us" to -1
                    ),
                    isRefreshingLatencies = false,
                    latencyScanProgress = 4 to 4,
                    selectedLatencyTier = tier,
                    onRefreshLatencies = { refreshTriggered = true },
                    onLatencyTierSelected = { tier = it }
                )
            }
        }

        composeRule.waitForIdle()

        // Trigger background latency refresh button
        composeRule.onNodeWithTag("servers_refresh_latencies_button").performScrollTo().performClick()
        assertTrue(refreshTriggered)

        // Scroll LazyColumn to Latency Filter bar, then click < 80ms Ultra Fast tier
        composeRule.onNodeWithTag("servers_location_list")
            .performScrollToNode(hasTestTag("latency_filter_chip_ALL"))
        composeRule.onNodeWithTag("latency_filter_chip_FAST_80").performScrollTo().performClick()
        composeRule.waitForIdle()

        // Sub-80ms servers remain visible; 190ms & offline nodes are filtered out
        composeRule.onNodeWithTag("servers_location_list")
            .performScrollToNode(hasTestTag("server_item_loc_fi"))
        composeRule.onNodeWithTag("server_item_loc_fi").assertIsDisplayed()

        composeRule.onNodeWithTag("servers_location_list")
            .performScrollToNode(hasTestTag("server_item_loc_de"))
        composeRule.onNodeWithTag("server_item_loc_de").assertIsDisplayed()

        composeRule.onAllNodesWithTag("server_item_loc_nl").assertCountEquals(0)
        composeRule.onAllNodesWithTag("server_item_loc_us").assertCountEquals(0)

        // Select Germany server and connect
        composeRule.onNodeWithTag("server_item_loc_de").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("servers_location_list")
            .performScrollToNode(hasTestTag("connect_server_button_loc_de"))
        composeRule.onNodeWithTag("connect_server_button_loc_de").performClick()
        assertEquals("loc_de", connectedLocationId)

        // Combine <80ms latency filter with 190ms Netherlands region -> shows empty state -> Reset restores list
        composeRule.onNodeWithTag("servers_location_list")
            .performScrollToNode(hasTestTag("location_filter_chip_ALL"))
        composeRule.onNodeWithTag("location_filter_chip_NL").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("servers_location_list")
            .performScrollToNode(hasTestTag("servers_empty_filtered_state"))
        composeRule.onNodeWithTag("servers_empty_filtered_state").assertIsDisplayed()

        composeRule.onNodeWithTag("reset_latency_filters_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("servers_location_list")
            .performScrollToNode(hasTestTag("server_item_loc_nl"))
        composeRule.onNodeWithTag("server_item_loc_nl").assertIsDisplayed()
    }
}
