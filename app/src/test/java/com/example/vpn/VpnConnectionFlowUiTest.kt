package com.example.vpn

import android.app.Application
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.VpnConfig
import com.example.data.model.VpnConnectionState
import com.example.ui.screens.UserHomeScreen
import com.example.ui.theme.TmsVpnTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VpnConnectionFlowUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val testConfig = VpnConfig(
        id = "node-de-1",
        name = "Germany Cyber Node",
        protocol = "vless",
        server = "104.16.132.229",
        port = 443,
        rawConfig = "vless://11111111-1111-1111-1111-111111111111@104.16.132.229:443?security=tls#Germany"
    )

    @After
    fun tearDown() {
        VpnTunnelService.activeInstance?.requestDisconnect()
        VpnController.markDisconnected()
    }

    @Test
    fun fullConnectTimerDisconnectAndErrorFlowsSynchronizeComposeUi() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        VpnController.markDisconnected()

        composeRule.setContent {
            val vpnState by VpnController.connectionState.collectAsStateWithLifecycle()
            val stats by VpnController.sessionStats.collectAsStateWithLifecycle()
            TmsVpnTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    UserHomeScreen(
                        vpnState = vpnState,
                        sessionStats = stats,
                        configs = listOf(testConfig),
                        servers = emptyList(),
                        selectedConfig = testConfig,
                        isPersian = true,
                        onConnectVpn = { VpnController.startVpn(context, testConfig) },
                        onDisconnectVpn = { VpnController.stopVpn(context) },
                        onSelectConfig = {},
                        onImportConfig = {},
                        onImportBatch = {},
                        onOpenServersTab = {},
                        onOpenRadar = {}
                    )
                }
            }
        }

        // 1. Disconnected initial state
        composeRule.waitForIdle()
        composeRule.onNodeWithText("آماده اتصال امن").assertIsDisplayed()
        composeRule.onNodeWithTag("home_connect_button").assertTextContains("اتصال امن")
        composeRule.onNodeWithTag("vpn_connect_orb_button").assertIsDisplayed()

        // 2. Press Connect -> CONNECTING only (NOT CONNECTED yet)
        composeRule.onNodeWithTag("home_connect_button").performClick()
        composeRule.waitForIdle()
        assertEquals(VpnConnectionState.CONNECTING, VpnController.connectionState.value)
        composeRule.onNodeWithTag("home_connect_button").assertTextContains("در حال اتصال...")
        composeRule.onNodeWithTag("home_connected_banner").assertDoesNotExist()

        // 3. Confirm tunnel establishment -> CONNECTED + timer + Disconnect button
        val genId = VpnController.currentGenerationId()
        VpnController.markConnected(genId, testConfig, initialLatencyMs = 42)
        VpnController.updateSessionStats(
            durationSec = 42L,
            dlBytesSec = 4096L,
            ulBytesSec = 1024L,
            totalRx = 8192L,
            totalTx = 2048L,
            latencyMs = 42,
            generationId = genId
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("vpn_status_pill").assertTextContains("متصل و رمزنگاری‌شده")
        composeRule.onNodeWithTag("home_connected_banner").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("00:42", substring = true).onFirst().assertIsDisplayed()
        composeRule.onNodeWithTag("home_connect_button").performScrollTo().assertTextContains("قطع اتصال")

        // 4. Transition to DISCONNECTING disables duplicate click, then DISCONNECTED resets timer
        VpnController.updateState(VpnConnectionState.DISCONNECTING)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_connect_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("home_connect_button").assertTextContains("در حال قطع اتصال...")

        VpnController.markDisconnected()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_connect_button").assertIsEnabled()
        composeRule.onNodeWithTag("vpn_status_pill").assertTextContains("آماده اتصال امن")
        composeRule.onNodeWithTag("home_connect_button").assertTextContains("اتصال امن")
        composeRule.onNodeWithTag("home_connected_banner").assertDoesNotExist()

        // 5. Permission denied / error displays Persian error message and never CONNECTED
        VpnController.updateState(
            VpnConnectionState.ERROR,
            "مجوز ایجاد تونل VPN تایید نشد"
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("vpn_status_pill").assertTextContains("خطای اتصال")
        composeRule.onNodeWithTag("home_error_banner").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("مجوز ایجاد تونل VPN تایید نشد").assertIsDisplayed()
    }

    @Test
    fun reopeningUiWhileServiceIsOperationalRestoresConnectedState() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val serviceController = Robolectric.buildService(VpnTunnelService::class.java).create()
        val service = serviceController.get()
        val pipe = ParcelFileDescriptor.createPipe()

        try {
            val vpnInterfaceField = VpnTunnelService::class.java.getDeclaredField("vpnInterface")
            vpnInterfaceField.isAccessible = true
            vpnInterfaceField.set(service, pipe[0])

            val establishedField = VpnTunnelService::class.java.getDeclaredField("isTunnelEstablished")
            establishedField.isAccessible = true
            (establishedField.get(service) as AtomicBoolean).set(true)

            val configField = VpnTunnelService::class.java.getDeclaredField("serviceActiveConfig")
            configField.isAccessible = true
            configField.set(service, testConfig)

            val startField = VpnTunnelService::class.java.getDeclaredField("sessionStartTimeMs")
            startField.isAccessible = true
            startField.set(service, System.currentTimeMillis() - 15_000L)

            VpnController.syncWithService(context)
            assertEquals(VpnConnectionState.CONNECTED, VpnController.connectionState.value)

            composeRule.setContent {
                val vpnState by VpnController.connectionState.collectAsStateWithLifecycle()
                val stats by VpnController.sessionStats.collectAsStateWithLifecycle()
                TmsVpnTheme {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        UserHomeScreen(
                            vpnState = vpnState,
                            sessionStats = stats,
                            configs = listOf(testConfig),
                            servers = emptyList(),
                            selectedConfig = stats.activeConfig,
                            isPersian = true,
                            onConnectVpn = {},
                            onDisconnectVpn = { VpnController.stopVpn(context) },
                            onSelectConfig = {},
                            onImportConfig = {},
                            onImportBatch = {},
                            onOpenServersTab = {},
                            onOpenRadar = {}
                        )
                    }
                }
            }

            composeRule.waitForIdle()
            composeRule.onNodeWithTag("vpn_status_pill").assertTextContains("متصل و رمزنگاری‌شده")
            composeRule.onNodeWithTag("home_connected_banner").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithTag("home_connect_button").performScrollTo().assertTextContains("قطع اتصال")

            // Clicking the orb while connected triggers stopVpn -> DISCONNECTED
            composeRule.onNodeWithTag("vpn_connect_orb_button").performScrollTo().performClick()
            composeRule.waitForIdle()
            assertEquals(VpnConnectionState.DISCONNECTED, VpnController.connectionState.value)
            composeRule.onNodeWithTag("vpn_status_pill").performScrollTo().assertTextContains("آماده اتصال امن")
        } finally {
            try { pipe[0].close() } catch (_: Exception) {}
            try { pipe[1].close() } catch (_: Exception) {}
            try { serviceController.destroy() } catch (_: Exception) {}
        }
    }
}
