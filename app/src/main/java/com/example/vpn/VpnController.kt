package com.example.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.data.local.CleanIpHealthStatus
import com.example.data.local.LocalVpnConfigRepository
import com.example.data.model.DefaultVpnConfigs
import com.example.data.model.VpnConfig
import com.example.data.model.VpnConnectionState
import com.example.data.model.VpnSessionStats
import com.example.vpn.engine.AntiDpiComboEngine
import com.example.vpn.engine.AntiDpiFailureReason
import com.example.vpn.engine.IspAutoTuner
import com.example.vpn.engine.LeakCheckEngine
import com.example.vpn.engine.LeakCheckReport
import com.example.vpn.engine.TunnelHealthChecker
import com.example.vpn.engine.TunnelHealthReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

object VpnController {

    private val stateLock = Any()
    private val sessionGeneration = AtomicLong(0L)
    private val controllerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _connectionState = MutableStateFlow(VpnConnectionState.DISCONNECTED)
    val connectionState: StateFlow<VpnConnectionState> = _connectionState.asStateFlow()

    private val _sessionStats = MutableStateFlow(VpnSessionStats())
    val sessionStats: StateFlow<VpnSessionStats> = _sessionStats.asStateFlow()

    private val _healthReport = MutableStateFlow<TunnelHealthReport?>(null)
    val healthReport: StateFlow<TunnelHealthReport?> = _healthReport.asStateFlow()

    private val _healthCheckInProgress = MutableStateFlow(false)
    val healthCheckInProgress: StateFlow<Boolean> = _healthCheckInProgress.asStateFlow()

    private val _leakCheckReport = MutableStateFlow<LeakCheckReport?>(null)
    val leakCheckReport: StateFlow<LeakCheckReport?> = _leakCheckReport.asStateFlow()

    private val _leakCheckInProgress = MutableStateFlow(false)
    val leakCheckInProgress: StateFlow<Boolean> = _leakCheckInProgress.asStateFlow()

    private val _trafficCommittedFlow = MutableSharedFlow<Pair<String, Long>>(extraBufferCapacity = 32)
    val trafficCommittedFlow: SharedFlow<Pair<String, Long>> = _trafficCommittedFlow.asSharedFlow()

    @Volatile
    private var activeAccountId: String? = null

    private val failoverInFlight = AtomicBoolean(false)

    @Volatile
    var activeConfig: VpnConfig? = null
        private set

    fun setActiveAccountId(userId: String?) {
        activeAccountId = userId?.trim()?.takeIf { it.isNotBlank() }
    }

    fun getActiveAccountId(): String? = activeAccountId

    fun reportSessionTrafficDelta(context: Context, deltaBytes: Long) {
        if (deltaBytes <= 0L) return
        val uid = activeAccountId ?: return
        val appCtx = context.applicationContext
        controllerScope.launch {
            runCatching {
                LocalVpnConfigRepository(appCtx).recordTrafficUsageDelta(
                    userId = uid,
                    deltaBytes = deltaBytes
                )
                _trafficCommittedFlow.tryEmit(uid to deltaBytes)
            }
        }
    }

