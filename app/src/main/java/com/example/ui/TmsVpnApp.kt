package com.example.ui

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.AdminRole
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.Radar
import com.example.ui.screens.AntiFilterRadarScreen
import com.example.ui.screens.ConfigManagementScreen
import com.example.ui.screens.IntelligentEngineScreen
import com.example.ui.screens.OverviewScreen
import com.example.ui.screens.ServerManagementScreen
import com.example.ui.screens.SystemSettingsScreen
import com.example.ui.screens.UserManagementScreen
import com.example.ui.theme.BorderGoldHighlight
import com.example.ui.theme.BorderGoldMuted
import com.example.ui.theme.CardSurfaceDark
import com.example.ui.theme.DeepBlackBackground
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.GoldContainer
import com.example.ui.theme.MetallicGold
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.StatusOfflineRed
import com.example.ui.theme.StatusWarningAmber
import com.example.ui.theme.TextGoldMuted
import com.example.ui.theme.TextGraySecondary
import com.example.ui.theme.TextWhitePrimary
import com.example.ui.viewmodel.TmsVpnViewModel
import com.example.ui.viewmodel.UiMessage
import com.example.vpn.VpnController

import com.example.ui.screens.UserDashboardScreen
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExitToApp

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
    val context = androidx.compose.ui.platform.LocalContext.current
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

    // Real VPN Tunnel State
    val vpnConnectionState by viewModel.vpnConnectionState.collectAsStateWithLifecycle()
    val vpnSessionStats by viewModel.vpnSessionStats.collectAsStateWithLifecycle()
    val selectedVpnConfig by viewModel.selectedVpnConfig.collectAsStateWithLifecycle()

    // RBAC & Security state
    val currentRole by viewModel.currentRole.collectAsStateWithLifecycle()
    val isUnlocked by viewModel.isUnlocked.collectAsStateWithLifecycle()
    val lockoutSeconds by viewModel.lockoutSeconds.collectAsStateWithLifecycle()
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()

    // User Space vs Admin Space separation
    val isInAdminSpace by viewModel.isInAdminSpace.collectAsStateWithLifecycle()
    var showRadarInUserMode by remember { mutableStateOf(false) }

    var currentScreen by remember { mutableStateOf(TmsDestination.OVERVIEW) }
    val snackbarHostState = remember { SnackbarHostState() }

    var showQuickRoleSwitcher by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var pinModalInput by remember { mutableStateOf("") }

    LaunchedEffect(uiMessage) {
        uiMessage?.let { msg ->
            when (msg) {
                is UiMessage.Success -> snackbarHostState.showSnackbar(msg.message)
                is UiMessage.Error -> snackbarHostState.showSnackbar(msg.message)
            }
            viewModel.clearUiMessage()
        }
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides if (isPersian) LayoutDirection.Rtl else LayoutDirection.Ltr
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().background(DeepBlackBackground)) {
            val isWideScreen = maxWidth >= 760.dp

            if (!isInAdminSpace) {
                // ==========================================
                // 💎 USER SPACE: LUXURY 3D NEON GLASS UI
                // ==========================================
                if (showRadarInUserMode) {
                    BackHandler { showRadarInUserMode = false }
                    Scaffold(
                        topBar = {
                            CenterAlignedTopAppBar(
                                title = {
                                    Text(
                                        text = if (isPersian) "رادار ضد فیلتر و بایپس هوشمند" else "Anti-Censorship Radar",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MetallicGold
                                    )
                                },
                                navigationIcon = {
                                    IconButton(onClick = { showRadarInUserMode = false }) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowBack,
                                            contentDescription = "Back",
                                            tint = MetallicGold
                                        )
                                    }
                                },
                                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                    containerColor = ObsidianSurface
                                )
                            )
                        },
                        snackbarHost = {
                            SnackbarHost(hostState = snackbarHostState)
                        }
                    ) { innerPadding ->
                        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                            AntiFilterRadarScreen(
                                vpnState = vpnConnectionState,
                                sessionStats = vpnSessionStats,
                                configs = configs,
                                isPersian = isPersian,
                                onConnectVpn = { cfg, antiFilter ->
                                    onRequestVpnPermission {
                                        viewModel.selectConfigForVpn(cfg)
                                        VpnController.startVpn(context, cfg, antiFilter)
                                    }
                                },
                                onDisconnectVpn = {
                                    viewModel.disconnectVpn(context)
                                }
                            )
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        UserDashboardScreen(
                            vpnState = vpnConnectionState,
                            sessionStats = vpnSessionStats,
                            configs = configs,
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
                            onSelectConfig = { config ->
                                viewModel.selectConfigForVpn(config)
                            },
                            onImportConfig = { text ->
                                viewModel.importConfig(text)
                            },
                            onImportBatch = { batchText ->
                                viewModel.importBatchConfigs(batchText)
                            },
                            onAddManual = { name, proto, address, port, json ->
                                viewModel.addManualConfig(name, proto, address, port, json)
                            },
                            onOpenRadar = {
                                showRadarInUserMode = true
                            },
                            onRequestAdminSpace = {
                                pinModalInput = ""
                                showPinDialog = true
                            },
                            onToggleLanguage = {
                                viewModel.toggleLanguage()
                            }
                        )

                        // Floating Snackbar for user space
                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp)
                        )
                    }
                }
            } else {
                // ==========================================
                // 🛡️ ADMIN SPACE: ENTERPRISE CONTROL CENTER
                // ==========================================
                Scaffold(
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
                                    // Live Emerald indicator
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .background(EmeraldGreen, CircleShape)
                                    )
                                }
                            },
                            navigationIcon = {
                                // Exit Admin space button
                                Row(
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .background(Color(0xFF201214), RoundedCornerShape(8.dp))
                                        .border(BorderStroke(0.8.dp, StatusOfflineRed.copy(alpha = 0.5f)), RoundedCornerShape(8.dp))
                                        .clickable { viewModel.exitAdminSpace() }
                                        .padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ExitToApp,
                                        contentDescription = "Exit to User Mode",
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
                                // Active RBAC Role badge button
                                Row(
                                    modifier = Modifier
                                        .background(Color(0xFF141722), RoundedCornerShape(8.dp))
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

                                // Language switcher toggle
                                IconButton(onClick = { viewModel.toggleLanguage() }) {
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFF141722), RoundedCornerShape(8.dp))
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
                    // Desktop / Tablet Navigation Rail
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

                    // Content Pane
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
                                    onDisconnectVpn = {
                                        viewModel.disconnectVpn(context)
                                    },
                                    onAutoSelectBest = {
                                        viewModel.autoSelectBestConfig()
                                    }
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
                                            viewModel.selectConfigForVpn(cfg)
                                            VpnController.startVpn(context, cfg, antiFilter)
                                        }
                                    },
                                    onDisconnectVpn = {
                                        viewModel.disconnectVpn(context)
                                    }
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
                                    onDeleteServer = { viewModel.deleteServer(it) }
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

        // Quick Role Switcher Modal
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
                                    containerColor = if (isSelected) Color(0xFF1E1700) else Color(0xFF0C0E14)
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

        // Admin Space Access & PIN Unlock Modal
        if (showPinDialog) {
            AlertDialog(
                onDismissRequest = { showPinDialog = false },
                containerColor = CardSurfaceDark,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = null,
                            tint = MetallicGold,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isPersian) "ورود به پنل مدیریت ارشد" else "Enter Admin Control Center",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MetallicGold
                        )
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = if (isPersian)
                                "رمز عبور مدیریت را وارد کنید (رمز مدیر: 14361436):"
                            else
                                "Enter administrator password (Admin PIN: 14361436):",
                            fontSize = 12.sp,
                            color = TextWhitePrimary
                        )

                        OutlinedTextField(
                            value = pinModalInput,
                            onValueChange = { pinModalInput = it },
                            label = { Text(if (isPersian) "رمز عبور مدیر" else "Admin PIN") },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = PasswordVisualTransformation(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MetallicGold,
                                unfocusedBorderColor = BorderGoldMuted,
                                focusedTextColor = TextWhitePrimary,
                                unfocusedTextColor = TextWhitePrimary
                            ),
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val ok = viewModel.enterAdminSpace(pinModalInput)
                            if (ok) {
                                showPinDialog = false
                                pinModalInput = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = Color(0xFF1E1700)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(text = if (isPersian) "تایید و ورود به مدیریت" else "Authorize & Enter", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPinDialog = false }) {
                        Text(text = if (isPersian) "انصراف" else "Cancel", color = TextGraySecondary)
                    }
                }
            )
        }
    }
}
}
