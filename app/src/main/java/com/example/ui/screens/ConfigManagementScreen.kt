package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AdminPermission
import com.example.data.model.AdminRole
import com.example.data.model.ConfigOrigin
import com.example.data.model.VpnConfig
import com.example.data.subscription.BulkDeletionPlan
import com.example.data.subscription.ConfigDeletionPolicy
import com.example.ui.theme.BorderGoldHighlight
import com.example.ui.theme.BorderGoldMuted
import com.example.ui.theme.CardSurfaceDark
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.GoldContainer
import com.example.ui.theme.MetallicGold
import com.example.ui.theme.PremiumTeal
import com.example.ui.theme.StatusOfflineRed
import com.example.ui.theme.StatusWarningAmber
import com.example.ui.theme.TextGoldMuted
import com.example.ui.theme.TextGraySecondary
import com.example.ui.theme.TextWhitePrimary
import java.io.BufferedReader
import java.io.InputStreamReader

private enum class BulkDeleteDialogMode {
    NONE,
    DELETE_SELECTED,
    DELETE_ALL_PERMITTED
}

@Composable
fun ConfigManagementScreen(
    configs: List<VpnConfig>,
    currentRole: AdminRole,
    selectedConfigId: String? = null,
    isPersian: Boolean,
    isPinging: Boolean,
    onImportConfig: (String) -> Unit,
    onImportBatch: (String) -> Unit,
    onSelectForVpn: (VpnConfig) -> Unit,
    onPingConfig: (VpnConfig) -> Unit,
    onDeleteConfig: (String) -> Unit,
    onToggleFavorite: (VpnConfig) -> Unit,
    onQuickAudit: (VpnConfig) -> Unit,
    selectedConfigIds: Set<String> = emptySet(),
    onToggleSelection: (String) -> Unit = {},
    onSelectAll: (List<String>) -> Unit = {},
    onDeselectAll: () -> Unit = {},
    onDeleteSelected: () -> Unit = {},
    onDeleteAllPermitted: (List<VpnConfig>) -> Unit = {},
    previewBulkDeletion: ((Set<String>) -> BulkDeletionPlan)? = null
) {
    val context = LocalContext.current
    var showImportDialog by remember { mutableStateOf(false) }
    var rawInputText by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("ALL") }
    var selectedCategoryFilter by remember { mutableStateOf("ALL") }
    var deleteDialogMode by remember { mutableStateOf(BulkDeleteDialogMode.NONE) }
    var singleDeleteTarget by remember { mutableStateOf<VpnConfig?>(null) }

    val canManageConfigs = currentRole.hasPermission(AdminPermission.MANAGE_CONFIGS)

    // File picker launcher for importing VPN config files
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val text = BufferedReader(InputStreamReader(stream)).readText()
                    rawInputText = text
                    showImportDialog = true
                }
            } catch (_: Exception) {
            }
        }
    }

    val filteredConfigs = remember(configs, searchQuery, statusFilter, selectedCategoryFilter) {
        ConfigDeletionPolicy.filterConfigs(
            configs = configs,
            query = searchQuery,
            statusFilter = statusFilter,
            protocolOrOriginFilter = selectedCategoryFilter
        )
    }

    val selectedVisibleCount = remember(filteredConfigs, selectedConfigIds) {
        filteredConfigs.count { it.id in selectedConfigIds }
    }

    val statusCounts = remember(configs) {
        mapOf(
            "ALL" to configs.size,
            "ONLINE" to configs.count { it.status.equals("online", ignoreCase = true) },
            "OFFLINE" to configs.count { it.status.equals("offline", ignoreCase = true) },
            "UNTESTED" to configs.count { it.status.equals("untested", ignoreCase = true) || it.status.isBlank() }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .testTag("config_management_list"),
            contentPadding = PaddingValues(top = 16.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isPersian) "مخزن پیشرفته کانفیگ‌های VPN" else "Advanced VPN Config Vault",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = MetallicGold
                        )
                        Text(
                            text = if (isPersian) {
                                "مدیریت گروهی، تفکیک سطوح حذف (کاربر / اشتراک / سیستمی)"
                            } else {
                                "Bulk control with separated origin deletion permissions"
                            },
                            fontSize = 11.sp,
                            color = TextGraySecondary
                        )
                    }

                    if (canManageConfigs) {
                        Button(
                            onClick = { showImportDialog = true },
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("import_config_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MetallicGold,
                                contentColor = Color(0xFF1E1700)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = if (isPersian) "واردسازی" else "Import", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("config_search_input"),
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = MetallicGold)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear search", tint = TextGraySecondary)
                            }
                        }
                    },
                    placeholder = {
                        Text(
                            text = if (isPersian) "جستجو بر اساس نام، آدرس IP، پورت یا پروتکل..." else "Search by name, host, port, or protocol...",
                            fontSize = 12.sp,
                            color = TextGraySecondary
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MetallicGold,
                        unfocusedBorderColor = BorderGoldMuted,
                        focusedTextColor = TextWhitePrimary,
                        unfocusedTextColor = TextWhitePrimary,
                        focusedContainerColor = CardSurfaceDark,
                        unfocusedContainerColor = CardSurfaceDark
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Quick Import Shortcuts Bar (Clipboard & File)
            if (canManageConfigs) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = clipboard.primaryClip
                                if (clip != null && clip.itemCount > 0) {
                                    val text = clip.getItemAt(0).text?.toString() ?: ""
                                    if (text.isNotBlank()) {
                                        rawInputText = text
                                        showImportDialog = true
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MetallicGold),
                            border = BorderStroke(1.dp, BorderGoldHighlight),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = if (isPersian) "از کلیپ‌بورد" else "Clipboard", fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = { filePickerLauncher.launch("text/*") },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldGreen),
                            border = BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = if (isPersian) "از فایل (.txt)" else "From File", fontSize = 11.sp)
                        }
                    }
                }
            }

            // Status Filter Chips Row
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val statusOptions = listOf(
                        "ALL" to (if (isPersian) "همه وضعیت‌ها" else "All Status"),
                        "ONLINE" to (if (isPersian) "آنلاین" else "Online"),
                        "OFFLINE" to (if (isPersian) "آفلاین" else "Offline"),
                        "UNTESTED" to (if (isPersian) "تست‌نشده" else "Untested")
                    )

                    statusOptions.forEach { (key, label) ->
                        val selected = statusFilter == key
                        val count = statusCounts[key] ?: 0
                        FilterChip(
                            selected = selected,
                            onClick = { statusFilter = key },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("config_status_filter_$key"),
                            label = {
                                Text(
                                    text = "$label ($count)",
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldContainer,
                                selectedLabelColor = EmeraldGreen,
                                containerColor = CardSurfaceDark,
                                labelColor = TextGraySecondary
                            ),
                            border = BorderStroke(0.8.dp, if (selected) EmeraldGreen else BorderGoldMuted)
                        )
                    }
                }
            }

            // Category / Origin / Protocol Chips Group
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val filterOptions = listOf(
                        "ALL" to (if (isPersian) "همه (${configs.size})" else "All (${configs.size})"),
                        "USER_IMPORTED" to (if (isPersian) "کاربری" else "User Imported"),
                        "SUBSCRIPTION_ASSIGNED" to (if (isPersian) "IP اشتراک" else "Subscription IP"),
                        "SYSTEM_MANAGED" to (if (isPersian) "سیستمی" else "System"),
                        "FAVORITES" to (if (isPersian) "نشان‌شده" else "Starred"),
                        "VLESS" to "VLESS",
                        "VMESS" to "VMess",
                        "TROJAN" to "Trojan",
                        "SHADOWSOCKS" to "Shadowsocks"
                    )

                    filterOptions.forEach { (key, label) ->
                        val selected = selectedCategoryFilter == key
                        FilterChip(
                            selected = selected,
                            onClick = { selectedCategoryFilter = key },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("config_category_filter_$key"),
                            label = { Text(text = label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GoldContainer,
                                selectedLabelColor = MetallicGold,
                                containerColor = CardSurfaceDark,
                                labelColor = TextGraySecondary
                            ),
                            border = BorderStroke(0.8.dp, if (selected) MetallicGold else BorderGoldMuted)
                        )
                    }
                }
            }

            // Bulk Selection & Action Controls Card
            if (filteredConfigs.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(BorderStroke(1.dp, if (selectedConfigIds.isNotEmpty()) MetallicGold else BorderGoldMuted), RoundedCornerShape(14.dp))
                            .testTag("bulk_action_bar"),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF10141F)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isPersian) {
                                        "انتخاب‌شده: ${selectedConfigIds.size} (نمایشی: $selectedVisibleCount از ${filteredConfigs.size})"
                                    } else {
                                        "Selected: ${selectedConfigIds.size} (${selectedVisibleCount}/${filteredConfigs.size} shown)"
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedConfigIds.isNotEmpty()) MetallicGold else TextWhitePrimary,
                                    modifier = Modifier.testTag("selected_configs_count")
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onSelectAll(filteredConfigs.map { it.id }) },
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("select_all_configs_button"),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MetallicGold),
                                    border = BorderStroke(1.dp, BorderGoldHighlight),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.SelectAll, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = if (isPersian) "انتخاب همه" else "Select All", fontSize = 11.sp)
                                }

                                OutlinedButton(
                                    onClick = onDeselectAll,
                                    enabled = selectedConfigIds.isNotEmpty(),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("deselect_all_configs_button"),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextGraySecondary),
                                    border = BorderStroke(1.dp, BorderGoldMuted),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.CheckBoxOutlineBlank, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = if (isPersian) "لغو انتخاب" else "Deselect All", fontSize = 11.sp)
                                }

                                if (canManageConfigs) {
                                    Button(
                                        onClick = { deleteDialogMode = BulkDeleteDialogMode.DELETE_SELECTED },
                                        enabled = selectedConfigIds.isNotEmpty(),
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("delete_selected_configs_button"),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = StatusOfflineRed,
                                            contentColor = TextWhitePrimary
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isPersian) "حذف انتخابی (${selectedConfigIds.size})" else "Delete Selected (${selectedConfigIds.size})",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = { deleteDialogMode = BulkDeleteDialogMode.DELETE_ALL_PERMITTED },
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("delete_all_configs_button"),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusOfflineRed),
                                        border = BorderStroke(1.dp, StatusOfflineRed.copy(alpha = 0.7f)),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = if (isPersian) "حذف همه مجاز" else "Delete All", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Zero State
            if (filteredConfigs.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(BorderStroke(1.dp, BorderGoldMuted), RoundedCornerShape(16.dp))
                            .testTag("config_empty_state"),
                        colors = CardDefaults.cardColors(containerColor = CardSurfaceDark)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .background(GoldContainer, CircleShape)
                                    .border(BorderStroke(1.dp, MetallicGold), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VpnKey,
                                    contentDescription = null,
                                    tint = MetallicGold,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = if (isPersian) "هیچ کانفیگی مطابق فیلتر یافت نشد" else "No Matching Configurations",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhitePrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (isPersian) {
                                    "جستجو یا فیلتر وضعیت را تغییر دهید یا کانفیگ‌های جدید (VLESS, VMess, Trojan, Shadowsocks) وارد کنید."
                                } else {
                                    "Adjust your search/status filters or import new VPN configurations."
                                },
                                fontSize = 12.sp,
                                color = TextGraySecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(filteredConfigs, key = { it.id }) { config ->
                    val isSelectedForTunnel = config.id == selectedConfigId || (selectedConfigId == null && filteredConfigs.firstOrNull()?.id == config.id)
                    val isChecked = config.id in selectedConfigIds
                    val origin = config.originEnum()
                    val allowSingleDelete = canManageConfigs && origin != ConfigOrigin.SYSTEM_MANAGED

                    ConfigCard(
                        config = config,
                        origin = origin,
                        isChecked = isChecked,
                        canDeleteSingle = allowSingleDelete,
                        isSelected = isSelectedForTunnel,
                        isPersian = isPersian,
                        isPinging = isPinging,
                        onToggleCheck = { onToggleSelection(config.id) },
                        onSelectForVpn = { onSelectForVpn(config) },
                        onPing = { onPingConfig(config) },
                        onDelete = { singleDeleteTarget = config },
                        onToggleFavorite = { onToggleFavorite(config) },
                        onAudit = { onQuickAudit(config) },
                        onCopy = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("VPN Config", config.rawConfig))
                        }
                    )
                }
            }
        }

        // Floating Action Button
        if (canManageConfigs) {
            FloatingActionButton(
                onClick = { showImportDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp),
                containerColor = MetallicGold,
                contentColor = Color(0xFF1E1700)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Config")
            }
        }
    }

    // Explicit Single Delete Confirmation Dialog
    singleDeleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { singleDeleteTarget = null },
            containerColor = CardSurfaceDark,
            modifier = Modifier.testTag("confirm_single_delete_dialog"),
            title = {
                Text(
                    text = if (isPersian) "تایید حذف کانفیگ" else "Confirm Delete Configuration",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = StatusOfflineRed
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (isPersian) {
                            "آیا از حذف کانفیگ «${target.name}» اطمینان دارید؟ این مورد در پایگاه‌داده محلی و ابری پاک شده و مجدداً بازنمی‌گردد."
                        } else {
                            "Delete '${target.name}'? It will be permanently removed from local cache and cloud sync."
                        },
                        fontSize = 12.sp,
                        color = TextWhitePrimary
                    )
                    if (target.id == selectedConfigId) {
                        Text(
                            text = if (isPersian) "توجه: این کانفیگ هم‌اکنون به عنوان نود فعال انتخاب شده است." else "Warning: This is currently the selected tunnel config.",
                            fontSize = 11.sp,
                            color = StatusWarningAmber
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteConfig(target.id)
                        singleDeleteTarget = null
                    },
                    modifier = Modifier.testTag("confirm_single_delete_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = StatusOfflineRed, contentColor = TextWhitePrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = if (isPersian) "حذف قطعی" else "Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { singleDeleteTarget = null }) {
                    Text(text = if (isPersian) "انصراف" else "Cancel", color = TextGraySecondary)
                }
            }
        )
    }

    // Explicit Bulk Deletion Confirmation Dialog
    if (deleteDialogMode != BulkDeleteDialogMode.NONE) {
        val targetSet = if (deleteDialogMode == BulkDeleteDialogMode.DELETE_SELECTED) {
            selectedConfigIds
        } else {
            filteredConfigs.map { it.id }.toSet()
        }
        val plan = previewBulkDeletion?.invoke(targetSet)
        val deletableCount = plan?.deletableIds?.size ?: targetSet.size
        val blockedCount = plan?.blockedCount ?: 0

        AlertDialog(
            onDismissRequest = { deleteDialogMode = BulkDeleteDialogMode.NONE },
            containerColor = CardSurfaceDark,
            modifier = Modifier.testTag("confirm_bulk_delete_dialog"),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = StatusOfflineRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (deleteDialogMode == BulkDeleteDialogMode.DELETE_ALL_PERMITTED) {
                            if (isPersian) "تایید حذف گروهی همه موارد مجاز" else "Confirm Delete All Permitted"
                        } else {
                            if (isPersian) "تایید حذف کانفیگ‌های انتخاب‌شده" else "Confirm Bulk Deletion"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = StatusOfflineRed
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (isPersian) {
                            "$deletableCount کانفیگ مجاز به صورت قطعی حذف خواهند شد و پس از همگام‌سازی نیز بازنمی‌گردند."
                        } else {
                            "$deletableCount permitted configuration(s) will be permanently deleted with offline tombstone protection."
                        },
                        fontSize = 12.sp,
                        color = TextWhitePrimary
                    )
                    if (blockedCount > 0) {
                        Text(
                            text = if (isPersian) {
                                "$blockedCount کانفیگ سیستمی یا محافظت‌شده در فهرست باقی می‌مانند و حذف نمی‌شوند."
                            } else {
                                "$blockedCount protected system-managed config(s) will be preserved."
                            },
                            fontSize = 11.sp,
                            color = EmeraldGreen
                        )
                    }
                    if (plan?.includesActiveTunnel == true) {
                        Text(
                            text = if (isPersian) {
                                "هشدار: تونل فعال کنونی جزو موارد حذفی است و هنگام حذف به‌صورت ایمن قطع خواهد شد."
                            } else {
                                "Warning: Your active VPN tunnel is included and will be safely disconnected."
                            },
                            fontSize = 11.sp,
                            color = StatusWarningAmber
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (deleteDialogMode == BulkDeleteDialogMode.DELETE_SELECTED) {
                            onDeleteSelected()
                        } else {
                            onDeleteAllPermitted(filteredConfigs)
                        }
                        deleteDialogMode = BulkDeleteDialogMode.NONE
                    },
                    modifier = Modifier.testTag("confirm_bulk_delete_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = StatusOfflineRed, contentColor = TextWhitePrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isPersian) "بله، حذف شود ($deletableCount)" else "Confirm Delete ($deletableCount)",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteDialogMode = BulkDeleteDialogMode.NONE }) {
                    Text(text = if (isPersian) "انصراف" else "Cancel", color = TextGraySecondary)
                }
            }
        )
    }

    // Import Modal Dialog (Single or Batch Multi-Line)
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            containerColor = CardSurfaceDark,
            title = {
                Text(
                    text = if (isPersian) "واردسازی کانفیگ VPN (تکی یا گروهی)" else "Import VPN Configurations",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MetallicGold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (isPersian)
                            "یک یا چند لینک (VLESS, VMess, Trojan, Shadowsocks) را در کادر زیر قرار دهید:"
                        else
                            "Paste one or multiple VPN configuration URIs (separated by line breaks):",
                        fontSize = 12.sp,
                        color = TextWhitePrimary
                    )

                    OutlinedTextField(
                        value = rawInputText,
                        onValueChange = { rawInputText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        placeholder = {
                            Text(
                                text = "vless://uuid@server:443?security=reality&type=tcp#Node-1\nvmess://...\ntrojan://...",
                                fontSize = 11.sp,
                                color = TextGraySecondary
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MetallicGold,
                            unfocusedBorderColor = BorderGoldMuted,
                            focusedTextColor = TextWhitePrimary,
                            unfocusedTextColor = TextWhitePrimary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0D0F15), RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isPersian) "اعتبارسنجی خودکار، شناسایی تکراری‌ها و ذخیره‌سازی ایزوله اختصاصی شما" else "Auto-validation, duplicate detection, and private user storage",
                            fontSize = 10.sp,
                            color = TextGraySecondary
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (rawInputText.isNotBlank()) {
                            if (rawInputText.contains("\n")) {
                                onImportBatch(rawInputText)
                            } else {
                                onImportConfig(rawInputText)
                            }
                            rawInputText = ""
                            showImportDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = Color(0xFF1E1700)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = if (isPersian) "تجزیه و ثبت" else "Parse & Import", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text(text = if (isPersian) "انصراف" else "Cancel", color = TextGraySecondary)
                }
            }
        )
    }
}