    fun triggerAutoFailoverDeTr(
        context: Context,
        failedConfigId: String?,
        attemptIndex: Int = 1
    ) {
        if (!failoverInFlight.compareAndSet(false, true)) return
        val appCtx = context.applicationContext
        controllerScope.launch {
            try {
                IspAutoTuner.rotateFragmentAndFingerprintForRetry(appCtx, attemptIndex)
                val repo = LocalVpnConfigRepository(appCtx)
                val stored = repo.allConfigs.firstOrNull().orEmpty()
                val defaults = DefaultVpnConfigs.getDefaultConfigs(activeAccountId ?: "local_user")
                val combined = (stored + defaults).distinctBy { it.id }
                val deTrCandidates = combined.filter { DefaultVpnConfigs.isDeOrTrNode(it) }
                val candidatePool = deTrCandidates.ifEmpty { combined }
                val nextConfig = candidatePool.firstOrNull { it.id != failedConfigId }
                    ?: candidatePool.firstOrNull()
                    ?: return@launch

                val healthyCleanIps = repo.getCleanIpPoolOnce()
                    .filter { it.healthStatus == CleanIpHealthStatus.HEALTHY.name && it.host.isNotBlank() }
                    .sortedBy { if (it.latencyMs > 0) it.latencyMs else Int.MAX_VALUE }
                val cleanIpOverride = healthyCleanIps
                    .getOrNull((attemptIndex - 1).coerceAtLeast(0) % healthyCleanIps.size.coerceAtLeast(1))
                    ?.host
                    ?: IspAutoTuner.getCachedCleanIp(appCtx)

                val useCombo = _comboShieldEnabled.value
                if (useCombo) {
                    val (netType, _) = IspAutoTuner.detectCurrentNetwork(appCtx)
                    val adapted = AntiDpiComboEngine.recommendPresetForNetwork(
                        networkType = netType,
                        attemptIndex = attemptIndex,
                        lastFailure = AntiDpiFailureReason.TIMEOUT
                    )
                    AntiDpiComboEngine.setActivePresetIndex(appCtx, adapted.index)
                    _activeComboSummary.value = adapted.shortSummary(true)
                }

                startVpn(
                    context = appCtx,
                    config = nextConfig,
                    antiFilterMode = true,
                    cleanIpOverride = cleanIpOverride,
                    forceRestart = true,
                    comboMode = useCombo
                )
            } finally {
                failoverInFlight.set(false)
            }
        }
    }

    fun currentGenerationId(): Long = sessionGeneration.get()

    fun updateState(state: VpnConnectionState, errorMessage: String? = null) {
        synchronized(stateLock) {
            _connectionState.value = state
            _sessionStats.value = when (state) {
                VpnConnectionState.DISCONNECTED -> VpnSessionStats(
                    state = VpnConnectionState.DISCONNECTED,
                    activeConfig = activeConfig ?: _sessionStats.value.activeConfig,
                    connectedDurationSeconds = 0L,
                    downloadSpeedBytesPerSec = 0L,
                    uploadSpeedBytesPerSec = 0L,
                    totalBytesReceived = 0L,
                    totalBytesSent = 0L,
                    realTunnelLatencyMs = -1,
                    stabilityScore = 0,
                    errorMessage = null
                )
                VpnConnectionState.ERROR -> _sessionStats.value.copy(
                    state = VpnConnectionState.ERROR,
                    activeConfig = activeConfig ?: _sessionStats.value.activeConfig,
                    downloadSpeedBytesPerSec = 0L,
                    uploadSpeedBytesPerSec = 0L,
                    errorMessage = errorMessage
                )
                else -> _sessionStats.value.copy(
                    state = state,
                    activeConfig = activeConfig ?: _sessionStats.value.activeConfig,
                    errorMessage = errorMessage
                )
            }
        }
    }

    fun markConnected(
        generationId: Long,
        config: VpnConfig? = null,
        initialLatencyMs: Int = -1,
        engineMode: String = "Kotlin TUN Router",
        xrayVersion: String = ""
    ): Boolean {
        synchronized(stateLock) {
            if (generationId != sessionGeneration.get()) return false
            if (config != null) {
                activeConfig = config
            }
            _connectionState.value = VpnConnectionState.CONNECTED
            _sessionStats.value = VpnSessionStats(
                state = VpnConnectionState.CONNECTED,
                activeConfig = activeConfig ?: _sessionStats.value.activeConfig,
                connectedDurationSeconds = 0L,
                downloadSpeedBytesPerSec = 0L,
                uploadSpeedBytesPerSec = 0L,
                totalBytesReceived = 0L,
                totalBytesSent = 0L,
                realTunnelLatencyMs = initialLatencyMs,
                stabilityScore = if (initialLatencyMs in 1..250) 99 else if (initialLatencyMs > 250) 85 else 90,
                errorMessage = null,
                engineMode = engineMode,
                xrayVersion = xrayVersion
            )
            return true
        }
    }

