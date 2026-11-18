package com.example.ui.viewmodel

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiResult
import com.example.data.ai.VpnAiEngine
import com.example.data.model.AdminPermission
import com.example.data.model.AdminRole
import com.example.data.model.AuthAuditLog
import com.example.data.model.FailoverPolicy
import com.example.data.model.OverviewMetrics
import com.example.data.model.UserProfile
import com.example.data.model.VpnConfig
import com.example.data.model.VpnServer
import com.example.data.network.NodeControlApiClient
import com.example.data.network.RealPingEngine
import com.example.data.network.SubscriptionImporter
import com.example.data.network.UbuntuNodeHealthResult
import com.example.data.parser.VpnConfigParser
import com.example.data.repository.VpnRepository
import com.example.data.security.AdminSecurityManager
import com.example.data.security.AuthAttemptResult
import com.example.data.security.LocalUserAuthManager
import com.example.data.security.SessionToken
import com.example.data.local.LocalVpnConfigEntity
import com.example.data.local.LocalVpnConfigRepository
import com.example.data.validation.InputValidator
import com.example.data.validation.ValidationResult
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

import android.content.Context
import com.example.data.model.VpnConnectionState
import com.example.data.model.VpnSessionStats
import com.example.vpn.SplitTunnelManager
import com.example.vpn.VpnController
import com.example.vpn.engine.IspAutoTuner
import com.example.vpn.engine.IspTuningReport
import com.example.vpn.engine.LatencyFilterTier
import com.example.vpn.engine.LeakCheckReport
import com.example.vpn.engine.RacingSummary
import com.example.vpn.engine.SmartRacingSelector
import com.example.vpn.engine.TunnelHealthReport

private const val TAG = "TmsVpnViewModel"

sealed interface UiMessage {
    data class Success(val message: String) : UiMessage
    data class Error(val message: String) : UiMessage
}

