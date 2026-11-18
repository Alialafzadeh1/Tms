package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.AdminRole
import com.example.ui.components.CyberAmbientBackdrop
import com.example.ui.components.CyberBottomNavBar
import com.example.ui.components.CyberTopHeader
import com.example.ui.components.UserTabDestination
import com.example.ui.components.statusColorFor
import com.example.ui.screens.AntiFilterRadarScreen
import com.example.ui.screens.ConfigManagementScreen
import com.example.ui.screens.IntelligentEngineScreen
import com.example.ui.screens.OverviewScreen
import com.example.ui.screens.ServerManagementScreen
import com.example.ui.screens.SystemSettingsScreen
import com.example.ui.screens.UserHomeScreen
import com.example.ui.screens.UserManagementScreen
import com.example.ui.screens.UserProfileSettingsScreen
import com.example.ui.screens.UserServersScreen
import com.example.ui.screens.UserStatisticsScreen
import com.example.ui.screens.UserSubscriptionScreen
import com.example.ui.theme.BorderGoldHighlight
import com.example.ui.theme.BorderGoldMuted
import com.example.ui.theme.CardSurfaceDark
import com.example.ui.theme.DeepGraphite
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.GlassBorderCyan
import com.example.ui.theme.GlassBorderSubtle
import com.example.ui.theme.GoldContainer
import com.example.ui.theme.MetallicGold
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.SecondarySurface
import com.example.ui.theme.StatusConnected
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusOfflineRed
import com.example.ui.theme.TextCyberPrimary
import com.example.ui.theme.TextCyberSecondary
import com.example.ui.theme.TextGraySecondary
import com.example.ui.theme.TextWhitePrimary
import com.example.ui.viewmodel.TmsVpnViewModel
import com.example.ui.viewmodel.UiMessage
import com.example.vpn.VpnController