    fun markDisconnected(generationId: Long? = null): Boolean {
        synchronized(stateLock) {
            if (generationId != null && generationId != sessionGeneration.get()) return false
            _connectionState.value = VpnConnectionState.DISCONNECTED
            _healthReport.value = null
            _healthCheckInProgress.value = false
            _leakCheckReport.value = null
            _leakCheckInProgress.value = false
            _sessionStats.value = VpnSessionStats(
                state = VpnConnectionState.DISCONNECTED,
                activeConfig = activeConfig ?: _sessionStats.value.activeConfig,
                connectedDurationSeconds = 0L,
                downloadSpeedBytesPerSec = 0L,
                uploadSpeedBytesPerSec = 0L,
                totalBytesReceived = 0L,
                totalBytesSent = 0L,
                realTunnelLatencyMs = -1,
                stabilityScore = 0,
                errorMessage = null
            )
            return true
        }
    }

    fun markError(generationId: Long? = null, errorMessage: String): Boolean {
        synchronized(stateLock) {
            if (generationId != null && generationId != sessionGeneration.get()) return false
            _connectionState.value = VpnConnectionState.ERROR
            _healthCheckInProgress.value = false
            _sessionStats.value = _sessionStats.value.copy(
                state = VpnConnectionState.ERROR,
                activeConfig = activeConfig ?: _sessionStats.value.activeConfig,
                downloadSpeedBytesPerSec = 0L,
                uploadSpeedBytesPerSec = 0L,
                errorMessage = errorMessage
            )
            return true
        }
    }

    fun updateSessionStats(
        durationSec: Long,
        dlBytesSec: Long,
        ulBytesSec: Long,
        totalRx: Long,
        totalTx: Long,
        latencyMs: Int = -1,
        stabilityScore: Int = 98,
        generationId: Long? = null,
        engineMode: String? = null,
        xrayVersion: String? = null
    ) {
        synchronized(stateLock) {
            if (generationId != null && generationId != sessionGeneration.get()) return
            if (_connectionState.value != VpnConnectionState.CONNECTED) return
            val prev = _sessionStats.value
            _sessionStats.value = prev.copy(
                state = VpnConnectionState.CONNECTED,
                activeConfig = activeConfig ?: prev.activeConfig,
                connectedDurationSeconds = durationSec,
                downloadSpeedBytesPerSec = dlBytesSec,
                uploadSpeedBytesPerSec = ulBytesSec,
                totalBytesReceived = totalRx,
                totalBytesSent = totalTx,
                realTunnelLatencyMs = latencyMs,
                stabilityScore = stabilityScore,
                errorMessage = null,
                engineMode = engineMode ?: prev.engineMode,
                xrayVersion = xrayVersion ?: prev.xrayVersion
            )
        }
    }

    fun updateHealthReport(report: TunnelHealthReport?) {
        _healthReport.value = report
    }

    fun runTunnelHealthCheck(onResult: ((TunnelHealthReport) -> Unit)? = null) {
        if (_healthCheckInProgress.value) return
        _healthCheckInProgress.value = true
        controllerScope.launch {
            try {
                val report = TunnelHealthChecker.runFullHealthCheck()
                _healthReport.value = report
                onResult?.invoke(report)
            } finally {
                _healthCheckInProgress.value = false
            }
        }
    }

    fun runLeakGuardCheck(onResult: ((LeakCheckReport) -> Unit)? = null) {
        if (_leakCheckInProgress.value) return
        _leakCheckInProgress.value = true
        controllerScope.launch {
            try {
                val isTunnelActive = _connectionState.value == VpnConnectionState.CONNECTED
                val report = LeakCheckEngine.runLeakCheck(tunnelActive = isTunnelActive)
                _leakCheckReport.value = report
                onResult?.invoke(report)
            } finally {
                _leakCheckInProgress.value = false
            }
        }
    }

