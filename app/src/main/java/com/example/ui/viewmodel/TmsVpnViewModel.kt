package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiResult
import com.example.data.ai.NexusAiScope
import com.example.data.ai.NexusChatMessage
import com.example.data.ai.NexusContextSanitizer
import com.example.data.ai.NexusDiagnosticReport
import com.example.data.ai.NexusStreamEvent
import com.example.data.ai.VpnAiEngine
import com.example.data.model.AdminPermission
import com.example.data.model.AdminRole
import com.example.data.model.AllocatedEndpointRecord
import com.example.data.model.AllocationHistoryRecord
import com.example.data.model.AuthAuditLog
import com.example.data.model.AppReleaseInfo
import com.example.data.model.BackendErrorCategory
import com.example.data.model.BackendResult
import com.example.data.model.BackendSessionSync
import com.example.data.model.BackgroundBackendError
import com.example.data.model.ConfigOrigin
import com.example.data.model.CryptoAsset
import com.example.data.model.CryptoInvoiceStatus
import com.example.data.model.CryptoPaymentInvoice
import com.example.data.model.classifyBackgroundError
import com.example.data.model.FailoverPolicy
import com.example.data.model.FreeTrialStatus
import com.example.data.model.InventoryShortageAlert
import com.example.data.model.ManagedEndpoint
import com.example.data.model.OverviewMetrics
import com.example.data.model.PaymentRequestRecord
import com.example.data.model.ReferralDashboardData
import com.example.data.model.ReferralRewardRequest
import com.example.data.model.RotationAuditLog
import com.example.data.model.SubscriptionHistoryEvent
import com.example.data.model.SubscriptionPlanId
import com.example.data.model.SubscriptionStatus
import com.example.data.model.SupportTicket
import com.example.data.model.SupportTicketCategory
import com.example.data.model.SupportTicketPriority
import com.example.data.model.SupportTicketReply
import com.example.data.model.SupportTicketStatus
import com.example.data.model.UserProfile
import com.example.data.model.UserSubscription
import com.example.data.model.VpnConfig
import com.example.data.model.VpnServer
import com.example.data.network.ApkDownloadUrlValidator
import com.example.data.network.NodeControlApiClient
import com.example.data.network.RealPingEngine
import com.example.data.network.SubscriptionImporter
import com.example.data.network.TmsBackendApiClient
import com.example.data.network.UbuntuNodeHealthResult
import com.example.data.parser.VpnConfigParser
import com.example.data.repository.SubscriptionRepository
import com.example.data.repository.SupportTicketRepository
import com.example.data.repository.VpnRepository
import com.example.data.security.AdminSecurityManager
import com.example.data.security.AuthAttemptResult
import com.example.data.security.LocalUserAuthManager
import com.example.data.security.SessionToken
import com.example.data.subscription.BulkDeletionPlan
import com.example.data.subscription.ConfigDeletionContext
import com.example.data.subscription.ConfigDeletionPolicy
import com.example.data.subscription.DedicatedIpAllocationEngine
import com.example.data.subscription.ReferralAndCryptoCalculator
import com.example.data.subscription.SubscriptionLifecycleEngine
import com.example.data.local.CleanIpAddressType
import com.example.data.local.CleanIpEntity
import com.example.data.local.CleanIpHealthStatus
import com.example.data.local.LegacyAccountMigrationHelper
import com.example.data.local.LocalVpnConfigEntity
import com.example.data.local.LocalVpnConfigRepository
import com.example.data.local.RegisteredUserEntity
import com.example.data.model.PaymentStatus
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

