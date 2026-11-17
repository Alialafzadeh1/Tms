package com.example.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.TrafficStats
import android.net.VpnService
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.VpnConfig
import com.example.data.model.VpnConnectionState
import com.example.data.model.VpnSessionStats
import com.example.vpn.engine.DnsPacketHandler
import com.example.vpn.engine.DynamicIpDnsRotator
import com.example.vpn.engine.Ipv4Header
import com.example.vpn.engine.IspAutoTuner
import com.example.vpn.engine.NationalIntranetPiercer
import com.example.vpn.engine.PacketBufferPool
import com.example.vpn.engine.ParsedVpnConfig
import com.example.vpn.engine.TcpHeader
import com.example.vpn.engine.TcpTunRouter
import com.example.vpn.engine.UdpHeader
import com.example.vpn.engine.XrayCoreEngine
import com.example.vpn.engine.XrayEngineMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "VpnTunnelService"
private const val NOTIFICATION_ID = 9110
private const val CHANNEL_ID = "tms_vpn_tunnel_channel"

/**
 * Android VpnService implementation managing the real lifecycle of the VPN tunnel.
 *
 * Intercepts Layer 3 IP packets from the TUN virtual interface, routes DNS queries
 * through protected DNS resolvers, dynamically manages TCP sessions to real proxy nodes,
 * and exposes live state/binder synchronization for VpnController & Compose UI.
 */
class VpnTunnelService : VpnService() {

    inner class LocalBinder : Binder() {
        fun getService(): VpnTunnelService = this@VpnTunnelService
    }