@Composable
private fun ConfigCard(
    config: VpnConfig,
    origin: ConfigOrigin,
    isChecked: Boolean,
    canDeleteSingle: Boolean,
    isSelected: Boolean,
    isPersian: Boolean,
    isPinging: Boolean,
    onToggleCheck: () -> Unit,
    onSelectForVpn: () -> Unit,
    onPing: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAudit: () -> Unit,
    onCopy: () -> Unit
) {
    val originBadgeColor = when (origin) {
        ConfigOrigin.SYSTEM_MANAGED -> StatusWarningAmber
        ConfigOrigin.SUBSCRIPTION_ASSIGNED -> ElectricCyan
        ConfigOrigin.USER_IMPORTED -> PremiumTeal
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                BorderStroke(
                    when {
                        isChecked -> 1.8.dp
                        isSelected -> 1.6.dp
                        config.isFavorite -> 1.2.dp
                        else -> 0.8.dp
                    },
                    when {
                        isChecked -> MetallicGold
                        isSelected -> EmeraldGreen
                        config.isFavorite -> MetallicGold.copy(alpha = 0.7f)
                        else -> BorderGoldMuted
                    }
                ),
                RoundedCornerShape(14.dp)
            )
            .testTag("config_card_${config.id}"),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Top row: Selection Checkbox, Protocol tag, Name, Origin Badge, Active Indicator, Favorite
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = { onToggleCheck() },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("config_select_checkbox_${config.id}"),
                        colors = CheckboxDefaults.colors(
                            checkedColor = MetallicGold,
                            checkmarkColor = Color(0xFF1E1700),
                            uncheckedColor = TextGraySecondary
                        )
                    )

                    Box(
                        modifier = Modifier
                            .background(GoldContainer, RoundedCornerShape(6.dp))
                            .border(BorderStroke(1.dp, BorderGoldHighlight), RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = config.protocol.uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MetallicGold
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = config.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhitePrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (origin == ConfigOrigin.SYSTEM_MANAGED) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = StatusWarningAmber,
                                    modifier = Modifier.size(11.dp)
                                )
                            } else if (origin == ConfigOrigin.SUBSCRIPTION_ASSIGNED) {
                                Icon(
                                    imageVector = Icons.Default.VerifiedUser,
                                    contentDescription = null,
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                            Text(
                                text = if (isPersian) origin.titleFa else origin.titleEn,
                                fontSize = 10.sp,
                                color = originBadgeColor
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .background(EmeraldContainer, RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isPersian) "فعال" else "ACTIVE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldGreen
                            )
                        }
                    }

                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (config.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Favorite",
                            tint = if (config.isFavorite) MetallicGold else TextGraySecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Server & Port detail + Health Status Badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0C0E14), RoundedCornerShape(8.dp))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${config.server}:${config.port}",
                        fontSize = 12.sp,
                        color = TextWhitePrimary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Transport: ${config.transport.uppercase()} • Security: ${config.security.uppercase()} ${if (config.sni.isNotBlank()) "• SNI: ${config.sni}" else ""}",
                        fontSize = 10.sp,
                        color = TextGraySecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val isOnline = config.status == "online" && config.pingMs > 0
                val isOffline = config.status == "offline"
                val badgeColor = when {
                    isOnline -> EmeraldGreen
                    isOffline -> StatusOfflineRed
                    else -> StatusWarningAmber
                }
                Box(
                    modifier = Modifier
                        .background(badgeColor.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
                        .border(BorderStroke(0.8.dp, badgeColor), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(6.dp).background(badgeColor, CircleShape))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when {
                                config.pingMs > 0 && config.status == "online" -> "${config.pingMs} ms"
                                isOffline -> if (isPersian) "آفلاین" else "Offline"
                                else -> if (isPersian) "تست‌نشده" else "Untested"
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons: Select For Tunnel, Real Ping, AI Audit, Copy, Delete (with Origin Guard)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onSelectForVpn,
                    modifier = Modifier.height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) EmeraldGreen else Color(0xFF191B24),
                        contentColor = if (isSelected) Color(0xFF021B10) else MetallicGold
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (isSelected) (if (isPersian) "انتخاب شده" else "Selected") else (if (isPersian) "انتخاب" else "Select"),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = onPing,
                    modifier = Modifier.height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldGreen),
                    border = BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    enabled = !isPinging
                ) {
                    Icon(imageVector = Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(text = if (isPersian) "پینگ" else "Ping", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onAudit,
                    modifier = Modifier.height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MetallicGold),
                    border = BorderStroke(1.dp, MetallicGold.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(text = if (isPersian) "بررسی" else "Audit", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.weight(1f))

                IconButton(
                    onClick = onCopy,
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFF141720), RoundedCornerShape(8.dp))
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy URI", tint = TextGoldMuted, modifier = Modifier.size(16.dp))
                }

                IconButton(
                    onClick = onDelete,
                    enabled = canDeleteSingle,
                    modifier = Modifier
                        .size(48.dp)
                        .background(if (canDeleteSingle) Color(0xFF221114) else Color(0xFF15161A), RoundedCornerShape(8.dp))
                        .testTag("delete_config_button_${config.id}")
                ) {
                    Icon(
                        imageVector = if (origin == ConfigOrigin.SYSTEM_MANAGED) Icons.Default.Lock else Icons.Default.Delete,
                        contentDescription = if (origin == ConfigOrigin.SYSTEM_MANAGED) "System Config Protected" else "Delete Config",
                        tint = if (canDeleteSingle) StatusOfflineRed else Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
