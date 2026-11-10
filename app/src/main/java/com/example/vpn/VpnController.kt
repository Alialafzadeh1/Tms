package com.example.vpn

import android.content.Context
import android.content.Intent
import com.example.data.model.VpnConfig
import com.example.data.model.VpnConnectionState
import com.example.data.model.VpnSessionStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object VpnController {

    private val _connectionState = MutableStateFlow(VpnConnectionState.DISCONNECTED)
    val connectionState: StateFlow<VpnConnectionState> = _connectionState.asStateFlow()

    private val _sessionStats = MutableStateFlow(VpnSessionStats())
    val sessionStats: StateFlow<VpnSessionStats> = _sessionStats.asStateFlow()

    var activeConfig: VpnConfig? = null
        private set

    fun updateState(state: VpnConnectionState, errorMessage: String? = null) {
        _connectionState.value = state
        _sessionStats.value = _sessionStats.value.copy(
            state = state,
            errorMessage = errorMessage
        )
    }

    fun updateSessionStats(
        durationSec: Long,
        dlBytesSec: Long,
        ulBytesSec: Long,
        totalRx: Long,
        totalTx: Long,
        latencyMs: Int = -1,
        stabilityScore: Int = 98
    ) {
        _sessionStats.value = _sessionStats.value.copy(
            connectedDurationSeconds = durationSec,
            downloadSpeedBytesPerSec = dlBytesSec,
            uploadSpeedBytesPerSec = ulBytesSec,
            totalBytesReceived = totalRx,
            totalBytesSent = totalTx,
            realTunnelLatencyMs = latencyMs,
            stabilityScore = stabilityScore
        )
    }

    fun startVpn(context: Context, config: VpnConfig, antiFilterMode: Boolean = true) {
        activeConfig = config
        _sessionStats.value = VpnSessionStats(
            state = VpnConnectionState.CONNECTING,
            activeConfig = config
        )
        VpnTunnelService.startTunnel(
            context = context,
            host = config.server,
            port = config.port,
            protocol = config.protocol,
            configName = config.name,
            configJson = config.rawConfig,
            antiFilterMode = antiFilterMode
        )
    }

    fun stopVpn(context: Context) {
        _connectionState.value = VpnConnectionState.DISCONNECTING
        VpnTunnelService.stopTunnel(context)
    }
}