    private val _nationalIntranetMode = MutableStateFlow(false)
    val nationalIntranetMode: StateFlow<Boolean> = _nationalIntranetMode.asStateFlow()

    private val _iranRelayServer = MutableStateFlow("")
    val iranRelayServer: StateFlow<String> = _iranRelayServer.asStateFlow()

    private val _comboShieldEnabled = MutableStateFlow(false)
    val comboShieldEnabled: StateFlow<Boolean> = _comboShieldEnabled.asStateFlow()

    private val _activeComboSummary = MutableStateFlow("")
    val activeComboSummary: StateFlow<String> = _activeComboSummary.asStateFlow()

    fun initComboShieldState(context: Context, isEntitled: Boolean) {
        val persisted = AntiDpiComboEngine.isComboShieldEnabled(context)
        val effective = persisted && isEntitled
        _comboShieldEnabled.value = effective
        if (effective) {
            _activeComboSummary.value = AntiDpiComboEngine.getActivePreset(context).shortSummary(true)
        } else {
            _activeComboSummary.value = ""
        }
    }

    fun setComboShieldEnabled(context: Context, enabled: Boolean, isEntitled: Boolean): Boolean {
        if (enabled && !isEntitled) {
            _comboShieldEnabled.value = false
            AntiDpiComboEngine.setComboShieldEnabled(context, false)
            _activeComboSummary.value = ""
            return false
        }
        _comboShieldEnabled.value = enabled
        AntiDpiComboEngine.setComboShieldEnabled(context, enabled)
        _activeComboSummary.value = if (enabled) {
            AntiDpiComboEngine.getActivePreset(context).shortSummary(true)
        } else {
            ""
        }
        return true
    }

    fun updateActiveComboSummary(summary: String) {
        _activeComboSummary.value = summary
    }

    fun toggleNationalIntranetMode() {
        _nationalIntranetMode.value = !_nationalIntranetMode.value
    }

    fun setNationalIntranetMode(enabled: Boolean) {
        _nationalIntranetMode.value = enabled
    }

    fun setIranRelayServer(relay: String) {
        _iranRelayServer.value = relay
    }

    fun startVpn(
        context: Context,
        config: VpnConfig,
        antiFilterMode: Boolean = true,
        nationalIntranetMode: Boolean = _nationalIntranetMode.value,
        iranRelayHost: String? = _iranRelayServer.value.ifBlank { null },
        cleanIpOverride: String? = null,
        forceRestart: Boolean = false,
        comboMode: Boolean = _comboShieldEnabled.value
    ) {
        val genId: Long
        synchronized(stateLock) {
            val currentState = _connectionState.value
            val currentActive = activeConfig
            // Prevent starting duplicate tunnel if already connecting/connected to the exact same node and live
            if (!forceRestart &&
                (currentState == VpnConnectionState.CONNECTING || currentState == VpnConnectionState.CONNECTED) &&
                currentActive?.id == config.id &&
                VpnTunnelService.isTunnelOperational()
            ) {
                return
            }

            genId = sessionGeneration.incrementAndGet()
            activeConfig = config
            _connectionState.value = VpnConnectionState.CONNECTING
            _sessionStats.value = VpnSessionStats(
                state = VpnConnectionState.CONNECTING,
                activeConfig = config,
                connectedDurationSeconds = 0L,
                downloadSpeedBytesPerSec = 0L,
                uploadSpeedBytesPerSec = 0L,
                totalBytesReceived = 0L,
                totalBytesSent = 0L,
                realTunnelLatencyMs = -1,
                stabilityScore = 0,
                errorMessage = null
            )
        }

        try {
            VpnTunnelService.startTunnel(
                context = context,
                sessionId = genId,
                configId = config.id,
                host = config.server,
                port = config.port,
                protocol = config.protocol,
                configName = config.name,
                configJson = config.rawConfig,
                antiFilterMode = antiFilterMode,
                nationalIntranetMode = nationalIntranetMode,
                iranRelayHost = iranRelayHost,
                cleanIpOverride = cleanIpOverride,
                comboMode = comboMode
            )
        } catch (e: Exception) {
            markError(
                genId,
                "خطا در اجرای سرویس VPN: ${e.localizedMessage ?: "امکان شروع سرویس وجود ندارد"}"
            )
        }
    }

