package com.example.ui.screens

import androidx.compose.runtime.Composable
import com.example.data.model.VpnConfig
import com.example.data.model.VpnConnectionState
import com.example.data.model.VpnSessionStats

/**
 * Legacy compatibility wrapper preserved so external callers continue compiling,
 * delegating directly to the redesigned Cyber Technology UserHomeScreen.
 */
@Composable
fun UserDashboardScreen(
    vpnState: VpnConnectionState,
    sessionStats: VpnSessionStats,
    configs: List<VpnConfig>,
    selectedConfig: VpnConfig?,
    isPersian: Boolean,
    onConnectVpn: () -> Unit,
    onDisconnectVpn: () -> Unit,
    onSelectConfig: (VpnConfig) -> Unit,
    onImportConfig: (String) -> Unit,
    onImportBatch: (String) -> Unit,
    onAddManual: (String, String, String, Int, String) -> Unit,
    onOpenRadar: () -> Unit,
    onRequestAdminSpace: () -> Unit,
    onToggleLanguage: () -> Unit
) {
    UserHomeScreen(
        vpnState = vpnState,
        sessionStats = sessionStats,
        configs = configs,
        servers = emptyList(),
        selectedConfig = selectedConfig,
        isPersian = isPersian,
        onConnectVpn = onConnectVpn,
        onDisconnectVpn = onDisconnectVpn,
        onSelectConfig = onSelectConfig,
        onImportConfig = onImportConfig,
        onImportBatch = onImportBatch,
        onOpenServersTab = {},
        onOpenRadar = onOpenRadar
    )
}