import android.content.Context
import com.example.data.model.VpnConnectionState
import com.example.data.model.VpnSessionStats
import com.example.vpn.engine.AntiDpiComboEngine
import com.example.vpn.engine.ComboBypassSummary
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
    private val subscriptionRepository = SubscriptionRepository(application)
    private val supportTicketRepository = SupportTicketRepository(application)
    private val localConfigRepository = LocalVpnConfigRepository(application)
    private val aiEngine = VpnAiEngine()
    private val securityManager = AdminSecurityManager(application)
    private val userAuthManager = LocalUserAuthManager(application)
    private var backendClient = TmsBackendApiClient()

    @VisibleForTesting
    internal var testAuthenticatedUid: String? = null

    @VisibleForTesting
    internal fun setBackendClientForTesting(client: TmsBackendApiClient) {
        backendClient = client
    }

    private fun safeFirebaseAuth(): FirebaseAuth? =
        runCatching { Firebase.auth }.getOrNull()

    private fun safeFirebaseUser(): FirebaseUser? =
        runCatching { Firebase.auth.currentUser }.getOrNull()

    private fun hasAuthenticatedIdentity(): Boolean =
        safeFirebaseUser() != null || !testAuthenticatedUid.isNullOrBlank()

    private fun activeAuthenticatedUid(): String? =
        safeFirebaseUser()?.uid ?: testAuthenticatedUid

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

    // --- TMS NEXUS AI STATE (STRICTLY SCOPED: USER_ASSISTANT vs ADMIN_COPILOT) ---
    private val _userNexusMessages = MutableStateFlow<List<NexusChatMessage>>(emptyList())
    val userNexusMessages: StateFlow<List<NexusChatMessage>> = _userNexusMessages.asStateFlow()

    private val _userNexusStreaming = MutableStateFlow(false)
    val userNexusStreaming: StateFlow<Boolean> = _userNexusStreaming.asStateFlow()

    private val _userDiagnosticReport = MutableStateFlow<NexusDiagnosticReport?>(null)
    val userDiagnosticReport: StateFlow<NexusDiagnosticReport?> = _userDiagnosticReport.asStateFlow()

    private val _userDiagnosticLoading = MutableStateFlow(false)
    val userDiagnosticLoading: StateFlow<Boolean> = _userDiagnosticLoading.asStateFlow()

    private val _userDiagnosticError = MutableStateFlow<String?>(null)
    val userDiagnosticError: StateFlow<String?> = _userDiagnosticError.asStateFlow()

    private val _adminNexusMessages = MutableStateFlow<List<NexusChatMessage>>(emptyList())
    val adminNexusMessages: StateFlow<List<NexusChatMessage>> = _adminNexusMessages.asStateFlow()

    private val _adminNexusStreaming = MutableStateFlow(false)
    val adminNexusStreaming: StateFlow<Boolean> = _adminNexusStreaming.asStateFlow()

    private val _ticketBusy = MutableStateFlow(false)
    val ticketBusy: StateFlow<Boolean> = _ticketBusy.asStateFlow()

    // --- RBAC & SERVER-VERIFIED ADMIN SECURITY STATE ---

    private val _activeSession = MutableStateFlow<SessionToken?>(null)
    val activeSession: StateFlow<SessionToken?> = _activeSession.asStateFlow()

    private val _verifiedServerAdmin = MutableStateFlow(false)
    val isVerifiedAdmin: StateFlow<Boolean> = _verifiedServerAdmin.asStateFlow()

    private val _verifiedAdminRole = MutableStateFlow<AdminRole?>(null)

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private val _adminPinConfigured = MutableStateFlow(securityManager.hasConfiguredPin())
    val adminPinConfigured: StateFlow<Boolean> = _adminPinConfigured.asStateFlow()

    private val _showAdminPinPrompt = MutableStateFlow(false)
    val showAdminPinPrompt: StateFlow<Boolean> = _showAdminPinPrompt.asStateFlow()

    private val _lockoutSeconds = MutableStateFlow(0L)
    val lockoutSeconds: StateFlow<Long> = _lockoutSeconds.asStateFlow()

    // Distinct User Space vs Admin Control Center state (only enters after server-verified admin privilege + PIN unlock)
    private val _isInAdminSpace = MutableStateFlow(false)
    val isInAdminSpace: StateFlow<Boolean> = _isInAdminSpace.asStateFlow()

    private val _selectedRole = MutableStateFlow<AdminRole?>(null)

    // --- BACKEND ECOSYSTEM STATE (CRYPTO, REFERRALS, FREE TRIAL, RELEASES) ---

    private val _backendSessionSync = MutableStateFlow<BackendSessionSync?>(null)
    val backendSessionSync: StateFlow<BackendSessionSync?> = _backendSessionSync.asStateFlow()

    private val _cryptoInvoices = MutableStateFlow<List<CryptoPaymentInvoice>>(emptyList())
    val cryptoInvoices: StateFlow<List<CryptoPaymentInvoice>> = _cryptoInvoices.asStateFlow()

    private val _activeCryptoInvoice = MutableStateFlow<CryptoPaymentInvoice?>(null)
    val activeCryptoInvoice: StateFlow<CryptoPaymentInvoice?> = _activeCryptoInvoice.asStateFlow()

    private val _cryptoBusy = MutableStateFlow(false)
    val cryptoBusy: StateFlow<Boolean> = _cryptoBusy.asStateFlow()

    private val _referralDashboard = MutableStateFlow<ReferralDashboardData?>(null)
    val referralDashboard: StateFlow<ReferralDashboardData?> = _referralDashboard.asStateFlow()

    private val _referralBusy = MutableStateFlow(false)
    val referralBusy: StateFlow<Boolean> = _referralBusy.asStateFlow()

    private val _adminRewardRequests = MutableStateFlow<List<ReferralRewardRequest>>(emptyList())
    val adminRewardRequests: StateFlow<List<ReferralRewardRequest>> = _adminRewardRequests.asStateFlow()

    private val _freeTrialStatus = MutableStateFlow<FreeTrialStatus?>(null)
    val freeTrialStatus: StateFlow<FreeTrialStatus?> = _freeTrialStatus.asStateFlow()

    private val _trialBusy = MutableStateFlow(false)
    val trialBusy: StateFlow<Boolean> = _trialBusy.asStateFlow()

    private val _trialGrantedBanner = MutableStateFlow<FreeTrialStatus?>(null)
    val trialGrantedBanner: StateFlow<FreeTrialStatus?> = _trialGrantedBanner.asStateFlow()

    private val _latestRelease = MutableStateFlow<AppReleaseInfo?>(null)
    val latestRelease: StateFlow<AppReleaseInfo?> = _latestRelease.asStateFlow()

    private val _adminReleases = MutableStateFlow<List<AppReleaseInfo>>(emptyList())
    val adminReleases: StateFlow<List<AppReleaseInfo>> = _adminReleases.asStateFlow()

    private val _releaseBusy = MutableStateFlow(false)
    val releaseBusy: StateFlow<Boolean> = _releaseBusy.asStateFlow()

    private val _apkUploadProgress = MutableStateFlow<Float?>(null)
    val apkUploadProgress: StateFlow<Float?> = _apkUploadProgress.asStateFlow()

    private val _apkUploadError = MutableStateFlow<String?>(null)
    val apkUploadError: StateFlow<String?> = _apkUploadError.asStateFlow()

    private val _backgroundBackendError = MutableStateFlow<BackgroundBackendError?>(null)
    val backgroundBackendError: StateFlow<BackgroundBackendError?> = _backgroundBackendError.asStateFlow()

    private var _lastPendingReferralCode: String? = null

    private val _isEmailVerified = MutableStateFlow(false)
    val isEmailVerified: StateFlow<Boolean> = _isEmailVerified.asStateFlow()

    // --- BULK CONFIG MANAGEMENT & TOMBSTONES ---
    private val _selectedConfigIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedConfigIds: StateFlow<Set<String>> = _selectedConfigIds.asStateFlow()

    private val _tombstonedConfigIds = MutableStateFlow<Set<String>>(emptySet())

    // --- SERVER-TIME ANCHOR (PREVENT DEVICE CLOCK SPOOFING) ---
    private val _serverAnchorEpochMs = MutableStateFlow(System.currentTimeMillis())
    private val _serverAnchorElapsedMs = MutableStateFlow(SystemClock.elapsedRealtime())
    private val _trustedServerNowMs = MutableStateFlow(System.currentTimeMillis())
    val trustedServerNowMs: StateFlow<Long> = _trustedServerNowMs.asStateFlow()

    private val _isSubscriptionOfflineCached = MutableStateFlow(false)
    val isSubscriptionOfflineCached: StateFlow<Boolean> = _isSubscriptionOfflineCached.asStateFlow()

    private val _subscriptionBusy = MutableStateFlow(false)
    val subscriptionBusy: StateFlow<Boolean> = _subscriptionBusy.asStateFlow()

    private val _selectedRotationUserIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedRotationUserIds: StateFlow<Set<String>> = _selectedRotationUserIds.asStateFlow()

    // Room local configurations StateFlow for offline persistence and inspection
    val localConfigs: StateFlow<List<LocalVpnConfigEntity>> = localConfigRepository.allEntities
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    private val _isProbingCleanIps = MutableStateFlow(false)
    val isProbingCleanIps: StateFlow<Boolean> = _isProbingCleanIps.asStateFlow()

    private val _comboBusy = MutableStateFlow(false)
    val comboBusy: StateFlow<Boolean> = _comboBusy.asStateFlow()

    val comboShieldEnabled: StateFlow<Boolean> = VpnController.comboShieldEnabled
    val activeComboSummary: StateFlow<String> = VpnController.activeComboSummary

    val cleanIpPool: StateFlow<List<CleanIpEntity>> = (localConfigRepository.observeCleanIpPool() ?: flowOf(emptyList()))
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val authStateFlow: Flow<FirebaseUser?> = callbackFlow {
        val auth = safeFirebaseAuth()
        if (auth == null) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }
        val listener = FirebaseAuth.AuthStateListener { currentAuth ->
            trySend(runCatching { currentAuth.currentUser }.getOrNull())
        }
        runCatching { auth.addAuthStateListener(listener) }
        trySend(runCatching { auth.currentUser }.getOrNull())
        awaitClose { runCatching { auth.removeAuthStateListener(listener) } }
    }

    private data class SessionIdentity(
        val uid: String,
        val email: String,
        val displayName: String,
        val isFirebaseUser: Boolean,
        val isAdmin: Boolean
    )

    private val activeAuthIdentityFlow: Flow<SessionIdentity?> = combine(
        authStateFlow,
        _verifiedServerAdmin
    ) { fbUser, serverAdmin ->
        if (fbUser != null) {
            val em = fbUser.email.orEmpty()
            val name = fbUser.displayName?.takeIf { it.isNotBlank() }
                ?: em.substringBefore("@").ifBlank { "TMS User" }
            SessionIdentity(
                uid = fbUser.uid,
                email = em,
                displayName = name,
                isFirebaseUser = true,
                isAdmin = serverAdmin
            )
        } else {
            null
        }
    }.distinctUntilChanged()

    private fun currentActorUid(): String {
        return activeAuthenticatedUid() ?: "local_user"
    }

    private fun currentActorEmail(): String {
        return safeFirebaseUser()?.email
            ?: userProfile.value?.email
            ?: ""
    }

    fun currentTrustedNowMs(): Long {
        val elapsedDelta = (SystemClock.elapsedRealtime() - _serverAnchorElapsedMs.value).coerceAtLeast(0L)
        return _serverAnchorEpochMs.value + elapsedDelta
    }

    private fun updateTrustedServerAnchor(serverEpochMs: Long) {
        if (serverEpochMs > 0L) {
            _serverAnchorEpochMs.value = serverEpochMs
            _serverAnchorElapsedMs.value = SystemClock.elapsedRealtime()
            _trustedServerNowMs.value = serverEpochMs
        }
    }

    private suspend fun syncPendingConfigDeletes(uid: String) {
        try {
            val pending = localConfigRepository.getPendingRemoteDeletes(uid)
            if (pending.isNotEmpty()) {
                val res = repository.deleteConfigsBatch(pending)
                if (res.isSuccess) {
                    localConfigRepository.markRemoteDeletesSynced(pending)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "Deferred remote delete sync deferred: ${e.message}")
        }
    }

    fun isCurrentUserAdmin(): Boolean {
        return hasAuthenticatedIdentity() && _verifiedServerAdmin.value
    }

    fun isAdminSessionAuthorized(): Boolean {
        if (!isCurrentUserAdmin()) return false
        val pinRequired = securityManager.hasConfiguredPin()
        _adminPinConfigured.value = pinRequired
        if (!pinRequired) return true
        if (!_isUnlocked.value) return false
        val session = _activeSession.value ?: return false
        val (valid, _) = securityManager.validateSessionToken(session.rawToken)
        return valid
    }

    fun enterAdminSpace(pin: String = ""): Boolean {
        if (!isCurrentUserAdmin()) {
            _isInAdminSpace.value = false
            _isUnlocked.value = false
            _activeSession.value = null
            _showAdminPinPrompt.value = false
            return false
        }
        val role = _verifiedAdminRole.value ?: AdminRole.SUPER_ADMIN
        val pinConfigured = securityManager.hasConfiguredPin()
        _adminPinConfigured.value = pinConfigured
        if (!pinConfigured) {
            _isInAdminSpace.value = true
            _isUnlocked.value = true
            _showAdminPinPrompt.value = false
            _selectedRole.value = role
            _lockoutSeconds.value = 0L
            return true
        }
        if (pin.isBlank()) {
            // Never bypass configured Admin PIN on blank entry: require PIN challenge first.
            _isInAdminSpace.value = false
            _isUnlocked.value = false
            _activeSession.value = null
            _showAdminPinPrompt.value = true
            return false
        }
        val verified = verifyAdminPin(pin)
        if (verified) {
            _showAdminPinPrompt.value = false
            _isInAdminSpace.value = true
            _selectedRole.value = role
        } else {
            _isInAdminSpace.value = false
            _isUnlocked.value = false
        }
        return verified
    }

    fun dismissAdminPinPrompt() {
        _showAdminPinPrompt.value = false
    }

    fun exitAdminSpace() {
        securityManager.revokeCurrentSession()
        _activeSession.value = null
        _isInAdminSpace.value = false
        _isUnlocked.value = false
        _showAdminPinPrompt.value = false
        _selectedRole.value = null
    }

    // Unified configurations: remote Firestore merged with local Room database and strictly filtered by tombstones
    val configs: StateFlow<List<VpnConfig>> = activeAuthIdentityFlow.flatMapLatest { identity ->
        if (identity?.isFirebaseUser == true) {
            combine(
                repository.observeConfigs().catch { error ->
                    Log.d(TAG, "Firestore observeConfigs caught, using local fallback: ${error.message}")
                    emit(emptyList())
                },
                localConfigRepository.allConfigs,
                _tombstonedConfigIds
            ) { remoteList, localList, tombstones ->
                val byId = linkedMapOf<String, VpnConfig>()
                remoteList.filter { it.id !in tombstones }.forEach { byId[it.id] = it }
                localList.filter { it.id !in tombstones }.forEach { localCfg ->
                    byId.putIfAbsent(localCfg.id, localCfg)
                }
                val merged = byId.values.toList()
                if (_selectedVpnConfig.value == null && merged.isNotEmpty()) {
                    _selectedVpnConfig.value = merged.firstOrNull()
                }
                merged
            }
        } else {
            combine(localConfigRepository.allConfigs, _tombstonedConfigIds) { localList, tombstones ->
                val visible = localList.filter { it.id !in tombstones }
                if (_selectedVpnConfig.value == null && visible.isNotEmpty()) {
                    _selectedVpnConfig.value = visible.firstOrNull()
                }
                visible
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    val servers: StateFlow<List<VpnServer>> = activeAuthIdentityFlow.flatMapLatest { identity ->
        if (identity?.isFirebaseUser == true) {
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

    val failoverPolicies: StateFlow<List<FailoverPolicy>> = activeAuthIdentityFlow.flatMapLatest { identity ->
        if (identity?.isFirebaseUser == true) {
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

    val userProfile: StateFlow<UserProfile?> = activeAuthIdentityFlow.flatMapLatest { identity ->
        if (identity == null) {
            flowOf(null)
        } else {
            val localFlow = (localConfigRepository.observeRegisteredUsers() ?: flowOf(emptyList()))
                .map { list ->
                    list.firstOrNull {
                        it.userId == identity.uid ||
                            (identity.email.isNotBlank() && it.email.equals(identity.email, ignoreCase = true))
                    } ?: UserProfile(
                        id = identity.uid,
                        userId = identity.uid,
                        displayName = identity.displayName,
                        email = identity.email,
                        role = if (identity.isAdmin) "SUPER_ADMIN" else "USER",
                        deviceCount = 1,
                        maxDevices = 10,
                        dataUsageBytes = 0.0,
                        dataLimitBytes = 500.0 * 1024 * 1024 * 1024,
                        subscriptionStatus = if (identity.isAdmin) "active" else "expired"
                    )
                }
            if (identity.isFirebaseUser) {
                combine(
                    repository.observeUserProfile().catch { emit(null) },
                    localFlow
                ) { remoteProfile, localProfile ->
                    remoteProfile?.copy(
                        dataUsageBytes = maxOf(remoteProfile.dataUsageBytes, localProfile.dataUsageBytes)
                    ) ?: localProfile
                }
            } else {
                localFlow
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = null
    )

    val auditLogs: StateFlow<List<AuthAuditLog>> = activeAuthIdentityFlow.flatMapLatest { identity ->
        if (identity?.isFirebaseUser == true) {
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

    val currentRole: StateFlow<AdminRole> = combine(
        _selectedRole,
        _verifiedAdminRole,
        _verifiedServerAdmin
    ) { selected, verifiedRole, isServerAdmin ->
        if (!isServerAdmin) {
            AdminRole.AUDITOR
        } else {
            selected ?: verifiedRole ?: AdminRole.SUPER_ADMIN
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = AdminRole.AUDITOR
    )

    // --- USER SUBSCRIPTION & DEDICATED IP ALLOCATIONS (UNIFIED ROOM + FIRESTORE, ZERO CHURN) ---

    val userSubscription: StateFlow<UserSubscription?> = activeAuthIdentityFlow.flatMapLatest { identity ->
        val uid = identity?.uid ?: "local_user"
        val cachedFlow = (localConfigRepository.observeCachedSubscription(uid) ?: flowOf(null))
            .distinctUntilChanged()
        _isSubscriptionOfflineCached.value = false
        if (identity?.isFirebaseUser == true) {
            val remoteFlow = subscriptionRepository.observeMySubscription()
                .onEach { remoteSub ->
                    remoteSub?.updatedAt?.toDate()?.time?.let { updateTrustedServerAnchor(it) }
                    if (remoteSub != null) {
                        localConfigRepository.cacheSubscription(remoteSub, currentTrustedNowMs())
                    }
                }
                .catch { emit(null) }
                .distinctUntilChanged()
            combine(remoteFlow, cachedFlow) { remoteSub, cachedSub ->
                remoteSub ?: cachedSub
            }
        } else {
            cachedFlow
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = null
    )

    val allocatedEndpoints: StateFlow<List<AllocatedEndpointRecord>> = activeAuthIdentityFlow.flatMapLatest { identity ->
        val uid = identity?.uid ?: "local_user"
        val cachedFlow = (localConfigRepository.observeCachedAllocatedEndpoints(uid) ?: flowOf(emptyList()))
            .distinctUntilChanged()
        if (identity?.isFirebaseUser == true) {
            val remoteFlow = subscriptionRepository.observeMyAllocatedEndpoints()
                .onEach { remoteList ->
                    if (remoteList.isNotEmpty()) {
                        localConfigRepository.cacheAllocatedEndpoints(uid, remoteList)
                        localConfigRepository.replaceSubscriptionConfigs(uid, remoteList)
                    }
                }
                .catch { emit(emptyList()) }
                .distinctUntilChanged()
            combine(remoteFlow, cachedFlow) { remoteList, cachedList ->
                remoteList.ifEmpty { cachedList }
            }
        } else {
            cachedFlow
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    val subscriptionHistory: StateFlow<List<SubscriptionHistoryEvent>> = activeAuthIdentityFlow.flatMapLatest { identity ->
        val uid = identity?.uid ?: "local_user"
        val cachedFlow = (localConfigRepository.observeCachedHistory(uid) ?: flowOf(emptyList()))
            .distinctUntilChanged()
        if (identity?.isFirebaseUser == true) {
            val remoteFlow = subscriptionRepository.observeMySubscriptionHistory()
                .onEach { remoteList ->
                    if (remoteList.isNotEmpty()) {
                        localConfigRepository.cacheSubscriptionHistory(remoteList)
                    }
                }
                .catch { emit(emptyList()) }
                .distinctUntilChanged()
            combine(remoteFlow, cachedFlow) { remoteList, cachedList ->
                remoteList.ifEmpty { cachedList }
            }
        } else {
            cachedFlow
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    val allocationHistory: StateFlow<List<AllocationHistoryRecord>> = activeAuthIdentityFlow.flatMapLatest { identity ->
        val uid = identity?.uid ?: "local_user"
        val cachedFlow = (localConfigRepository.observeCachedAllocationHistory(uid) ?: flowOf(emptyList()))
            .distinctUntilChanged()
        if (identity?.isFirebaseUser == true) {
            val remoteFlow = subscriptionRepository.observeMyAllocationHistory()
                .onEach { remoteList ->
                    if (remoteList.isNotEmpty()) {
                        localConfigRepository.cacheAllocationHistory(remoteList)
                    }
                }
                .catch { emit(emptyList()) }
                .distinctUntilChanged()
            combine(remoteFlow, cachedFlow) { remoteList, cachedList ->
                remoteList.ifEmpty { cachedList }
            }
        } else {
            cachedFlow
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    private val _inspectedHistoryUserId = MutableStateFlow<String?>(null)
    val inspectedHistoryUserId: StateFlow<String?> = _inspectedHistoryUserId.asStateFlow()

    val adminInspectedAllocationHistory: StateFlow<List<AllocationHistoryRecord>> =
        combine(activeAuthIdentityFlow, _inspectedHistoryUserId) { identity, targetUid -> identity to targetUid }
            .flatMapLatest { (identity, targetUid) ->
                if (identity?.isAdmin == true && !targetUid.isNullOrBlank()) {
                    val localHist = localConfigRepository.observeCachedAllocationHistory(targetUid) ?: flowOf(emptyList())
                    if (identity.isFirebaseUser) {
                        combine(
                            localHist,
                            subscriptionRepository.observeAllocationHistoryForUser(targetUid).catch { emit(emptyList()) }
                        ) { local, remote ->
                            (remote + local).distinctBy { it.historyId.ifBlank { "${it.endpointId}_${it.action}_${it.cycleKey}" } }
                        }
                    } else {
                        localHist
                    }
                } else {
                    flowOf(emptyList())
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = emptyList()
            )

    val myPaymentRequests: StateFlow<List<PaymentRequestRecord>> = activeAuthIdentityFlow.flatMapLatest { identity ->
        val uid = identity?.uid ?: "local_user"
        val localFlow = (localConfigRepository.observeLocalPaymentRequestsForUser(uid) ?: flowOf(emptyList()))
            .distinctUntilChanged()
        if (identity?.isFirebaseUser == true) {
            val remoteFlow = subscriptionRepository.observeMyPaymentRequests()
                .onEach { if (it.isNotEmpty()) localConfigRepository.cachePaymentRequests(it) }
                .catch { emit(emptyList()) }
                .distinctUntilChanged()
            combine(remoteFlow, localFlow) { remote, local ->
                (remote + local).distinctBy { it.paymentId.ifBlank { "${it.userId}_${it.planId}_${it.createdAt}" } }
            }
        } else {
            localFlow
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    // --- ADMIN SUBSCRIPTION, REGISTERED USERS & ALLOCATION CONSOLE STREAMS ---

    val allSubscriptionsAdmin: StateFlow<List<UserSubscription>> = activeAuthIdentityFlow.flatMapLatest { identity ->
        if (identity?.isAdmin == true) {
            val localFlow = (localConfigRepository.observeAllCachedSubscriptions() ?: flowOf(emptyList()))
                .distinctUntilChanged()
            if (identity.isFirebaseUser) {
                val remoteFlow = subscriptionRepository.observeAllSubscriptionsForAdmin()
                    .catch { emit(emptyList()) }
                    .distinctUntilChanged()
                combine(localFlow, remoteFlow) { local, remote ->
                    val byUid = linkedMapOf<String, UserSubscription>()
                    remote.forEach { byUid[it.userId] = it }
                    local.forEach { byUid.putIfAbsent(it.userId, it) }
                    byUid.values.toList()
                }
            } else {
                localFlow
            }
        } else {
            flowOf(emptyList())
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    val endpointInventoryAdmin: StateFlow<List<ManagedEndpoint>> = activeAuthIdentityFlow.flatMapLatest { identity ->
        if (identity?.isAdmin == true) {
            val localFlow = (localConfigRepository.observeManagedEndpoints() ?: flowOf(emptyList()))
                .distinctUntilChanged()
            if (identity.isFirebaseUser) {
                val remoteFlow = subscriptionRepository.observeEndpointInventoryForAdmin()
                    .onEach { if (it.isNotEmpty()) localConfigRepository.upsertLocalManagedEndpoints(it) }
                    .catch { emit(emptyList()) }
                    .distinctUntilChanged()
                combine(localFlow, remoteFlow) { local, remote ->
                    val byId = linkedMapOf<String, ManagedEndpoint>()
                    remote.forEach { byId[it.endpointId] = it }
                    local.forEach { byId.putIfAbsent(it.endpointId, it) }
                    byId.values.toList()
                }
            } else {
                localFlow
            }
        } else {
            flowOf(emptyList())
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    val allPaymentRequestsAdmin: StateFlow<List<PaymentRequestRecord>> = activeAuthIdentityFlow.flatMapLatest { identity ->
        if (identity?.isAdmin == true) {
            val localFlow = (localConfigRepository.observeAllLocalPaymentRequests() ?: flowOf(emptyList()))
                .distinctUntilChanged()
            if (identity.isFirebaseUser) {
                val remoteFlow = subscriptionRepository.observeAllPaymentRequestsForAdmin()
                    .onEach { if (it.isNotEmpty()) localConfigRepository.cachePaymentRequests(it) }
                    .catch { emit(emptyList()) }
                    .distinctUntilChanged()
                combine(localFlow, remoteFlow) { local, remote ->
                    val byId = linkedMapOf<String, PaymentRequestRecord>()
                    remote.forEach { byId[it.paymentId] = it }
                    local.forEach { byId.putIfAbsent(it.paymentId, it) }
                    byId.values.toList()
                }
            } else {
                localFlow
            }
        } else {
            flowOf(emptyList())
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    val registeredUsers: StateFlow<List<UserProfile>> = activeAuthIdentityFlow.flatMapLatest { identity ->
        if (identity?.isAdmin != true) {
            flowOf(emptyList())
        } else {
            val localUsersFlow = (localConfigRepository.observeRegisteredUsers() ?: flowOf(emptyList()))
            val firestoreUsersFlow = if (identity.isFirebaseUser) {
                repository.observeAllUserProfilesForAdmin().catch { emit(emptyList()) }
            } else {
                flowOf(emptyList())
            }
            combine(
                localUsersFlow,
                firestoreUsersFlow,
                allSubscriptionsAdmin,
                allPaymentRequestsAdmin
            ) { localUsers, remoteUsers, subs, payments ->
                val byUid = linkedMapOf<String, UserProfile>()
                remoteUsers.forEach { u ->
                    val key = u.userId.ifBlank { u.id }
                    if (key.isNotBlank()) byUid[key] = u.copy(userId = key)
                }
                localUsers.forEach { u ->
                    val key = u.userId.ifBlank { u.id }
                    if (key.isNotBlank()) byUid.putIfAbsent(key, u.copy(userId = key))
                }
                subs.forEach { s ->
                    if (s.userId.isNotBlank() && s.userId !in byUid) {
                        byUid[s.userId] = UserProfile(
                            id = s.userId,
                            userId = s.userId,
                            displayName = s.userEmail.substringBefore("@").ifBlank { s.userId },
                            email = s.userEmail,
                            role = "USER",
                            subscriptionStatus = s.status.lowercase()
                        )
                    }
                }
                payments.forEach { p ->
                    if (p.userId.isNotBlank() && p.userId !in byUid) {
                        byUid[p.userId] = UserProfile(
                            id = p.userId,
                            userId = p.userId,
                            displayName = p.userEmail.substringBefore("@").ifBlank { p.userId },
                            email = p.userEmail,
                            role = "USER",
                            subscriptionStatus = "pending"
                        )
                    }
                }
                // Reflect latest active subscription status onto each profile
                val subByUid = subs.associateBy { it.userId }
                byUid.values.map { profile ->
                    val sub = subByUid[profile.userId]
                    if (sub != null) {
                        profile.copy(subscriptionStatus = sub.status.lowercase())
                    } else {
                        profile
                    }
                }.sortedByDescending { it.subscriptionStatus == "active" }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    val shortageAlertsAdmin: StateFlow<List<InventoryShortageAlert>> = activeAuthIdentityFlow.flatMapLatest { identity ->
        if (identity?.isAdmin == true && identity.isFirebaseUser) {
            subscriptionRepository.observeShortageAlertsForAdmin().catch { emit(emptyList()) }
        } else {
            flowOf(emptyList())
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    val rotationLogsAdmin: StateFlow<List<RotationAuditLog>> = activeAuthIdentityFlow.flatMapLatest { identity ->
        if (identity?.isAdmin == true && identity.isFirebaseUser) {
            subscriptionRepository.observeRotationLogsForAdmin().catch { emit(emptyList()) }
        } else {
            flowOf(emptyList())
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    val userTickets: StateFlow<List<SupportTicket>> = activeAuthIdentityFlow.flatMapLatest { identity ->
        if (identity?.isFirebaseUser == true) {
            supportTicketRepository.observeUserTickets(identity.uid).catch { emit(emptyList()) }
        } else {
            flowOf(emptyList())
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    val adminTickets: StateFlow<List<SupportTicket>> = activeAuthIdentityFlow.flatMapLatest { identity ->
        if (identity?.isAdmin == true && identity.isFirebaseUser) {
            supportTicketRepository.observeAllTicketsForAdmin().catch { emit(emptyList()) }
        } else {
            flowOf(emptyList())
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    val overviewMetrics: StateFlow<OverviewMetrics> = combine(
        combine(configs, servers, registeredUsers) { c, s, u -> Triple(c, s, u) },
        combine(allSubscriptionsAdmin, allPaymentRequestsAdmin, endpointInventoryAdmin, cleanIpPool) { subs, pays, eps, clean ->
            listOf(subs, pays, eps, clean)
        }
    ) { (cfgList, srvList, regUsers), adminTuple ->
        @Suppress("UNCHECKED_CAST")
        val subs = adminTuple[0] as List<UserSubscription>
        @Suppress("UNCHECKED_CAST")
        val payments = adminTuple[1] as List<PaymentRequestRecord>
        @Suppress("UNCHECKED_CAST")
        val endpoints = adminTuple[2] as List<ManagedEndpoint>
        @Suppress("UNCHECKED_CAST")
        val cleanIps = adminTuple[3] as List<CleanIpEntity>

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
            isRealBackendConnected = true,
            registeredUsersCount = regUsers.size,
            activeSubscriptionsCount = subs.count { it.statusEnum() == SubscriptionStatus.ACTIVE },
            pendingPaymentsCount = payments.count { it.status == "PENDING_PAYMENT" || it.status == "PENDING_VERIFICATION" },
            healthyEndpointsCount = endpoints.count { it.healthy && it.enabled },
            totalEndpointsCount = endpoints.size,
            cleanIpHealthyCount = cleanIps.count { it.healthStatus == CleanIpHealthStatus.HEALTHY.name },
            cleanIpTotalCount = cleanIps.size
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = OverviewMetrics()
    )

    init {
        runCatching { VpnController.syncWithService(application) }
        // Background IO seeding: default configs, 330+ Clean IP pool, 75-node dedicated IP inventory, and registered accounts
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val uid = currentActorUid()
                _tombstonedConfigIds.value = localConfigRepository.getTombstonedIds(uid)
                localConfigRepository.prepopulateDefaultsIfEmpty(uid)
                localConfigRepository.seedCleanIpPoolIfNeeded()
                localConfigRepository.prepopulateEndpointInventoryIfNeeded()
                val healthyIpv4s = localConfigRepository.getCleanIpPoolOnce()
                    .filter {
                        it.isAvailable &&
                            it.addressType == CleanIpAddressType.IPV4.name &&
                            it.healthStatus != CleanIpHealthStatus.QUARANTINED.name
                    }
                    .map { it.host }
                if (healthyIpv4s.isNotEmpty()) {
                    com.example.vpn.engine.DynamicIpDnsRotator.seedCandidateIps(healthyIpv4s)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed seeding default configs/clean IPs: ${e.message}")
            }
        }

        // Maintain live trusted server-time delta using monotonic elapsedRealtime
        viewModelScope.launch {
            while (true) {
                _trustedServerNowMs.value = currentTrustedNowMs()
                delay(30_000L)
            }
        }

        // Keep subscriber-only Combo Anti-DPI Shield state synced with active entitlement
        viewModelScope.launch {
            combine(userSubscription, _trustedServerNowMs) { sub, nowMs ->
                SubscriptionLifecycleEngine.isEntitled(sub, nowMs)
            }.distinctUntilChanged().collect { entitled ->
                VpnController.initComboShieldState(application, entitled)
            }
        }

        // Unified Firebase Authentication & Server-Verified Admin / Migration Sync
        viewModelScope.launch {
            authStateFlow.collect { fbUser ->
                if (fbUser != null) {
                    val uid = fbUser.uid
                    val email = fbUser.email.orEmpty()
                    val displayName = fbUser.displayName?.takeIf { it.isNotBlank() }
                        ?: email.substringBefore("@").ifBlank { "TMS User" }
                    _isEmailVerified.value = fbUser.isEmailVerified
                    VpnController.setActiveAccountId(uid)

                    launch(Dispatchers.IO) {
                        runCatching {
                            LegacyAccountMigrationHelper.migrateLegacyLocalDataIfNeeded(
                                context = getApplication(),
                                localRepo = localConfigRepository,
                                firebaseUid = uid,
                                email = email
                            )
                        }
                        try {
                            localConfigRepository.upsertRegisteredUser(
                                RegisteredUserEntity(
                                    userId = uid,
                                    displayName = displayName,
                                    email = email,
                                    role = "USER",
                                    maxDevices = 10,
                                    dataLimitBytes = 500.0 * 1024 * 1024 * 1024,
                                    subscriptionStatus = "expired"
                                )
                            )
                            _tombstonedConfigIds.value = localConfigRepository.getTombstonedIds(uid)
                        } catch (e: Exception) {
                            Log.d(TAG, "Local registered user sync failed: ${e.message}")
                        }
                    }

                    launch(Dispatchers.IO) {
                        var claimedAdmin = false
                        var claimedRole: AdminRole? = null

                        // 1. Check verified Firebase ID token custom claims
                        val verifiedSession = userAuthManager.getVerifiedFirebaseSession(fbUser, forceRefresh = false)
                        if (verifiedSession != null && verifiedSession.isServerAdmin) {
                            claimedAdmin = true
                            claimedRole = verifiedSession.verifiedAdminRole ?: AdminRole.SUPER_ADMIN
                        }

                        // 2. Check /admins/{uid} authoritative Firestore doc
                        if (!claimedAdmin) {
                            val hasAdminDoc = repository.verifyAdminDocumentExists(uid)
                            if (hasAdminDoc) {
                                claimedAdmin = true
                                claimedRole = AdminRole.SUPER_ADMIN
                            }
                        }

                        // 3. Verify via Worker /api/app/session & sync D1 ecosystem state
                        var sessionSyncError: BackgroundBackendError? = null
                        when (val syncRes = backendClient.syncSession()) {
                            is BackendResult.Success -> {
                                val sync = syncRes.data
                                _backendSessionSync.value = sync
                                if (sync.serverTimeMs > 0L) {
                                    updateTrustedServerAnchor(sync.serverTimeMs)
                                }
                                if (sync.trialStatus != null) {
                                    _freeTrialStatus.value = sync.trialStatus
                                }
                                if (sync.isAdmin) {
                                    claimedAdmin = true
                                    claimedRole = AdminRole.fromVerifiedClaimOrNull(sync.verifiedRole)
                                        ?: claimedRole
                                        ?: AdminRole.SUPER_ADMIN
                                }
                            }
                            else -> {
                                sessionSyncError = classifyBackgroundError("session_sync", syncRes, isPersianLanguage.value)
                            }
                        }

                        _verifiedServerAdmin.value = claimedAdmin
                        _verifiedAdminRole.value = claimedRole
                        if (!claimedAdmin) {
                            _isInAdminSpace.value = false
                            _isUnlocked.value = false
                            _activeSession.value = null
                            _showAdminPinPrompt.value = false
                            _selectedRole.value = null
                        }

                        // Refresh public & authenticated ecosystem endpoints (trial, referrals, crypto, releases)
                        val ecosystemError = refreshBackendEcosystemInternal(claimedAdmin)
                        _backgroundBackendError.value = selectHigherPriorityError(sessionSyncError, ecosystemError)

                        try {
                            repository.ensureUserProfile(
                                displayName = displayName,
                                email = email
                            )
                            syncPendingConfigDeletes(uid)
                            val serverMs = subscriptionRepository.fetchTrustedServerEpochMs(currentTrustedNowMs())
                            updateTrustedServerAnchor(serverMs)
                            repository.observeConfigs().catch { }.collect { remoteConfigs ->
                                val tombstones = localConfigRepository.getTombstonedIds(uid)
                                _tombstonedConfigIds.value = tombstones
                                val validRemote = remoteConfigs.filter { it.id !in tombstones }
                                if (validRemote.isNotEmpty()) {
                                    localConfigRepository.saveAllConfigs(validRemote, activeUserId = uid)
                                }
                            }
                        } catch (e: Exception) {
                            Log.d(TAG, "Firestore sync skipped/fallback: ${e.message}")
                        }
                    }
                } else if (testAuthenticatedUid.isNullOrBlank()) {
                    VpnController.setActiveAccountId(null)
                    _verifiedServerAdmin.value = false
                    _verifiedAdminRole.value = null
                    _isEmailVerified.value = false
                    _isInAdminSpace.value = false
                    _isUnlocked.value = false
                    _activeSession.value = null
                    _showAdminPinPrompt.value = false
                    _selectedRole.value = null
                    _backendSessionSync.value = null
                    _cryptoInvoices.value = emptyList()
                    _referralDashboard.value = null
                    _freeTrialStatus.value = null
                    _adminRewardRequests.value = emptyList()
                    _adminReleases.value = emptyList()
                    _backgroundBackendError.value = null
                    launch(Dispatchers.IO) {
                        runCatching {
                            _tombstonedConfigIds.value = localConfigRepository.getTombstonedIds(currentActorUid())
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
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
        if (!isCurrentUserAdmin()) {
            val deniedMsgFa = "دسترسی غیرمجاز: حساب کاربری شما مجوز مدیریت تاییدشده از سرور را ندارد."
            val deniedMsgEn = "Unauthorized: Your account lacks server-verified administrator privileges."
            _uiMessage.value = UiMessage.Error(if (isPersianLanguage.value) deniedMsgFa else deniedMsgEn)
            return false
        }
        if (!isAdminSessionAuthorized()) {
            val lockedMsgFa = "نشست مدیریت قفل است؛ ابتدا پین امنیتی مدیر را تایید کنید."
            val lockedMsgEn = "Admin session is locked. Verify your administrator PIN first."
            _uiMessage.value = UiMessage.Error(if (isPersianLanguage.value) lockedMsgFa else lockedMsgEn)
            return false
        }
        val role = currentRole.value
        if (!role.hasPermission(permission)) {
            val deniedMsgFa = "خطای سطح دسترسی: نقش فعلی (${role.titleFa}) اجازه $actionDescription را ندارد."
            val deniedMsgEn = "Permission denied: Current role (${role.titleEn}) lacks permission: ${permission.titleEn}"
            _uiMessage.value = UiMessage.Error(if (isPersianLanguage.value) deniedMsgFa else deniedMsgEn)

            viewModelScope.launch {
                try {
                    val user = safeFirebaseUser()
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
        if (!isCurrentUserAdmin()) {
            _isUnlocked.value = false
            _activeSession.value = null
            val deniedMsgFa = "دسترسی غیرمجاز: حساب کاربری شما مجوز مدیریت تاییدشده از سرور را ندارد."
            val deniedMsgEn = "Unauthorized: Server-verified administrator privilege required."
            _uiMessage.value = UiMessage.Error(if (isPersianLanguage.value) deniedMsgFa else deniedMsgEn)
            return false
        }
        val actorUid = activeAuthenticatedUid() ?: return false
        val role = currentRole.value
        val (result, auditLog) = securityManager.verifyAdminCredential(
            userId = actorUid,
            role = role,
            pinOrPassword = pin,
            deviceInfo = getDeviceInfo()
        )

        if (safeFirebaseUser() != null) {
            viewModelScope.launch {
                try {
                    repository.addAuditLog(auditLog)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed logging audit attempt", e)
                }
            }
        }

        return when (result) {
            is AuthAttemptResult.Success -> {
                _activeSession.value = result.sessionToken
                _isUnlocked.value = true
                _showAdminPinPrompt.value = false
                _lockoutSeconds.value = 0L
                _uiMessage.value = UiMessage.Success(
                    if (isPersianLanguage.value) "هویت مدیر با موفقیت تایید شد / نشست امن صادر گردید" else "Admin PIN verified. Secure session established."
                )
                true
            }
            is AuthAttemptResult.InvalidCredentials -> {
                _activeSession.value = null
                _isUnlocked.value = false
                _uiMessage.value = UiMessage.Error(
                    if (isPersianLanguage.value) result.messageFa else result.messageEn
                )
                false
            }
            is AuthAttemptResult.LockedOut -> {
                _activeSession.value = null
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
        if (!checkPermission(AdminPermission.MANAGE_SETTINGS, "تغییر پین امنیتی مدیر")) return
        val actorUid = activeAuthenticatedUid() ?: return
        val role = currentRole.value

        val validation = InputValidator.validateAdminPin(newPin)
        if (validation is ValidationResult.Invalid) {
            _uiMessage.value = UiMessage.Error(
                if (isPersianLanguage.value) validation.firstMessageFa() else validation.firstMessageEn()
            )
            return
        }

        val (success, message) = securityManager.changeAdminPin(actorUid, role, oldPin, newPin)
        if (success) {
            _adminPinConfigured.value = true
            _activeSession.value = securityManager.issueSessionToken(actorUid, role)
            _uiMessage.value = UiMessage.Success(message)
            if (safeFirebaseUser() != null) {
                viewModelScope.launch {
                    try {
                        repository.addAuditLog(
                            AuthAuditLog(
                                userId = actorUid,
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
            }
        } else {
            _uiMessage.value = UiMessage.Error(message)
        }
    }

    fun emergencyResetPin(recoveryToken: String, newPin: String) {
        if (!isCurrentUserAdmin()) {
            val deniedMsgFa = "دسترسی غیرمجاز: بازیابی پین فقط برای مدیر تاییدشده از سرور مجاز است."
            val deniedMsgEn = "Unauthorized: Server-verified administrator privilege required."
            _uiMessage.value = UiMessage.Error(if (isPersianLanguage.value) deniedMsgFa else deniedMsgEn)
            return
        }
        val actorUid = activeAuthenticatedUid() ?: return
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
            _adminPinConfigured.value = true
            _activeSession.value = securityManager.issueSessionToken(actorUid, role)
            _isUnlocked.value = true
            _showAdminPinPrompt.value = false
            _uiMessage.value = UiMessage.Success(message)
            if (safeFirebaseUser() != null) {
                viewModelScope.launch {
                    try {
                        repository.addAuditLog(
                            AuthAuditLog(
                                userId = actorUid,
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
            }
        } else {
            _uiMessage.value = UiMessage.Error(message)
            if (safeFirebaseUser() != null) {
                viewModelScope.launch {
                    try {
                        repository.addAuditLog(
                            AuthAuditLog(
                                userId = actorUid,
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
    }

    fun hasConfiguredAdminPin(): Boolean {
        val configured = securityManager.hasConfiguredPin()
        _adminPinConfigured.value = configured
        return configured
    }

    fun configureInitialAdminPin(newPin: String) {
        if (!isCurrentUserAdmin()) {
            checkPermission(AdminPermission.MANAGE_SETTINGS, "تنظیم پین امنیتی مدیر")
            return
        }
        val actorUid = activeAuthenticatedUid() ?: return
        val role = currentRole.value
        val validation = InputValidator.validateAdminPin(newPin)
        if (validation is ValidationResult.Invalid) {
            _uiMessage.value = UiMessage.Error(
                if (isPersianLanguage.value) validation.firstMessageFa() else validation.firstMessageEn()
            )
            return
        }
        val (ok, msg) = securityManager.configureInitialPin(actorUid, role, newPin)
        if (ok) {
            _adminPinConfigured.value = true
            _activeSession.value = securityManager.issueSessionToken(actorUid, role)
            _isUnlocked.value = true
            _showAdminPinPrompt.value = false
            _uiMessage.value = UiMessage.Success(msg)
        } else {
            _uiMessage.value = UiMessage.Error(msg)
        }
    }

    fun getRecoveryToken(): String {
        if (!isCurrentUserAdmin()) return ""
        return securityManager.getRecoveryToken()
    }

    fun switchRole(role: AdminRole) {
        if (!isCurrentUserAdmin()) {
            checkPermission(AdminPermission.MANAGE_SETTINGS, "تغییر نقش مدیریت")
            return
        }
        if (!isAdminSessionAuthorized()) {
            val lockedMsgFa = "ابتدا نشست مدیریت را با پین امنیتی باز کنید."
            val lockedMsgEn = "Unlock the admin session with your PIN first."
            _uiMessage.value = UiMessage.Error(if (isPersianLanguage.value) lockedMsgFa else lockedMsgEn)
            return
        }
        val uid = currentActorUid()
        _selectedRole.value = role
        if (securityManager.hasConfiguredPin()) {
            _activeSession.value = securityManager.issueSessionToken(uid, role)
        }

        viewModelScope.launch {
            try {
                if (safeFirebaseUser() != null) {
                    repository.addAuditLog(
                        AuthAuditLog(
                            userId = uid,
                            action = "ROLE_SWITCHED",
                            role = role.id,
                            module = "RBAC",
                            status = "SUCCESS",
                            ipOrDevice = getDeviceInfo(),
                            details = "تغییر نقش فعال مدیر به ${role.titleEn} (${role.id})"
                        )
                    )
                }
                _uiMessage.value = UiMessage.Success(
                    if (isPersianLanguage.value) "نقش مدیریتی به «${role.titleFa}» تغییر یافت" else "Active role switched to ${role.titleEn}"
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed updating role", e)
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
                val user = safeFirebaseUser()
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
                val user = safeFirebaseUser()
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
                    if (safeFirebaseUser() != null) {
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

    /**
     * Enables or disables the subscriber-only 4-Layer Anti-DPI Combo Shield (DoH + FakeDNS +
     * Verified Clean IP + TLS Fragment/uTLS/ALPN + ISP Combo Memory) whenever requested.
     * Returns false when user does not have an active subscription.
     */
    fun toggleComboShield(context: Context, enable: Boolean): Boolean {
        val entitled = SubscriptionLifecycleEngine.isEntitled(userSubscription.value, currentTrustedNowMs())
        val allowed = VpnController.setComboShieldEnabled(context, enable, entitled)
        if (!allowed) {
            _uiMessage.value = UiMessage.Error(
                if (isPersianLanguage.value) {
                    "سپر ترکیبی ضدفیلترینگ مخصوص مشترکین فعال است؛ از بخش اشتراک (داخل اپ، روبیکا یا تلگرام) تهیه کنید"
                } else {
                    "Anti-DPI Combo Shield is exclusive to active subscribers"
                }
            )
            return false
        }

        _uiMessage.value = UiMessage.Success(
            if (isPersianLanguage.value) {
                if (enable) "سپر ترکیبی ضدفیلترینگ (DoH + FakeDNS + Fragment) فعال شد"
                else "سپر ترکیبی غیرفعال شد (حالت اتصال عادی)"
            } else {
                if (enable) "Anti-DPI Combo Shield enabled" else "Combo Shield disabled"
            }
        )

        // If tunnel is already active, seamlessly apply or deactivate Combo Shield
        val activeState = VpnController.connectionState.value
        val activeCfg = _selectedVpnConfig.value ?: VpnController.activeConfig
        if (activeState == VpnConnectionState.CONNECTED || activeState == VpnConnectionState.CONNECTING) {
            if (enable) {
                rerollOrConnectComboShield(context, forceNextPreset = false)
            } else if (activeCfg != null) {
                VpnController.startVpn(
                    context = context,
                    config = activeCfg,
                    antiFilterMode = true,
                    forceRestart = true,
                    comboMode = false
                )
            }
        }
        return true
    }

    fun rerollOrConnectComboShield(context: Context, forceNextPreset: Boolean = true) {
        if (_comboBusy.value || _isRacingConnect.value) return
        val entitled = SubscriptionLifecycleEngine.isEntitled(userSubscription.value, currentTrustedNowMs())
        if (!entitled) {
            VpnController.setComboShieldEnabled(context, false, isEntitled = false)
            _uiMessage.value = UiMessage.Error(
                if (isPersianLanguage.value) "برای استفاده از ترکیب ضدفیلترینگ اشتراک فعال لازم است"
                else "Active subscription required for Combo Shield"
            )
            return
        }

        val candidates = configs.value.ifEmpty {
            localConfigs.value.map { it.toDomainModel() }
        }.ifEmpty {
            com.example.data.model.DefaultVpnConfigs.getDefaultConfigs(currentActorUid())
        }

        viewModelScope.launch {
            _comboBusy.value = true
            _isRacingConnect.value = true
            try {
                val cleanCandidates = localConfigRepository.getCleanIpPoolOnce()
                    .filter {
                        it.isAvailable &&
                            it.addressType == CleanIpAddressType.IPV4.name &&
                            it.healthStatus != CleanIpHealthStatus.QUARANTINED.name
                    }
                    .sortedBy { if (it.latencyMs > 0) it.latencyMs else Int.MAX_VALUE }
                    .map { it.host }
                    .take(10)

                val comboSummary: ComboBypassSummary = AntiDpiComboEngine.runSmartComboBypass(
                    context = context,
                    configs = candidates,
                    cleanIpCandidates = cleanCandidates,
                    forceNextPreset = forceNextPreset
                )

                val summaryText = comboSummary.preset.shortSummary(isPersianLanguage.value)
                VpnController.updateActiveComboSummary(summaryText)

                val winner = comboSummary.winnerConfig
                if (winner != null) {
                    val overrideIp = comboSummary.winnerCleanIp?.takeIf { it.isNotBlank() }
                    if (overrideIp != null) {
                        com.example.vpn.engine.DynamicIpDnsRotator.promoteCleanIp(overrideIp, pinForTunnel = true)
                    } else {
                        com.example.vpn.engine.DynamicIpDnsRotator.clearPinnedCleanIp()
                    }
                    val updatedWinner = winner.copy(pingMs = comboSummary.delayMs, status = "online")
                    _selectedVpnConfig.value = updatedWinner
                    VpnController.startVpn(
                        context = context,
                        config = updatedWinner,
                        antiFilterMode = true,
                        cleanIpOverride = overrideIp,
                        forceRestart = true,
                        comboMode = true
                    )
                    _uiMessage.value = UiMessage.Success(
                        if (isPersianLanguage.value) {
                            val memTag = if (comboSummary.usedMemoryHit) " (حافظه اپراتور)" else ""
                            val viaIp = if (overrideIp != null) " • IP: $overrideIp" else ""
                            "ترکیب برنده متصل شد$memTag: ${updatedWinner.name}$viaIp • $summaryText (${comboSummary.delayMs}ms)"
                        } else {
                            "Combo Bypass active: ${updatedWinner.name} • $summaryText (${comboSummary.delayMs}ms)"
                        }
                    )
                } else {
                    val reasonFa = comboSummary.lastFailureReason.labelFa
                    _uiMessage.value = UiMessage.Error(
                        if (isPersianLanguage.value) {
                            "ترکیب سالمی یافت نشد ($reasonFa)؛ از بخش مخزن آی‌پی، سلامت IPها را بررسی کنید"
                        } else {
                            "No healthy combo reachable (${comboSummary.lastFailureReason.labelEn})"
                        }
                    )
                }
            } finally {
                _comboBusy.value = false
                _isRacingConnect.value = false
            }
        }
    }

    /**
     * One-tap Automatic Anti-DPI Connect prioritizing Germany (DE) & Turkey (TR) servers,
     * automatically using the 4-layer Combo Bypass Engine when subscriber Combo Shield is enabled.
     */
    fun connectAutoBypassDeTr(context: Context) {
        if (_isRacingConnect.value) return
        val entitled = SubscriptionLifecycleEngine.isEntitled(userSubscription.value, currentTrustedNowMs())
        if (entitled && VpnController.comboShieldEnabled.value) {
            rerollOrConnectComboShield(context, forceNextPreset = false)
            return
        }

        val candidates = configs.value.ifEmpty {
            localConfigs.value.map { it.toDomainModel() }
        }.ifEmpty {
            com.example.data.model.DefaultVpnConfigs.getDefaultConfigs(currentActorUid())
        }

        viewModelScope.launch {
            _isRacingConnect.value = true
            try {
                val cleanCandidates = localConfigRepository.getCleanIpPoolOnce()
                    .filter {
                        it.isAvailable &&
                            it.addressType == CleanIpAddressType.IPV4.name &&
                            it.healthStatus != CleanIpHealthStatus.QUARANTINED.name
                    }
                    .sortedBy { if (it.latencyMs > 0) it.latencyMs else Int.MAX_VALUE }
                    .map { it.host }
                    .take(10)

                val summary = SmartRacingSelector.raceAutoBypassDeTr(
                    context = context,
                    configs = candidates,
                    cleanIpCandidates = cleanCandidates
                )
                _racingSummary.value = summary

                val winner = summary.winner
                if (winner != null) {
                    val overrideIp = summary.winnerCleanIp?.takeIf { it.isNotBlank() }
                    if (overrideIp != null) {
                        com.example.vpn.engine.DynamicIpDnsRotator.promoteCleanIp(overrideIp, pinForTunnel = true)
                    } else {
                        com.example.vpn.engine.DynamicIpDnsRotator.clearPinnedCleanIp()
                    }
                    val updatedWinner = winner.copy(pingMs = summary.winnerDelayMs, status = "online")
                    _selectedVpnConfig.value = updatedWinner
                    VpnController.startVpn(
                        context = context,
                        config = updatedWinner,
                        antiFilterMode = true,
                        cleanIpOverride = overrideIp
                    )
                    _uiMessage.value = UiMessage.Success(
                        if (isPersianLanguage.value) {
                            val viaIp = if (overrideIp != null) " با Clean IP $overrideIp" else ""
                            "اتصال ضد فیلتر برقرار شد: ${updatedWinner.name}$viaIp (${summary.winnerDelayMs}ms)"
                        } else {
                            "Auto-Bypass connected: ${updatedWinner.name} (${summary.winnerDelayMs}ms)"
                        }
                    )
                } else {
                    _uiMessage.value = UiMessage.Error(
                        if (isPersianLanguage.value) {
                            "نودهای آلمان و ترکیه موقتاً در دسترس نبودند؛ لطفاً پایش سلامت مخزن IP تمیز را اجرا کنید"
                        } else {
                            "DE/TR nodes unreachable; probe the Clean IP pool and retry"
                        }
                    )
                }
            } finally {
                _isRacingConnect.value = false
            }
        }
    }

    fun applyCleanIpToActiveConfig(
        context: Context,
        cleanHost: String,
        connectImmediately: Boolean = false
    ) {
        val clean = cleanHost.trim()
        if (clean.isBlank()) return
        com.example.vpn.engine.DynamicIpDnsRotator.promoteCleanIp(clean, pinForTunnel = true)
        val activeTarget = _selectedVpnConfig.value
            ?: configs.value.firstOrNull { com.example.data.model.DefaultVpnConfigs.isDeOrTrNode(it) }
            ?: configs.value.firstOrNull()
            ?: localConfigs.value.firstOrNull()?.toDomainModel()
        if (activeTarget != null) {
            _selectedVpnConfig.value = activeTarget
        }
        if (connectImmediately && activeTarget != null) {
            VpnController.startVpn(
                context = context,
                config = activeTarget,
                antiFilterMode = true,
                cleanIpOverride = clean
            )
            _uiMessage.value = UiMessage.Success(
                if (isPersianLanguage.value) {
                    "اتصال با Clean IP «$clean» روی ${activeTarget.name} آغاز شد"
                } else {
                    "Connecting ${activeTarget.name} via Clean IP $clean"
                }
            )
        } else {
            _uiMessage.value = UiMessage.Success(
                if (isPersianLanguage.value) {
                    "آدرس تمیز $clean روی کانفیگ فعال تنظیم شد (SNI اصلی حفظ می‌شود)"
                } else {
                    "Clean host $clean pinned for active tunnel (SNI preserved)"
                }
            )
        }
    }

    fun userProbeCleanIpPool(filterType: CleanIpAddressType? = null, limit: Int = 50) {
        if (_isProbingCleanIps.value) return
        viewModelScope.launch {
            _isProbingCleanIps.value = true
            try {
                val allItems = localConfigRepository.getCleanIpPoolOnce()
                val targets = allItems
                    .filter { filterType == null || it.addressType == filterType.name }
                    .sortedBy { it.lastCheckedAtMs }
                    .take(limit)

                if (targets.isEmpty()) {
                    _uiMessage.value = UiMessage.Error(
                        if (isPersianLanguage.value) "مخزن IPهای تمیز خالی است" else "Clean IP pool is empty"
                    )
                    return@launch
                }

                val semaphore = Semaphore(10)
                val probeResults = coroutineScope {
                    targets.map { entity ->
                        async(Dispatchers.IO) {
                            semaphore.withPermit {
                                val res = RealPingEngine.ping(entity.host, entity.defaultPort, 2200)
                                localConfigRepository.recordCleanIpHealth(
                                    host = entity.host,
                                    isReachable = res.isReachable,
                                    latencyMs = res.latencyMs
                                )
                                entity.host to res
                            }
                        }
                    }.awaitAll()
                }

                val healthyCount = probeResults.count { it.second.isReachable }
                val healthyHosts = probeResults
                    .filter { it.second.isReachable }
                    .sortedBy { it.second.latencyMs }
                    .map { it.first }
                if (healthyHosts.isNotEmpty()) {
                    com.example.vpn.engine.DynamicIpDnsRotator.seedCandidateIps(healthyHosts)
                }

                _uiMessage.value = UiMessage.Success(
                    if (isPersianLanguage.value) {
                        "تست ${probeResults.size} آدرس از مخزن انجام شد: $healthyCount آی‌پی سالم و پرسرعت"
                    } else {
                        "Probed ${probeResults.size} pool entries: $healthyCount healthy on TCP/443"
                    }
                )
            } finally {
                _isProbingCleanIps.value = false
            }
        }
    }

    fun importBulkCleanIps(rawText: String) {
        val text = rawText.trim()
        if (text.isBlank()) return
        viewModelScope.launch {
            val inserted = localConfigRepository.importBulkCleanIps(
                rawText = text,
                sourceTag = if (isCurrentUserAdmin()) "ADMIN_BULK" else "USER_BULK"
            )
            _uiMessage.value = if (inserted > 0) {
                UiMessage.Success(
                    if (isPersianLanguage.value) {
                        "$inserted آدرس جدید (شامل IP / رنج CIDR / دامنه) به مخزن IP تمیز اضافه شد"
                    } else {
                        "Added $inserted clean hosts to pool"
                    }
                )
            } else {
                UiMessage.Error(
                    if (isPersianLanguage.value) {
                        "آدرس جدیدی اضافه نشد (فرمت نامعتبر یا تکراری)"
                    } else {
                        "No new valid hosts inserted"
                    }
                )
            }
        }
    }

    fun addIpOnlyEndpoints(rawText: String) {
        val text = rawText.trim()
        if (text.isBlank()) return
        viewModelScope.launch {
            _subscriptionBusy.value = true
            try {
                val (cleanAdded, endpointsAdded) = localConfigRepository.addIpOnlyEndpoints(
                    rawText = text,
                    sourceTag = if (isCurrentUserAdmin()) "ADMIN_IP_ONLY" else "USER_IP_ONLY"
                )
                if (cleanAdded > 0 || endpointsAdded > 0) {
                    _uiMessage.value = UiMessage.Success(
                        if (isPersianLanguage.value) {
                            "$cleanAdded آی‌پی تمیز و $endpointsAdded نود اختصاصی بدون نیاز به پورت ثبت شد"
                        } else {
                            "Registered $cleanAdded clean IPs and $endpointsAdded dedicated endpoints"
                        }
                    )
                } else {
                    _uiMessage.value = UiMessage.Error(
                        if (isPersianLanguage.value) {
                            "آدرس IP معتبری یافت نشد (فرمت مجاز: IPv4، IPv6 یا رنج /24)"
                        } else {
                            "No valid IP addresses found"
                        }
                    )
                }
            } finally {
                _subscriptionBusy.value = false
            }
        }
    }

    fun handleActivationDeepLink(rawInput: String) {
        val payload = com.example.data.subscription.SubscriptionActivationLinkEngine.parseActivationUrl(rawInput)
        if (payload == null) {
            _uiMessage.value = UiMessage.Error(
                if (isPersianLanguage.value) {
                    "لینک یا کد فعال‌سازی نامعتبر است (فرمت معتبر: tmsvpn://activate?...)"
                } else {
                    "Invalid activation link (expected tmsvpn://activate?...)"
                }
            )
            return
        }

        val plan = SubscriptionPlanId.fromId(payload.planId)
        val actorUid = currentActorUid()
        val actorEmail = currentActorEmail()

        // Admin opening an activation QR/Link directly activates for the specified target user (or self)
        if (isCurrentUserAdmin()) {
            val targetUid = payload.targetUserId?.takeIf { it.isNotBlank() } ?: actorUid
            val targetUser = registeredUsers.value.firstOrNull { it.userId == targetUid }
            adminActivateUserSubscription(
                targetUserId = targetUid,
                targetUserEmail = targetUser?.email ?: actorEmail,
                plan = plan,
                paymentRequestId = null
            )
            return
        }

        // User opening/pasting an activation link: never self-activate client-side; submit verification request to server
        val matchesUser = payload.targetUserId.isNullOrBlank() || payload.targetUserId == actorUid
        if (!matchesUser) {
            _uiMessage.value = UiMessage.Error(
                if (isPersianLanguage.value) {
                    "این لینک فعال‌سازی برای شناسه کاربری دیگری صادر شده است"
                } else {
                    "This activation link belongs to a different user account"
                }
            )
            return
        }

        submitSubscriptionRequest(
            plan = plan,
            actionType = "LINK_VERIFICATION",
            referenceCode = payload.referenceCode ?: "LINK-${System.currentTimeMillis() % 10000}",
            note = "Submitted via tmsvpn://activate deep link for server confirmation"
        )
    }

    fun runIspAutoTune(context: Context) {
        if (_isTuningIsp.value) return
        viewModelScope.launch {
            _isTuningIsp.value = true
            try {
                val roomIpv4Candidates = localConfigRepository.getCleanIpPoolOnce()
                    .filter {
                        it.isAvailable &&
                            it.addressType == CleanIpAddressType.IPV4.name &&
                            it.healthStatus != CleanIpHealthStatus.QUARANTINED.name
                    }
                    .take(25)
                    .map { it.host }
                val mergedCandidates = (IspAutoTuner.CANDIDATE_CLEAN_IPS + roomIpv4Candidates).distinct()
                val report = IspAutoTuner.benchmarkAndTuneForCurrentIsp(context, candidateIps = mergedCandidates)
                if (report.bestCleanIp.isNotBlank() && report.bestCleanIpLatencyMs > 0) {
                    localConfigRepository.recordCleanIpHealth(
                        host = report.bestCleanIp,
                        isReachable = true,
                        latencyMs = report.bestCleanIpLatencyMs.toInt()
                    )
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
        val uid = safeFirebaseUser()?.uid ?: "local_user"
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
                    if (safeFirebaseUser() != null) {
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
        val user = safeFirebaseUser()
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
        val uid = safeFirebaseUser()?.uid ?: "local_user"
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
                    if (safeFirebaseUser() != null) {
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

            if (safeFirebaseUser() != null) {
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
        val uid = safeFirebaseUser()?.uid ?: "local_user"
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
                if (safeFirebaseUser() != null) {
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

                if (safeFirebaseUser() != null) {
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
        val uid = safeFirebaseUser()?.uid ?: "local_user"
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
                if (safeFirebaseUser() != null) {
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
                                    if (safeFirebaseUser() != null) {
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
                if (safeFirebaseUser() != null) {
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

    // --- BULK CONFIG SELECTION & SAFE TOMBSTONE DELETION ---

    fun toggleConfigSelection(configId: String) {
        if (configId.isBlank()) return
        _selectedConfigIds.update { current ->
            if (configId in current) current - configId else current + configId
        }
    }

    fun selectAllConfigs(visibleIds: List<String>) {
        _selectedConfigIds.value = visibleIds.filter { it.isNotBlank() }.toSet()
    }

    fun deselectAllConfigs() {
        _selectedConfigIds.value = emptySet()
    }

    private fun buildDeletionContext(): ConfigDeletionContext {
        val activeState = vpnConnectionState.value
        val activeTunnelId = if (
            activeState == VpnConnectionState.CONNECTED ||
            activeState == VpnConnectionState.CONNECTING
        ) {
            _selectedVpnConfig.value?.id ?: VpnController.activeConfig?.id
        } else {
            null
        }
        return ConfigDeletionContext(
            actorUserId = currentActorUid(),
            isAdminActor = isCurrentUserAdmin(),
            hasAdminManageConfigsPermission = currentRole.value.hasPermission(AdminPermission.MANAGE_CONFIGS),
            activeTunnelConfigId = activeTunnelId
        )
    }

    fun previewBulkDeletion(targetIds: Set<String> = _selectedConfigIds.value): BulkDeletionPlan {
        return ConfigDeletionPolicy.planBulkDeletion(
            allConfigs = configs.value,
            targetIds = targetIds,
            context = buildDeletionContext()
        )
    }

    fun deleteConfig(configId: String) {
        val target = configs.value.firstOrNull { it.id == configId }
        if (target == null) {
            // Fallback offline/legacy removal by ID
            viewModelScope.launch {
                val uid = currentActorUid()
                _tombstonedConfigIds.update { it + configId }
                localConfigRepository.deleteWithTombstone(configId, uid, pendingRemoteDelete = true)
                if (safeFirebaseUser() != null) {
                    try {
                        repository.deleteConfig(configId)
                        localConfigRepository.markRemoteDeletesSynced(listOf(configId))
                    } catch (_: Exception) {}
                }
            }
            return
        }
        executeBulkDeletionPlan(setOf(configId))
    }

    fun deleteSelectedConfigs(targetIds: Set<String> = _selectedConfigIds.value) {
        if (targetIds.isEmpty()) return
        executeBulkDeletionPlan(targetIds)
    }

    fun deleteAllPermittedConfigs(visibleConfigs: List<VpnConfig> = configs.value) {
        val ids = visibleConfigs.map { it.id }.filter { it.isNotBlank() }.toSet()
        if (ids.isEmpty()) return
        executeBulkDeletionPlan(ids)
    }

    private fun executeBulkDeletionPlan(targetIds: Set<String>) {
        val plan = previewBulkDeletion(targetIds)
        val deletableIds = plan.deletableIds
        if (deletableIds.isEmpty()) {
            val msg = if (plan.blockedSystemConfigs.isNotEmpty()) {
                if (isPersianLanguage.value) {
                    "کانفیگ‌های سیستمی محافظت‌شده هستند و قابل حذف نیستند (${plan.blockedSystemConfigs.size} مورد)"
                } else {
                    "System-managed configurations are protected (${plan.blockedSystemConfigs.size})"
                }
            } else if (plan.blockedSubscriptionConfigs.isNotEmpty()) {
                if (isPersianLanguage.value) {
                    "کانفیگ‌های اختصاصی اشتراک فقط توسط مدیر یا چرخش سرور قابل مدیریت هستند"
                } else {
                    "Subscription-assigned endpoints require administrator or rotation action"
                }
            } else {
                if (isPersianLanguage.value) "شما مجوز حذف موارد انتخابی را ندارید" else "No permitted configurations to delete"
            }
            _uiMessage.value = UiMessage.Error(msg)
            return
        }

        val uid = currentActorUid()
        val deletingSelectedTunnel = _selectedVpnConfig.value?.id in deletableIds

        viewModelScope.launch {
            try {
                // Immediate UI + Tombstone persistence (works identically online & offline)
                _tombstonedConfigIds.update { it + deletableIds }
                _selectedConfigIds.update { it - deletableIds.toSet() }

                if (deletingSelectedTunnel) {
                    if (vpnConnectionState.value == VpnConnectionState.CONNECTED ||
                        vpnConnectionState.value == VpnConnectionState.CONNECTING
                    ) {
                        VpnController.stopVpn(getApplication())
                    }
                    _selectedVpnConfig.value = configs.value.firstOrNull { it.id !in deletableIds }
                }

                localConfigRepository.deleteBatchWithTombstones(
                    configIds = deletableIds,
                    userId = uid,
                    pendingRemoteDelete = true
                )

                var remoteSynced = false
                if (safeFirebaseUser() != null) {
                    val remoteResult = repository.deleteConfigsBatch(deletableIds)
                    if (remoteResult.isSuccess) {
                        localConfigRepository.markRemoteDeletesSynced(deletableIds)
                        remoteSynced = true
                    }
                    try {
                        repository.addAuditLog(
                            AuthAuditLog(
                                userId = safeFirebaseUser()?.uid ?: uid,
                                action = if (deletableIds.size == 1) "CONFIG_DELETED" else "BULK_CONFIG_DELETED",
                                role = currentRole.value.id,
                                module = "ConfigurationManagement",
                                status = "SUCCESS",
                                ipOrDevice = getDeviceInfo(),
                                details = "حذف ${deletableIds.size} کانفیگ (محافظت‌شده رد شده: ${plan.blockedCount}، همگام‌سازی ابری: $remoteSynced)"
                            )
                        )
                    } catch (_: Exception) {}
                }

                val skippedSuffix = if (plan.blockedCount > 0) {
                    if (isPersianLanguage.value) " • ${plan.blockedCount} مورد سیستمی/اشتراکی محافظت شد"
                    else " • ${plan.blockedCount} protected kept"
                } else ""

                _uiMessage.value = UiMessage.Success(
                    if (isPersianLanguage.value) {
                        "${deletableIds.size} کانفیگ حذف شد$skippedSuffix"
                    } else {
                        "Deleted ${deletableIds.size} configuration(s)$skippedSuffix"
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiMessage.value = UiMessage.Error("خطا در حذف کانفیگ‌ها: ${e.localizedMessage}")
            }
        }
    }

    fun toggleFavorite(config: VpnConfig) {
        viewModelScope.launch {
            try {
                localConfigRepository.updateFavorite(config.id, !config.isFavorite)
                if (safeFirebaseUser() != null) {
                    repository.updateConfig(config.copy(isFavorite = !config.isFavorite))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed toggling favorite", e)
            }
        }
    }

    // --- USER SUBSCRIPTION REQUESTS & ALLOCATED CONNECTIONS ---

    fun submitSubscriptionRequest(
        plan: SubscriptionPlanId,
        actionType: String,
        referenceCode: String,
        note: String
    ) {
        val uid = currentActorUid()
        val email = currentActorEmail()
        if (uid == "local_user" && email.isBlank()) {
            _uiMessage.value = UiMessage.Error(
                if (isPersianLanguage.value) {
                    "لطفاً ابتدا با ایمیل و رمز عبور وارد شوید."
                } else {
                    "Please sign in with your email and password first."
                }
            )
            return
        }

        viewModelScope.launch {
            _subscriptionBusy.value = true
            try {
                val nowMs = currentTrustedNowMs()
                val subId = userSubscription.value?.subscriptionId ?: "sub_$uid"
                val payId = "pay_${uid}_$nowMs"
                val record = PaymentRequestRecord(
                    paymentId = payId,
                    userId = uid,
                    userEmail = email,
                    subscriptionId = subId,
                    planId = plan.id,
                    actionType = actionType,
                    provider = "MANUAL_ADMIN",
                    referenceCode = referenceCode.trim(),
                    status = "PENDING_PAYMENT",
                    note = note.trim(),
                    createdAt = SubscriptionLifecycleEngine.timestampOf(nowMs)
                )
                localConfigRepository.saveLocalPaymentRequest(record)
                if (userSubscription.value == null) {
                    val pendingSub = UserSubscription(
                        subscriptionId = subId,
                        userId = uid,
                        userEmail = email,
                        planId = plan.id,
                        planDurationDays = plan.durationDays,
                        endpointAllocationLimit = plan.dailyAllocationLimit,
                        status = SubscriptionStatus.PENDING_PAYMENT.name,
                        paymentStatus = PaymentStatus.UNPAID.name,
                        createdAt = SubscriptionLifecycleEngine.timestampOf(nowMs),
                        updatedAt = SubscriptionLifecycleEngine.timestampOf(nowMs)
                    )
                    localConfigRepository.cacheSubscription(pendingSub, nowMs)
                    localConfigRepository.syncRegisteredUserSubscriptionStatus(uid, "pending")
                }

                if (safeFirebaseUser() != null) {
                    subscriptionRepository.submitPaymentRequest(
                        plan = plan,
                        actionType = actionType,
                        subscriptionId = subId,
                        referenceCode = referenceCode,
                        note = note,
                        userEmail = email
                    )
                }

                _uiMessage.value = UiMessage.Success(
                    if (isPersianLanguage.value) {
                        "درخواست اشتراک «${plan.titleFa}» ثبت شد و در انتظار تایید مدیریت است."
                    } else {
                        "Subscription request '${plan.titleEn}' submitted as PENDING_PAYMENT."
                    }
                )
            } catch (e: Exception) {
                _uiMessage.value = UiMessage.Error(e.localizedMessage ?: "خطا در ثبت درخواست")
            } finally {
                _subscriptionBusy.value = false
            }
        }
    }

    fun connectAllocatedEndpoint(context: Context, record: AllocatedEndpointRecord) {
        val cfg = record.toVpnConfig()
        selectConfigForVpn(cfg)
        connectVpn(context, cfg, antiFilterMode = true)
    }

    // --- ADMIN SUBSCRIPTION, INVENTORY & DAILY ROTATION OPERATIONS ---

    fun toggleRotationUserSelection(userId: String) {
        _selectedRotationUserIds.update { current ->
            if (userId in current) current - userId else current + userId
        }
    }

    fun selectAllRotationUsers(userIds: List<String>) {
        _selectedRotationUserIds.value = userIds.toSet()
    }

    fun clearRotationUserSelection() {
        _selectedRotationUserIds.value = emptySet()
    }

    fun adminActivateUserSubscription(
        targetUserId: String,
        targetUserEmail: String,
        plan: SubscriptionPlanId,
        paymentRequestId: String? = null
    ) {
        if (!checkPermission(AdminPermission.MANAGE_USERS, "فعال‌سازی اشتراک کاربر")) return
        viewModelScope.launch {
            _subscriptionBusy.value = true
            try {
                val now = if (safeFirebaseUser() != null) {
                    subscriptionRepository.fetchTrustedServerEpochMs(currentTrustedNowMs())
                } else {
                    currentTrustedNowMs()
                }
                updateTrustedServerAnchor(now)
                val actorUid = currentActorUid()
                val cleanUid = targetUserId.trim()
                val existingSub = allSubscriptionsAdmin.value.firstOrNull {
                    it.userId == cleanUid ||
                        (targetUserEmail.isNotBlank() && it.userEmail.equals(targetUserEmail.trim(), ignoreCase = true))
                }
                val resolvedEmail = targetUserEmail.trim().ifBlank { existingSub?.userEmail.orEmpty() }

                val (activated, event) = SubscriptionLifecycleEngine.activateAfterPaymentConfirmation(
                    subscriptionId = existingSub?.subscriptionId ?: "sub_$cleanUid",
                    userId = cleanUid,
                    userEmail = resolvedEmail,
                    plan = plan,
                    actorId = actorUid,
                    trustedServerNowMs = now,
                    existingSubscription = existingSub
                )

                localConfigRepository.upsertRegisteredUser(
                    RegisteredUserEntity(
                        userId = cleanUid,
                        displayName = resolvedEmail.substringBefore("@").ifBlank { cleanUid },
                        email = resolvedEmail.ifBlank { "$cleanUid@tmsvpn.local" },
                        role = "USER",
                        maxDevices = 10,
                        dataLimitBytes = 500.0 * 1024 * 1024 * 1024,
                        subscriptionStatus = "active",
                        updatedAtMs = now
                    )
                )
                localConfigRepository.cacheSubscription(activated, now)
                localConfigRepository.cacheSubscriptionHistory(listOf(event))
                if (!paymentRequestId.isNullOrBlank()) {
                    localConfigRepository.markLocalPaymentStatus(
                        paymentId = paymentRequestId,
                        status = "CONFIRMED",
                        verifiedBy = actorUid,
                        note = "Verified & activated ${plan.titleEn}",
                        verifiedAtMs = now
                    )
                }

                val localBatch = localConfigRepository.executeLocalAllocationOrRotation(
                    targetSubscriptions = listOf(activated),
                    activeConnectedEndpointByUser = emptyMap(),
                    actorUid = actorUid,
                    triggerMode = "MANUAL_SINGLE",
                    nowMs = now,
                    forceManual = true
                )

                if (safeFirebaseUser() != null) {
                    subscriptionRepository.adminActivateSubscription(
                        targetUserId = cleanUid,
                        targetUserEmail = resolvedEmail,
                        plan = plan,
                        paymentRequestId = paymentRequestId,
                        trustedNowMs = now
                    ).onSuccess { remoteActivated ->
                        subscriptionRepository.executeRotationTransaction(
                            targetSubscriptions = listOf(remoteActivated),
                            activeEndpointByUser = emptyMap(),
                            triggerMode = "MANUAL_SINGLE",
                            trustedNowMs = now,
                            forceManual = true
                        )
                    }
                }

                val allocCount = localBatch.allocationsByUser[cleanUid]?.size ?: 0
                val shortageCount = localBatch.shortageAlerts.size
                _uiMessage.value = UiMessage.Success(
                    if (isPersianLanguage.value) {
                        "اشتراک ${plan.titleFa} فعال و $allocCount IP اختصاصی تخصیص یافت" +
                            if (shortageCount > 0) " (کمبود ظرفیت گزارش شد)" else ""
                    } else {
                        "Activated ${plan.titleEn}; allocated $allocCount endpoints"
                    }
                )
            } catch (e: Exception) {
                _uiMessage.value = UiMessage.Error(e.localizedMessage ?: "خطا در فعال‌سازی اشتراک")
            } finally {
                _subscriptionBusy.value = false
            }
        }
    }

    fun adminRejectPayment(paymentId: String, reason: String) {
        if (!checkPermission(AdminPermission.MANAGE_USERS, "رد درخواست پرداخت")) return
        viewModelScope.launch {
            _subscriptionBusy.value = true
            try {
                val actorUid = currentActorUid()
                val note = reason.ifBlank { "Rejected by administrator" }
                localConfigRepository.markLocalPaymentStatus(
                    paymentId = paymentId,
                    status = "REJECTED",
                    verifiedBy = actorUid,
                    note = note
                )
                if (safeFirebaseUser() != null) {
                    subscriptionRepository.adminRejectPaymentRequest(paymentId, note)
                }
                _uiMessage.value = UiMessage.Success(
                    if (isPersianLanguage.value) "درخواست پرداخت رد شد" else "Payment request rejected"
                )
            } catch (e: Exception) {
                _uiMessage.value = UiMessage.Error(e.localizedMessage ?: "خطا")
            } finally {
                _subscriptionBusy.value = false
            }
        }
    }

    fun adminRenewUserSubscription(subscription: UserSubscription, plan: SubscriptionPlanId = subscription.planEnum()) {
        if (!checkPermission(AdminPermission.MANAGE_USERS, "تمدید اشتراک")) return
        viewModelScope.launch {
            _subscriptionBusy.value = true
            try {
                val now = if (safeFirebaseUser() != null) {
                    subscriptionRepository.fetchTrustedServerEpochMs(currentTrustedNowMs())
                } else {
                    currentTrustedNowMs()
                }
                updateTrustedServerAnchor(now)
                val actorUid = currentActorUid()
                val (renewed, event) = SubscriptionLifecycleEngine.renew(
                    existing = subscription,
                    plan = plan,
                    actorId = actorUid,
                    trustedServerNowMs = now
                )
                localConfigRepository.cacheSubscription(renewed, now)
                localConfigRepository.cacheSubscriptionHistory(listOf(event))
                localConfigRepository.syncRegisteredUserSubscriptionStatus(renewed.userId, "active")
                localConfigRepository.executeLocalAllocationOrRotation(
                    targetSubscriptions = listOf(renewed),
                    activeConnectedEndpointByUser = emptyMap(),
                    actorUid = actorUid,
                    triggerMode = "MANUAL_SINGLE",
                    nowMs = now,
                    forceManual = false
                )
                if (safeFirebaseUser() != null) {
                    subscriptionRepository.adminRenewSubscription(subscription, plan, now)
                }
                _uiMessage.value = UiMessage.Success(
                    if (isPersianLanguage.value) "اشتراک به مدت ${plan.durationDays} روز تمدید شد" else "Subscription renewed (+${plan.durationDays}d)"
                )
            } catch (e: Exception) {
                _uiMessage.value = UiMessage.Error(e.localizedMessage ?: "خطا در تمدید")
            } finally {
                _subscriptionBusy.value = false
            }
        }
    }

    fun adminChangeUserPlan(subscription: UserSubscription, newPlan: SubscriptionPlanId) {
        if (!checkPermission(AdminPermission.MANAGE_USERS, "تغییر پلن اشتراک")) return
        viewModelScope.launch {
            _subscriptionBusy.value = true
            try {
                val now = if (safeFirebaseUser() != null) {
                    subscriptionRepository.fetchTrustedServerEpochMs(currentTrustedNowMs())
                } else {
                    currentTrustedNowMs()
                }
                updateTrustedServerAnchor(now)
                val actorUid = currentActorUid()
                val (updated, event) = SubscriptionLifecycleEngine.changePlan(
                    existing = subscription,
                    newPlan = newPlan,
                    actorId = actorUid,
                    trustedServerNowMs = now
                )
                localConfigRepository.cacheSubscription(updated, now)
                localConfigRepository.cacheSubscriptionHistory(listOf(event))
                localConfigRepository.executeLocalAllocationOrRotation(
                    targetSubscriptions = listOf(updated),
                    activeConnectedEndpointByUser = mapOf(updated.userId to _selectedVpnConfig.value?.endpointId),
                    actorUid = actorUid,
                    triggerMode = "MANUAL_SINGLE",
                    nowMs = now,
                    forceManual = true
                )
                if (safeFirebaseUser() != null) {
                    subscriptionRepository.adminChangePlan(subscription, newPlan, now).onSuccess { remoteUpdated ->
                        subscriptionRepository.executeRotationTransaction(
                            targetSubscriptions = listOf(remoteUpdated),
                            activeEndpointByUser = mapOf(remoteUpdated.userId to _selectedVpnConfig.value?.endpointId),
                            triggerMode = "MANUAL_SINGLE",
                            trustedNowMs = now,
                            forceManual = true
                        )
                    }
                }
                _uiMessage.value = UiMessage.Success(
                    if (isPersianLanguage.value) "پلن به ${newPlan.titleFa} تغییر یافت و سهمیه IP تنظیم شد" else "Plan changed to ${newPlan.titleEn}"
                )
            } catch (e: Exception) {
                _uiMessage.value = UiMessage.Error(e.localizedMessage ?: "خطا در تغییر پلن")
            } finally {
                _subscriptionBusy.value = false
            }
        }
    }

    fun adminUpdateSubscriptionStatus(subscription: UserSubscription, newStatus: SubscriptionStatus, reason: String) {
        if (!checkPermission(AdminPermission.MANAGE_USERS, "تغییر وضعیت اشتراک")) return
        viewModelScope.launch {
            _subscriptionBusy.value = true
            try {
                val now = if (safeFirebaseUser() != null) {
                    subscriptionRepository.fetchTrustedServerEpochMs(currentTrustedNowMs())
                } else {
                    currentTrustedNowMs()
                }
                updateTrustedServerAnchor(now)
                val actorUid = currentActorUid()
                val (updated, event) = SubscriptionLifecycleEngine.updateStatus(
                    existing = subscription,
                    newStatus = newStatus,
                    actorId = actorUid,
                    reason = reason,
                    trustedServerNowMs = now
                )
                localConfigRepository.cacheSubscription(updated, now)
                localConfigRepository.cacheSubscriptionHistory(listOf(event))
                localConfigRepository.syncRegisteredUserSubscriptionStatus(updated.userId, newStatus.name.lowercase())
                localConfigRepository.executeLocalAllocationOrRotation(
                    targetSubscriptions = listOf(updated),
                    activeConnectedEndpointByUser = emptyMap(),
                    actorUid = actorUid,
                    triggerMode = if (newStatus == SubscriptionStatus.ACTIVE) "MANUAL_SINGLE" else "STATUS_REVOCATION",
                    nowMs = now,
                    forceManual = true
                )
                if (safeFirebaseUser() != null) {
                    subscriptionRepository.adminSetSubscriptionStatus(subscription, newStatus, reason, now).onSuccess { remoteUpdated ->
                        if (newStatus != SubscriptionStatus.ACTIVE) {
                            subscriptionRepository.executeRotationTransaction(
                                targetSubscriptions = listOf(remoteUpdated),
                                activeEndpointByUser = emptyMap(),
                                triggerMode = "STATUS_REVOCATION",
                                trustedNowMs = now,
                                forceManual = true
                            )
                        }
                    }
                }
                _uiMessage.value = UiMessage.Success(
                    if (isPersianLanguage.value) "وضعیت اشتراک به ${newStatus.titleFa} تغییر یافت" else "Status updated to ${newStatus.name}"
                )
            } catch (e: Exception) {
                _uiMessage.value = UiMessage.Error(e.localizedMessage ?: "خطا")
            } finally {
                _subscriptionBusy.value = false
            }
        }
    }

    fun adminUpsertManagedEndpoint(endpoint: ManagedEndpoint) {
        if (!checkPermission(AdminPermission.MANAGE_SERVERS, "مدیریت استخر IP اختصاصی")) return
        viewModelScope.launch {
            _subscriptionBusy.value = true
            try {
                localConfigRepository.upsertLocalManagedEndpoint(endpoint)
                if (safeFirebaseUser() != null) {
                    subscriptionRepository.adminUpsertEndpoint(endpoint)
                }
                _uiMessage.value = UiMessage.Success(
                    if (isPersianLanguage.value) "نود ${endpoint.name} در مخزن مرکزی ثبت شد" else "Endpoint saved to central pool"
                )
            } catch (e: Exception) {
                _uiMessage.value = UiMessage.Error(e.localizedMessage ?: "خطا در ثبت نود")
            } finally {
                _subscriptionBusy.value = false
            }
        }
    }

    fun adminImportInventoryFromCurrentNodes() {
        if (!checkPermission(AdminPermission.MANAGE_SERVERS, "واردسازی نودهای سالم به استخر اختصاصی")) return
        val sourceServers = servers.value
        val sourceConfigs = configs.value
        if (sourceServers.isEmpty() && sourceConfigs.isEmpty()) {
            _uiMessage.value = UiMessage.Error(
                if (isPersianLanguage.value) "هیچ نود یا کانفیگ فعالی برای همگام‌سازی یافت نشد" else "No existing nodes to import"
            )
            return
        }
        viewModelScope.launch {
            _subscriptionBusy.value = true
            try {
                var imported = 0
                sourceServers.filter { it.status == "online" }.forEachIndexed { idx, srv ->
                    val ep = ManagedEndpoint(
                        endpointId = "inv_srv_${srv.id.ifBlank { idx.toString() }}",
                        name = srv.name,
                        country = srv.country,
                        countryCode = srv.countryCode,
                        host = srv.host,
                        port = srv.port,
                        protocol = when {
                            srv.protocol.contains("vmess", ignoreCase = true) -> "vmess"
                            srv.protocol.contains("trojan", ignoreCase = true) -> "trojan"
                            srv.protocol.contains("shadow", ignoreCase = true) -> "shadowsocks"
                            else -> "vless"
                        },
                        transport = "ws",
                        security = "tls",
                        sni = srv.host,
                        enabled = true,
                        healthy = true,
                        priorityTier = if (srv.stabilityScore >= 90) 3 else 2,
                        latencyMs = srv.latencyMs,
                        stabilityScore = srv.stabilityScore
                    )
                    localConfigRepository.upsertLocalManagedEndpoint(ep)
                    if (safeFirebaseUser() != null) {
                        subscriptionRepository.adminUpsertEndpoint(ep)
                    }
                    imported++
                }
                if (imported == 0) {
                    sourceConfigs.forEachIndexed { idx, cfg ->
                        val ep = ManagedEndpoint(
                            endpointId = "inv_cfg_${cfg.id.take(40).ifBlank { idx.toString() }}",
                            name = cfg.name,
                            country = "Global",
                            countryCode = "GL",
                            host = cfg.server,
                            port = cfg.port,
                            protocol = cfg.protocol.lowercase(),
                            transport = cfg.transport,
                            security = cfg.security,
                            sni = cfg.sni,
                            pathOrServiceName = cfg.pathOrServiceName,
                            enabled = true,
                            healthy = cfg.status != "offline",
                            priorityTier = if (cfg.pingMs in 1..150) 3 else 2,
                            latencyMs = cfg.pingMs,
                            stabilityScore = if (cfg.status == "online") 95 else 80
                        )
                        localConfigRepository.upsertLocalManagedEndpoint(ep)
                        if (safeFirebaseUser() != null) {
                            subscriptionRepository.adminUpsertEndpoint(ep)
                        }
                        imported++
                    }
                }
                _uiMessage.value = UiMessage.Success(
                    if (isPersianLanguage.value) "$imported نود واقعی به استخر IP اختصاصی اضافه شد (بدون افشای اطلاعات خصوصی)"
                    else "Synced $imported endpoints into central pool"
                )
            } finally {
                _subscriptionBusy.value = false
            }
        }
    }

    fun adminProbeCleanIpPool(filterType: CleanIpAddressType? = null, limit: Int = 50) {
        if (!checkPermission(AdminPermission.MANAGE_SERVERS, "پایش سلامت مخزن IPهای تمیز")) return
        userProbeCleanIpPool(filterType = filterType, limit = limit)
    }

    fun adminToggleCleanIpAvailability(entity: CleanIpEntity, isAvailable: Boolean) {
        if (!checkPermission(AdminPermission.MANAGE_SERVERS, "تغییر دسترسی‌پذیری IP تمیز")) return
        viewModelScope.launch {
            localConfigRepository.updateCleanIpAvailability(
                host = entity.host,
                isAvailable = isAvailable
            )
        }
    }

    fun adminPromoteHealthyCleanIpsToInventory(protocol: String = "vless") {
        if (!checkPermission(AdminPermission.MANAGE_SERVERS, "انتقال IPهای سالم به استخر اشتراک")) return
        viewModelScope.launch {
            _subscriptionBusy.value = true
            try {
                val existingHosts = endpointInventoryAdmin.value.map { it.host.lowercase() }.toSet()
                val healthyPool = localConfigRepository.getCleanIpPoolOnce()
                    .filter {
                        it.isAvailable &&
                            it.healthStatus == CleanIpHealthStatus.HEALTHY.name &&
                            it.host.lowercase() !in existingHosts
                    }
                    .sortedBy { if (it.latencyMs > 0) it.latencyMs else Int.MAX_VALUE }
                    .take(50)

                if (healthyPool.isEmpty()) {
                    _uiMessage.value = UiMessage.Error(
                        if (isPersianLanguage.value) {
                            "ابتدا «پایش سلامت واقعی» را اجرا کنید تا IPهای سالم تایید شوند (یا همه موارد سالم قبلاً وارد شده‌اند)."
                        } else {
                            "Run live health probe first so verified HEALTHY hosts can be promoted cleanly."
                        }
                    )
                    return@launch
                }

                var added = 0
                healthyPool.forEachIndexed { idx, cleanIp ->
                    val cleanId = "inv_clean_${cleanIp.host.replace(Regex("[^a-zA-Z0-9]"), "_").take(50)}"
                    val ep = ManagedEndpoint(
                        endpointId = cleanId,
                        name = "Clean ${cleanIp.addressTypeEnum().labelEn} #${idx + 1}",
                        country = "Anycast CDN",
                        countryCode = "CD",
                        host = cleanIp.host,
                        port = cleanIp.defaultPort.coerceIn(1, 65535),
                        protocol = protocol.lowercase(),
                        transport = "ws",
                        security = "tls",
                        sni = if (cleanIp.addressType == CleanIpAddressType.DOMAIN.name) cleanIp.host else "",
                        pathOrServiceName = "/ws",
                        enabled = true,
                        healthy = true,
                        priorityTier = if (cleanIp.latencyMs in 1..180) 3 else 2,
                        latencyMs = cleanIp.latencyMs,
                        stabilityScore = cleanIp.stabilityScore.coerceIn(60, 100)
                    )
                    localConfigRepository.upsertLocalManagedEndpoint(ep)
                    if (safeFirebaseUser() != null) {
                        subscriptionRepository.adminUpsertEndpoint(ep)
                    }
                    added++
                }
                _uiMessage.value = UiMessage.Success(
                    if (isPersianLanguage.value) {
                        "$added آدرس سالم و تست‌شده به استخر مرکزی IP اختصاصی منتقل شد"
                    } else {
                        "Promoted $added verified healthy endpoints to central pool"
                    }
                )
            } finally {
                _subscriptionBusy.value = false
            }
        }
    }

    fun adminToggleEndpointHealth(endpoint: ManagedEndpoint, enabled: Boolean, healthy: Boolean) {
        if (!checkPermission(AdminPermission.MANAGE_SERVERS, "تغییر وضعیت سلامت نود")) return
        viewModelScope.launch {
            _subscriptionBusy.value = true
            try {
                localConfigRepository.updateLocalManagedEndpointHealth(endpoint.endpointId, enabled, healthy)
                if (safeFirebaseUser() != null) {
                    subscriptionRepository.adminToggleEndpointHealth(endpoint, enabled, healthy)
                }
                _uiMessage.value = UiMessage.Success(
                    if (isPersianLanguage.value) "وضعیت نود ${endpoint.name} به‌روز شد" else "Endpoint health updated"
                )
            } finally {
                _subscriptionBusy.value = false
            }
        }
    }

    fun adminResolveShortageAlert(alertId: String) {
        if (!checkPermission(AdminPermission.MANAGE_SERVERS, "بستن هشدار کمبود ظرفیت")) return
        viewModelScope.launch {
            if (safeFirebaseUser() != null) {
                subscriptionRepository.adminResolveShortageAlert(alertId)
            }
        }
    }

    fun inspectUserAllocationHistory(userId: String?) {
        _inspectedHistoryUserId.value = userId?.takeIf { it.isNotBlank() }
    }

    fun adminReportEndpointFailureAndFailover(endpoint: ManagedEndpoint) {
        if (!checkPermission(AdminPermission.MANAGE_SERVERS, "ثبت خطا و جایگزینی خودکار نود اختصاصی")) return
        val targetUid = endpoint.assignedUserId.ifBlank { currentActorUid() }
        val targetSub = allSubscriptionsAdmin.value.firstOrNull { it.userId == targetUid }
        viewModelScope.launch {
            _subscriptionBusy.value = true
            try {
                val now = if (safeFirebaseUser() != null) {
                    subscriptionRepository.fetchTrustedServerEpochMs(currentTrustedNowMs())
                } else {
                    currentTrustedNowMs()
                }
                updateTrustedServerAnchor(now)
                val activeEp = if (vpnConnectionState.value == VpnConnectionState.CONNECTED) {
                    _selectedVpnConfig.value?.endpointId
                } else null
                localConfigRepository.recordLocalEndpointFailureAndFailover(
                    endpointId = endpoint.endpointId,
                    targetUserId = targetUid,
                    subscription = targetSub,
                    activeConnectedEndpointId = activeEp,
                    actorUid = currentActorUid(),
                    nowMs = now
                )
                if (safeFirebaseUser() != null) {
                    val res = subscriptionRepository.recordEndpointFailureAndFailover(
                        endpointId = endpoint.endpointId,
                        targetUserId = targetUid,
                        subscription = targetSub,
                        activeConnectedEndpointId = activeEp,
                        trustedNowMs = now
                    )
                    res.getOrNull()?.let { outcome ->
                        if (outcome.allocationHistoryEvents.isNotEmpty()) {
                            localConfigRepository.cacheAllocationHistory(outcome.allocationHistoryEvents)
                        }
                    }
                }
                _uiMessage.value = UiMessage.Success(
                    if (isPersianLanguage.value) {
                        "خطای نود ${endpoint.name} ثبت شد و جایگزینی اتمیک انجام شد"
                    } else {
                        "Recorded failure on ${endpoint.name}; atomic failover executed"
                    }
                )
            } finally {
                _subscriptionBusy.value = false
            }
        }
    }

    fun adminTriggerRotationForUser(subscription: UserSubscription, forceManual: Boolean = true) {
        if (!checkPermission(AdminPermission.MANAGE_USERS, "چرخش دستی IP کاربر")) return
        runRotationBatch(
            targets = listOf(subscription),
            triggerMode = "MANUAL_SINGLE",
            forceManual = forceManual
        )
    }

    fun adminTriggerRotationForSelectedUsers(forceManual: Boolean = true) {
        if (!checkPermission(AdminPermission.MANAGE_USERS, "چرخش گروهی IP کاربران انتخابی")) return
        val selectedIds = _selectedRotationUserIds.value
        val targets = allSubscriptionsAdmin.value.filter { it.userId in selectedIds || it.subscriptionId in selectedIds }
        if (targets.isEmpty()) {
            _uiMessage.value = UiMessage.Error(
                if (isPersianLanguage.value) "هیچ کاربری برای چرخش انتخاب نشده است" else "No users selected for rotation"
            )
            return
        }
        runRotationBatch(
            targets = targets,
            triggerMode = "MANUAL_SELECTED",
            forceManual = forceManual
        )
    }

    fun adminTriggerDailyRotationAll(forceManual: Boolean = false) {
        if (!checkPermission(AdminPermission.MANAGE_USERS, "اجرای زمان‌بند چرخش روزانه")) return
        val targets = allSubscriptionsAdmin.value.filter { it.statusEnum() == SubscriptionStatus.ACTIVE }
        if (targets.isEmpty()) {
            _uiMessage.value = UiMessage.Error(
                if (isPersianLanguage.value) "اشتراک فعالی برای چرخش وجود ندارد" else "No active subscriptions to rotate"
            )
            return
        }
        runRotationBatch(
            targets = targets,
            triggerMode = if (forceManual) "MANUAL_ALL" else "SCHEDULED_DAILY",
            forceManual = forceManual
        )
    }

    private fun runRotationBatch(
        targets: List<UserSubscription>,
        triggerMode: String,
        forceManual: Boolean
    ) {
        viewModelScope.launch {
            _subscriptionBusy.value = true
            try {
                val now = if (safeFirebaseUser() != null) {
                    subscriptionRepository.fetchTrustedServerEpochMs(currentTrustedNowMs())
                } else {
                    currentTrustedNowMs()
                }
                updateTrustedServerAnchor(now)
                val actorUid = currentActorUid()
                val activeEndpoint = if (vpnConnectionState.value == VpnConnectionState.CONNECTED) {
                    _selectedVpnConfig.value?.endpointId
                } else null
                val activeMap = mapOf(actorUid to activeEndpoint)

                val summary = localConfigRepository.executeLocalAllocationOrRotation(
                    targetSubscriptions = targets,
                    activeConnectedEndpointByUser = activeMap,
                    actorUid = actorUid,
                    triggerMode = triggerMode,
                    nowMs = now,
                    forceManual = forceManual
                )
                if (safeFirebaseUser() != null) {
                    subscriptionRepository.executeRotationTransaction(
                        targetSubscriptions = targets,
                        activeEndpointByUser = activeMap,
                        triggerMode = triggerMode,
                        trustedNowMs = now,
                        forceManual = forceManual
                    )
                }
                val log = summary.auditLog
                _uiMessage.value = UiMessage.Success(
                    if (isPersianLanguage.value) {
                        "چرخش (${log.status}): ${log.rotatedUsersCount}/${log.targetUserCount} کاربر • ${log.replacedEndpointsCount} جایگزینی • ${log.shortageCount} کمبود"
                    } else {
                        "Rotation ${log.status}: ${log.rotatedUsersCount}/${log.targetUserCount} rotated, ${log.shortageCount} shortages"
                    }
                )
            } catch (e: Exception) {
                _uiMessage.value = UiMessage.Error(e.localizedMessage ?: "خطا در چرخش اختصاصی")
            } finally {
                _subscriptionBusy.value = false
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

        val uid = currentActorUid()

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
                    userId = uid,
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
                if (safeFirebaseUser() != null) {
                    repository.addServer(server)
                    repository.addAuditLog(
                        AuthAuditLog(
                            userId = uid,
                            action = "SERVER_ADDED",
                            role = currentRole.value.id,
                            module = "ServerManagement",
                            status = "SUCCESS",
                            ipOrDevice = getDeviceInfo(),
                            details = "نود سرور جدید ${server.name} (${server.countryCode}) در هاست ${server.host}:${server.port} ثبت شد"
                        )
                    )
                }
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
                if (safeFirebaseUser() != null) {
                    repository.updateServerHealth(server.id, result.latencyMs, newStatus, score)
                }
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

        val uid = currentActorUid()
        viewModelScope.launch {
            try {
                if (safeFirebaseUser() != null) {
                    repository.deleteServer(serverId)
                    repository.addAuditLog(
                        AuthAuditLog(
                            userId = uid,
                            action = "SERVER_DELETED",
                            role = currentRole.value.id,
                            module = "ServerManagement",
                            status = "SUCCESS",
                            ipOrDevice = getDeviceInfo(),
                            details = "سرور با شناسه $serverId حذف شد"
                        )
                    )
                }
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

        val uid = currentActorUid()

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
                    userId = uid,
                    name = name.trim(),
                    primaryServerId = primaryServerId,
                    backupServerId = backupServerId,
                    latencyThresholdMs = thresholdMs,
                    autoSwitchEnabled = true
                )
                if (safeFirebaseUser() != null) {
                    repository.addFailoverPolicy(policy)
                    repository.addAuditLog(
                        AuthAuditLog(
                            userId = uid,
                            action = "FAILOVER_POLICY_ADDED",
                            role = currentRole.value.id,
                            module = "IntelligentEngine",
                            status = "SUCCESS",
                            ipOrDevice = getDeviceInfo(),
                            details = "سیاست تغییر مسیر خودکار «${policy.name}» با آستانه ${thresholdMs}ms ثبت شد"
                        )
                    )
                }
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
                if (safeFirebaseUser() != null) {
                    repository.deleteFailoverPolicy(policyId)
                }
                _uiMessage.value = UiMessage.Success("سیاست حذف شد")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiMessage.value = UiMessage.Error("خطا در حذف سیاست")
            }
        }
    }

    // --- USER MANAGEMENT & QUOTAS ---

    fun updateUserQuota(
        deviceCount: Int,
        maxDevices: Int,
        dataLimitBytes: Double,
        targetUserId: String = currentActorUid()
    ) {
        if (!checkPermission(AdminPermission.MANAGE_USERS, "تغییر سهمیه و مدیریت کاربران")) return

        val uid = currentActorUid()
        viewModelScope.launch {
            try {
                localConfigRepository.updateRegisteredUserQuota(targetUserId, maxDevices, dataLimitBytes)
                if (safeFirebaseUser() != null) {
                    repository.updateUserQuota(deviceCount, maxDevices, dataLimitBytes)
                    repository.addAuditLog(
                        AuthAuditLog(
                            userId = uid,
                            action = "USER_QUOTA_UPDATED",
                            role = currentRole.value.id,
                            module = "UserManagement",
                            status = "SUCCESS",
                            ipOrDevice = getDeviceInfo(),
                            details = "سهمیه کاربر $targetUserId ($deviceCount/$maxDevices دستگاه، $dataLimitBytes بایت) به‌روز شد"
                        )
                    )
                }
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
            val adminSnap = buildAdminOperationsContextSnapshot()
            when (
                val res = aiEngine.optimizeRoutingAndFailover(
                    serversSummary = srvSummary,
                    activeLatency = activeLat,
                    userQuery = userProblemPrompt,
                    adminOperationsSnapshot = adminSnap
                )
            ) {
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

    // --- TMS NEXUS AI (USER ASSISTANT & ADMIN OPERATIONS COPILOT + SUPPORT TICKETS) ---

    private fun buildUserSafeContextSnapshot(): String {
        val currentConfigs = configs.value
        val selected = _selectedVpnConfig.value ?: currentConfigs.firstOrNull()
        return NexusContextSanitizer.buildUserSafeContext(
            vpnState = vpnConnectionState.value,
            selectedConfig = selected,
            userConfigCount = currentConfigs.size,
            onlineConfigCount = currentConfigs.count { it.status == "online" },
            sessionStats = vpnSessionStats.value,
            healthReport = healthReport.value,
            leakCheckReport = leakCheckReport.value,
            ispTuningReport = ispTuningReport.value,
            userSubscription = userSubscription.value,
            allocatedEndpointCount = allocatedEndpoints.value.size
        )
    }

    private fun buildAdminOperationsContextSnapshot(): String {
        return NexusContextSanitizer.buildAdminOperationsContext(
            servers = servers.value,
            configs = configs.value,
            endpointInventory = endpointInventoryAdmin.value,
            cleanIpPool = cleanIpPool.value,
            subscriptions = allSubscriptionsAdmin.value,
            shortageAlerts = shortageAlertsAdmin.value,
            rotationLogs = rotationLogsAdmin.value
        )
    }

    fun sendUserNexusMessage(prompt: String) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isBlank() || _userNexusStreaming.value) return

        val userMsgId = "usr_${System.currentTimeMillis()}"
        val botMsgId = "bot_${System.currentTimeMillis() + 1}"
        val historySnapshot = _userNexusMessages.value

        _userNexusMessages.value = historySnapshot + listOf(
            NexusChatMessage(
                id = userMsgId,
                isFromUser = true,
                text = cleanPrompt,
                scope = NexusAiScope.USER_ASSISTANT
            ),
            NexusChatMessage(
                id = botMsgId,
                isFromUser = false,
                text = "",
                scope = NexusAiScope.USER_ASSISTANT,
                isStreaming = true
            )
        )

        viewModelScope.launch {
            _userNexusStreaming.value = true
            try {
                val safeContext = buildUserSafeContextSnapshot()
                aiEngine.streamNexusAssistant(
                    scope = NexusAiScope.USER_ASSISTANT,
                    sanitizedTelemetryContext = safeContext,
                    userMessage = cleanPrompt,
                    recentHistory = historySnapshot,
                    isPersian = isPersianLanguage.value
                ).collect { event ->
                    when (event) {
                        is NexusStreamEvent.Partial -> {
                            _userNexusMessages.update { list ->
                                list.map { if (it.id == botMsgId) it.copy(text = event.accumulatedText, isStreaming = true) else it }
                            }
                        }
                        is NexusStreamEvent.Completed -> {
                            _userNexusMessages.update { list ->
                                list.map { if (it.id == botMsgId) it.copy(text = event.fullText, isStreaming = false, isError = false) else it }
                            }
                        }
                        is NexusStreamEvent.Error -> {
                            _userNexusMessages.update { list ->
                                list.map { if (it.id == botMsgId) it.copy(text = event.message, isStreaming = false, isError = true) else it }
                            }
                        }
                    }
                }
            } finally {
                _userNexusStreaming.value = false
            }
        }
    }

    fun clearUserNexusChat() {
        _userNexusMessages.value = emptyList()
    }

    fun runUserSmartDiagnostics(symptom: String = "") {
        if (_userDiagnosticLoading.value) return
        viewModelScope.launch {
            _userDiagnosticLoading.value = true
            _userDiagnosticError.value = null
            val res = aiEngine.diagnoseConnectionStructured(
                sanitizedUserContext = buildUserSafeContextSnapshot(),
                symptomDescription = symptom
            )
            res.onSuccess { report ->
                _userDiagnosticReport.value = report
            }.onFailure { err ->
                _userDiagnosticError.value = err.localizedMessage ?: "خطا در اجرای عیب‌یابی هوشمند Firebase AI"
            }
            _userDiagnosticLoading.value = false
        }
    }

    fun sendAdminNexusMessage(prompt: String) {
        if (!checkPermission(AdminPermission.RUN_INTELLIGENCE, "استفاده از دستیار عملیات NEXUS AI")) return
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isBlank() || _adminNexusStreaming.value) return

        val userMsgId = "adm_usr_${System.currentTimeMillis()}"
        val botMsgId = "adm_bot_${System.currentTimeMillis() + 1}"
        val historySnapshot = _adminNexusMessages.value

        _adminNexusMessages.value = historySnapshot + listOf(
            NexusChatMessage(
                id = userMsgId,
                isFromUser = true,
                text = cleanPrompt,
                scope = NexusAiScope.ADMIN_COPILOT
            ),
            NexusChatMessage(
                id = botMsgId,
                isFromUser = false,
                text = "",
                scope = NexusAiScope.ADMIN_COPILOT,
                isStreaming = true
            )
        )

        viewModelScope.launch {
            _adminNexusStreaming.value = true
            try {
                val opsContext = buildAdminOperationsContextSnapshot()
                aiEngine.streamNexusAssistant(
                    scope = NexusAiScope.ADMIN_COPILOT,
                    sanitizedTelemetryContext = opsContext,
                    userMessage = cleanPrompt,
                    recentHistory = historySnapshot,
                    isPersian = isPersianLanguage.value
                ).collect { event ->
                    when (event) {
                        is NexusStreamEvent.Partial -> {
                            _adminNexusMessages.update { list ->
                                list.map { if (it.id == botMsgId) it.copy(text = event.accumulatedText, isStreaming = true) else it }
                            }
                        }
                        is NexusStreamEvent.Completed -> {
                            _adminNexusMessages.update { list ->
                                list.map { if (it.id == botMsgId) it.copy(text = event.fullText, isStreaming = false, isError = false) else it }
                            }
                        }
                        is NexusStreamEvent.Error -> {
                            _adminNexusMessages.update { list ->
                                list.map { if (it.id == botMsgId) it.copy(text = event.message, isStreaming = false, isError = true) else it }
                            }
                        }
                    }
                }
            } finally {
                _adminNexusStreaming.value = false
            }
        }
    }

    fun clearAdminNexusChat() {
        _adminNexusMessages.value = emptyList()
    }

    fun observeTicketReplies(ticketId: String): Flow<List<SupportTicketReply>> {
        return supportTicketRepository.observeTicketReplies(ticketId)
    }

    fun submitSupportTicket(
        subject: String,
        description: String,
        category: SupportTicketCategory,
        priority: SupportTicketPriority
    ) {
        val fbUser = safeFirebaseUser()
        if (fbUser == null) {
            _uiMessage.value = UiMessage.Error(
                if (isPersianLanguage.value) {
                    "ارسال تیکت پشتیبانی ابری نیازمند نشست فعال ابری است؛ از کانال‌های رسمی پشتیبانی یا دستیار هوشمند استفاده کنید."
                } else {
                    "Cloud ticket sync requires an active cloud session; please use AI diagnostics or official support channels."
                }
            )
            return
        }
        val cleanSubject = subject.trim()
        val cleanDesc = description.trim()
        if (cleanSubject.isBlank() || cleanDesc.isBlank()) {
            _uiMessage.value = UiMessage.Error(
                if (isPersianLanguage.value) "عنوان و توضیحات تیکت الزامی است." else "Subject and description are required."
            )
            return
        }

        viewModelScope.launch {
            _ticketBusy.value = true
            try {
                var finalCategory = category
                var finalPriority = priority
                var triageSummary = ""
                // Attempt AI triage cleanly; if offline/unavailable, ticket still submits normally
                aiEngine.triageSupportTicket(
                    subject = cleanSubject,
                    description = cleanDesc,
                    sanitizedUserContext = buildUserSafeContextSnapshot()
                ).onSuccess { triage ->
                    if (category == SupportTicketCategory.GENERAL) {
                        finalCategory = SupportTicketCategory.fromString(triage.category)
                    }
                    finalPriority = SupportTicketPriority.fromString(triage.priority)
                    triageSummary = buildString {
                        append(triage.aiTriageSummary)
                        if (triage.instantUserSteps.isNotEmpty()) {
                            append("\nپیشنهاد فوری NEXUS AI: ")
                            append(triage.instantUserSteps.joinToString(" | "))
                        }
                    }
                }

                val res = supportTicketRepository.createTicket(
                    subject = cleanSubject,
                    description = cleanDesc,
                    category = finalCategory,
                    priority = finalPriority,
                    aiTriageSummary = triageSummary,
                    userEmail = fbUser.email ?: userProfile.value?.email ?: ""
                )
                if (res.isSuccess) {
                    _uiMessage.value = UiMessage.Success(
                        if (isPersianLanguage.value) "تیکت پشتیبانی با موفقیت ثبت شد" else "Support ticket submitted"
                    )
                } else {
                    _uiMessage.value = UiMessage.Error(
                        res.exceptionOrNull()?.localizedMessage ?: "خطا در ثبت تیکت پشتیبانی"
                    )
                }
            } finally {
                _ticketBusy.value = false
            }
        }
    }

    fun replyToTicket(
        ticket: SupportTicket,
        message: String,
        asAdmin: Boolean,
        nextStatus: SupportTicketStatus? = null
    ) {
        if (asAdmin && !checkPermission(AdminPermission.MANAGE_USERS, "پاسخ‌دهی به تیکت پشتیبانی")) return
        val cleanMsg = message.trim()
        if (cleanMsg.isBlank()) return
        viewModelScope.launch {
            _ticketBusy.value = true
            try {
                val res = supportTicketRepository.postReply(
                    ticket = ticket,
                    message = cleanMsg,
                    isAdminReply = asAdmin,
                    nextStatus = nextStatus
                )
                if (res.isSuccess) {
                    _uiMessage.value = UiMessage.Success(
                        if (isPersianLanguage.value) "پاسخ با موفقیت ارسال شد" else "Reply posted"
                    )
                } else {
                    _uiMessage.value = UiMessage.Error(res.exceptionOrNull()?.localizedMessage ?: "خطا در ارسال پاسخ")
                }
            } finally {
                _ticketBusy.value = false
            }
        }
    }

    fun adminUpdateTicketStatus(ticketId: String, status: SupportTicketStatus) {
        if (!checkPermission(AdminPermission.MANAGE_USERS, "تغییر وضعیت تیکت پشتیبانی")) return
        viewModelScope.launch {
            _ticketBusy.value = true
            try {
                val res = supportTicketRepository.adminUpdateStatus(ticketId, status)
                if (res.isSuccess) {
                    _uiMessage.value = UiMessage.Success(
                        if (isPersianLanguage.value) "وضعیت تیکت به ${status.titleFa} تغییر یافت" else "Ticket marked ${status.titleEn}"
                    )
                } else {
                    _uiMessage.value = UiMessage.Error(res.exceptionOrNull()?.localizedMessage ?: "خطا")
                }
            } finally {
                _ticketBusy.value = false
            }
        }
    }

    // --- BACKEND ECOSYSTEM METHODS (CRYPTO PAYMENTS, REFERRALS, FREE TRIAL, APK RELEASES) ---

    private fun selectHigherPriorityError(
        first: BackgroundBackendError?,
        second: BackgroundBackendError?
    ): BackgroundBackendError? {
        if (first == null) return second
        if (second == null) return first
        fun priority(cat: BackendErrorCategory): Int = when (cat) {
            BackendErrorCategory.AUTHENTICATION -> 3
            BackendErrorCategory.SERVER -> 2
            BackendErrorCategory.NETWORK -> 1
        }
        return if (priority(first.category) >= priority(second.category)) first else second
    }

    fun clearBackgroundBackendError() {
        _backgroundBackendError.value = null
    }

    fun retryBackendEcosystemSync() {
        refreshBackendEcosystem(_lastPendingReferralCode)
    }

    fun reportInvalidApkDownloadUrl(errorMessage: String) {
        _uiMessage.value = UiMessage.Error(errorMessage)
    }

    fun clearApkUploadError() {
        _apkUploadError.value = null
    }

    fun refreshBackendEcosystem(pendingReferralCode: String? = null) {
        if (!pendingReferralCode.isNullOrBlank()) {
            _lastPendingReferralCode = pendingReferralCode.trim()
        }
        viewModelScope.launch(Dispatchers.IO) {
            syncBackendEcosystemInternal(pendingReferralCode ?: _lastPendingReferralCode)
        }
    }

    @VisibleForTesting
    internal suspend fun syncBackendEcosystemOnceForTest(pendingReferralCode: String? = null) {
        if (!pendingReferralCode.isNullOrBlank()) {
            _lastPendingReferralCode = pendingReferralCode.trim()
        }
        syncBackendEcosystemInternal(pendingReferralCode ?: _lastPendingReferralCode)
    }

    private suspend fun syncBackendEcosystemInternal(pendingReferralCode: String?) {
        var accumulatedError: BackgroundBackendError? = null

        if (!pendingReferralCode.isNullOrBlank()) {
            val qualifyRes = backendClient.qualifyReferralOnLogin(pendingReferralCode)
            if (qualifyRes is BackendResult.Success) {
                _lastPendingReferralCode = null
            } else {
                val err = classifyBackgroundError("referral_qualify", qualifyRes, isPersianLanguage.value)
                accumulatedError = selectHigherPriorityError(accumulatedError, err)
                Log.w(TAG, "Background referral qualify failed: $err")
            }
        }

        when (val syncRes = backendClient.syncSession()) {
            is BackendResult.Success -> {
                val sync = syncRes.data
                _backendSessionSync.value = sync
                if (sync.serverTimeMs > 0L) {
                    updateTrustedServerAnchor(sync.serverTimeMs)
                }
                if (sync.trialStatus != null) {
                    _freeTrialStatus.value = sync.trialStatus
                }
                if (sync.isAdmin && !_verifiedServerAdmin.value) {
                    _verifiedServerAdmin.value = true
                    _verifiedAdminRole.value = AdminRole.fromVerifiedClaimOrNull(sync.verifiedRole)
                        ?: AdminRole.SUPER_ADMIN
                }
            }
            else -> {
                val err = classifyBackgroundError("session_sync", syncRes, isPersianLanguage.value)
                accumulatedError = selectHigherPriorityError(accumulatedError, err)
                Log.w(TAG, "Background session sync failed: $err")
            }
        }

        val ecosystemError = refreshBackendEcosystemInternal(isCurrentUserAdmin())
        _backgroundBackendError.value = selectHigherPriorityError(accumulatedError, ecosystemError)
    }

    private suspend fun refreshBackendEcosystemInternal(isAdminUser: Boolean): BackgroundBackendError? {
        var accumulatedError: BackgroundBackendError? = null

        when (val trialRes = backendClient.getFreeTrialStatus()) {
            is BackendResult.Success -> _freeTrialStatus.value = trialRes.data
            else -> {
                val err = classifyBackgroundError("trial_status", trialRes, isPersianLanguage.value)
                accumulatedError = selectHigherPriorityError(accumulatedError, err)
                Log.w(TAG, "Background trial status failed: $err")
            }
        }
        when (val refRes = backendClient.getReferralDashboard()) {
            is BackendResult.Success -> _referralDashboard.value = refRes.data
            else -> {
                val err = classifyBackgroundError("referral_dashboard", refRes, isPersianLanguage.value)
                accumulatedError = selectHigherPriorityError(accumulatedError, err)
                Log.w(TAG, "Background referral dashboard failed: $err")
            }
        }
        when (val invRes = backendClient.listCryptoInvoices(isAdminScope = isAdminUser)) {
            is BackendResult.Success -> _cryptoInvoices.value = invRes.data
            else -> {
                val err = classifyBackgroundError("crypto_invoices", invRes, isPersianLanguage.value)
                accumulatedError = selectHigherPriorityError(accumulatedError, err)
                Log.w(TAG, "Background crypto invoices failed: $err")
            }
        }
        when (val relRes = backendClient.getLatestRelease()) {
            is BackendResult.Success -> _latestRelease.value = relRes.data
            else -> {
                val err = classifyBackgroundError("latest_release", relRes, isPersianLanguage.value)
                accumulatedError = selectHigherPriorityError(accumulatedError, err)
                if (err != null) {
                    Log.w(TAG, "Background latest release failed: $err")
                }
            }
        }
        if (isAdminUser) {
            when (val rewardRes = backendClient.adminListReferralRewardRequests()) {
                is BackendResult.Success -> _adminRewardRequests.value = rewardRes.data
                else -> {
                    val err = classifyBackgroundError("admin_referral_rewards", rewardRes, isPersianLanguage.value)
                    accumulatedError = selectHigherPriorityError(accumulatedError, err)
                    Log.w(TAG, "Background admin reward requests failed: $err")
                }
            }
            when (val releasesRes = backendClient.adminListReleases()) {
                is BackendResult.Success -> _adminReleases.value = releasesRes.data
                else -> {
                    val err = classifyBackgroundError("admin_releases", releasesRes, isPersianLanguage.value)
                    accumulatedError = selectHigherPriorityError(accumulatedError, err)
                    Log.w(TAG, "Background admin releases failed: $err")
                }
            }
        }
        return accumulatedError
    }

    private fun describeBackendFailure(err: BackendResult<Any?>, fallbackFa: String): String {
        return when (err) {
            is BackendResult.Unauthenticated -> if (isPersianLanguage.value) {
                "ابتدا با ایمیل خود وارد حساب کاربری شوید."
            } else {
                "Please sign in first."
            }
            is BackendResult.HttpError -> err.message.ifBlank { "$fallbackFa (HTTP ${err.httpCode})" }
            is BackendResult.NetworkError -> if (isPersianLanguage.value) {
                "خطای ارتباط با سرور: ${err.message}"
            } else {
                "Network error: ${err.message}"
            }
            is BackendResult.Success -> fallbackFa
        }
    }

    fun createCryptoInvoice(plan: SubscriptionPlanId, asset: CryptoAsset) {
        if (_cryptoBusy.value) return
        viewModelScope.launch {
            _cryptoBusy.value = true
            try {
                when (val res = backendClient.createCryptoInvoice(plan.id, asset)) {
                    is BackendResult.Success -> {
                        val invoice = res.data
                        _activeCryptoInvoice.value = invoice
                        _cryptoInvoices.update { list -> listOf(invoice) + list.filterNot { it.invoiceId == invoice.invoiceId } }
                        _uiMessage.value = UiMessage.Success(
                            if (isPersianLanguage.value) {
                                "فاکتور رمزارزی (${asset.symbol}) صادر شد. مبلغ دقیق را قبل از انقضا واریز و تایید کنید."
                            } else {
                                "${asset.symbol} invoice created. Transfer the exact amount before expiry."
                            }
                        )
                    }
                    else -> {
                        _uiMessage.value = UiMessage.Error(describeBackendFailure(res, "خطا در صدور فاکتور رمزارزی"))
                    }
                }
            } finally {
                _cryptoBusy.value = false
            }
        }
    }

    fun verifyCryptoInvoice(invoiceId: String, txHash: String) {
        val cleanHash = txHash.trim()
        if (cleanHash.length < 16) {
            _uiMessage.value = UiMessage.Error(
                if (isPersianLanguage.value) "شناسه تراکنش (TxHash) معتبر وارد کنید." else "Enter a valid transaction hash (TxHash)."
            )
            return
        }
        if (_cryptoBusy.value) return
        viewModelScope.launch {
            _cryptoBusy.value = true
            try {
                when (val res = backendClient.verifyCryptoInvoice(invoiceId, cleanHash)) {
                    is BackendResult.Success -> {
                        val updated = res.data
                        _activeCryptoInvoice.value = updated
                        _cryptoInvoices.update { list ->
                            list.map { if (it.invoiceId == updated.invoiceId) updated else it }
                                .ifEmpty { listOf(updated) }
                        }
                        if (updated.status == CryptoInvoiceStatus.CONFIRMED) {
                            refreshBackendEcosystem()
                            _uiMessage.value = UiMessage.Success(
                                if (isPersianLanguage.value) {
                                    "تراکنش روی شبکه تایید شد و اشتراک شما فعال گردید."
                                } else {
                                    "On-chain payment confirmed and subscription activated."
                                }
                            )
                        } else {
                            _uiMessage.value = UiMessage.Success(
                                updated.statusMessage.ifBlank {
                                    if (isPersianLanguage.value) {
                                        "وضعیت تراکنش: ${updated.status.titleFa}"
                                    } else {
                                        "Transaction status: ${updated.status.titleEn}"
                                    }
                                }
                            )
                        }
                    }
                    else -> {
                        _uiMessage.value = UiMessage.Error(describeBackendFailure(res, "تایید تراکنش ناموفق بود"))
                    }
                }
            } finally {
                _cryptoBusy.value = false
            }
        }
    }

    fun selectCryptoInvoice(invoice: CryptoPaymentInvoice) {
        _activeCryptoInvoice.value = invoice
    }

    fun dismissActiveCryptoInvoice() {
        _activeCryptoInvoice.value = null
    }

    fun activateFreeTrial() {
        if (_trialBusy.value) return
        viewModelScope.launch {
            _trialBusy.value = true
            try {
                when (val res = backendClient.activateFreeTrial()) {
                    is BackendResult.Success -> {
                        val status = res.data
                        _freeTrialStatus.value = status
                        if (status.active) {
                            _trialGrantedBanner.value = status
                            refreshBackendEcosystem()
                            _uiMessage.value = UiMessage.Success(
                                if (isPersianLanguage.value) {
                                    "تست رایگان ۲۴ ساعته برای شما فعال شد!"
                                } else {
                                    "Your 24-hour free trial is now active!"
                                }
                            )
                        } else {
                            val remain = ReferralAndCryptoCalculator.formatDurationCountdown(
                                status.cooldownRemainingSeconds,
                                isPersianLanguage.value
                            )
                            _uiMessage.value = UiMessage.Error(
                                status.lastReason.ifBlank {
                                    if (isPersianLanguage.value) {
                                        "تست رایگان هر ۷ روز یک بار قابل دریافت است ($remain باقی‌مانده)."
                                    } else {
                                        "Free trial allowed once every 7 days ($remain remaining)."
                                    }
                                }
                            )
                        }
                    }
                    else -> {
                        _uiMessage.value = UiMessage.Error(describeBackendFailure(res, "خطا در فعال‌سازی تست رایگان"))
                    }
                }
            } finally {
                _trialBusy.value = false
            }
        }
    }

    fun dismissTrialGrantedBanner() {
        _trialGrantedBanner.value = null
    }

    fun claimReferralReward(milestoneThreshold: Int) {
        if (_referralBusy.value) return
        viewModelScope.launch {
            _referralBusy.value = true
            try {
                when (val res = backendClient.claimReferralReward(milestoneThreshold)) {
                    is BackendResult.Success -> {
                        refreshBackendEcosystem()
                        _uiMessage.value = UiMessage.Success(
                            if (isPersianLanguage.value) {
                                "درخواست پاداش جایگاه $milestoneThreshold دعوت ثبت و به پنل مدیر ارسال شد."
                            } else {
                                "Reward claim for $milestoneThreshold referrals submitted for admin review."
                            }
                        )
                    }
                    else -> {
                        _uiMessage.value = UiMessage.Error(describeBackendFailure(res, "خطا در ثبت درخواست پاداش"))
                    }
                }
            } finally {
                _referralBusy.value = false
            }
        }
    }

    fun adminRefreshEcosystemData() {
        if (!checkPermission(AdminPermission.VIEW_USERS, "بارگذاری داده‌های اکوسیستم")) return
        viewModelScope.launch(Dispatchers.IO) {
            val err = refreshBackendEcosystemInternal(isAdminUser = true)
            _backgroundBackendError.value = err
        }
    }

    fun adminReviewReferralReward(requestId: String, approve: Boolean, adminNote: String) {
        if (!checkPermission(AdminPermission.MANAGE_USERS, "بررسی درخواست پاداش معرفی")) return
        if (_referralBusy.value) return
        viewModelScope.launch {
            _referralBusy.value = true
            try {
                when (val res = backendClient.adminReviewReferralReward(requestId, approve, adminNote)) {
                    is BackendResult.Success -> {
                        val updated = res.data
                        _adminRewardRequests.update { list ->
                            list.map { if (it.requestId == updated.requestId) updated else it }
                        }
                        _uiMessage.value = UiMessage.Success(
                            if (isPersianLanguage.value) {
                                if (approve) "پاداش رفرال تایید شد" else "درخواست پاداش رد شد"
                            } else {
                                if (approve) "Referral reward approved" else "Referral reward rejected"
                            }
                        )
                    }
                    else -> {
                        _uiMessage.value = UiMessage.Error(describeBackendFailure(res, "خطا در بررسی درخواست پاداش"))
                    }
                }
            } finally {
                _referralBusy.value = false
            }
        }
    }

    fun adminUploadApkReleaseFromUri(
        fileUri: Uri,
        fileName: String,
        fileSizeBytes: Long,
        versionName: String,
        versionCode: Int,
        releaseNotes: String,
        publishImmediately: Boolean
    ) {
        if (!checkPermission(AdminPermission.MANAGE_SETTINGS, "انتشار نسخه جدید APK")) return
        if (_releaseBusy.value) return
        if (fileSizeBytes == 0L) {
            val emptyMsg = if (isPersianLanguage.value) "فایل APK انتخاب‌شده خالی است." else "Selected APK file is empty."
            _apkUploadError.value = emptyMsg
            _uiMessage.value = UiMessage.Error(emptyMsg)
            return
        }
        if (fileSizeBytes > TmsBackendApiClient.MAX_APK_UPLOAD_BYTES) {
            val maxMsg = if (isPersianLanguage.value) {
                "حجم فایل APK از حد مجاز (۱۰۰ مگابایت) بیشتر است."
            } else {
                "APK file exceeds the 100 MB upload limit."
            }
            _apkUploadError.value = maxMsg
            _uiMessage.value = UiMessage.Error(maxMsg)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _releaseBusy.value = true
            _apkUploadError.value = null
            _apkUploadProgress.value = 0f
            try {
                val resolver = getApplication<Application>().contentResolver
                val res = backendClient.adminUploadApkReleaseStream(
                    fileName = fileName,
                    fileSizeBytes = fileSizeBytes,
                    versionName = versionName,
                    versionCode = versionCode,
                    releaseNotes = releaseNotes,
                    publishImmediately = publishImmediately,
                    streamProvider = { resolver.openInputStream(fileUri) },
                    onProgress = { uploaded, total ->
                        if (total > 0L) {
                            _apkUploadProgress.value = (uploaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                        }
                    }
                )
                handleApkUploadResult(res)
            } catch (oom: OutOfMemoryError) {
                Log.e(TAG, "OOM during APK upload", oom)
                val msg = if (isPersianLanguage.value) "حافظه کافی برای بارگذاری فایل وجود ندارد." else "Out of memory while uploading APK."
                _apkUploadError.value = msg
                _uiMessage.value = UiMessage.Error(msg)
            } finally {
                _apkUploadProgress.value = null
                _releaseBusy.value = false
            }
        }
    }

    fun adminUploadApkRelease(
        fileName: String,
        apkBytes: ByteArray,
        versionName: String,
        versionCode: Int,
        releaseNotes: String,
        publishImmediately: Boolean
    ) {
        if (!checkPermission(AdminPermission.MANAGE_SETTINGS, "انتشار نسخه جدید APK")) return
        if (_releaseBusy.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _releaseBusy.value = true
            _apkUploadError.value = null
            _apkUploadProgress.value = 0f
            try {
                val res = backendClient.adminUploadApkRelease(
                    fileName = fileName,
                    apkBytes = apkBytes,
                    versionName = versionName,
                    versionCode = versionCode,
                    releaseNotes = releaseNotes,
                    publishImmediately = publishImmediately,
                    onProgress = { uploaded, total ->
                        if (total > 0L) {
                            _apkUploadProgress.value = (uploaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                        }
                    }
                )
                handleApkUploadResult(res)
            } finally {
                _apkUploadProgress.value = null
                _releaseBusy.value = false
            }
        }
    }

    private fun handleApkUploadResult(res: BackendResult<AppReleaseInfo>) {
        when (res) {
            is BackendResult.Success -> {
                val rel = res.data
                _apkUploadError.value = null
                _adminReleases.update { list -> listOf(rel) + list.filterNot { it.releaseId == rel.releaseId } }
                if (rel.isPublished) {
                    _latestRelease.value = rel
                }
                _uiMessage.value = UiMessage.Success(
                    if (isPersianLanguage.value) {
                        "نسخه ${rel.versionName} با موفقیت روی سرور بارگذاری شد"
                    } else {
                        "Release ${rel.versionName} uploaded successfully"
                    }
                )
            }
            else -> {
                val errMsg = describeBackendFailure(res, "خطا در آپلود فایل APK")
                _apkUploadError.value = errMsg
                _uiMessage.value = UiMessage.Error(errMsg)
            }
        }
    }

    fun adminToggleReleasePublish(releaseId: String, publish: Boolean) {
        if (!checkPermission(AdminPermission.MANAGE_SETTINGS, "تغییر وضعیت انتشار نسخه APK")) return
        if (_releaseBusy.value) return
        viewModelScope.launch {
            _releaseBusy.value = true
            try {
                when (val res = backendClient.adminSetReleasePublished(releaseId, publish)) {
                    is BackendResult.Success -> {
                        val updated = res.data
                        _adminReleases.update { list ->
                            list.map { if (it.releaseId == updated.releaseId) updated else it }
                        }
                        refreshBackendEcosystem()
                        _uiMessage.value = UiMessage.Success(
                            if (isPersianLanguage.value) {
                                if (publish) "نسخه منتشر شد" else "نسخه از انتشار خارج شد"
                            } else {
                                if (publish) "Release published" else "Release unpublished"
                            }
                        )
                    }
                    else -> {
                        _uiMessage.value = UiMessage.Error(describeBackendFailure(res, "خطا در تغییر وضعیت انتشار"))
                    }
                }
            } finally {
                _releaseBusy.value = false
            }
        }
    }

    fun recordApkDownload(releaseId: String) {
        val currentRelease = _latestRelease.value
        if (currentRelease != null && currentRelease.releaseId == releaseId && !ApkDownloadUrlValidator.isValid(currentRelease.downloadUrl)) {
            Log.w(TAG, "Skipped recording download for invalid release URL")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            when (val res = backendClient.recordReleaseDownload(releaseId)) {
                is BackendResult.Success -> _latestRelease.value = res.data
                else -> {
                    val err = classifyBackgroundError("record_download", res, isPersianLanguage.value)
                    if (err != null) {
                        _backgroundBackendError.value = err
                        Log.w(TAG, "Background record download failed: $err")
                    }
                }
            }
        }
    }

    fun sendVerificationEmail() {
        viewModelScope.launch {
            val res = userAuthManager.sendEmailVerification()
            if (res.isSuccess) {
                _uiMessage.value = UiMessage.Success(
                    if (isPersianLanguage.value) {
                        "ایمیل تایید به نشانی شما ارسال شد."
                    } else {
                        "Verification email sent."
                    }
                )
            } else {
                _uiMessage.value = UiMessage.Error(
                    res.exceptionOrNull()?.localizedMessage ?: "خطا در ارسال ایمیل تایید"
                )
            }
        }
    }

    fun sendPasswordResetEmailFromProfile() {
        val email = currentActorEmail()
        viewModelScope.launch {
            val res = userAuthManager.sendPasswordReset(email)
            if (res.isSuccess) {
                _uiMessage.value = UiMessage.Success(
                    if (isPersianLanguage.value) {
                        "لینک تغییر رمز عبور به $email ارسال شد."
                    } else {
                        "Password reset link sent to $email."
                    }
                )
            } else {
                _uiMessage.value = UiMessage.Error(
                    res.exceptionOrNull()?.localizedMessage ?: "خطا در ارسال ایمیل بازیابی"
                )
            }
        }
    }

    private fun logValidationFailure(module: String, errorDetails: String) {
        viewModelScope.launch {
            try {
                val user = safeFirebaseUser() ?: return@launch
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
