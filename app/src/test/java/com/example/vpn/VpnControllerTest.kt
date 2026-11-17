package com.example.vpn

import android.app.Application
import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.VpnConfig
import com.example.data.model.VpnConnectionState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VpnControllerTest {

    private val sampleConfig = VpnConfig(
        id = "cfg-1",
        name = "Germany Ultra Node",
        protocol = "vless",
        server = "104.16.132.229",
        port = 443,
        rawConfig = "vless://11111111-1111-1111-1111-111111111111@104.16.132.229:443?security=tls#Germany"
    )

    @After
    fun tearDown() {
        VpnTunnelService.activeInstance?.let { svc ->
            try { svc.requestDisconnect() } catch (_: Exception) {}
        }
        VpnController.markDisconnected()
    }

    @Test
    fun startVpnAndConnectedTransitionsKeepConnectionStateAndSessionStatsSynced() {
        val app = ApplicationProvider.getApplicationContext<Application>()

        VpnController.startVpn(app, sampleConfig)
        val genId = VpnController.currentGenerationId()

        assertEquals(VpnConnectionState.CONNECTING, VpnController.connectionState.value)
        assertEquals(VpnConnectionState.CONNECTING, VpnController.sessionStats.value.state)
        assertEquals(sampleConfig, VpnController.sessionStats.value.activeConfig)
        assertNull(VpnController.sessionStats.value.errorMessage)

        val startedIntent = Shadows.shadowOf(app).nextStartedService
        assertNotNull(startedIntent)
        assertEquals(VpnTunnelService::class.java.name, startedIntent.component?.className)
        assertEquals(VpnTunnelService.ACTION_CONNECT, startedIntent.action)
        assertEquals(genId, startedIntent.getLongExtra(VpnTunnelService.EXTRA_SESSION_ID, -1L))

        assertTrue(VpnController.markConnected(genId, sampleConfig, initialLatencyMs = 48))
        assertEquals(VpnConnectionState.CONNECTED, VpnController.connectionState.value)
        assertEquals(VpnConnectionState.CONNECTED, VpnController.sessionStats.value.state)
        assertEquals(48, VpnController.sessionStats.value.realTunnelLatencyMs)

        VpnController.updateSessionStats(
            durationSec = 12L,
            dlBytesSec = 2048L,
            ulBytesSec = 1024L,
            totalRx = 50000L,
            totalTx = 25000L,
            latencyMs = 58,
            stabilityScore = 99,
            generationId = genId
        )
        assertEquals(VpnConnectionState.CONNECTED, VpnController.sessionStats.value.state)
        assertEquals(12L, VpnController.sessionStats.value.connectedDurationSeconds)
        assertEquals(2048L, VpnController.sessionStats.value.downloadSpeedBytesPerSec)
        assertEquals(58, VpnController.sessionStats.value.realTunnelLatencyMs)

        // Stop VPN when no live service exists still transitions cleanly to DISCONNECTED & resets counters
        VpnController.stopVpn(app)
        assertEquals(VpnConnectionState.DISCONNECTED, VpnController.connectionState.value)
        assertEquals(VpnConnectionState.DISCONNECTED, VpnController.sessionStats.value.state)
        assertEquals(0L, VpnController.sessionStats.value.connectedDurationSeconds)
        assertEquals(0L, VpnController.sessionStats.value.downloadSpeedBytesPerSec)
        assertEquals(-1, VpnController.sessionStats.value.realTunnelLatencyMs)
    }

    @Test
    fun staleGenerationTokenCannotOverwriteNewConnectionState() {
        val app = ApplicationProvider.getApplicationContext<Application>()

        VpnController.startVpn(app, sampleConfig)
        val staleGenId = VpnController.currentGenerationId()

        VpnController.startVpn(app, sampleConfig.copy(id = "cfg-2", name = "Finland Node"))
        val newGenId = VpnController.currentGenerationId()

        // Stale disconnect/connect/error from earlier attempt must be ignored
        assertFalse(VpnController.markDisconnected(staleGenId))
        assertFalse(VpnController.markConnected(staleGenId))
        assertFalse(VpnController.markError(staleGenId, "Stale error"))
        assertEquals(VpnConnectionState.CONNECTING, VpnController.connectionState.value)

        // Current generation succeeds
        assertTrue(VpnController.markConnected(newGenId, initialLatencyMs = -1))
        assertEquals(VpnConnectionState.CONNECTED, VpnController.connectionState.value)
        assertEquals(-1, VpnController.sessionStats.value.realTunnelLatencyMs)
    }

    @Test
    fun syncWithServiceDetectsUnexpectedTerminationWhenServiceIsMissing() {
        val app = ApplicationProvider.getApplicationContext<Application>()

        VpnController.startVpn(app, sampleConfig)
        val genId = VpnController.currentGenerationId()
        assertTrue(VpnController.markConnected(genId, sampleConfig, initialLatencyMs = 42))

        // No VpnTunnelService activeInstance exists in memory or ConnectivityManager
        VpnController.syncWithService(app)
        assertEquals(VpnConnectionState.ERROR, VpnController.connectionState.value)
        assertNotNull(VpnController.sessionStats.value.errorMessage)
        assertTrue(VpnController.sessionStats.value.errorMessage!!.contains("غیرمنتظره"))
    }

    @Test
    fun serviceBinderAndRevocationReportCorrectLifecycleStates() {
        val controller = Robolectric.buildService(VpnTunnelService::class.java).create()
        val service = controller.get()

        val binder = service.onBind(Intent(VpnTunnelService.ACTION_BIND_LOCAL))
        assertTrue(binder is VpnTunnelService.LocalBinder)
        assertEquals(service, (binder as VpnTunnelService.LocalBinder).getService())

        val app = ApplicationProvider.getApplicationContext<Application>()
        VpnController.startVpn(app, sampleConfig)
        val genId = VpnController.currentGenerationId()
        assertTrue(VpnController.markConnected(genId, sampleConfig, initialLatencyMs = 35))

        // Simulate OS revoking VPN permission unexpectedly
        service.onRevoke()
        assertEquals(VpnConnectionState.ERROR, VpnController.connectionState.value)
        assertTrue(VpnController.sessionStats.value.errorMessage!!.contains("لغو"))

        controller.destroy()
    }

    @Test
    fun unexpectedServiceDestroyWhileOperationalMovesStateToError() {
        val controller = Robolectric.buildService(VpnTunnelService::class.java).create()
        val service = controller.get()

        // Mark internal flags to simulate active TUN session before sudden OS kill
        val pipe = ParcelFileDescriptor.createPipe()
        try {
            val vpnInterfaceField = VpnTunnelService::class.java.getDeclaredField("vpnInterface")
            vpnInterfaceField.isAccessible = true
            vpnInterfaceField.set(service, pipe[0])

            val establishedField = VpnTunnelService::class.java.getDeclaredField("isTunnelEstablished")
            establishedField.isAccessible = true
            (establishedField.get(service) as java.util.concurrent.atomic.AtomicBoolean).set(true)

            val app = ApplicationProvider.getApplicationContext<Application>()
            VpnController.startVpn(app, sampleConfig)
            VpnController.markConnected(VpnController.currentGenerationId(), sampleConfig, 30)

            // Re-sync confirms operational session stays CONNECTED
            VpnController.syncWithService(app)
            assertEquals(VpnConnectionState.CONNECTED, VpnController.connectionState.value)

            // Unexpected onDestroy without user disconnect reports ERROR
            controller.destroy()
            assertEquals(VpnConnectionState.ERROR, VpnController.connectionState.value)
            assertTrue(VpnController.sessionStats.value.errorMessage!!.contains("غیرمنتظره"))
        } finally {
            try { pipe[0].close() } catch (_: Exception) {}
            try { pipe[1].close() } catch (_: Exception) {}
        }
    }

    @Test
    fun errorStatePreservesMessageAndClearsOnReconnectAttempt() {
        val app = ApplicationProvider.getApplicationContext<Application>()

        VpnController.updateState(VpnConnectionState.ERROR, "مجوز ایجاد تونل VPN تایید نشد")
        assertEquals(VpnConnectionState.ERROR, VpnController.connectionState.value)
        assertEquals(VpnConnectionState.ERROR, VpnController.sessionStats.value.state)
        assertEquals("مجوز ایجاد تونل VPN تایید نشد", VpnController.sessionStats.value.errorMessage)

        VpnController.startVpn(app, sampleConfig)
        assertEquals(VpnConnectionState.CONNECTING, VpnController.connectionState.value)
        assertNull(VpnController.sessionStats.value.errorMessage)
    }
}