    companion object {
        const val ACTION_CONNECT = "com.example.vpn.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.example.vpn.ACTION_DISCONNECT"
        const val ACTION_BIND_LOCAL = "com.example.vpn.ACTION_BIND_LOCAL"

        const val EXTRA_SESSION_ID = "extra_session_id"
        const val EXTRA_CONFIG_ID = "extra_config_id"
        const val EXTRA_SERVER_HOST = "extra_server_host"
        const val EXTRA_SERVER_PORT = "extra_server_port"
        const val EXTRA_PROTOCOL = "extra_protocol"
        const val EXTRA_CONFIG_NAME = "extra_config_name"
        const val EXTRA_CONFIG_JSON = "extra_config_json"
        const val EXTRA_ANTI_FILTER_MODE = "extra_anti_filter_mode"
        const val EXTRA_NATIONAL_INTRANET_MODE = "extra_national_intranet_mode"
        const val EXTRA_IRAN_RELAY_HOST = "extra_iran_relay_host"

        @Volatile
        var activeInstance: VpnTunnelService? = null
            private set

        fun isTunnelOperational(): Boolean {
            return activeInstance?.isTunnelOperational() == true
        }

        fun startTunnel(
            context: Context,
            sessionId: Long,
            configId: String,
            host: String,
            port: Int,
            protocol: String,
            configName: String,
            configJson: String = "",
            antiFilterMode: Boolean = true,
            nationalIntranetMode: Boolean = false,
            iranRelayHost: String? = null
        ) {
            val intent = Intent(context, VpnTunnelService::class.java).apply {
                action = ACTION_CONNECT
                putExtra(EXTRA_SESSION_ID, sessionId)
                putExtra(EXTRA_CONFIG_ID, configId)
                putExtra(EXTRA_SERVER_HOST, host)
                putExtra(EXTRA_SERVER_PORT, port)
                putExtra(EXTRA_PROTOCOL, protocol)
                putExtra(EXTRA_CONFIG_NAME, configName)
                putExtra(EXTRA_CONFIG_JSON, configJson)
                putExtra(EXTRA_ANTI_FILTER_MODE, antiFilterMode)
                putExtra(EXTRA_NATIONAL_INTRANET_MODE, nationalIntranetMode)
                putExtra(EXTRA_IRAN_RELAY_HOST, iranRelayHost)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Stops the running tunnel cleanly.
         * Returns true if an active service instance existed and disconnect was dispatched;
         * false if no live service instance was running.
         */
        fun stopTunnel(context: Context): Boolean {
            val running = activeInstance
            if (running != null) {
                running.requestDisconnect()
                return true
            }
            return try {
                val intent = Intent(context, VpnTunnelService::class.java).apply {
                    action = ACTION_DISCONNECT
                }
                context.startService(intent)
                false
            } catch (_: Exception) {
                false
            }
        }
    }

    private val localBinder = LocalBinder()

    @Volatile
    private var vpnInterface: ParcelFileDescriptor? = null

    @Volatile
    private var tcpRouter: TcpTunRouter? = null

    @Volatile
    private var dnsHandler: DnsPacketHandler? = null

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var workerJob: Job? = null
    private var metricsJob: Job? = null
    private var rotationWorkerJob: Job? = null
    private var networkLossGraceJob: Job? = null

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    @Volatile
    private var activeEngineMode: XrayEngineMode = XrayEngineMode.KOTLIN_ROUTER

    @Volatile
    private var activeXrayVersion: String = ""

    private val currentRunId = AtomicLong(0L)
    private val isTunnelEstablished = AtomicBoolean(false)
    private val isConnecting = AtomicBoolean(false)
    private val userInitiatedDisconnect = AtomicBoolean(false)

    @Volatile
    private var serviceActiveConfig: VpnConfig? = null

    @Volatile
    private var sessionStartTimeMs = 0L

    @Volatile
    private var lastRxBytes = 0L

    @Volatile
    private var lastTxBytes = 0L

    @Volatile
    private var latestSnapshot: VpnSessionStats = VpnSessionStats()

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        createNotificationChannel()
        // Non-blocking native JNI + Xray asset warm-up on IO dispatcher
        XrayCoreEngine.preloadNativeAssetsAsync(this, serviceScope)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return if (intent?.action == SERVICE_INTERFACE) {
            super.onBind(intent)
        } else {
            localBinder
        }
    }

    fun isTunnelOperational(): Boolean {
        return isTunnelEstablished.get() && vpnInterface != null
    }

    fun isConnectingInProgress(): Boolean {
        return isConnecting.get()
    }

    fun getActiveRunId(): Long = currentRunId.get()

    fun getLiveSnapshot(): VpnSessionStats {
        val duration = if (isTunnelEstablished.get() && sessionStartTimeMs > 0L) {
            ((System.currentTimeMillis() - sessionStartTimeMs) / 1000L).coerceAtLeast(0L)
        } else {
            0L
        }
        return latestSnapshot.copy(
            state = when {
                isTunnelEstablished.get() -> VpnConnectionState.CONNECTED
                isConnecting.get() -> VpnConnectionState.CONNECTING
                else -> latestSnapshot.state
            },
            activeConfig = serviceActiveConfig ?: latestSnapshot.activeConfig,
            connectedDurationSeconds = duration
        )
    }

    fun requestDisconnect() {
        userInitiatedDisconnect.set(true)
        terminateTunnel(isUserInitiated = true)
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {}
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val controllerGenId = intent.getLongExtra(EXTRA_SESSION_ID, -1L)
                val runId = if (controllerGenId > 0L) controllerGenId else VpnController.currentGenerationId()
                val configId = intent.getStringExtra(EXTRA_CONFIG_ID).orEmpty()
                val host = intent.getStringExtra(EXTRA_SERVER_HOST).orEmpty()
                val port = intent.getIntExtra(EXTRA_SERVER_PORT, 443)
                val protocol = intent.getStringExtra(EXTRA_PROTOCOL) ?: "VLESS"
                val name = intent.getStringExtra(EXTRA_CONFIG_NAME) ?: "TMS Secure Node"
                val configJson = intent.getStringExtra(EXTRA_CONFIG_JSON) ?: ""
                val antiFilter = intent.getBooleanExtra(EXTRA_ANTI_FILTER_MODE, true)
                val nationalIntranet = intent.getBooleanExtra(EXTRA_NATIONAL_INTRANET_MODE, false)
                val iranRelay = intent.getStringExtra(EXTRA_IRAN_RELAY_HOST)

                val existingCfg = VpnController.activeConfig
                val configSnapshot = if (existingCfg != null &&
                    (configId.isBlank() || existingCfg.id == configId)
                ) {
                    existingCfg
                } else {
                    VpnConfig(
                        id = configId.ifBlank { "${host}_$port" },
                        name = name,
                        protocol = protocol,
                        server = host,
                        port = port,
                        rawConfig = configJson
                    )
                }

                // Prevent accidental duplicate start on exact same active node
                if ((isTunnelOperational() || isConnecting.get()) &&
                    serviceActiveConfig?.id == configSnapshot.id &&
                    runId == currentRunId.get()
                ) {
                    return START_NOT_STICKY
                }

                userInitiatedDisconnect.set(false)
                serviceActiveConfig = configSnapshot

                val statusText = if (nationalIntranet) {
                    "سپر نفوذ اینترنت ملی فعال شد • در حال اتصال از مسیر اضطراری"
                } else {
                    "در حال اتصال و راه‌اندازی تونل امن... / Connecting"
                }

                try {
                    val notification = buildNotification(name, statusText)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        startForeground(
                            NOTIFICATION_ID,
                            notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                        )
                    } else {
                        startForeground(NOTIFICATION_ID, notification)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Foreground service start failed: ${e.message}", e)
                    isConnecting.set(false)
                    VpnController.markError(
                        runId,
                        "خطا در اجرای سرویس پیش‌زمینه VPN: ${e.localizedMessage ?: e.javaClass.simpleName}"
                    )
                    stopSelf()
                    return START_NOT_STICKY
                }

                startTunnelConnection(
                    runId = runId,
                    configSnapshot = configSnapshot,
                    host = host,
                    port = port,
                    protocol = protocol,
                    configName = name,
                    configJson = configJson,
                    antiFilterMode = antiFilter,
                    nationalIntranetMode = nationalIntranet,
                    iranRelayHost = iranRelay
                )
            }
            ACTION_DISCONNECT -> {
                requestDisconnect()
            }
        }
        return START_NOT_STICKY
    }

    private fun startTunnelConnection(
        runId: Long,
        configSnapshot: VpnConfig,
        host: String,
        port: Int,
        protocol: String,
        configName: String,
        configJson: String,
        antiFilterMode: Boolean,
        nationalIntranetMode: Boolean = false,
        iranRelayHost: String? = null
    ) {
        currentRunId.set(runId)
        isConnecting.set(true)
        isTunnelEstablished.set(false)

        // Cancel previous tunnel workers and tear down old TUN before starting new run
        workerJob?.cancel()
        metricsJob?.cancel()
        rotationWorkerJob?.cancel()
        networkLossGraceJob?.cancel()
        unregisterNetworkCallback()
        try { XrayCoreEngine.stop() } catch (_: Exception) {}
        try { tcpRouter?.closeAll() } catch (_: Exception) {}
        tcpRouter = null
        dnsHandler = null
        try { vpnInterface?.close() } catch (_: Exception) {}
        vpnInterface = null

        workerJob = serviceScope.launch {
            var localPfd: ParcelFileDescriptor? = null
            var localRouter: TcpTunRouter? = null
            try {
                Log.i(TAG, "Starting REAL tunnel runId=$runId to $host:$port via $protocol")
                // Overlap non-blocking native asset loading concurrently with permission & TUN setup
                val nativePreloadDeferred = XrayCoreEngine.preloadNativeAssetsAsync(this@VpnTunnelService, serviceScope)
                SplitTunnelManager.init(this@VpnTunnelService)

                // 1. Verify OS VPN permission is actually granted
                if (prepare(this@VpnTunnelService) != null) {
                    failTunnelRun(runId, "مجوز ایجاد تونل VPN صادر نشده یا لغو شده است")
                    return@launch
                }

                // 2. Verify active underlying internet network exists
                if (!hasUnderlyingInternetNetwork()) {
                    failTunnelRun(runId, "اتصال اینترنت دستگاه برقرار نیست؛ لطفاً شبکه را بررسی کنید")
                    return@launch
                }

                // 3. Parse and validate engine configuration
                var parsedConfig = ParsedVpnConfig.from(
                    rawText = configJson,
                    fallbackHost = host,
                    fallbackPort = port,
                    fallbackProto = protocol
                )
                if (parsedConfig.server.isBlank() || parsedConfig.port !in 1..65535) {
                    failTunnelRun(runId, "پیکربندی سرور تونل نامعتبر است")
                    return@launch
                }

                if (nationalIntranetMode) {
                    parsedConfig = NationalIntranetPiercer.transformForIntranetPiercing(
                        originalConfig = parsedConfig,
                        customIranRelay = iranRelayHost
                    )
                }

                // 4. Establish TUN Virtual Network Interface with Play-compliant Split Tunneling
                val builder = Builder()
                    .setSession("TMS VPN - $configName")
                    .setMtu(1500)
                    .addAddress("10.8.0.2", 24)
                    .addRoute("0.0.0.0", 0)
                    .addDnsServer("1.1.1.1")
                    .addDnsServer("8.8.8.8")

                val disallowedApps = SplitTunnelManager.getEffectiveDisallowedPackages(this@VpnTunnelService)
                for (pkg in disallowedApps) {
                    try {
                        builder.addDisallowedApplication(pkg)
                    } catch (e: Exception) {
                        Log.d(TAG, "addDisallowedApplication skipped for $pkg: ${e.message}")
                    }
                }

                localPfd = builder.establish()
                if (localPfd == null) {
                    failTunnelRun(runId, "خطا در ایجاد کارت شبکه مجازی TUN")
                    return@launch
                }
                vpnInterface = localPfd

                if (!isActive || userInitiatedDisconnect.get() || currentRunId.get() != runId) {
                    return@launch
                }

                // 5. Launch native libXray Core asynchronously after overlapped asset loading with ISP-tuned Fragment & Clean IP
                nativePreloadDeferred.await()
                IspAutoTuner.getCachedCleanIp(this@VpnTunnelService)?.let { cachedIp ->
                    DynamicIpDnsRotator.promoteCleanIp(cachedIp)
                }
                val activeFragmentProfile = IspAutoTuner.getActiveFragmentProfile(this@VpnTunnelService)
                val xrayStart = XrayCoreEngine.startWithTunAsync(
                    context = this@VpnTunnelService,
                    vpnService = this@VpnTunnelService,
                    tunFd = localPfd.fd,
                    parsed = parsedConfig,
                    rawConfig = configSnapshot,
                    enableFragment = antiFilterMode,
                    fragmentProfile = activeFragmentProfile,
                    preloadScope = serviceScope
                )
                activeEngineMode = xrayStart.mode
                activeXrayVersion = xrayStart.xrayVersion

                var tunInput: FileInputStream? = null
                var dns: DnsPacketHandler? = null
                if (!xrayStart.startedNative) {
                    tunInput = FileInputStream(localPfd.fileDescriptor)
                    val tunOutput = FileOutputStream(localPfd.fileDescriptor)
                    dns = DnsPacketHandler(this@VpnTunnelService, tunOutput, serviceScope)
                    dnsHandler = dns

                    localRouter = TcpTunRouter(
                        vpnService = this@VpnTunnelService,
                        tunOutput = tunOutput,
                        scope = serviceScope,
                        config = parsedConfig,
                        antiFilterMode = antiFilterMode
                    )
                    tcpRouter = localRouter
                }

                if (!isActive || userInitiatedDisconnect.get() || currentRunId.get() != runId) {
                    return@launch
                }

                // 6. Measure real initial ping latency (-1 if unreachable; never fabricated)
                val initialPing = measureRealPing(parsedConfig.server, parsedConfig.port)

                // 7. Register network loss monitor for active session
                registerNetworkInterruptionCallback(runId)

                // 8. Transition to CONNECTED only AFTER TUN + engine are operational
                sessionStartTimeMs = System.currentTimeMillis()
                lastRxBytes = 0L
                lastTxBytes = 0L
                isConnecting.set(false)
                isTunnelEstablished.set(true)

                val initialLatencyInt = initialPing.toInt()
                latestSnapshot = VpnSessionStats(
                    state = VpnConnectionState.CONNECTED,
                    activeConfig = configSnapshot,
                    connectedDurationSeconds = 0L,
                    downloadSpeedBytesPerSec = 0L,
                    uploadSpeedBytesPerSec = 0L,
                    totalBytesReceived = 0L,
                    totalBytesSent = 0L,
                    realTunnelLatencyMs = initialLatencyInt,
                    stabilityScore = if (initialLatencyInt in 1..250) 99 else if (initialLatencyInt > 250) 85 else 90,
                    errorMessage = null,
                    engineMode = activeEngineMode.labelEn,
                    xrayVersion = activeXrayVersion
                )
                VpnController.markConnected(
                    generationId = runId,
                    config = configSnapshot,
                    initialLatencyMs = initialLatencyInt,
                    engineMode = activeEngineMode.labelEn,
                    xrayVersion = activeXrayVersion
                )

                val modeBadge = if (xrayStart.startedNative) "Xray Core" else "TUN Router"
                updateNotification(
                    configName,
                    "متصل ($modeBadge) • تمام برنامه‌ها تحت تونل امن فعال شدند"
                )

                // 9. Start live telemetry loop, 10-second DNS/IP rotation, & initial tunnel health probe
                startMetricsLoop(runId, configName, parsedConfig.server, parsedConfig.port, initialPing)
                startBackgroundDnsRefreshAndIpRotationWorker()
                serviceScope.launch {
                    delay(700L)
                    if (currentRunId.get() == runId && isTunnelEstablished.get()) {
                        VpnController.runTunnelHealthCheck()
                    }
                }

                // 10. Keep-alive monitor (native libXray) OR Raw IP Packet Dispatcher (Kotlin Router)
                if (xrayStart.startedNative) {
                    while (isActive && !userInitiatedDisconnect.get() && vpnInterface != null && XrayCoreEngine.isXrayRunning()) {
                        delay(500L)
                    }
                } else if (tunInput != null && localRouter != null && dns != null) {
                    PacketBufferPool.useBuffer(PacketBufferPool.LARGE_CAPACITY) { packetBuffer ->
                        while (isActive && !userInitiatedDisconnect.get() && vpnInterface != null) {
                            val length = try {
                                tunInput.read(packetBuffer)
                            } catch (io: IOException) {
                                if (!userInitiatedDisconnect.get()) {
                                    Log.w(TAG, "TUN read ended unexpectedly: ${io.message}")
                                }
                                -1
                            }
                            if (length <= 0) break

                            val ipHeader = Ipv4Header.parse(packetBuffer, 0, length) ?: continue

                            when (ipHeader.protocol) {
                                1 -> localRouter.handleIcmpEcho(ipHeader, packetBuffer, 0, length)
                                17 -> {
                                    val udpHeader = UdpHeader.parse(
                                        packetBuffer,
                                        ipHeader.headerLengthBytes,
                                        length - ipHeader.headerLengthBytes
                                    )
                                    if (udpHeader != null && (udpHeader.dstPort == 53 || udpHeader.srcPort == 53)) {
                                        val queryOffset = ipHeader.headerLengthBytes + 8
                                        val queryLen = (length - queryOffset).coerceAtLeast(0)
                                        dns.handleDnsQuery(ipHeader, udpHeader, packetBuffer, queryOffset, queryLen)
                                    }
                                }
                                6 -> {
                                    val tcpHeader = TcpHeader.parse(
                                        packetBuffer,
                                        ipHeader.headerLengthBytes,
                                        length - ipHeader.headerLengthBytes
                                    )
                                    if (tcpHeader != null) {
                                        localRouter.handleTcpPacket(ipHeader, tcpHeader, packetBuffer, 0, length)
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "VPN Tunnel runId=$runId cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "Tunnel execution error", e)
                if (currentRunId.get() == runId && !userInitiatedDisconnect.get()) {
                    VpnController.markError(
                        runId,
                        "خطای اتصال تونل: ${e.localizedMessage ?: "اتصال به سرور برقرار نشد"}"
                    )
                }
            } finally {
                // Only clean up shared state if this coroutine is still the active run
                if (currentRunId.get() == runId) {
                    isConnecting.set(false)
                    isTunnelEstablished.set(false)
                    metricsJob?.cancel()
                    rotationWorkerJob?.cancel()
                    networkLossGraceJob?.cancel()
                    unregisterNetworkCallback()
                    try { XrayCoreEngine.stop() } catch (_: Exception) {}
                    try { localRouter?.closeAll() } catch (_: Exception) {}
                    try { localPfd?.close() } catch (_: Exception) {}
                    if (vpnInterface === localPfd) vpnInterface = null
                    if (tcpRouter === localRouter) tcpRouter = null

                    val currentState = VpnController.connectionState.value
                    if (userInitiatedDisconnect.get()) {
                        VpnController.markDisconnected(runId)
                    } else if (currentState == VpnConnectionState.CONNECTED || currentState == VpnConnectionState.CONNECTING) {
                        VpnController.markError(runId, "تونل VPN به‌طور غیرمنتظره قطع شد")
                        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
                        stopSelf()
                    }
                } else {
                    try { localRouter?.closeAll() } catch (_: Exception) {}
                    try { localPfd?.close() } catch (_: Exception) {}
                }
            }
        }
    }

    private fun failTunnelRun(runId: Long, message: String) {
        isConnecting.set(false)
        isTunnelEstablished.set(false)
        if (currentRunId.get() == runId) {
            VpnController.markError(runId, message)
        }
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
        stopSelf()
    }

    private fun hasUnderlyingInternetNetwork(): Boolean {
        return try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
            val active = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(active) ?: return true
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            true
        }
    }

    private fun registerNetworkInterruptionCallback(runId: Long) {
        unregisterNetworkCallback()
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    networkLossGraceJob?.cancel()
                    networkLossGraceJob = null
                }

                override fun onLost(network: Network) {
                    if (currentRunId.get() == runId && !userInitiatedDisconnect.get()) {
                        val autoReconnect = SplitTunnelManager.isAutoReconnectEnabled(this@VpnTunnelService)
                        if (autoReconnect) {
                            networkLossGraceJob?.cancel()
                            networkLossGraceJob = serviceScope.launch {
                                delay(1800L)
                                if (currentRunId.get() == runId &&
                                    !userInitiatedDisconnect.get() &&
                                    !hasUnderlyingInternetNetwork()
                                ) {
                                    Log.w(TAG, "Underlying network remained unavailable after handoff grace period")
                                    terminateTunnel(
                                        isUserInitiated = false,
                                        errorReason = "ارتباط شبکه اینترنت قطع شد"
                                    )
                                    try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
                                    stopSelf()
                                }
                            }
                        } else if (!hasUnderlyingInternetNetwork()) {
                            Log.w(TAG, "Underlying network lost during active VPN session")
                            terminateTunnel(
                                isUserInitiated = false,
                                errorReason = "ارتباط شبکه اینترنت قطع شد"
                            )
                            try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
                            stopSelf()
                        }
                    }
                }
            }
            cm.registerNetworkCallback(request, callback)
            networkCallback = callback
        } catch (e: Exception) {
            Log.d(TAG, "NetworkCallback registration skipped: ${e.message}")
        }
    }

    private fun unregisterNetworkCallback() {
        networkLossGraceJob?.cancel()
        networkLossGraceJob = null
        val cb = networkCallback ?: return
        networkCallback = null
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            cm?.unregisterNetworkCallback(cb)
        } catch (_: Exception) {}
    }

    private fun startMetricsLoop(
        runId: Long,
        configName: String,
        host: String,
        port: Int,
        initialPing: Long
    ) {
        metricsJob = serviceScope.launch {
            var measuredLatency = initialPing
            var pingCheckCounter = 0
            var lastTrafficRx = TrafficStats.getTotalRxBytes()
            var lastTrafficTx = TrafficStats.getTotalTxBytes()
            var nativeSessionRx = 0L
            var nativeSessionTx = 0L

            while (isActive && currentRunId.get() == runId && isTunnelEstablished.get()) {
                delay(1000L)
                val router = tcpRouter
                val duration = ((System.currentTimeMillis() - sessionStartTimeMs) / 1000L).coerceAtLeast(0L)

                val currentDeviceRx = TrafficStats.getTotalRxBytes()
                val currentDeviceTx = TrafficStats.getTotalTxBytes()

                val trafficDeltaIn = if (currentDeviceRx != TrafficStats.UNSUPPORTED.toLong() &&
                    lastTrafficRx != TrafficStats.UNSUPPORTED.toLong() && lastTrafficRx > 0
                ) {
                    (currentDeviceRx - lastTrafficRx).coerceAtLeast(0L)
                } else {
                    0L
                }
                val trafficDeltaOut = if (currentDeviceTx != TrafficStats.UNSUPPORTED.toLong() &&
                    lastTrafficTx != TrafficStats.UNSUPPORTED.toLong() && lastTrafficTx > 0
                ) {
                    (currentDeviceTx - lastTrafficTx).coerceAtLeast(0L)
                } else {
                    0L
                }

                if (currentDeviceRx != TrafficStats.UNSUPPORTED.toLong()) lastTrafficRx = currentDeviceRx
                if (currentDeviceTx != TrafficStats.UNSUPPORTED.toLong()) lastTrafficTx = currentDeviceTx

                val effectiveRxTotal: Long
                val effectiveTxTotal: Long
                val effectiveDlSpeed: Long
                val effectiveUlSpeed: Long

                if (router != null) {
                    val routerIn = router.totalRxBytes.get()
                    val routerOut = router.totalTxBytes.get()
                    val routerDeltaIn = (routerIn - lastRxBytes).coerceAtLeast(0L)
                    val routerDeltaOut = (routerOut - lastTxBytes).coerceAtLeast(0L)
                    lastRxBytes = routerIn
                    lastTxBytes = routerOut
                    effectiveRxTotal = routerIn
                    effectiveTxTotal = routerOut
                    effectiveDlSpeed = maxOf(routerDeltaIn, trafficDeltaIn)
                    effectiveUlSpeed = maxOf(routerDeltaOut, trafficDeltaOut)
                } else {
                    nativeSessionRx += trafficDeltaIn
                    nativeSessionTx += trafficDeltaOut
                    effectiveRxTotal = nativeSessionRx
                    effectiveTxTotal = nativeSessionTx
                    effectiveDlSpeed = trafficDeltaIn
                    effectiveUlSpeed = trafficDeltaOut
                }

                // Measure real TCP ping every 5 seconds (-1 if unreachable; never fabricated)
                pingCheckCounter++
                if (pingCheckCounter >= 5) {
                    pingCheckCounter = 0
                    measuredLatency = measureRealPing(host, port)
                }

                val latencyInt = measuredLatency.toInt()
                val stability = when {
                    latencyInt in 1..250 -> 99
                    latencyInt > 250 -> 85
                    else -> 80
                }

                latestSnapshot = VpnSessionStats(
                    state = VpnConnectionState.CONNECTED,
                    activeConfig = serviceActiveConfig,
                    connectedDurationSeconds = duration,
                    downloadSpeedBytesPerSec = effectiveDlSpeed,
                    uploadSpeedBytesPerSec = effectiveUlSpeed,
                    totalBytesReceived = effectiveRxTotal,
                    totalBytesSent = effectiveTxTotal,
                    realTunnelLatencyMs = latencyInt,
                    stabilityScore = stability,
                    errorMessage = null,
                    engineMode = activeEngineMode.labelEn,
                    xrayVersion = activeXrayVersion
                )

                VpnController.updateSessionStats(
                    durationSec = duration,
                    dlBytesSec = effectiveDlSpeed,
                    ulBytesSec = effectiveUlSpeed,
                    totalRx = effectiveRxTotal,
                    totalTx = effectiveTxTotal,
                    latencyMs = latencyInt,
                    stabilityScore = stability,
                    generationId = runId,
                    engineMode = activeEngineMode.labelEn,
                    xrayVersion = activeXrayVersion
                )

                val mbTotal = (effectiveRxTotal + effectiveTxTotal) / (1024.0 * 1024.0)
                val timeStr = formatTime(duration)
                val pingLabel = if (latencyInt > 0) "${latencyInt}ms" else "--"
                updateNotification(
                    configName,
                    "متصل • $timeStr • پینگ: $pingLabel • حجم: ${String.format("%.1f", mbTotal)} MB"
                )
            }
        }
    }

    private fun startBackgroundDnsRefreshAndIpRotationWorker() {
        rotationWorkerJob?.cancel()
        rotationWorkerJob = serviceScope.launch {
            Log.i(TAG, "Background Worker started: managing 10s periodic DNS refresh & server IP rotation")
            while (isActive) {
                delay(10_000L)
                if (DynamicIpDnsRotator.isAutoRotateEnabled.value) {
                    DynamicIpDnsRotator.forceHopNow()
                    val activeIp = DynamicIpDnsRotator.currentCleanIp.value
                    val activeDns = DynamicIpDnsRotator.currentDns.value
                    Log.i(TAG, "10-Second Auto-Rotation: Server Clean IP=$activeIp, Refreshed DNS=$activeDns")
                }
            }
        }
    }

    private fun measureRealPing(host: String, port: Int): Long {
        if (host.isBlank() || port !in 1..65535) return -1L
        return try {
            val start = System.currentTimeMillis()
            val socket = Socket()
            protect(socket)
            socket.connect(InetSocketAddress(host, port), 2000)
            val latency = (System.currentTimeMillis() - start).coerceAtLeast(1L)
            socket.close()
            latency
        } catch (_: Exception) {
            -1L
        }
    }

    private fun formatTime(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) String.format("%02d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
    }

    private fun terminateTunnel(isUserInitiated: Boolean, errorReason: String? = null) {
        val runId = currentRunId.get()
        isConnecting.set(false)
        isTunnelEstablished.set(false)

        if (isUserInitiated) {
            VpnController.updateState(VpnConnectionState.DISCONNECTING)
        }

        unregisterNetworkCallback()
        workerJob?.cancel()
        metricsJob?.cancel()
        rotationWorkerJob?.cancel()
        networkLossGraceJob?.cancel()

        try { XrayCoreEngine.stop() } catch (_: Exception) {}
        try { tcpRouter?.closeAll() } catch (_: Exception) {}
        tcpRouter = null
        dnsHandler = null

        try { vpnInterface?.close() } catch (_: Exception) {}
        vpnInterface = null

        sessionStartTimeMs = 0L
        lastRxBytes = 0L
        lastTxBytes = 0L

        if (!isUserInitiated && errorReason != null) {
            latestSnapshot = latestSnapshot.copy(
                state = VpnConnectionState.ERROR,
                connectedDurationSeconds = 0L,
                downloadSpeedBytesPerSec = 0L,
                uploadSpeedBytesPerSec = 0L,
                errorMessage = errorReason
            )
            VpnController.markError(runId.takeIf { it > 0L }, errorReason)
        } else {
            latestSnapshot = VpnSessionStats(
                state = VpnConnectionState.DISCONNECTED,
                activeConfig = serviceActiveConfig
            )
            VpnController.markDisconnected(runId.takeIf { it > 0L })
        }
    }

    override fun onRevoke() {
        userInitiatedDisconnect.set(false)
        terminateTunnel(
            isUserInitiated = false,
            errorReason = "مجوز اتصال VPN توسط سیستم یا سرویس دیگر لغو شد"
        )
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        val wasActiveOrConnecting = isTunnelEstablished.get() || isConnecting.get()
        if (userInitiatedDisconnect.get()) {
            terminateTunnel(isUserInitiated = true)
        } else if (wasActiveOrConnecting) {
            terminateTunnel(
                isUserInitiated = false,
                errorReason = "سرویس تونل VPN به‌طور غیرمنتظره متوقف شد"
            )
        } else {
            unregisterNetworkCallback()
            workerJob?.cancel()
            metricsJob?.cancel()
            rotationWorkerJob?.cancel()
        }
        if (activeInstance === this) {
            activeInstance = null
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "TMS VPN Tunnel Active",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active VPN connection status and traffic bandwidth"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, content: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val disconnectIntent = Intent(this, VpnTunnelService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val disconnectPendingIntent = PendingIntent.getService(
            this,
            1,
            disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("TMS VPN: $title")
            .setContentText(content)
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "قطع اتصال / Disconnect",
                disconnectPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(title: String, content: String) {
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, buildNotification(title, content))
        } catch (e: SecurityException) {
            Log.d(TAG, "Notification permission not granted: ${e.message}")
        } catch (e: Exception) {
            Log.d(TAG, "Notification update skipped: ${e.message}")
        }
    }
}
