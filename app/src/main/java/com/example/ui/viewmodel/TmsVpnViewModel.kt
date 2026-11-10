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
import com.example.data.network.RealPingEngine
import com.example.data.parser.VpnConfigParser
import com.example.data.repository.VpnRepository
import com.example.data.security.AdminSecurityManager
import com.example.data.security.AuthAttemptResult
import com.example.data.security.SessionToken
import com.example.data.local.LocalVpnConfigEntity
import com.example.data.local.LocalVpnConfigRepository
import com.example.data.validation.InputValidator
import com.example.data.validation.ValidationResult
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import android.content.Context
import com.example.data.model.VpnConnectionState
import com.example.data.model.VpnSessionStats
import com.example.vpn.VpnController

private const val TAG = "TmsVpnViewModel"

sealed interface UiMessage {
    data class Success(val message: String) : UiMessage
    data class Error(val message: String) : UiMessage
}

class TmsVpnViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VpnRepository(application)
    private val localConfigRepository = LocalVpnConfigRepository(application)
    private val aiEngine = VpnAiEngine()
    private val securityManager = AdminSecurityManager(application)

    // --- REAL VPN ENGINE INTEGRATION ---
    val vpnConnectionState: StateFlow<VpnConnectionState> = VpnController.connectionState
    val vpnSessionStats: StateFlow<VpnSessionStats> = VpnController.sessionStats

    private val _selectedVpnConfig = MutableStateFlow<VpnConfig?>(null)
    val selectedVpnConfig: StateFlow<VpnConfig?> = _selectedVpnConfig.asStateFlow()

    private val _uiMessage = MutableStateFlow<UiMessage?>(null)
    val uiMessage: StateFlow<UiMessage?> = _uiMessage.asStateFlow()

    private val _isPinging = MutableStateFlow(false)
    val isPinging: StateFlow<Boolean> = _isPinging.asStateFlow()

    private val _aiLoading = MutableStateFlow(false)
    val aiLoading: StateFlow<Boolean> = _aiLoading.asStateFlow()

    private val _aiResponse = MutableStateFlow<String?>(null)
    val aiResponse: StateFlow<String?> = _aiResponse.asStateFlow()

    private val _isPersianLanguage = MutableStateFlow(true)
    val isPersianLanguage: StateFlow<Boolean> = _isPersianLanguage.asStateFlow()

    // --- RBAC & SECURITY STATE ---

    private val _activeSession = MutableStateFlow<SessionToken?>(null)
    val activeSession: StateFlow<SessionToken?> = _activeSession.asStateFlow()

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private val _lockoutSeconds = MutableStateFlow(0L)
    val lockoutSeconds: StateFlow<Long> = _lockoutSeconds.asStateFlow()

    // Explicit role override (allows switching between Super Admin, Server Manager, User Admin, and Auditor in the UI)
    private val _selectedRole = MutableStateFlow<AdminRole?>(null)

    // Room local configurations StateFlow for offline persistence and inspection
    val localConfigs: StateFlow<List<LocalVpnConfigEntity>> = localConfigRepository.allEntities
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    init {
        // Automatically synchronize remote Firestore configurations into Room local database
        viewModelScope.launch {
            try {
                repository.observeConfigs().catch { }.collect { remoteConfigs ->
                    if (remoteConfigs.isNotEmpty()) {
                        localConfigRepository.saveAllConfigs(remoteConfigs)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Sync to local Room database: ${e.message}")
            }
        }
    }

    // Unified configurations: remote Firestore with seamless local Room database fallback
    val configs: StateFlow<List<VpnConfig>> = combine(
        repository.observeConfigs().catch { error ->
            Log.d(TAG, "Firestore observeConfigs caught, using local fallback: ${error.message}")
            emit(emptyList())
        },
        localConfigRepository.allConfigs
    ) { remoteList, localList ->
        if (remoteList.isNotEmpty()) remoteList else localList
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    val servers: StateFlow<List<VpnServer>> = repository.observeServers()
        .catch { error ->
            Log.e(TAG, "Error observing servers", error)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val failoverPolicies: StateFlow<List<FailoverPolicy>> = repository.observeFailoverPolicies()
        .catch { error ->
            Log.e(TAG, "Error observing failover policies", error)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val userProfile: StateFlow<UserProfile?> = repository.observeUserProfile()
        .catch { error ->
            Log.e(TAG, "Error observing profile", error)
            emit(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null
        )

    val auditLogs: StateFlow<List<AuthAuditLog>> = repository.observeAuditLogs()
        .catch { error ->
            Log.e(TAG, "Error observing audit logs", error)
            emit(emptyList())
        }
        .stateIn(
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
                    Log.e(TAG, "Failed ensuring user profile", e)
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

    fun connectVpn(context: Context, configToUse: VpnConfig? = null) {
        val target = configToUse ?: _selectedVpnConfig.value ?: configs.value.firstOrNull()
        if (target == null) {
            _uiMessage.value = UiMessage.Error(
                if (isPersianLanguage.value) "هیچ کانفیگی برای برقراری تونل انتخاب نشده است / No config selected"
                else "No VPN configuration selected"
            )
            return
        }

        _selectedVpnConfig.value = target
        VpnController.startVpn(context, target)

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

    // --- CONFIG MANAGEMENT ---

    fun importBatchConfigs(rawBatchText: String) {
        if (!checkPermission(AdminPermission.MANAGE_CONFIGS, "واردسازی گروهی کانفیگ‌های VPN")) return

        val user = Firebase.auth.currentUser ?: return
        val parseResults = VpnConfigParser.parseMultiple(rawBatchText, user.uid)

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
                    repository.addConfig(cfg)
                    localConfigRepository.saveConfig(cfg)
                    addedCount++
                } catch (e: Exception) {
                    Log.e(TAG, "Error adding batch item to Firestore, saving locally", e)
                    try {
                        localConfigRepository.saveConfig(cfg)
                        addedCount++
                    } catch (_: Exception) {}
                }
            }

            val msgFa = "$addedCount کانفیگ با موفقیت ثبت شد ($duplicateCount تکراری، $invalidCount نامعتبر)"
            val msgEn = "Imported $addedCount configs ($duplicateCount duplicates, $invalidCount invalid)"
            _uiMessage.value = UiMessage.Success(if (isPersianLanguage.value) msgFa else msgEn)

            repository.addAuditLog(
                AuthAuditLog(
                    userId = user.uid,
                    action = "BATCH_CONFIG_IMPORT",
                    role = currentRole.value.id,
                    module = "ConfigurationManagement",
                    status = "SUCCESS",
                    ipOrDevice = getDeviceInfo(),
                    details = "واردسازی گروهی: $addedCount موفق، $duplicateCount تکراری، $invalidCount نامعتبر"
                )
            )
        }
    }

    fun importConfig(rawUri: String) {
        if (!checkPermission(AdminPermission.MANAGE_CONFIGS, "افزودن و واردسازی کانفیگ VPN")) return

        val user = Firebase.auth.currentUser ?: return
        val parseResult = VpnConfigParser.parse(rawUri, user.uid)
        if (!parseResult.isSuccess || parseResult.config == null) {
            val err = parseResult.errorMessage ?: "خطا در تجزیه ساختار کانفیگ"
            _uiMessage.value = UiMessage.Error(err)
            logValidationFailure("ConfigParsing", err)
            return
        }

        val newConfig = parseResult.config

        // Comprehensive RFC & Field Validation
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

        if (VpnConfigParser.isDuplicate(newConfig, configs.value)) {
            val err = "این کانفیگ تکراری است و قبلاً در پایگاه داده ثبت شده است / Duplicate configuration detected"
            _uiMessage.value = UiMessage.Error(err)
            logValidationFailure("ConfigDuplicate", err)
            return
        }

        viewModelScope.launch {
            try {
                repository.addConfig(newConfig)
            } catch (e: Exception) {
                Log.w(TAG, "Firestore sync failed, saving locally: ${e.message}")
            }
            try {
                localConfigRepository.saveConfig(newConfig)
                repository.addAuditLog(
                    AuthAuditLog(
                        userId = user.uid,
                        action = "CONFIG_IMPORT_SUCCESS",
                        role = currentRole.value.id,
                        module = "ConfigurationManagement",
                        status = "SUCCESS",
                        ipOrDevice = getDeviceInfo(),
                        details = "کانفیگ ${newConfig.protocol.uppercase()} برای سرور ${newConfig.server}:${newConfig.port} با موفقیت اعتبارسنجی و ذخیره شد"
                    )
                )
                _uiMessage.value = UiMessage.Success(
                    if (isPersianLanguage.value) "کانفیگ با موفقیت اعتبارسنجی و ذخیره امن شد" else "Config validated & securely saved"
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save config", e)
                _uiMessage.value = UiMessage.Error(e.localizedMessage ?: "خطا در ذخیره کانفیگ")
            }
        }
    }

    fun pingConfig(config: VpnConfig) {
        viewModelScope.launch {
            _isPinging.value = true
            val result = RealPingEngine.ping(config.server, config.port, 3000)
            val newStatus = if (result.isReachable) "online" else "offline"
            try {
                localConfigRepository.updatePingStatus(config.id, result.latencyMs, newStatus)
                repository.updateConfigPing(config.id, result.latencyMs, newStatus)
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
                repository.updateConfig(config.copy(isFavorite = !config.isFavorite))
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
