package com.example.vpn

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.example.MainActivity
import com.example.data.local.LocalVpnConfigRepository
import com.example.data.model.DefaultVpnConfigs
import com.example.data.model.VpnConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

private const val TAG = "TmsVpnTileService"

/**
 * Android Quick Settings Tile for one-tap VPN connect/disconnect from the system shade.
 * Synchronizes directly with VpnController.connectionState and opens MainActivity only when
 * OS VPN consent hasn't been granted yet.
 */
class TmsVpnTileService : TileService() {

    private var tileScope: CoroutineScope? = null
    private var stateJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        VpnController.syncWithService(this)
        tileScope?.cancel()
        val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
        tileScope = scope
        stateJob = scope.launch {
            VpnController.connectionState.collect { state ->
                updateTileForState(state)
            }
        }
    }

    override fun onStopListening() {
        stateJob?.cancel()
        stateJob = null
        tileScope?.cancel()
        tileScope = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        VpnController.syncWithService(this)
        val currentState = VpnController.connectionState.value
        if (currentState == VpnConnectionState.CONNECTED || currentState == VpnConnectionState.CONNECTING) {
            VpnController.stopVpn(this)
            updateTileForState(VpnConnectionState.DISCONNECTING)
            return
        }

        val prepareIntent = try {
            VpnService.prepare(this)
        } catch (e: Exception) {
            Log.w(TAG, "VpnService.prepare failed: ${e.message}")
            null
        }

        if (prepareIntent != null) {
            openMainAppForConsent()
            return
        }

        val scope = tileScope ?: CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
        scope.launch {
            val targetConfig = VpnController.activeConfig
                ?: runCatching {
                    LocalVpnConfigRepository(applicationContext).allConfigs.firstOrNull()?.firstOrNull()
                }.getOrNull()
                ?: DefaultVpnConfigs.getDefaultConfigs().firstOrNull()

            if (targetConfig == null) {
                openMainAppForConsent()
                return@launch
            }

            updateTileForState(VpnConnectionState.CONNECTING)
            VpnController.startVpn(
                context = applicationContext,
                config = targetConfig,
                antiFilterMode = true
            )
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openMainAppForConsent() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    1042,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to launch MainActivity from QS tile: ${e.message}")
        }
    }

    private fun updateTileForState(state: VpnConnectionState) {
        val tile = qsTile ?: return
        val nodeName = VpnController.activeConfig?.name?.takeIf { it.isNotBlank() } ?: "TMS VPN"
        when (state) {
            VpnConnectionState.CONNECTED -> {
                tile.state = Tile.STATE_ACTIVE
                tile.label = "TMS VPN"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = "متصل • $nodeName"
                }
            }
            VpnConnectionState.CONNECTING -> {
                tile.state = Tile.STATE_ACTIVE
                tile.label = "TMS VPN"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = "در حال اتصال..."
                }
            }
            VpnConnectionState.DISCONNECTING -> {
                tile.state = Tile.STATE_INACTIVE
                tile.label = "TMS VPN"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = "در حال قطع..."
                }
            }
            VpnConnectionState.ERROR -> {
                tile.state = Tile.STATE_INACTIVE
                tile.label = "TMS VPN"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = "خطا در اتصال"
                }
            }
            VpnConnectionState.DISCONNECTED -> {
                tile.state = Tile.STATE_INACTIVE
                tile.label = "TMS VPN"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = "آماده اتصال امن"
                }
            }
        }
        try {
            tile.updateTile()
        } catch (_: Exception) {}
    }
}