    fun stopVpn(context: Context) {
        val genId: Long
        synchronized(stateLock) {
            val current = _connectionState.value
            if (current == VpnConnectionState.DISCONNECTED) return
            genId = sessionGeneration.get()
            _connectionState.value = VpnConnectionState.DISCONNECTING
            _sessionStats.value = _sessionStats.value.copy(
                state = VpnConnectionState.DISCONNECTING,
                activeConfig = activeConfig ?: _sessionStats.value.activeConfig,
                errorMessage = null
            )
        }

        try {
            val stoppedDirectly = VpnTunnelService.stopTunnel(context)
            if (!stoppedDirectly) {
                // No live service instance existed; complete disconnect cleanly
                markDisconnected(genId)
            }
        } catch (e: Exception) {
            markError(
                genId,
                "خطا در قطع اتصال VPN: ${e.localizedMessage ?: "امکان توقف سرویس وجود ندارد"}"
            )
        }
    }

    /**
     * Reconciles VpnController reactive state with the actual running VpnTunnelService & OS VPN transport.
     * Called on Activity creation / resume to restore active sessions or detect unexpected termination.
     */
    fun syncWithService(context: Context) {
        val liveService = VpnTunnelService.activeInstance
        synchronized(stateLock) {
            if (liveService != null) {
                val snapshot = liveService.getLiveSnapshot()
                val liveRunId = liveService.getActiveRunId()
                if (liveRunId > sessionGeneration.get()) {
                    sessionGeneration.set(liveRunId)
                }
                if (liveService.isTunnelOperational()) {
                    if (snapshot.activeConfig != null) {
                        activeConfig = snapshot.activeConfig
                    }
                    _connectionState.value = VpnConnectionState.CONNECTED
                    _sessionStats.value = snapshot.copy(
                        state = VpnConnectionState.CONNECTED,
                        activeConfig = activeConfig ?: snapshot.activeConfig,
                        errorMessage = null
                    )
                    return
                } else if (liveService.isConnectingInProgress()) {
                    _connectionState.value = VpnConnectionState.CONNECTING
                    _sessionStats.value = _sessionStats.value.copy(
                        state = VpnConnectionState.CONNECTING,
                        activeConfig = activeConfig ?: snapshot.activeConfig
                    )
                    return
                }
            }

            // No live service instance: check if we thought we were active -> unexpected service termination
            val state = _connectionState.value
            if (state == VpnConnectionState.CONNECTED || state == VpnConnectionState.CONNECTING) {
                val hasSystemVpn = hasSystemVpnTransport(context)
                if (!hasSystemVpn) {
                    sessionGeneration.incrementAndGet()
                    _connectionState.value = VpnConnectionState.ERROR
                    _sessionStats.value = VpnSessionStats(
                        state = VpnConnectionState.ERROR,
                        activeConfig = activeConfig ?: _sessionStats.value.activeConfig,
                        connectedDurationSeconds = 0L,
                        downloadSpeedBytesPerSec = 0L,
                        uploadSpeedBytesPerSec = 0L,
                        totalBytesReceived = 0L,
                        totalBytesSent = 0L,
                        realTunnelLatencyMs = -1,
                        stabilityScore = 0,
                        errorMessage = "سرویس تونل VPN به‌طور غیرمنتظره متوقف شد"
                    )
                }
            } else if (state == VpnConnectionState.DISCONNECTING) {
                markDisconnected()
            }
        }
    }

    private fun hasSystemVpnTransport(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            cm.allNetworks.any { network ->
                cm.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            }
        } catch (_: Exception) {
            false
        }
    }
}