enum class TmsDestination(
    val titleFa: String,
    val titleEn: String,
    val icon: ImageVector
) {
    OVERVIEW("نمای کلی", "Overview", Icons.Default.Dashboard),
    RADAR("رادار ضد فیلتر", "Radar", Icons.Default.Radar),
    CONFIGS("کانفیگ‌ها", "Configs", Icons.Default.VpnKey),
    SERVERS("سرورها", "Servers", Icons.Default.Dns),
    INTELLIGENCE("موتور هوشمند", "Intelligence", Icons.Default.Psychology),
    USERS("کاربران", "Users", Icons.Default.People),
    SETTINGS("تنظیمات", "Settings", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TmsVpnApp(
    currentUserId: String,
    onSignOut: () -> Unit,
    onRequestVpnPermission: ((() -> Unit)) -> Unit = { it() },
    viewModel: TmsVpnViewModel = viewModel()
) {
    val context = LocalContext.current
    val isPersian by viewModel.isPersianLanguage.collectAsStateWithLifecycle()
    val uiMessage by viewModel.uiMessage.collectAsStateWithLifecycle()
    val isPinging by viewModel.isPinging.collectAsStateWithLifecycle()
    val aiLoading by viewModel.aiLoading.collectAsStateWithLifecycle()
    val aiResponse by viewModel.aiResponse.collectAsStateWithLifecycle()

    val configs by viewModel.configs.collectAsStateWithLifecycle()
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val policies by viewModel.failoverPolicies.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val metrics by viewModel.overviewMetrics.collectAsStateWithLifecycle()

    val vpnConnectionState by viewModel.vpnConnectionState.collectAsStateWithLifecycle()
    val vpnSessionStats by viewModel.vpnSessionStats.collectAsStateWithLifecycle()
    val selectedVpnConfig by viewModel.selectedVpnConfig.collectAsStateWithLifecycle()
    val healthReport by viewModel.healthReport.collectAsStateWithLifecycle()
    val healthCheckInProgress by viewModel.healthCheckInProgress.collectAsStateWithLifecycle()
    val leakCheckReport by viewModel.leakCheckReport.collectAsStateWithLifecycle()
    val leakCheckInProgress by viewModel.leakCheckInProgress.collectAsStateWithLifecycle()
    val ispTuningReport by viewModel.ispTuningReport.collectAsStateWithLifecycle()
    val isTuningIsp by viewModel.isTuningIsp.collectAsStateWithLifecycle()
    val isRacingConnect by viewModel.isRacingConnect.collectAsStateWithLifecycle()
    val racingSummary by viewModel.racingSummary.collectAsStateWithLifecycle()
    val liveLatencies by viewModel.liveLatenciesMs.collectAsStateWithLifecycle()
    val isRefreshingLatencies by viewModel.isRefreshingLatencies.collectAsStateWithLifecycle()
    val latencyScanProgress by viewModel.latencyScanProgress.collectAsStateWithLifecycle()
    val selectedLatencyTier by viewModel.selectedLatencyTier.collectAsStateWithLifecycle()
    val sortLocationsByLatency by viewModel.sortLocationsByLatency.collectAsStateWithLifecycle()
    val isSubscriptionImporting by viewModel.isSubscriptionImporting.collectAsStateWithLifecycle()
    val nodeControlLoading by viewModel.nodeControlLoading.collectAsStateWithLifecycle()
    val ubuntuNodeHealth by viewModel.ubuntuNodeHealth.collectAsStateWithLifecycle()

    val currentRole by viewModel.currentRole.collectAsStateWithLifecycle()
    val isUnlocked by viewModel.isUnlocked.collectAsStateWithLifecycle()
    val lockoutSeconds by viewModel.lockoutSeconds.collectAsStateWithLifecycle()
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()

    val isInAdminSpace by viewModel.isInAdminSpace.collectAsStateWithLifecycle()
    var showRadarInUserMode by remember { mutableStateOf(false) }
    var currentUserTab by remember { mutableStateOf(UserTabDestination.HOME) }
    var showNotificationsDialog by remember { mutableStateOf(false) }

    var currentScreen by remember { mutableStateOf(TmsDestination.OVERVIEW) }
    val snackbarHostState = remember { SnackbarHostState() }

    var showQuickRoleSwitcher by remember { mutableStateOf(false) }

    LaunchedEffect(uiMessage) {
        uiMessage?.let { msg ->
            when (msg) {
                is UiMessage.Success -> snackbarHostState.showSnackbar(msg.message)
                is UiMessage.Error -> snackbarHostState.showSnackbar(msg.message)
            }
            viewModel.clearUiMessage()
        }
    }

    LaunchedEffect(currentUserTab, configs.size) {
        if (currentUserTab == UserTabDestination.SERVERS && configs.isNotEmpty() && liveLatencies.isEmpty()) {
            viewModel.refreshAllServerLatencies(context, force = false)
        }
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides if (isPersian) LayoutDirection.Rtl else LayoutDirection.Ltr
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().background(DeepGraphite)) {
            val isWideScreen = maxWidth >= 760.dp

            if (!isInAdminSpace) {
                // ======================================================
                // USER SPACE: PREMIUM CYBER TECHNOLOGY 5-TAB EXPERIENCE
                // ======================================================
                if (showRadarInUserMode) {
                    BackHandler { showRadarInUserMode = false }
                    CyberAmbientBackdrop(accentColor = ElectricCyan) {
                        Scaffold(
                            containerColor = Color.Transparent,
                            contentWindowInsets = WindowInsets.safeDrawing,
                            topBar = {
                                CenterAlignedTopAppBar(
                                    title = {
                                        Text(
                                            text = if (isPersian) "رادار ضد فیلتر و بایپس هوشمند" else "Anti-Censorship Radar",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ElectricCyan
                                        )
                                    },
                                    navigationIcon = {
                                        IconButton(onClick = { showRadarInUserMode = false }) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = "Back",
                                                tint = ElectricCyan
                                            )
                                        }
                                    },
                                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                        containerColor = SecondarySurface.copy(alpha = 0.9f)
                                    )
                                )
                            },
                            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
                        ) { innerPadding ->
                            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                                AntiFilterRadarScreen(
                                    vpnState = vpnConnectionState,
                                    sessionStats = vpnSessionStats,
                                    configs = configs,
                                    isPersian = isPersian,
                                    onConnectVpn = { cfg, antiFilter ->
                                        onRequestVpnPermission {
                                            viewModel.connectVpn(context, cfg, antiFilter)
                                        }
                                    },
                                    onDisconnectVpn = {
                                        viewModel.disconnectVpn(context)
                                    }
                                )
                            }
                        }
                    }
                } else {
                    BackHandler(enabled = currentUserTab != UserTabDestination.HOME) {
                        currentUserTab = UserTabDestination.HOME
                    }

                    CyberAmbientBackdrop(accentColor = statusColorFor(vpnConnectionState)) {
                        Scaffold(
                            containerColor = Color.Transparent,
                            contentWindowInsets = WindowInsets(0, 0, 0, 0),
                            topBar = {
                                CyberTopHeader(
                                    modifier = Modifier.statusBarsPadding(),
                                    isPersian = isPersian,
                                    vpnState = vpnConnectionState,
                                    unreadAlertsCount = auditLogs.size.coerceAtMost(9),
                                    userInitial = userProfile?.displayName ?: "T",
                                    onOpenNotifications = { showNotificationsDialog = true },
                                    onOpenProfile = { currentUserTab = UserTabDestination.PROFILE },
                                    onToggleLanguage = { viewModel.toggleLanguage() }
                                )
                            },
                            bottomBar = {
                                if (!isWideScreen) {
                                    CyberBottomNavBar(
                                        currentTab = currentUserTab,
                                        isPersian = isPersian,
                                        onSelectTab = { currentUserTab = it }
                                    )
                                }
                            },
                            snackbarHost = {
                                SnackbarHost(hostState = snackbarHostState) { data ->
                                    Snackbar(
                                        containerColor = SecondarySurface,
                                        contentColor = TextCyberPrimary,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.border(
                                            BorderStroke(1.dp, GlassBorderCyan),
                                            RoundedCornerShape(12.dp)
                                        )
                                    ) {
                                        Text(text = data.visuals.message, fontSize = 12.sp)
                                    }
                                }
                            }
                        ) { innerPadding ->
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                                if (isWideScreen) {
                                    NavigationRail(
                                        containerColor = SecondarySurface,
                                        modifier = Modifier.fillMaxHeight()
                                    ) {
                                        Spacer(modifier = Modifier.padding(top = 16.dp))
                                        UserTabDestination.entries.forEach { tab ->
                                            val selected = currentUserTab == tab
                                            NavigationRailItem(
                                                selected = selected,
                                                onClick = { currentUserTab = tab },
                                                icon = {
                                                    Icon(
                                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                        contentDescription = if (isPersian) tab.titleFa else tab.titleEn,
                                                        tint = if (selected) ElectricCyan else TextCyberSecondary
                                                    )
                                                },
                                                label = {
                                                    Text(
                                                        text = if (isPersian) tab.titleFa else tab.titleEn,
                                                        fontSize = 11.sp,
                                                        color = if (selected) ElectricCyan else TextCyberSecondary
                                                    )
                                                },
                                                colors = NavigationRailItemDefaults.colors(
                                                    indicatorColor = ElectricCyan.copy(alpha = 0.16f)
                                                )
                                            )
                                        }
                                    }
                                }

                                Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                                    AnimatedContent(
                                        targetState = currentUserTab,
                                        transitionSpec = {
                                            fadeIn(animationSpec = tween(220)) togetherWith
                                                fadeOut(animationSpec = tween(180))
                                        },
                                        label = "UserTabTransition"
                                    ) { tab ->
                                        when (tab) {
                                            UserTabDestination.HOME -> {
                                                UserHomeScreen(
                                                    vpnState = vpnConnectionState,
                                                    sessionStats = vpnSessionStats,
                                                    configs = configs,
                                                    servers = servers,
                                                    selectedConfig = selectedVpnConfig,
                                                    isPersian = isPersian,
                                                    onConnectVpn = {
                                                        onRequestVpnPermission {
                                                            viewModel.connectVpn(context)
                                                        }
                                                    },
                                                    onDisconnectVpn = {
                                                        viewModel.disconnectVpn(context)
                                                    },
                                                    onSelectConfig = { viewModel.selectConfigForVpn(it) },
                                                    onImportConfig = { viewModel.importConfig(it) },
                                                    onImportBatch = { viewModel.importBatchConfigs(it) },
                                                    onOpenServersTab = { currentUserTab = UserTabDestination.SERVERS },
                                                    onOpenRadar = { showRadarInUserMode = true },
                                                    healthReport = healthReport,
                                                    healthCheckInProgress = healthCheckInProgress,
                                                    isSubscriptionImporting = isSubscriptionImporting,
                                                    onRunHealthCheck = { viewModel.runTunnelHealthCheck() },
                                                    onFailoverNextNode = {
                                                        onRequestVpnPermission {
                                                            viewModel.failoverToNextHealthyConfig(context)
                                                        }
                                                    },
                                                    onImportSubscription = { viewModel.importSubscriptionUrl(it) },
                                                    isRacingConnect = isRacingConnect,
                                                    racingSummary = racingSummary,
                                                    onRacingConnect = {
                                                        onRequestVpnPermission {
                                                            viewModel.racingConnect(context, connectOnWin = true)
                                                        }
                                                    },
                                                    ispTuningReport = ispTuningReport,
                                                    isTuningIsp = isTuningIsp,
                                                    onRunIspAutoTune = { viewModel.runIspAutoTune(context) },
                                                    leakCheckReport = leakCheckReport,
                                                    leakCheckInProgress = leakCheckInProgress,
                                                    onRunLeakCheck = { viewModel.runLeakGuardCheck() }
                                                )
                                            }
                                            UserTabDestination.SERVERS -> {
                                                UserServersScreen(
                                                    configs = configs,
                                                    servers = servers,
                                                    selectedConfig = selectedVpnConfig,
                                                    isPinging = isPinging,
                                                    isPersian = isPersian,
                                                    onSelectConfig = { viewModel.selectConfigForVpn(it) },
                                                    onConnectSelected = { cfg ->
                                                        onRequestVpnPermission {
                                                            viewModel.connectVpn(context, cfg)
                                                            currentUserTab = UserTabDestination.HOME
                                                        }
                                                    },
                                                    onPingConfig = { viewModel.pingConfig(it) },
                                                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                                                    onAutoSelectBest = { viewModel.autoSelectBestConfig() },
                                                    isRacingConnect = isRacingConnect,
                                                    onRacingConnectAll = {
                                                        onRequestVpnPermission {
                                                            viewModel.racingConnect(context, connectOnWin = true)
                                                            currentUserTab = UserTabDestination.HOME
                                                        }
                                                    },
                                                    liveLatencies = liveLatencies,
                                                    isRefreshingLatencies = isRefreshingLatencies,
                                                    latencyScanProgress = latencyScanProgress,
                                                    selectedLatencyTier = selectedLatencyTier,
                                                    sortByLatency = sortLocationsByLatency,
                                                    onRefreshLatencies = { viewModel.refreshAllServerLatencies(context, force = true) },
                                                    onLatencyTierSelected = { viewModel.setLatencyFilterTier(it) },
                                                    onToggleSortByLatency = { viewModel.toggleSortLocationsByLatency() }
                                                )
                                            }
                                            UserTabDestination.STATISTICS -> {
                                                UserStatisticsScreen(
                                                    vpnState = vpnConnectionState,
                                                    sessionStats = vpnSessionStats,
                                                    userProfile = userProfile,
                                                    configs = configs,
                                                    auditLogs = auditLogs,
                                                    isPersian = isPersian
                                                )
                                            }
                                            UserTabDestination.SUBSCRIPTION -> {
                                                UserSubscriptionScreen(
                                                    userProfile = userProfile,
                                                    isPersian = isPersian
                                                )
                                            }
                                            UserTabDestination.PROFILE -> {
                                                UserProfileSettingsScreen(
                                                    currentUserId = currentUserId,
                                                    userProfile = userProfile,
                                                    isPersian = isPersian,
                                                    onToggleLanguage = { viewModel.toggleLanguage() },
                                                    onOpenRadar = { showRadarInUserMode = true },
                                                    onSignOut = onSignOut
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // ======================================================
                // ADMIN SPACE: PRESERVED CONTROL CENTER (CYBER THEME)
                // ======================================================
                Scaffold(
                    containerColor = DeepGraphite,
                    contentWindowInsets = WindowInsets.safeDrawing,
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(GoldContainer, CircleShape)
                                            .border(BorderStroke(1.dp, MetallicGold), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Shield,
                                            contentDescription = null,
                                            tint = MetallicGold,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isPersian) "مدیریت TMS VPN" else "TMS Admin",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MetallicGold,
                                        letterSpacing = 1.sp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .background(EmeraldGreen, CircleShape)
                                    )
                                }
                            },
                            navigationIcon = {
                                Row(
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .background(Color(0xFF201214), RoundedCornerShape(8.dp))
                                        .border(BorderStroke(0.8.dp, StatusOfflineRed.copy(alpha = 0.5f)), RoundedCornerShape(8.dp))
                                        .clickable { onSignOut() }
                                        .padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ExitToApp,
                                        contentDescription = "Sign Out",
                                        tint = StatusOfflineRed,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isPersian) "خروج" else "Exit",
                                        color = StatusOfflineRed,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            },
                            actions = {
                                Row(
                                    modifier = Modifier
                                        .background(ElevatedSurface, RoundedCornerShape(8.dp))
                                        .border(BorderStroke(0.8.dp, BorderGoldMuted), RoundedCornerShape(8.dp))
                                        .clickable { showQuickRoleSwitcher = true }
                                        .padding(horizontal = 7.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AdminPanelSettings,
                                        contentDescription = null,
                                        tint = MetallicGold,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = currentRole.id.replace("_", " "),
                                        color = MetallicGold,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                IconButton(onClick = { viewModel.toggleLanguage() }) {
                                    Box(
                                        modifier = Modifier
                                            .background(ElevatedSurface, RoundedCornerShape(8.dp))
                                            .border(BorderStroke(0.8.dp, BorderGoldMuted), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 7.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = if (isPersian) "EN" else "فا",
                                            color = MetallicGold,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = ObsidianSurface
                            )
                        )
                    },
                    bottomBar = {
                        if (!isWideScreen) {
                            NavigationBar(
                                containerColor = ObsidianSurface,
                                tonalElevation = 8.dp
                            ) {
                                TmsDestination.entries.forEach { dest ->
                                    val selected = currentScreen == dest
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { currentScreen = dest },
                                        icon = {
                                            Icon(
                                                imageVector = dest.icon,
                                                contentDescription = if (isPersian) dest.titleFa else dest.titleEn,
                                                tint = if (selected) MetallicGold else TextGraySecondary
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = if (isPersian) dest.titleFa else dest.titleEn,
                                                fontSize = 9.sp,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (selected) MetallicGold else TextGraySecondary
                                            )
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = MetallicGold,
                                            selectedTextColor = MetallicGold,
                                            indicatorColor = GoldContainer
                                        )
                                    )
                                }
                            }
                        }
                    },
                    snackbarHost = {
                        SnackbarHost(hostState = snackbarHostState) { data ->
                            Snackbar(
                                containerColor = CardSurfaceDark,
                                contentColor = TextWhitePrimary,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.border(BorderStroke(1.dp, BorderGoldHighlight), RoundedCornerShape(10.dp))
                            ) {
                                Text(text = data.visuals.message, fontSize = 12.sp)
                            }
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (isWideScreen) {
                            NavigationRail(
                                containerColor = ObsidianSurface,
                                modifier = Modifier.fillMaxHeight()
                            ) {
                                Spacer(modifier = Modifier.padding(top = 16.dp))
                                TmsDestination.entries.forEach { dest ->
                                    val selected = currentScreen == dest
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = { currentScreen = dest },
                                        icon = {
                                            Icon(
                                                imageVector = dest.icon,
                                                contentDescription = if (isPersian) dest.titleFa else dest.titleEn,
                                                tint = if (selected) MetallicGold else TextGraySecondary
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = if (isPersian) dest.titleFa else dest.titleEn,
                                                fontSize = 11.sp,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (selected) MetallicGold else TextGraySecondary
                                            )
                                        },
                                        colors = NavigationRailItemDefaults.colors(
                                            selectedIconColor = MetallicGold,
                                            selectedTextColor = MetallicGold,
                                            indicatorColor = GoldContainer
                                        )
                                    )
                                }
                            }
                        }

                        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                            BackHandler(enabled = currentScreen != TmsDestination.OVERVIEW) {
                                currentScreen = TmsDestination.OVERVIEW
                            }

                            when (currentScreen) {
                                TmsDestination.OVERVIEW -> {
                                    OverviewScreen(
                                        metrics = metrics,
                                        configs = configs,
                                        servers = servers,
                                        vpnState = vpnConnectionState,
                                        vpnStats = vpnSessionStats,
                                        selectedConfig = selectedVpnConfig,
                                        isPersian = isPersian,
                                        onConnectVpn = {
                                            onRequestVpnPermission {
                                                viewModel.connectVpn(context)
                                            }
                                        },
                                        onDisconnectVpn = { viewModel.disconnectVpn(context) },
                                        onAutoSelectBest = { viewModel.autoSelectBestConfig() }
                                    )
                                }
                                TmsDestination.RADAR -> {
                                    AntiFilterRadarScreen(
                                        vpnState = vpnConnectionState,
                                        sessionStats = vpnSessionStats,
                                        configs = configs,
                                        isPersian = isPersian,
                                        onConnectVpn = { cfg, antiFilter ->
                                            onRequestVpnPermission {
                                                viewModel.connectVpn(context, cfg, antiFilter)
                                            }
                                        },
                                        onDisconnectVpn = { viewModel.disconnectVpn(context) }
                                    )
                                }
                                TmsDestination.CONFIGS -> {
                                    ConfigManagementScreen(
                                        configs = configs,
                                        currentRole = currentRole,
                                        selectedConfigId = selectedVpnConfig?.id,
                                        isPersian = isPersian,
                                        isPinging = isPinging,
                                        onImportConfig = { viewModel.importConfig(it) },
                                        onImportBatch = { viewModel.importBatchConfigs(it) },
                                        onSelectForVpn = { viewModel.selectConfigForVpn(it) },
                                        onPingConfig = { viewModel.pingConfig(it) },
                                        onDeleteConfig = { viewModel.deleteConfig(it) },
                                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                                        onQuickAudit = { viewModel.quickAudit(it) }
                                    )
                                }
                                TmsDestination.SERVERS -> {
                                    ServerManagementScreen(
                                        servers = servers,
                                        currentRole = currentRole,
                                        isPersian = isPersian,
                                        isPinging = isPinging,
                                        onAddServer = { name, country, code, host, port, proto ->
                                            viewModel.addServer(name, country, code, host, port, proto)
                                        },
                                        onPingServer = { viewModel.pingServer(it) },
                                        onDeleteServer = { viewModel.deleteServer(it) },
                                        nodeControlLoading = nodeControlLoading,
                                        ubuntuNodeHealth = ubuntuNodeHealth,
                                        onCheckUbuntuNode = { url, token ->
                                            viewModel.checkUbuntuNodeHealth(url, token)
                                        },
                                        onProvisionUbuntuUser = { url, token ->
                                            viewModel.provisionFromUbuntuNode(url, token)
                                        }
                                    )
                                }
                                TmsDestination.INTELLIGENCE -> {
                                    IntelligentEngineScreen(
                                        configs = configs,
                                        servers = servers,
                                        policies = policies,
                                        currentRole = currentRole,
                                        isPersian = isPersian,
                                        aiLoading = aiLoading,
                                        aiResponse = aiResponse,
                                        onAddPolicy = { name, pId, bId, threshold ->
                                            viewModel.addFailoverPolicy(name, pId, bId, threshold)
                                        },
                                        onDeletePolicy = { viewModel.deleteFailoverPolicy(it) },
                                        onOptimizeTopology = { viewModel.optimizeTopology(it) },
                                        onClearAiResponse = { viewModel.clearAiResponse() }
                                    )
                                }
                                TmsDestination.USERS -> {
                                    UserManagementScreen(
                                        profile = userProfile,
                                        currentRole = currentRole,
                                        auditLogs = auditLogs,
                                        isPersian = isPersian,
                                        onSwitchRole = { viewModel.switchRole(it) },
                                        onUpdateQuota = { count, max, limit ->
                                            viewModel.updateUserQuota(count, max, limit)
                                        }
                                    )
                                }
                                TmsDestination.SETTINGS -> {
                                    SystemSettingsScreen(
                                        currentRole = currentRole,
                                        isPersian = isPersian,
                                        isUnlocked = isUnlocked,
                                        lockoutSeconds = lockoutSeconds,
                                        onToggleLanguage = { viewModel.toggleLanguage() },
                                        onChangePin = { oldPin, newPin -> viewModel.changeAdminPin(oldPin, newPin) },
                                        onEmergencyResetPin = { token, newPin -> viewModel.emergencyResetPin(token, newPin) },
                                        onLockSession = { viewModel.lockAdminSession() },
                                        onVerifyPin = { viewModel.verifyAdminPin(it) },
                                        getRecoveryToken = { viewModel.getRecoveryToken() },
                                        onSignOutComplete = onSignOut
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Notifications / Real Audit Events Modal
            if (showNotificationsDialog) {
                AlertDialog(
                    onDismissRequest = { showNotificationsDialog = false },
                    containerColor = SecondarySurface,
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Filled.Notifications, contentDescription = null, tint = ElectricCyan)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isPersian) "اعلان‌ها و رویدادهای امنیتی" else "System Notifications",
                                color = ElectricCyan,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    text = {
                        if (auditLogs.isEmpty()) {
                            Text(
                                text = if (isPersian) "رویداد امنیتی جدیدی ثبت نشده است." else "No recent security or tunnel alerts.",
                                color = TextCyberSecondary
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(260.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(auditLogs.take(10), key = { it.id.ifBlank { "${it.action}_${it.details.hashCode()}" } }) { log ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(ElevatedSurface, RoundedCornerShape(10.dp))
                                            .border(BorderStroke(0.6.dp, GlassBorderSubtle), RoundedCornerShape(10.dp))
                                            .padding(10.dp)
                                    ) {
                                        Text(
                                            text = log.action,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (log.status == "SUCCESS") StatusConnected else StatusError
                                        )
                                        Text(
                                            text = log.details,
                                            fontSize = 11.sp,
                                            color = TextCyberPrimary
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showNotificationsDialog = false }) {
                            Text(text = if (isPersian) "بستن" else "Close", color = ElectricCyan)
                        }
                    }
                )
            }

            // Quick Role Switcher Modal (Admin Space)
            if (showQuickRoleSwitcher) {
                AlertDialog(
                    onDismissRequest = { showQuickRoleSwitcher = false },
                    containerColor = CardSurfaceDark,
                    title = {
                        Text(
                            text = if (isPersian) "تغییر نقش مدیر (RBAC Simulator)" else "Switch Administrator Role",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MetallicGold
                        )
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = if (isPersian)
                                    "نقش مورد نظر را برای تست بررسی دقیق مجوزهای امنیتی ماژول‌ها انتخاب کنید:"
                                else
                                    "Select a role to verify granular access enforcement across dashboard tabs:",
                                fontSize = 11.sp,
                                color = TextWhitePrimary
                            )

                            AdminRole.entries.forEach { role ->
                                val isSelected = currentRole == role
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(
                                            BorderStroke(if (isSelected) 1.5.dp else 0.8.dp, if (isSelected) MetallicGold else BorderGoldMuted),
                                            RoundedCornerShape(8.dp)
                                        ),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) ElevatedSurface else SecondarySurface
                                    ),
                                    onClick = {
                                        viewModel.switchRole(role)
                                        showQuickRoleSwitcher = false
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = if (isPersian) role.titleFa else role.titleEn,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) MetallicGold else TextWhitePrimary
                                            )
                                            Text(
                                                text = "${role.permissions.size} مجوز فعال • ${role.id}",
                                                fontSize = 10.sp,
                                                color = TextGraySecondary
                                            )
                                        }

                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = MetallicGold,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { showQuickRoleSwitcher = false }) {
                            Text(text = if (isPersian) "بستن" else "Close", color = TextGraySecondary)
                        }
                    }
                )
            }
        }
    }
}