@OptIn(ExperimentalCoroutinesApi::class)
class TmsVpnViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VpnRepository(application)
    private val localConfigRepository = LocalVpnConfigRepository(application)
    private val aiEngine = VpnAiEngine()
    private val securityManager = AdminSecurityManager(application)

    // --- REAL VPN ENGINE INTEGRATION ---
    val vpnConnectionState: StateFlow<VpnConnectionState> = VpnController.connectionState
    val vpnSessionStats: StateFlow<VpnSessionStats> = VpnController.sessionStats
    val healthReport: StateFlow<TunnelHealthReport?> = VpnController.healthReport
    val healthCheckInProgress: StateFlow<Boolean> = VpnController.healthCheckInProgress
    val leakCheckReport: StateFlow<LeakCheckReport?> = VpnController.leakCheckReport
    val leakCheckInProgress: StateFlow<Boolean> = VpnController.leakCheckInProgress
    val ispTuningReport: StateFlow<IspTuningReport?> = IspAutoTuner.latestReport

    private val _isTuningIsp = MutableStateFlow(false)
    val isTuningIsp: StateFlow<Boolean> = _isTuningIsp.asStateFlow()

    private val _isRacingConnect = MutableStateFlow(false)
    val isRacingConnect: StateFlow<Boolean> = _isRacingConnect.asStateFlow()

    private val _racingSummary = MutableStateFlow<RacingSummary?>(null)
    val racingSummary: StateFlow<RacingSummary?> = _racingSummary.asStateFlow()

    private val _selectedVpnConfig = MutableStateFlow<VpnConfig?>(VpnController.activeConfig)
    val selectedVpnConfig: StateFlow<VpnConfig?> = _selectedVpnConfig.asStateFlow()

    private val _uiMessage = MutableStateFlow<UiMessage?>(null)
    val uiMessage: StateFlow<UiMessage?> = _uiMessage.asStateFlow()

    private val _isPinging = MutableStateFlow(false)
    val isPinging: StateFlow<Boolean> = _isPinging.asStateFlow()

    // --- BACKGROUND COROUTINE SERVER LOCATION LATENCY & FILTER STATE ---
    private val _liveLatenciesMs = MutableStateFlow<Map<String, Int>>(emptyMap())
    val liveLatenciesMs: StateFlow<Map<String, Int>> = _liveLatenciesMs.asStateFlow()

    private val _isRefreshingLatencies = MutableStateFlow(false)
    val isRefreshingLatencies: StateFlow<Boolean> = _isRefreshingLatencies.asStateFlow()

    private val _latencyScanProgress = MutableStateFlow(0 to 0)
    val latencyScanProgress: StateFlow<Pair<Int, Int>> = _latencyScanProgress.asStateFlow()

    private val _selectedLatencyTier = MutableStateFlow(LatencyFilterTier.ALL)
    val selectedLatencyTier: StateFlow<LatencyFilterTier> = _selectedLatencyTier.asStateFlow()

    private val _sortLocationsByLatency = MutableStateFlow(true)
    val sortLocationsByLatency: StateFlow<Boolean> = _sortLocationsByLatency.asStateFlow()

    private var latencyScanJob: Job? = null

    private val _isSubscriptionImporting = MutableStateFlow(false)
    val isSubscriptionImporting: StateFlow<Boolean> = _isSubscriptionImporting.asStateFlow()

    private val _nodeControlLoading = MutableStateFlow(false)
    val nodeControlLoading: StateFlow<Boolean> = _nodeControlLoading.asStateFlow()

    private val _ubuntuNodeHealth = MutableStateFlow<UbuntuNodeHealthResult?>(null)
    val ubuntuNodeHealth: StateFlow<UbuntuNodeHealthResult?> = _ubuntuNodeHealth.asStateFlow()

    private val _aiLoading = MutableStateFlow(false)
    val aiLoading: StateFlow<Boolean> = _aiLoading.asStateFlow()

    private val _aiResponse = MutableStateFlow<String?>(null)
    val aiResponse: StateFlow<String?> = _aiResponse.asStateFlow()

    private val _isPersianLanguage = MutableStateFlow(true)
    val isPersianLanguage: StateFlow<Boolean> = _isPersianLanguage.asStateFlow()

    // --- RBAC & SECURITY STATE ---

    private val _activeSession = MutableStateFlow<SessionToken?>(null)
    val activeSession: StateFlow<SessionToken?> = _activeSession.asStateFlow()

    private val initialAdminSnapshot: Boolean = LocalUserAuthManager.isAdminEmail(
        Firebase.auth.currentUser?.email ?: LocalUserAuthManager.offlineUserFlow.value?.email
    )

    private val _isUnlocked = MutableStateFlow(initialAdminSnapshot)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private val _lockoutSeconds = MutableStateFlow(0L)
    val lockoutSeconds: StateFlow<Long> = _lockoutSeconds.asStateFlow()

    // Distinct User Space vs Admin Control Center state (opens automatically only for ADMIN_EMAIL)
    private val _isInAdminSpace = MutableStateFlow(initialAdminSnapshot)
    val isInAdminSpace: StateFlow<Boolean> = _isInAdminSpace.asStateFlow()

    // Explicit role override (allows switching between Super Admin, Server Manager, User Admin, and Auditor in the UI)
    private val _selectedRole = MutableStateFlow<AdminRole?>(
        if (initialAdminSnapshot) AdminRole.SUPER_ADMIN else null
    )

    // Room local configurations StateFlow for offline persistence and inspection
    val localConfigs: StateFlow<List<LocalVpnConfigEntity>> = localConfigRepository.allEntities
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val authStateFlow: Flow<FirebaseUser?> = callbackFlow {
        val auth = Firebase.auth
        val listener = FirebaseAuth.AuthStateListener { currentAuth ->
            trySend(currentAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        trySend(auth.currentUser)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    init {
        VpnController.syncWithService(application)
        // Prepopulate default high-speed tested configurations so app is never empty
        viewModelScope.launch {
            try {
                localConfigRepository.prepopulateDefaultsIfEmpty()
            } catch (e: Exception) {
                Log.w(TAG, "Failed seeding default configs: ${e.message}")
            }
        }

        // Automatically synchronize remote Firestore configurations into Room local database when user is authenticated
        viewModelScope.launch {
            authStateFlow.collect { user ->
                if (user != null) {
                    try {
                        repository.observeConfigs().catch { }.collect { remoteConfigs ->
                            if (remoteConfigs.isNotEmpty()) {
                                localConfigRepository.saveAllConfigs(remoteConfigs)
                            }
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Sync to local Room database: ${e.message}")
                    }
                }
            }
        }

        // Automatically open full Admin Control Center exclusively when signed in as alafzadehali@gmail.com
        viewModelScope.launch {
            combine(
                authStateFlow,
                LocalUserAuthManager.offlineUserFlow
            ) { fbUser, offUser ->
                fbUser?.email ?: offUser?.email
            }.collect { activeEmail ->
                val isAdmin = LocalUserAuthManager.isAdminEmail(activeEmail)
                if (isAdmin) {
                    _isInAdminSpace.value = true
                    _isUnlocked.value = true
                    _selectedRole.value = AdminRole.SUPER_ADMIN
                    _lockoutSeconds.value = 0L
                } else {
                    _isInAdminSpace.value = false
                    _isUnlocked.value = false
                    _selectedRole.value = null
                }
            }
        }
    }

    fun isCurrentUserAdmin(): Boolean {
        val activeEmail = Firebase.auth.currentUser?.email ?: LocalUserAuthManager.offlineUserFlow.value?.email
        return LocalUserAuthManager.isAdminEmail(activeEmail)
    }

    fun enterAdminSpace(pin: String): Boolean {
        return if (isCurrentUserAdmin()) {
            _isInAdminSpace.value = true
            _isUnlocked.value = true
            _selectedRole.value = AdminRole.SUPER_ADMIN
            _lockoutSeconds.value = 0L
            true
        } else {
            _isInAdminSpace.value = false
            _isUnlocked.value = false
            false
        }
    }

    fun exitAdminSpace() {
        _isInAdminSpace.value = false
        _isUnlocked.value = false
        _selectedRole.value = null
    }

    // Unified configurations: remote Firestore with seamless local Room database fallback
    val configs: StateFlow<List<VpnConfig>> = authStateFlow.flatMapLatest { user ->
        if (user != null) {
            combine(
                repository.observeConfigs().catch { error ->
                    Log.d(TAG, "Firestore observeConfigs caught, using local fallback: ${error.message}")
                    emit(emptyList())
                },
                localConfigRepository.allConfigs
            ) { remoteList, localList ->
                val merged = if (remoteList.isNotEmpty()) remoteList else localList
                if (_selectedVpnConfig.value == null && merged.isNotEmpty()) {
                    _selectedVpnConfig.value = merged.firstOrNull()
                }
                merged
            }
        } else {
            localConfigRepository.allConfigs.map { localList ->
                if (_selectedVpnConfig.value == null && localList.isNotEmpty()) {
                    _selectedVpnConfig.value = localList.firstOrNull()
                }
                localList
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    val servers: StateFlow<List<VpnServer>> = authStateFlow.flatMapLatest { user ->
        if (user != null) {
            repository.observeServers().catch { error ->
                Log.d(TAG, "Firestore observeServers: ${error.message}")
                emit(emptyList())
            }
        } else {
            flowOf(emptyList())
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    val failoverPolicies: StateFlow<List<FailoverPolicy>> = authStateFlow.flatMapLatest { user ->
        if (user != null) {
            repository.observeFailoverPolicies().catch { error ->
                Log.d(TAG, "Firestore observeFailoverPolicies: ${error.message}")
                emit(emptyList())
            }
        } else {
            flowOf(emptyList())
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    val userProfile: StateFlow<UserProfile?> = authStateFlow.flatMapLatest { user ->
        if (user != null) {
            repository.observeUserProfile().catch { error ->
                Log.d(TAG, "Firestore observeUserProfile: ${error.message}")
                emit(null)
            }
        } else {
            flowOf(null)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = null
    )

    val auditLogs: StateFlow<List<AuthAuditLog>> = authStateFlow.flatMapLatest { user ->
        if (user != null) {
            repository.observeAuditLogs().catch { error ->
                Log.d(TAG, "Firestore observeAuditLogs: ${error.message}")
                emit(emptyList())
            }
        } else {
            flowOf(emptyList())
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    val currentRole: StateFlow<AdminRole> = combine(_selectedRole, userProfile) { selected, profile ->
        selected ?: AdminRole.fromString(profile?.role ?: "SUPER_ADMIN")
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = AdminRole.SUPER_ADMIN
    )

    val overviewMetrics: StateFlow<OverviewMetrics> = combine(configs, servers, userProfile) { cfgList, srvList, profile ->
        val totalCfg = cfgList.size
        val totalSrv = srvList.size
        val activeSrv = srvList.count { it.status == "online" }
        val onlineUsers = srvList.sumOf { it.activeUsers }
        val onlineServersWithLatency = srvList.filter { it.status == "online" && it.latencyMs > 0 }
        val avgLatency = if (onlineServersWithLatency.isNotEmpty()) {
            onlineServersWithLatency.map { it.latencyMs }.average().toInt()
        } else {
            0
        }
        val totalBandwidth = srvList.sumOf { it.bandwidthUsageGb }
        val healthScore = if (totalSrv > 0) {
            ((activeSrv.toDouble() / totalSrv) * 100).toInt()
        } else {
            100
        }

        OverviewMetrics(
            totalConfigs = totalCfg,
            activeServers = activeSrv,
            totalServers = totalSrv,
            onlineUsers = onlineUsers,
            averageLatencyMs = avgLatency,
            totalBandwidthGb = totalBandwidth,
            systemHealthScore = healthScore,
            isRealBackendConnected = true
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = OverviewMetrics()
    )

    init {
        viewModelScope.launch {
            val user = Firebase.auth.currentUser
            if (user != null) {
                try {
                    repository.ensureUserProfile(
                        displayName = user.displayName ?: "Admin Operator",
                        email = user.email ?: "operator@tmsvpn.local"
                    )
                } catch (e: Exception) {
                    Log.d(TAG, "Failed ensuring user profile: ${e.message}")
                }
            }

            // Check initial lockout state
            val (locked, remaining) = securityManager.isLockedOut()
            if (locked) {
                _lockoutSeconds.value = remaining
                startLockoutCountdown()
            }
        }
    }

    private fun startLockoutCountdown() {
        viewModelScope.launch {
            while (_lockoutSeconds.value > 0) {
                delay(1000L)
                _lockoutSeconds.value = (_lockoutSeconds.value - 1).coerceAtLeast(0L)
            }
        }
    }

    fun toggleLanguage() {
        _isPersianLanguage.value = !_isPersianLanguage.value
    }

    fun clearUiMessage() {
        _uiMessage.value = null
    }

    private fun getDeviceInfo(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL} (Android API ${Build.VERSION.SDK_INT})"
    }

    private fun checkPermission(permission: AdminPermission, actionDescription: String): Boolean {
        val role = currentRole.value
        if (!role.hasPermission(permission)) {
            val deniedMsgFa = "خطای سطح دسترسی: نقش فعلی (${role.titleFa}) اجازه $actionDescription را ندارد."
            val deniedMsgEn = "Permission denied: Current role (${role.titleEn}) lacks permission: ${permission.titleEn}"
            _uiMessage.value = UiMessage.Error(if (isPersianLanguage.value) deniedMsgFa else deniedMsgEn)

            viewModelScope.launch {
                try {
                    val user = Firebase.auth.currentUser
                    if (user != null) {
                        repository.addAuditLog(
                            AuthAuditLog(
                                userId = user.uid,
                                action = "RBAC_PERMISSION_DENIED",
                                role = role.id,
                                module = permission.name,
                                status = "FAILED",
                                ipOrDevice = getDeviceInfo(),
                                details = "تلاش غیرمجاز برای $actionDescription توسط نقش ${role.id} مسدود شد"
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to record audit log", e)
                }
            }
            return false
        }
        return true
    }

    // --- ADMINISTRATOR AUTHENTICATION & SECURITY ---

    fun verifyAdminPin(pin: String): Boolean {
        val user = Firebase.auth.currentUser ?: return false
        val role = currentRole.value
        val (result, auditLog) = securityManager.verifyAdminCredential(
            userId = user.uid,
            role = role,
            pinOrPassword = pin,
            deviceInfo = getDeviceInfo()
        )

        viewModelScope.launch {
            try {
                repository.addAuditLog(auditLog)
            } catch (e: Exception) {
                Log.e(TAG, "Failed logging audit attempt", e)
            }
        }

        return when (result) {
            is AuthAttemptResult.Success -> {
                _activeSession.value = result.sessionToken
                _isUnlocked.value = true
                _lockoutSeconds.value = 0L
                _uiMessage.value = UiMessage.Success(
                    if (isPersianLanguage.value) "هویت مدیر با موفقیت تایید شد / نشست امن صادر گردید" else "Admin PIN verified. Secure session established."
                )
                true
            }
            is AuthAttemptResult.InvalidCredentials -> {
                _isUnlocked.value = false
                _uiMessage.value = UiMessage.Error(
                    if (isPersianLanguage.value) result.messageFa else result.messageEn
                )
                false
            }
            is AuthAttemptResult.LockedOut -> {
                _isUnlocked.value = false
                _lockoutSeconds.value = result.remainingLockoutSeconds
                startLockoutCountdown()
                _uiMessage.value = UiMessage.Error(
                    if (isPersianLanguage.value) result.messageFa else result.messageEn
                )
                false
            }
        }
    }

    fun lockAdminSession() {
        securityManager.revokeCurrentSession()
        _activeSession.value = null
        _isUnlocked.value = false
        _uiMessage.value = UiMessage.Success(
            if (isPersianLanguage.value) "پنل امنیتی قفل شد / Session locked" else "Admin session locked"
        )
    }

    fun changeAdminPin(oldPin: String, newPin: String) {
        val user = Firebase.auth.currentUser ?: return
        val role = currentRole.value

        val validation = InputValidator.validateAdminPin(newPin)
        if (validation is ValidationResult.Invalid) {
            _uiMessage.value = UiMessage.Error(
                if (isPersianLanguage.value) validation.firstMessageFa() else validation.firstMessageEn()
            )
            return
        }

        val (success, message) = securityManager.changeAdminPin(user.uid, role, oldPin, newPin)
        if (success) {
            _uiMessage.value = UiMessage.Success(message)
            viewModelScope.launch {
                try {
                    repository.addAuditLog(
                        AuthAuditLog(
                            userId = user.uid,
                            action = "ADMIN_PIN_CHANGED",
                            role = role.id,
                            module = "SecuritySettings",
                            status = "SUCCESS",
                            ipOrDevice = getDeviceInfo(),
                            details = "پین امنیتی تغییر یافت / Master security PIN successfully rotated"
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed audit log for PIN change", e)
                }
            }
        } else {
            _uiMessage.value = UiMessage.Error(message)
        }
    }

    fun emergencyResetPin(recoveryToken: String, newPin: String) {
        val user = Firebase.auth.currentUser ?: return
        val role = currentRole.value

        val validation = InputValidator.validateAdminPin(newPin)
        if (validation is ValidationResult.Invalid) {
            _uiMessage.value = UiMessage.Error(
                if (isPersianLanguage.value) validation.firstMessageFa() else validation.firstMessageEn()
            )
            return
        }

        val (success, message) = securityManager.emergencyResetPinWithRecoveryToken(recoveryToken, newPin)
        if (success) {
            _lockoutSeconds.value = 0L
            _isUnlocked.value = true
            _uiMessage.value = UiMessage.Success(message)
            viewModelScope.launch {
                try {
                    repository.addAuditLog(
                        AuthAuditLog(
                            userId = user.uid,
                            action = "EMERGENCY_PIN_RECOVERY_SUCCESS",
                            role = role.id,
                            module = "SecuritySettings",
                            status = "SUCCESS",
                            ipOrDevice = getDeviceInfo(),
                            details = "بازیابی اضطراری پین با توکن ریکاوری / Master PIN reset via emergency token"
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed emergency audit log", e)
                }
            }
        } else {
            _uiMessage.value = UiMessage.Error(message)
            viewModelScope.launch {
                try {
                    repository.addAuditLog(
                        AuthAuditLog(
                            userId = user.uid,
                            action = "EMERGENCY_PIN_RECOVERY_FAILED",
                            role = role.id,
                            module = "SecuritySettings",
                            status = "FAILED",
                            ipOrDevice = getDeviceInfo(),
                            details = "تلاش ناموفق برای بازیابی اضطراری با توکن نامعتبر"
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed recovery failure audit log", e)
                }
            }
        }
    }

    fun getRecoveryToken(): String {
        return securityManager.getRecoveryToken()
    }

    fun switchRole(role: AdminRole) {
        val user = Firebase.auth.currentUser ?: return
        _selectedRole.value = role

        viewModelScope.launch {
            try {
                repository.updateUserRole(role.id)
                repository.addAuditLog(
                    AuthAuditLog(
                        userId = user.uid,
                        action = "ROLE_SWITCHED",
                        role = role.id,
                        module = "RBAC",
                        status = "SUCCESS",
                        ipOrDevice = getDeviceInfo(),
                        details = "تغییر نقش فعال مدیر به ${role.titleEn} (${role.id})"
                    )
                )
                _uiMessage.value = UiMessage.Success(
                    if (isPersianLanguage.value) "نقش مدیریتی به «${role.titleFa}» تغییر یافت" else "Active role switched to ${role.titleEn}"
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed updating role in Firestore", e)
            }
        }
    }

    // --- REAL VPN CONNECTION CONTROL ---

    fun selectConfigForVpn(config: VpnConfig) {
        _selectedVpnConfig.value = config
    }

    fun connectVpn(
        context: Context,
        configToUse: VpnConfig? = null,
        antiFilterMode: Boolean = true
    ) {
        val target: VpnConfig? = configToUse
            ?: _selectedVpnConfig.value
            ?: VpnController.activeConfig
            ?: configs.value.firstOrNull()
            ?: localConfigs.value.firstOrNull()?.toDomainModel()
            ?: com.example.data.model.DefaultVpnConfigs.getDefaultConfigs().firstOrNull()
        if (target == null) {
            val errMsg = if (isPersianLanguage.value) {
                "هیچ کانفیگی برای برقراری تونل انتخاب نشده است"
            } else {
                "No VPN configuration selected"
            }
            VpnController.updateState(VpnConnectionState.ERROR, errMsg)
            _uiMessage.value = UiMessage.Error(errMsg)
            return
        }

        _selectedVpnConfig.value = target
        VpnController.startVpn(context, target, antiFilterMode)

        viewModelScope.launch {
            try {
                val user = Firebase.auth.currentUser
                if (user != null) {
                    repository.addAuditLog(
                        AuthAuditLog(
                            userId = user.uid,
                            action = "VPN_TUNNEL_CONNECTED",
                            role = currentRole.value.id,
                            module = "VpnService",
                            status = "SUCCESS",
                            ipOrDevice = getDeviceInfo(),
                            details = "اتصال تونل VPN به سرور ${target.name} (${target.server}:${target.port})"
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed audit log on vpn connect", e)
            }
        }
    }

    fun disconnectVpn(context: Context) {
        VpnController.stopVpn(context)

        viewModelScope.launch {
            try {
                val user = Firebase.auth.currentUser
                if (user != null) {
                    repository.addAuditLog(
                        AuthAuditLog(
                            userId = user.uid,
                            action = "VPN_TUNNEL_DISCONNECTED",
                            role = currentRole.value.id,
                            module = "VpnService",
                            status = "SUCCESS",
                            ipOrDevice = getDeviceInfo(),
                            details = "قطع ایمن تونل VPN توسط کاربر"
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed audit log on vpn disconnect", e)
            }
        }
    }

    fun autoSelectBestConfig(): VpnConfig? {
        val available = configs.value
        if (available.isEmpty()) return null

        val onlineWithLatency = available.filter { it.status == "online" && it.pingMs > 0 }
            .sortedBy { it.pingMs }
        val best = onlineWithLatency.firstOrNull() ?: available.firstOrNull()

        if (best != null) {
            _selectedVpnConfig.value = best
            _uiMessage.value = UiMessage.Success(
                if (isPersianLanguage.value) "بهترین نود انتخابی بر اساس کمترین تاخیر: ${best.name} (${if (best.pingMs > 0) "${best.pingMs}ms" else "تست نشده"})"
                else "Best node selected: ${best.name}"
            )
        }
        return best
    }

    fun runTunnelHealthCheck() {
        VpnController.runTunnelHealthCheck { report ->
            if (!report.isHealthy && SplitTunnelManager.isAutoReconnectEnabled(getApplication())) {
                _uiMessage.value = UiMessage.Error(
                    if (isPersianLanguage.value) "تونل فعلی پاسخ نمی‌دهد؛ می‌توانید به نود بعدی سوییچ کنید"
                    else "Current tunnel probe failed; ready to fail over to next node"
                )
            }
        }
    }

    fun failoverToNextHealthyConfig(context: Context) {
        val list = configs.value.ifEmpty {
            localConfigs.value.map { it.toDomainModel() }
        }
        val currentId = _selectedVpnConfig.value?.id ?: VpnController.activeConfig?.id
        val candidate = list
            .filter { it.id != currentId }
            .sortedWith(
                compareByDescending<VpnConfig> { it.status == "online" }
                    .thenBy { if (it.pingMs > 0) it.pingMs else Int.MAX_VALUE }
            )
            .firstOrNull()

        if (candidate == null) {
            _uiMessage.value = UiMessage.Error(
                if (isPersianLanguage.value) "کانفیگ جایگزین دیگری در لیست وجود ندارد"
                else "No backup VPN configuration available for failover"
            )
            return
        }

        _selectedVpnConfig.value = candidate
        VpnController.startVpn(context, candidate, antiFilterMode = true)
        _uiMessage.value = UiMessage.Success(
            if (isPersianLanguage.value) "سوییچ خودکار به نود پشتیبان: ${candidate.name}"
            else "Switched to backup node: ${candidate.name}"
        )
    }

    fun racingConnect(context: Context, connectOnWin: Boolean = true) {
        if (_isRacingConnect.value) return
        val candidates = configs.value.ifEmpty {
            localConfigs.value.map { it.toDomainModel() }
        }.ifEmpty {
            com.example.data.model.DefaultVpnConfigs.getDefaultConfigs()
        }
        if (candidates.isEmpty()) {
            _uiMessage.value = UiMessage.Error(
                if (isPersianLanguage.value) "هیچ سروری برای تست مسابقه‌ای وجود ندارد"
                else "No servers available for racing connect"
            )
            return
        }

        viewModelScope.launch {
            _isRacingConnect.value = true
            try {
                val summary = SmartRacingSelector.raceConfigs(context, candidates)
                _racingSummary.value = summary

                val latencyUpdates = mutableMapOf<String, Int>()
                summary.results.forEach { res ->
                    latencyUpdates[res.config.id] = res.delayMs
                    val status = if (res.delayMs > 0) "online" else "offline"
                    try {
                        localConfigRepository.updatePingStatus(res.config.id, res.delayMs, status)
                    } catch (_: Exception) {}
                    if (Firebase.auth.currentUser != null) {
                        try {
                            repository.updateConfigPing(res.config.id, res.delayMs, status)
                        } catch (_: Exception) {}
                    }
                }
                _liveLatenciesMs.update { it + latencyUpdates }

                val winner = summary.winner
                if (winner != null) {
                    val updatedWinner = winner.copy(pingMs = summary.winnerDelayMs, status = "online")
                    _selectedVpnConfig.value = updatedWinner
                    if (connectOnWin) {
                        VpnController.startVpn(context, updatedWinner, antiFilterMode = true)
                        _uiMessage.value = UiMessage.Success(
                            if (isPersianLanguage.value) {
                                "اتصال مسابقه‌ای به سریع‌ترین نود: ${updatedWinner.name} (${summary.winnerDelayMs}ms)"
                            } else {
                                "Racing Connect: ${updatedWinner.name} (${summary.winnerDelayMs}ms)"
                            }
                        )
                    } else {
                        _uiMessage.value = UiMessage.Success(
                            if (isPersianLanguage.value) {
                                "سریع‌ترین نود کشف شد: ${updatedWinner.name} (${summary.winnerDelayMs}ms)"
                            } else {
                                "Fastest node: ${updatedWinner.name} (${summary.winnerDelayMs}ms)"
                            }
                        )
                    }
                } else {
                    _uiMessage.value = UiMessage.Error(
                        if (isPersianLanguage.value) {
                            "از ${summary.testedCount} نود تست‌شده، هیچ سروری پاسخ نداد؛ اینترنت یا فیلترشکن پایه را بررسی کنید"
                        } else {
                            "No reachable nodes found across ${summary.testedCount} tested servers"
                        }
                    )
                }
            } finally {
                _isRacingConnect.value = false
            }
        }
    }

    fun runIspAutoTune(context: Context) {
        if (_isTuningIsp.value) return
        viewModelScope.launch {
            _isTuningIsp.value = true
            try {
                val report = IspAutoTuner.benchmarkAndTuneForCurrentIsp(context)
                if (report.bestCleanIp.isNotBlank() && report.bestCleanIpLatencyMs > 0) {
                    _uiMessage.value = UiMessage.Success(
                        if (isPersianLanguage.value) {
                            "بهینه‌سازی ${report.transportLabel}: Clean IP ${report.bestCleanIp} (${report.bestCleanIpLatencyMs}ms) • فرگمنت ${report.fragmentProfile.length}"
                        } else {
                            "ISP Tuned (${report.networkType.labelEn}): ${report.bestCleanIp} (${report.bestCleanIpLatencyMs}ms)"
                        }
                    )
                } else {
                    _uiMessage.value = UiMessage.Error(
                        if (isPersianLanguage.value) {
                            "پروفایل فرگمنت برای ${report.transportLabel} تنظیم شد، اما Clean IP در دسترس یافت نشد"
                        } else {
                            "Fragment profile updated for ${report.networkType.labelEn}, no Clean IP reachable"
                        }
                    )
                }
            } finally {
                _isTuningIsp.value = false
            }
        }
    }

    fun runLeakGuardCheck() {
        VpnController.runLeakGuardCheck { report ->
            if (!report.errorMessage.isNullOrBlank()) {
                _uiMessage.value = UiMessage.Error(report.errorMessage)
            } else {
                _uiMessage.value = UiMessage.Success(
                    if (isPersianLanguage.value) {
                        "IP خروجی: ${report.exitIp} (${report.countryCode.ifBlank { "--" }}) • ${report.dnsStatusLabel}"
                    } else {
                        "Exit IP: ${report.exitIp} (${report.countryCode.ifBlank { "--" }}) • ${report.dnsStatusLabel}"
                    }
                )
            }
        }
    }

    fun importSubscriptionUrl(url: String) {
        if (_isSubscriptionImporting.value) return
        val uid = Firebase.auth.currentUser?.uid ?: LocalUserAuthManager.offlineUserFlow.value?.uid ?: "local_user"
        viewModelScope.launch {
            _isSubscriptionImporting.value = true
            try {
                val res = SubscriptionImporter.fetchSubscription(url, uid)
                if (!res.isSuccess) {
                    _uiMessage.value = UiMessage.Error(res.errorMessage ?: "خطا در دریافت سابسکریپشن")
                    return@launch
                }

                var added = 0
                var duplicates = 0
                for (cfg in res.configs) {
                    val validation = InputValidator.validateVpnConfig(
                        protocol = cfg.protocol,
                        server = cfg.server,
                        port = cfg.port,
                        uuidOrPassword = cfg.uuidOrPassword,
                        security = cfg.security,
                        transport = cfg.transport,
                        rawConfig = cfg.rawConfig
                    )
                    if (validation is ValidationResult.Invalid) continue
                    if (VpnConfigParser.isDuplicate(cfg, configs.value)) {
                        duplicates++
                        continue
                    }
                    localConfigRepository.saveConfig(cfg)
                    if (Firebase.auth.currentUser != null) {
                        try {
                            repository.addConfig(cfg)
                        } catch (_: Exception) {}
                    }
                    if (_selectedVpnConfig.value == null) {
                        _selectedVpnConfig.value = cfg
                    }
                    added++
                }

                _uiMessage.value = UiMessage.Success(
                    if (isPersianLanguage.value) {
                        if (added > 0) "$added کانفیگ از لینک سابسکریپشن اضافه شد"
                        else "همه کانفیگ‌های سابسکریپشن از قبل موجود بودند ($duplicates مورد)"
                    } else {
                        "Imported $added configs from subscription ($duplicates duplicates)"
                    }
                )
            } finally {
                _isSubscriptionImporting.value = false
            }
        }
    }

    fun checkUbuntuNodeHealth(baseUrl: String, apiToken: String) {
        if (_nodeControlLoading.value) return
        viewModelScope.launch {
            _nodeControlLoading.value = true
            try {
                val result = NodeControlApiClient.checkNodeHealth(baseUrl, apiToken)
                _ubuntuNodeHealth.value = result
                _uiMessage.value = if (result.isReachable) {
                    UiMessage.Success(
                        if (isPersianLanguage.value) {
                            "نود Ubuntu آنلاین است • Xray: ${if (result.xrayRunning) "فعال" else "متوقف"} • کاربران: ${result.activeUsers}"
                        } else {
                            "Ubuntu node online • Xray=${result.xrayRunning} • Users=${result.activeUsers}"
                        }
                    )
                } else {
                    UiMessage.Error(result.errorMessage ?: "خطا در اتصال به نود Ubuntu")
                }
            } finally {
                _nodeControlLoading.value = false
            }
        }
    }

    fun provisionFromUbuntuNode(baseUrl: String, apiToken: String, quotaGb: Int = 100) {
        if (!checkPermission(AdminPermission.MANAGE_SERVERS, "تولید کانفیگ اختصاصی از نود Ubuntu")) return
        if (_nodeControlLoading.value) return
        val user = Firebase.auth.currentUser
        val uid = user?.uid ?: "local_user"
        val email = user?.email ?: "$uid@tmsvpn.local"

        viewModelScope.launch {
            _nodeControlLoading.value = true
            try {
                val provisioned = NodeControlApiClient.provisionPrivateVlessUser(
                    baseUrl = baseUrl,
                    apiToken = apiToken,
                    userId = uid,
                    userEmail = email,
                    quotaGb = quotaGb
                )
                if (provisioned.isSuccess && provisioned.vlessUri.isNotBlank()) {
                    importConfig(provisioned.vlessUri)
                } else {
                    _uiMessage.value = UiMessage.Error(
                        provisioned.errorMessage ?: "خطا در ساخت اکانت اختصاصی Xray"
                    )
                }
            } finally {
                _nodeControlLoading.value = false
            }
        }
    }

    // --- CONFIG MANAGEMENT ---

    fun importBatchConfigs(rawBatchText: String) {
        val uid = Firebase.auth.currentUser?.uid ?: LocalUserAuthManager.offlineUserFlow.value?.uid ?: "local_user"
        val parseResults = VpnConfigParser.parseMultiple(rawBatchText, uid)

        var addedCount = 0
        var duplicateCount = 0
        var invalidCount = 0

        viewModelScope.launch {
            for (res in parseResults) {
                if (!res.isSuccess || res.config == null) {
                    invalidCount++
                    continue
                }
                val cfg = res.config
                val validation = InputValidator.validateVpnConfig(
                    protocol = cfg.protocol,
                    server = cfg.server,
                    port = cfg.port,
                    uuidOrPassword = cfg.uuidOrPassword,
                    security = cfg.security,
                    transport = cfg.transport,
                    rawConfig = cfg.rawConfig
                )
                if (validation is ValidationResult.Invalid) {
                    invalidCount++
                    continue
                }
                if (VpnConfigParser.isDuplicate(cfg, configs.value)) {
                    duplicateCount++
                    continue
                }
                try {
                    localConfigRepository.saveConfig(cfg)
                    if (Firebase.auth.currentUser != null) {
                        try {
                            repository.addConfig(cfg)
                        } catch (_: Exception) {}
                    }
                    addedCount++
                } catch (e: Exception) {
                    Log.e(TAG, "Error adding batch item", e)
                }
            }

            val msgFa = if (addedCount > 0) "$addedCount کانفیگ با موفقیت ثبت شد" else "هیچ کانفیگ جدیدی اضافه نشد ($duplicateCount تکراری، $invalidCount نامعتبر)"
            val msgEn = "Imported $addedCount configs ($duplicateCount duplicates, $invalidCount invalid)"
            _uiMessage.value = UiMessage.Success(if (isPersianLanguage.value) msgFa else msgEn)

            if (Firebase.auth.currentUser != null) {
                try {
                    repository.addAuditLog(
                        AuthAuditLog(
                            userId = uid,
                            action = "BATCH_CONFIG_IMPORT",
                            role = currentRole.value.id,
                            module = "ConfigurationManagement",
                            status = "SUCCESS",
                            ipOrDevice = getDeviceInfo(),
                            details = "واردسازی گروهی: $addedCount موفق، $duplicateCount تکراری، $invalidCount نامعتبر"
                        )
                    )
                } catch (_: Exception) {}
            }
        }
    }

    fun importConfig(rawUri: String) {
        val uid = Firebase.auth.currentUser?.uid ?: LocalUserAuthManager.offlineUserFlow.value?.uid ?: "local_user"
        val parseResult = VpnConfigParser.parse(rawUri, uid)
        if (!parseResult.isSuccess || parseResult.config == null) {
            val err = parseResult.errorMessage ?: "خطا در تجزیه ساختار کانفیگ"
            _uiMessage.value = UiMessage.Error(err)
            logValidationFailure("ConfigParsing", err)
            return
        }

        val newConfig = parseResult.config

        // Field Validation
        val validation = InputValidator.validateVpnConfig(
            protocol = newConfig.protocol,
            server = newConfig.server,
            port = newConfig.port,
            uuidOrPassword = newConfig.uuidOrPassword,
            security = newConfig.security,
            transport = newConfig.transport,
            rawConfig = newConfig.rawConfig
        )

        if (validation is ValidationResult.Invalid) {
            val err = if (isPersianLanguage.value) validation.firstMessageFa() else validation.firstMessageEn()
            _uiMessage.value = UiMessage.Error(err)
            logValidationFailure("ConfigValidation", err)
            return
        }

        viewModelScope.launch {
            try {
                localConfigRepository.saveConfig(newConfig)
                if (Firebase.auth.currentUser != null) {
                    try {
                        repository.addConfig(newConfig)
                    } catch (e: Exception) {
                        Log.w(TAG, "Firestore sync skipped/failed: ${e.message}")
                    }
                }

                _selectedVpnConfig.value = newConfig
                _uiMessage.value = UiMessage.Success(
                    if (isPersianLanguage.value) "کانفیگ ${newConfig.name} با موفقیت افزوده شد" else "Config added: ${newConfig.name}"
                )

                if (Firebase.auth.currentUser != null) {
                    try {
                        repository.addAuditLog(
                            AuthAuditLog(
                                userId = uid,
                                action = "CONFIG_IMPORT_SUCCESS",
                                role = currentRole.value.id,
                                module = "ConfigurationManagement",
                                status = "SUCCESS",
                                ipOrDevice = getDeviceInfo(),
                                details = "کانفیگ ${newConfig.protocol.uppercase()} برای سرور ${newConfig.server}:${newConfig.port} ذخیره شد"
                            )
                        )
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save config", e)
                _uiMessage.value = UiMessage.Error(e.localizedMessage ?: "خطا در ذخیره کانفیگ")
            }
        }
    }

    fun addManualConfig(
        name: String,
        protocol: String,
        server: String,
        port: Int,
        uuidOrPassword: String,
        security: String = "tls",
        transport: String = "tcp",
        sni: String = ""
    ) {
        val uid = Firebase.auth.currentUser?.uid ?: LocalUserAuthManager.offlineUserFlow.value?.uid ?: "local_user"
        val trimmedServer = server.trim()
        if (trimmedServer.isBlank()) {
            _uiMessage.value = UiMessage.Error("آدرس سرور نمی‌تواند خالی باشد / Server address is required")
            return
        }

        val rawUri = "$protocol://${uuidOrPassword}@$trimmedServer:$port?security=$security&sni=$sni&type=$transport#$name"
        val config = VpnConfig(
            id = java.util.UUID.randomUUID().toString(),
            userId = uid,
            name = name.ifBlank { "$protocol-$trimmedServer:$port" },
            protocol = protocol.lowercase(),
            server = trimmedServer,
            port = port,
            uuidOrPassword = uuidOrPassword,
            transport = transport,
            security = security,
            sni = sni.ifBlank { trimmedServer },
            rawConfig = rawUri,
            status = "untested",
            pingMs = -1
        )

        viewModelScope.launch {
            try {
                localConfigRepository.saveConfig(config)
                if (Firebase.auth.currentUser != null) {
                    try {
                        repository.addConfig(config)
                    } catch (_: Exception) {}
                }
                _selectedVpnConfig.value = config
                _uiMessage.value = UiMessage.Success(
                    if (isPersianLanguage.value) "کانفیگ دستی با موفقیت ذخیره شد" else "Manual configuration saved"
                )
            } catch (e: Exception) {
                _uiMessage.value = UiMessage.Error("خطا در ذخیره کانفیگ: ${e.localizedMessage}")
            }
        }
    }

    fun setLatencyFilterTier(tier: LatencyFilterTier) {
        _selectedLatencyTier.value = tier
    }

    fun toggleSortLocationsByLatency() {
        _sortLocationsByLatency.value = !_sortLocationsByLatency.value
    }

    /**
     * Fetches real-time latency for all available VPN server locations in parallel using
     * bounded background coroutines on [Dispatchers.IO] without interrupting active connections
     * or blocking the main thread.
     */
    fun refreshAllServerLatencies(
        context: Context,
        force: Boolean = true,
        maxParallel: Int = 5,
        customPingProbe: (suspend (VpnConfig) -> Int)? = null
    ) {
        if (!force && _isRefreshingLatencies.value) return
        if (force) {
            latencyScanJob?.cancel()
        }

        val candidates = configs.value.ifEmpty {
            localConfigs.value.map { it.toDomainModel() }
        }.ifEmpty {
            com.example.data.model.DefaultVpnConfigs.getDefaultConfigs()
        }

        if (candidates.isEmpty()) {
            _latencyScanProgress.value = 0 to 0
            return
        }

        latencyScanJob = viewModelScope.launch(Dispatchers.IO) {
            _isRefreshingLatencies.value = true
            _latencyScanProgress.value = 0 to candidates.size
            val completedCounter = java.util.concurrent.atomic.AtomicInteger(0)
            val semaphore = Semaphore(maxParallel.coerceIn(1, 10))
            val appContext = context.applicationContext

            try {
                coroutineScope {
                    candidates.map { cfg ->
                        async {
                            semaphore.withPermit {
                                val delayResult = SmartRacingSelector.measureConfigRealDelay(
                                    context = appContext,
                                    config = cfg,
                                    timeoutSec = 3,
                                    customPingProbe = customPingProbe
                                )
                                val measuredMs = delayResult.delayMs
                                val status = if (measuredMs > 0) "online" else "offline"

                                _liveLatenciesMs.update { current ->
                                    current + (cfg.id to measuredMs)
                                }
                                if (_selectedVpnConfig.value?.id == cfg.id) {
                                    _selectedVpnConfig.update { sel ->
                                        sel?.copy(pingMs = measuredMs, status = status)
                                    }
                                }

                                try {
                                    localConfigRepository.updatePingStatus(cfg.id, measuredMs, status)
                                    if (Firebase.auth.currentUser != null) {
                                        repository.updateConfigPing(cfg.id, measuredMs, status)
                                    }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                }

                                val done = completedCounter.incrementAndGet()
                                _latencyScanProgress.value = done to candidates.size
                            }
                        }
                    }.awaitAll()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Background latency scan failed: ${e.message}")
            } finally {
                _isRefreshingLatencies.value = false
            }
        }
    }

    fun pingConfig(config: VpnConfig) {
        viewModelScope.launch {
            _isPinging.value = true
            val result = RealPingEngine.ping(config.server, config.port, 3000)
            val newStatus = if (result.isReachable) "online" else "offline"
            _liveLatenciesMs.update { it + (config.id to result.latencyMs) }
            try {
                localConfigRepository.updatePingStatus(config.id, result.latencyMs, newStatus)
                if (Firebase.auth.currentUser != null) {
                    repository.updateConfigPing(config.id, result.latencyMs, newStatus)
                }
                if (result.isReachable) {
                    _uiMessage.value = UiMessage.Success("سرور آنلاین است - تاخیر واقعی: ${result.latencyMs}ms")
                } else {
                    _uiMessage.value = UiMessage.Error("سرور در دسترس نیست: ${result.errorMessage}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update ping in Firestore", e)
            } finally {
                _isPinging.value = false
            }
        }
    }

    fun deleteConfig(configId: String) {
        if (!checkPermission(AdminPermission.MANAGE_CONFIGS, "حذف کانفیگ VPN")) return

        val user = Firebase.auth.currentUser ?: return
        viewModelScope.launch {
            try {
                localConfigRepository.deleteById(configId)
                repository.deleteConfig(configId)
                repository.addAuditLog(
                    AuthAuditLog(
                        userId = user.uid,
                        action = "CONFIG_DELETED",
                        role = currentRole.value.id,
                        module = "ConfigurationManagement",
                        status = "SUCCESS",
                        ipOrDevice = getDeviceInfo(),
                        details = "کانفیگ با شناسه $configId حذف شد"
                    )
                )
                _uiMessage.value = UiMessage.Success("کانفیگ با موفقیت حذف شد / Config deleted")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiMessage.value = UiMessage.Error("خطا در حذف کانفیگ: ${e.localizedMessage}")
            }
        }
    }

    fun toggleFavorite(config: VpnConfig) {
        viewModelScope.launch {
            try {
                localConfigRepository.updateFavorite(config.id, !config.isFavorite)
                if (Firebase.auth.currentUser != null) {
                    repository.updateConfig(config.copy(isFavorite = !config.isFavorite))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed toggling favorite", e)
            }
        }
    }

    // --- SERVER MANAGEMENT ---

    fun addServer(
        name: String,
        country: String,
        countryCode: String,
        host: String,
        port: Int,
        protocol: String
    ) {
        if (!checkPermission(AdminPermission.MANAGE_SERVERS, "افزودن نود سرور جدید")) return

        val user = Firebase.auth.currentUser ?: return

        // Comprehensive Field Validation
        val validation = InputValidator.validateServer(
            name = name,
            country = country,
            countryCode = countryCode,
            host = host,
            port = port,
            protocol = protocol
        )

        if (validation is ValidationResult.Invalid) {
            val err = if (isPersianLanguage.value) validation.firstMessageFa() else validation.firstMessageEn()
            _uiMessage.value = UiMessage.Error(err)
            logValidationFailure("ServerValidation", err)
            return
        }

        viewModelScope.launch {
            try {
                val server = VpnServer(
                    userId = user.uid,
                    name = name.trim(),
                    country = country.trim(),
                    countryCode = countryCode.trim().uppercase(),
                    host = host.trim(),
                    port = port,
                    protocol = protocol.trim(),
                    status = "online",
                    latencyMs = -1,
                    uptimePercentage = 99.9,
                    bandwidthUsageGb = 0.0,
                    bandwidthLimitGb = 1000.0,
                    activeUsers = 0,
                    stabilityScore = 95
                )
                repository.addServer(server)
                repository.addAuditLog(
                    AuthAuditLog(
                        userId = user.uid,
                        action = "SERVER_ADDED",
                        role = currentRole.value.id,
                        module = "ServerManagement",
                        status = "SUCCESS",
                        ipOrDevice = getDeviceInfo(),
                        details = "نود سرور جدید ${server.name} (${server.countryCode}) در هاست ${server.host}:${server.port} ثبت شد"
                    )
                )
                _uiMessage.value = UiMessage.Success("سرور جدید با موفقیت اعتبارسنجی و افزوده شد / Node added")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiMessage.value = UiMessage.Error("خطا در افزودن سرور: ${e.localizedMessage}")
            }
        }
    }

    fun pingServer(server: VpnServer) {
        viewModelScope.launch {
            _isPinging.value = true
            val result = RealPingEngine.ping(server.host, server.port, 3000)
            val newStatus = if (result.isReachable) "online" else "offline"
            val score = if (result.isReachable) {
                when {
                    result.latencyMs < 100 -> 98
                    result.latencyMs < 200 -> 90
                    result.latencyMs < 350 -> 78
                    else -> 60
                }
            } else {
                10
            }
            try {
                repository.updateServerHealth(server.id, result.latencyMs, newStatus, score)
                if (result.isReachable) {
                    _uiMessage.value = UiMessage.Success("پینگ واقعی سرور: ${result.latencyMs}ms (امتیاز پایداری: $score%)")
                } else {
                    _uiMessage.value = UiMessage.Error("سرور در دسترس نیست: ${result.errorMessage}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed updating server health", e)
            } finally {
                _isPinging.value = false
            }
        }
    }

    fun deleteServer(serverId: String) {
        if (!checkPermission(AdminPermission.MANAGE_SERVERS, "حذف نود سرور")) return

        val user = Firebase.auth.currentUser ?: return
        viewModelScope.launch {
            try {
                repository.deleteServer(serverId)
                repository.addAuditLog(
                    AuthAuditLog(
                        userId = user.uid,
                        action = "SERVER_DELETED",
                        role = currentRole.value.id,
                        module = "ServerManagement",
                        status = "SUCCESS",
                        ipOrDevice = getDeviceInfo(),
                        details = "سرور با شناسه $serverId حذف شد"
                    )
                )
                _uiMessage.value = UiMessage.Success("سرور با موفقیت حذف شد / Server deleted")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiMessage.value = UiMessage.Error("خطا در حذف سرور: ${e.localizedMessage}")
            }
        }
    }

    // --- FAILOVER POLICIES ---

    fun addFailoverPolicy(
        name: String,
        primaryServerId: String,
        backupServerId: String,
        thresholdMs: Int
    ) {
        if (!checkPermission(AdminPermission.MANAGE_FAILOVER, "افزودن سیاست مسیریابی خودکار")) return

        val user = Firebase.auth.currentUser ?: return

        val validation = InputValidator.validateFailoverPolicy(
            name = name,
            primaryServerId = primaryServerId,
            backupServerId = backupServerId,
            thresholdMs = thresholdMs
        )

        if (validation is ValidationResult.Invalid) {
            val err = if (isPersianLanguage.value) validation.firstMessageFa() else validation.firstMessageEn()
            _uiMessage.value = UiMessage.Error(err)
            logValidationFailure("FailoverValidation", err)
            return
        }

        viewModelScope.launch {
            try {
                val policy = FailoverPolicy(
                    userId = user.uid,
                    name = name.trim(),
                    primaryServerId = primaryServerId,
                    backupServerId = backupServerId,
                    latencyThresholdMs = thresholdMs,
                    autoSwitchEnabled = true
                )
                repository.addFailoverPolicy(policy)
                repository.addAuditLog(
                    AuthAuditLog(
                        userId = user.uid,
                        action = "FAILOVER_POLICY_ADDED",
                        role = currentRole.value.id,
                        module = "IntelligentEngine",
                        status = "SUCCESS",
                        ipOrDevice = getDeviceInfo(),
                        details = "سیاست تغییر مسیر خودکار «${policy.name}» با آستانه ${thresholdMs}ms ثبت شد"
                    )
                )
                _uiMessage.value = UiMessage.Success("سیاست مسیریابی جایگزین (Failover) فعال شد")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiMessage.value = UiMessage.Error("خطا در ثبت سیاست: ${e.localizedMessage}")
            }
        }
    }

    fun deleteFailoverPolicy(policyId: String) {
        if (!checkPermission(AdminPermission.MANAGE_FAILOVER, "حذف سیاست مسیریابی")) return

        viewModelScope.launch {
            try {
                repository.deleteFailoverPolicy(policyId)
                _uiMessage.value = UiMessage.Success("سیاست حذف شد")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiMessage.value = UiMessage.Error("خطا در حذف سیاست")
            }
        }
    }

    // --- USER MANAGEMENT & QUOTAS ---

    fun updateUserQuota(deviceCount: Int, maxDevices: Int, dataLimitBytes: Double) {
        if (!checkPermission(AdminPermission.MANAGE_USERS, "تغییر سهمیه و مدیریت کاربران")) return

        val user = Firebase.auth.currentUser ?: return
        viewModelScope.launch {
            try {
                repository.updateUserQuota(deviceCount, maxDevices, dataLimitBytes)
                repository.addAuditLog(
                    AuthAuditLog(
                        userId = user.uid,
                        action = "USER_QUOTA_UPDATED",
                        role = currentRole.value.id,
                        module = "UserManagement",
                        status = "SUCCESS",
                        ipOrDevice = getDeviceInfo(),
                        details = "سهمیه دستگاه‌ها ($deviceCount/$maxDevices) و حجم داده ($dataLimitBytes bytes) به‌روز شد"
                    )
                )
                _uiMessage.value = UiMessage.Success(
                    if (isPersianLanguage.value) "سهمیه کاربر با موفقیت به‌روزرسانی شد" else "User quota updated successfully"
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiMessage.value = UiMessage.Error("خطا در به‌روزرسانی سهمیه: ${e.localizedMessage}")
            }
        }
    }

    // --- AI ASSISTANCE ---

    fun quickAudit(config: VpnConfig) {
        if (!checkPermission(AdminPermission.RUN_INTELLIGENCE, "اجرای ارزیابی هوش مصنوعی")) return

        viewModelScope.launch {
            _aiLoading.value = true
            _aiResponse.value = null
            when (val res = aiEngine.quickAuditConfig(config.protocol, config.server, config.port, config.security, config.transport)) {
                is AiResult.Success -> {
                    _aiResponse.value = res.responseText
                }
                is AiResult.Error -> {
                    _uiMessage.value = UiMessage.Error(res.message)
                }
            }
            _aiLoading.value = false
        }
    }

    fun optimizeTopology(userProblemPrompt: String) {
        if (!checkPermission(AdminPermission.RUN_INTELLIGENCE, "بهینه‌سازی هوشمند توپولوژی شبکه")) return

        viewModelScope.launch {
            _aiLoading.value = true
            _aiResponse.value = null
            val srvSummary = servers.value.joinToString("\n") { s ->
                "- ${s.name} (${s.countryCode}): ${s.host}:${s.port} [${s.protocol}] Status=${s.status}, Latency=${s.latencyMs}ms, Stability=${s.stabilityScore}%"
            }
            val activeLat = configs.value.take(5).joinToString("\n") { c ->
                "- Config ${c.name}: Ping=${c.pingMs}ms Status=${c.status}"
            }
            when (val res = aiEngine.optimizeRoutingAndFailover(srvSummary, activeLat, userProblemPrompt)) {
                is AiResult.Success -> {
                    _aiResponse.value = res.responseText
                }
                is AiResult.Error -> {
                    _uiMessage.value = UiMessage.Error(res.message)
                }
            }
            _aiLoading.value = false
        }
    }

    fun clearAiResponse() {
        _aiResponse.value = null
    }

    private fun logValidationFailure(module: String, errorDetails: String) {
        viewModelScope.launch {
            try {
                val user = Firebase.auth.currentUser ?: return@launch
                repository.addAuditLog(
                    AuthAuditLog(
                        userId = user.uid,
                        action = "INPUT_VALIDATION_FAILED",
                        role = currentRole.value.id,
                        module = module,
                        status = "FAILED",
                        ipOrDevice = getDeviceInfo(),
                        details = "اعتبارسنجی ورودی رد شد: $errorDetails"
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed logging validation failure", e)
            }
        }
    }
}
