package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VerifiedUser
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
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import com.example.data.subscription.ActivationQrCodeBox
import com.example.data.subscription.SubscriptionActivationLinkEngine
import com.example.data.local.CleanIpAddressType
import com.example.data.local.CleanIpEntity
import com.example.data.local.CleanIpHealthStatus
import com.example.data.model.AllocationHistoryRecord
import com.example.data.model.AppReleaseInfo
import com.example.data.model.BackgroundBackendError
import com.example.data.model.CryptoPaymentInvoice
import com.example.data.model.InventoryShortageAlert
import com.example.data.model.ManagedEndpoint
import com.example.data.model.PaymentRequestRecord
import com.example.data.model.ReferralRewardRequest
import com.example.data.model.ReferralRewardStatus
import com.example.data.model.RotationAuditLog
import com.example.data.model.SubscriptionPlanId
import com.example.data.model.SubscriptionStatus
import com.example.data.model.UserProfile
import com.example.data.model.UserSubscription
import com.example.data.subscription.SubscriptionLifecycleEngine
import com.example.ui.components.AdminEcosystemTabContent
import com.example.ui.components.BackgroundBackendErrorBanner
import com.example.ui.theme.BorderGoldHighlight
import com.example.ui.theme.BorderGoldMuted
import com.example.ui.theme.CardSurfaceDark
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.GoldContainer
import com.example.ui.theme.MetallicGold
import com.example.ui.theme.StatusOfflineRed
import com.example.ui.theme.StatusWarningAmber
import com.example.ui.theme.TextGraySecondary
import com.example.ui.theme.TextWhitePrimary
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class AdminSubSection(val titleFa: String, val titleEn: String) {
    SUBSCRIPTIONS("اشتراک‌ها و تاییدها", "Subscriptions"),
    INVENTORY("استخر IP اختصاصی", "Endpoint Pool"),
    SHORTAGES_LOGS("کمبود و لاگ چرخش", "Alerts & Logs"),
    RELEASES_REFERRALS("پاداش و انتشار نسخه", "Rewards & APK")
}

@Composable
fun AdminSubscriptionScreen(
    subscriptions: List<UserSubscription>,
    endpointInventory: List<ManagedEndpoint>,
    paymentRequests: List<PaymentRequestRecord>,
    shortageAlerts: List<InventoryShortageAlert>,
    rotationLogs: List<RotationAuditLog>,
    selectedRotationUserIds: Set<String>,
    trustedNowMs: Long,
    isBusy: Boolean,
    isPersian: Boolean,
    onToggleSelectUser: (String) -> Unit,
    onSelectAllUsers: (List<String>) -> Unit,
    onClearUserSelection: () -> Unit,
    onActivateSubscription: (userId: String, userEmail: String, plan: SubscriptionPlanId, paymentRequestId: String?) -> Unit,
    onRejectPayment: (paymentId: String, reason: String) -> Unit,
    onRenewSubscription: (UserSubscription) -> Unit,
    onChangePlan: (UserSubscription, SubscriptionPlanId) -> Unit,
    onUpdateStatus: (UserSubscription, SubscriptionStatus, String) -> Unit,
    onUpsertEndpoint: (ManagedEndpoint) -> Unit,
    onImportFromNodes: () -> Unit,
    onToggleEndpointHealth: (ManagedEndpoint, enabled: Boolean, healthy: Boolean) -> Unit,
    onResolveShortage: (String) -> Unit,
    onRotateSingleUser: (UserSubscription) -> Unit,
    onRotateSelectedUsers: () -> Unit,
    onRunDailyRotationAll: (forceManual: Boolean) -> Unit,
    inspectedHistoryUserId: String? = null,
    inspectedAllocationHistory: List<AllocationHistoryRecord> = emptyList(),
    onInspectUserAllocationHistory: (String?) -> Unit = {},
    onRecordEndpointFailure: (ManagedEndpoint) -> Unit = {},
    cleanIpPool: List<CleanIpEntity> = emptyList(),
    isProbingCleanIps: Boolean = false,
    onProbeCleanIpPool: (CleanIpAddressType?) -> Unit = {},
    onToggleCleanIpAvailability: (CleanIpEntity, Boolean) -> Unit = { _, _ -> },
    onPromoteHealthyCleanIps: () -> Unit = {},
    registeredUsers: List<UserProfile> = emptyList(),
    onImportBulkCleanIps: (String) -> Unit = {},
    onAddIpOnlyEndpoints: (String) -> Unit = {},
    preselectedGrantUid: String = "",
    preselectedGrantEmail: String = "",
    openGrantTrigger: Int = 0,
    adminRewardRequests: List<ReferralRewardRequest> = emptyList(),
    adminReleases: List<AppReleaseInfo> = emptyList(),
    adminCryptoInvoices: List<CryptoPaymentInvoice> = emptyList(),
    referralBusy: Boolean = false,
    releaseBusy: Boolean = false,
    onReviewReferralReward: (requestId: String, approve: Boolean, note: String) -> Unit = { _, _, _ -> },
    onUploadApkRelease: (fileName: String, bytes: ByteArray, versionName: String, versionCode: Int, notes: String, publish: Boolean) -> Unit = { _, _, _, _, _, _ -> },
    onToggleReleasePublish: (releaseId: String, publish: Boolean) -> Unit = { _, _ -> },
    onRefreshEcosystem: () -> Unit = {},
    apkUploadProgress: Float? = null,
    apkUploadError: String? = null,
    backgroundBackendError: BackgroundBackendError? = null,
    onUploadApkReleaseUri: ((fileUri: Uri, fileName: String, fileSizeBytes: Long, versionName: String, versionCode: Int, notes: String, publish: Boolean) -> Unit)? = null,
    onRetryBackgroundSync: () -> Unit = {},
    onDismissBackgroundError: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var activeSection by remember { mutableStateOf(AdminSubSection.SUBSCRIPTIONS) }
    var cleanIpFilter by remember { mutableStateOf<String>("ALL") }
    var showCleanIpPoolList by remember { mutableStateOf(false) }
    var showGrantModal by remember { mutableStateOf(false) }
    var showAddEndpointModal by remember { mutableStateOf(false) }
    var showBulkCleanIpDialog by remember { mutableStateOf(false) }
    var bulkCleanIpText by remember { mutableStateOf("") }
    var changePlanTarget by remember { mutableStateOf<UserSubscription?>(null) }
    var qrActivationTarget by remember { mutableStateOf<Pair<String, SubscriptionPlanId>?>(null) }

    // Grant manual subscription form state
    var grantUid by remember { mutableStateOf("") }
    var grantEmail by remember { mutableStateOf("") }
    var grantPlan by remember { mutableStateOf(SubscriptionPlanId.ONE_MONTH) }

    LaunchedEffect(openGrantTrigger) {
        if (openGrantTrigger > 0 && (preselectedGrantUid.isNotBlank() || preselectedGrantEmail.isNotBlank())) {
            grantUid = preselectedGrantUid
            grantEmail = preselectedGrantEmail
            activeSection = AdminSubSection.SUBSCRIPTIONS
            showGrantModal = true
        }
    }

    // Strictly IP-only input state (auto port 443, protocol vless/ws/tls, DE/TR rotation)
    var ipOnlyInputText by remember { mutableStateOf("") }

    val pendingRequests = remember(paymentRequests) {
        paymentRequests.filter { it.status == "PENDING_PAYMENT" || it.status == "PENDING_VERIFICATION" }
    }
    val unresolvedAlertsCount = remember(shortageAlerts) {
        shortageAlerts.count { !it.resolved }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("admin_subscription_screen"),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (backgroundBackendError != null) {
            item {
                BackgroundBackendErrorBanner(
                    error = backgroundBackendError,
                    isPersian = isPersian,
                    onRetry = onRetryBackgroundSync,
                    onDismiss = onDismissBackgroundError
                )
            }
        }

        // Section Selector Bar
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AdminSubSection.entries.forEach { section ->
                    val selected = activeSection == section
                    val pendingRewardsCount = adminRewardRequests.count { it.status == ReferralRewardStatus.PENDING }
                    val badge = when (section) {
                        AdminSubSection.SUBSCRIPTIONS -> if (pendingRequests.isNotEmpty()) " (${pendingRequests.size})" else ""
                        AdminSubSection.INVENTORY -> " (${endpointInventory.size})"
                        AdminSubSection.SHORTAGES_LOGS -> if (unresolvedAlertsCount > 0) " ($unresolvedAlertsCount)" else ""
                        AdminSubSection.RELEASES_REFERRALS -> if (pendingRewardsCount > 0) " ($pendingRewardsCount)" else ""
                    }
                    FilterChip(
                        selected = selected,
                        onClick = { activeSection = section },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("admin_sub_tab_${section.name}"),
                        label = {
                            Text(
                                text = (if (isPersian) section.titleFa else section.titleEn) + badge,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
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

        // Daily & Batch Rotation Trigger Toolbar
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, BorderGoldHighlight), RoundedCornerShape(14.dp)),
                colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isPersian) "موتور تخصیص و چرخش روزانه IP" else "Daily Rotation & Allocation Scheduler",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MetallicGold
                            )
                            Text(
                                text = if (isPersian) {
                                    "چرخه امروز (UTC): ${SubscriptionLifecycleEngine.utcCycleKey(trustedNowMs)} • استخر: ${endpointInventory.count { it.healthy && it.enabled }} سالم"
                                } else {
                                    "Cycle ${SubscriptionLifecycleEngine.utcCycleKey(trustedNowMs)} • Pool: ${endpointInventory.count { it.healthy && it.enabled }} healthy"
                                },
                                fontSize = 11.sp,
                                color = TextGraySecondary
                            )
                        }

                        Button(
                            onClick = { showGrantModal = true },
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("admin_grant_subscription_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = Color(0xFF1E1700)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = if (isPersian) "اعطای اشتراک" else "Grant Plan", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onRunDailyRotationAll(false) },
                            enabled = !isBusy,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("admin_run_daily_rotation_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen, contentColor = Color(0xFF021B10)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = if (isPersian) "چرخش روزانه همه (Idempotent)" else "Run Daily Cycle", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { onRunDailyRotationAll(true) },
                            enabled = !isBusy,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("admin_force_rotate_all_btn"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MetallicGold),
                            border = BorderStroke(1.dp, BorderGoldHighlight),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = if (isPersian) "چرخش اجباری همه" else "Force Rotate All", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onRotateSelectedUsers,
                            enabled = !isBusy && selectedRotationUserIds.isNotEmpty(),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("admin_rotate_selected_btn"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricCyan),
                            border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Autorenew, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isPersian) "چرخش انتخابی (${selectedRotationUserIds.size})" else "Rotate Selected (${selectedRotationUserIds.size})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        when (activeSection) {
            AdminSubSection.SUBSCRIPTIONS -> {
                // Pending Payment Verification Queue
                if (pendingRequests.isNotEmpty()) {
                    item {
                        Text(
                            text = if (isPersian) "درخواست‌های پرداخت در انتظار تایید (${pendingRequests.size})" else "Pending Payment Requests (${pendingRequests.size})",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = StatusWarningAmber,
                            modifier = Modifier.testTag("admin_payment_requests_section")
                        )
                    }

                    items(pendingRequests, key = { it.paymentId.ifBlank { it.userId + it.planId } }) { req ->
                        val plan = SubscriptionPlanId.fromId(req.planId)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(BorderStroke(1.dp, StatusWarningAmber.copy(alpha = 0.7f)), RoundedCornerShape(14.dp)),
                            colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${req.userEmail.ifBlank { req.userId }} • ${if (isPersian) plan.titleFa else plan.titleEn}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextWhitePrimary
                                        )
                                        Text(
                                            text = "Action: ${req.actionType} • Ref: ${req.referenceCode.ifBlank { "--" }}",
                                            fontSize = 11.sp,
                                            color = TextGraySecondary
                                        )
                                        if (req.note.isNotBlank()) {
                                            Text(text = req.note, fontSize = 10.sp, color = MetallicGold)
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            onActivateSubscription(req.userId, req.userEmail, plan, req.paymentId)
                                        },
                                        enabled = !isBusy,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("approve_payment_${req.paymentId}"),
                                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen, contentColor = Color(0xFF021B10)),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = if (isPersian) "تایید و فعال‌سازی" else "Approve & Activate", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = { onRejectPayment(req.paymentId, "Rejected by admin") },
                                        enabled = !isBusy,
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("reject_payment_${req.paymentId}"),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusOfflineRed),
                                        border = BorderStroke(1.dp, StatusOfflineRed.copy(alpha = 0.7f)),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = if (isPersian) "رد" else "Reject", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // Active / Registered User Subscriptions List
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isPersian) "لیست اشتراک‌های کاربران (${subscriptions.size})" else "User Subscriptions (${subscriptions.size})",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MetallicGold,
                            modifier = Modifier.testTag("admin_subscriptions_list")
                        )
                        if (subscriptions.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    if (selectedRotationUserIds.isEmpty()) {
                                        onSelectAllUsers(subscriptions.map { it.userId })
                                    } else {
                                        onClearUserSelection()
                                    }
                                }
                            ) {
                                Text(
                                    text = if (selectedRotationUserIds.isEmpty()) {
                                        if (isPersian) "انتخاب همه" else "Select All"
                                    } else {
                                        if (isPersian) "لغو انتخاب" else "Clear"
                                    },
                                    color = MetallicGold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                if (subscriptions.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(BorderStroke(1.dp, BorderGoldMuted), RoundedCornerShape(14.dp)),
                            colors = CardDefaults.cardColors(containerColor = CardSurfaceDark)
                        ) {
                            Text(
                                text = if (isPersian) {
                                    "هنوز هیچ اشتراک ثبت‌شده‌ای وجود ندارد. از دکمه «اعطای اشتراک» استفاده کنید."
                                } else {
                                    "No subscriptions recorded yet. Tap 'Grant Plan' to create one."
                                },
                                fontSize = 12.sp,
                                color = TextGraySecondary,
                                modifier = Modifier.padding(18.dp)
                            )
                        }
                    }
                } else {
                    items(subscriptions, key = { it.subscriptionId.ifBlank { it.userId } }) { sub ->
                        val plan = sub.planEnum()
                        val remaining = SubscriptionLifecycleEngine.remainingDays(sub, trustedNowMs)
                        val isEntitled = SubscriptionLifecycleEngine.isEntitled(sub, trustedNowMs)
                        val statusColor = if (isEntitled) EmeraldGreen else StatusOfflineRed
                        val isChecked = sub.userId in selectedRotationUserIds

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(BorderStroke(1.dp, if (isChecked) MetallicGold else BorderGoldMuted), RoundedCornerShape(14.dp))
                                .testTag("admin_sub_card_${sub.userId}"),
                            colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { onToggleSelectUser(sub.userId) },
                                            modifier = Modifier.minimumInteractiveComponentSize(),
                                            colors = CheckboxDefaults.colors(checkedColor = MetallicGold, checkmarkColor = Color(0xFF1E1700))
                                        )
                                        Column {
                                            Text(
                                                text = sub.userEmail.ifBlank { sub.userId },
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextWhitePrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${if (isPersian) plan.titleFa else plan.titleEn} • ${sub.endpointAllocationLimit} IP/day • v${sub.currentAllocationVersion}",
                                                fontSize = 11.sp,
                                                color = MetallicGold
                                            )
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .background(statusColor.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "${sub.status} (${remaining}d)",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = statusColor
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { onRotateSingleUser(sub) },
                                        enabled = !isBusy && isEntitled,
                                        modifier = Modifier.height(48.dp).testTag("rotate_user_${sub.userId}"),
                                        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.6f)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricCyan)
                                    ) {
                                        Text(text = if (isPersian) "چرخش IP" else "Rotate", fontSize = 11.sp)
                                    }

                                    OutlinedButton(
                                        onClick = { onRenewSubscription(sub) },
                                        enabled = !isBusy,
                                        modifier = Modifier.height(48.dp),
                                        border = BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.6f)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldGreen)
                                    ) {
                                        Text(text = if (isPersian) "تمدید" else "Renew", fontSize = 11.sp)
                                    }

                                    OutlinedButton(
                                        onClick = { changePlanTarget = sub },
                                        enabled = !isBusy,
                                        modifier = Modifier.height(48.dp),
                                        border = BorderStroke(1.dp, BorderGoldHighlight),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MetallicGold)
                                    ) {
                                        Text(text = if (isPersian) "تغییر پلن" else "Change Plan", fontSize = 11.sp)
                                    }

                                    val nextStatus = if (sub.statusEnum() == SubscriptionStatus.ACTIVE) {
                                        SubscriptionStatus.SUSPENDED
                                    } else {
                                        SubscriptionStatus.ACTIVE
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            onUpdateStatus(sub, nextStatus, "Updated via Admin Console")
                                        },
                                        enabled = !isBusy,
                                        modifier = Modifier.height(48.dp),
                                        border = BorderStroke(1.dp, StatusOfflineRed.copy(alpha = 0.6f)),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = if (nextStatus == SubscriptionStatus.SUSPENDED) StatusOfflineRed else EmeraldGreen
                                        )
                                    ) {
                                        Text(
                                            text = if (nextStatus == SubscriptionStatus.SUSPENDED) {
                                                if (isPersian) "تعلیق" else "Suspend"
                                            } else {
                                                if (isPersian) "فعال‌سازی" else "Reactivate"
                                            },
                                            fontSize = 11.sp
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = { onInspectUserAllocationHistory(sub.userId) },
                                        enabled = !isBusy,
                                        modifier = Modifier.height(48.dp).testTag("inspect_alloc_hist_${sub.userId}"),
                                        border = BorderStroke(1.dp, BorderGoldMuted),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhitePrimary)
                                    ) {
                                        Icon(imageVector = Icons.Default.History, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = if (isPersian) "تاریخچه IP" else "IP History", fontSize = 11.sp)
                                    }

                                    OutlinedButton(
                                        onClick = { qrActivationTarget = sub.userId to plan },
                                        modifier = Modifier.height(48.dp).testTag("admin_generate_qr_${sub.userId}"),
                                        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.7f)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricCyan)
                                    ) {
                                        Icon(imageVector = Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = if (isPersian) "کد QR / لینک" else "QR Link", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            AdminSubSection.INVENTORY -> {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_endpoint_inventory_section"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showAddEndpointModal = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("admin_add_endpoint_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = Color(0xFF1E1700)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = if (isPersian) "افزودن IP اختصاصی" else "Add Endpoint", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onImportFromNodes,
                            enabled = !isBusy,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("admin_sync_inventory_btn"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldGreen),
                            border = BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.7f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = if (isPersian) "ورود از نودهای فعال" else "Import from Nodes", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Local Room Clean IP & SNI Edge Pool (150+ entries)
                item {
                    val ipv4Count = remember(cleanIpPool) { cleanIpPool.count { it.addressType == CleanIpAddressType.IPV4.name } }
                    val ipv6Count = remember(cleanIpPool) { cleanIpPool.count { it.addressType == CleanIpAddressType.IPV6.name } }
                    val domainCount = remember(cleanIpPool) { cleanIpPool.count { it.addressType == CleanIpAddressType.DOMAIN.name } }
                    val healthyCleanCount = remember(cleanIpPool) { cleanIpPool.count { it.healthStatus == CleanIpHealthStatus.HEALTHY.name } }
                    val availableCleanCount = remember(cleanIpPool) { cleanIpPool.count { it.isAvailable } }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.45f)), RoundedCornerShape(14.dp))
                            .testTag("admin_clean_ip_pool_card"),
                        colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isPersian) "مخزن IPهای تمیز محلی (Room)" else "Local Clean IP & Edge Pool (Room)",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ElectricCyan
                                    )
                                    Text(
                                        text = if (isPersian) {
                                            "کل: ${cleanIpPool.size} (IPv4: $ipv4Count • IPv6: $ipv6Count • دامنه: $domainCount) | سالم: $healthyCleanCount | آزاد: $availableCleanCount"
                                        } else {
                                            "Total: ${cleanIpPool.size} (v4: $ipv4Count • v6: $ipv6Count • Domain: $domainCount) | Healthy: $healthyCleanCount"
                                        },
                                        fontSize = 10.sp,
                                        color = TextGraySecondary
                                    )
                                }
                                TextButton(onClick = { showCleanIpPoolList = !showCleanIpPoolList }) {
                                    Text(
                                        text = if (showCleanIpPoolList) {
                                            if (isPersian) "بستن لیست" else "Hide"
                                        } else {
                                            if (isPersian) "مشاهده لیست" else "Inspect"
                                        },
                                        color = MetallicGold,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    "ALL" to (if (isPersian) "همه (${cleanIpPool.size})" else "All"),
                                    "IPV4" to "IPv4 ($ipv4Count)",
                                    "IPV6" to "IPv6 ($ipv6Count)",
                                    "DOMAIN" to (if (isPersian) "دامنه ($domainCount)" else "Domain ($domainCount)"),
                                    "HEALTHY" to (if (isPersian) "سالم ($healthyCleanCount)" else "Healthy ($healthyCleanCount)")
                                ).forEach { (key, label) ->
                                    FilterChip(
                                        selected = cleanIpFilter == key,
                                        onClick = { cleanIpFilter = key },
                                        label = { Text(label, fontSize = 10.sp) }
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val targetFilterType = when (cleanIpFilter) {
                                    "IPV4" -> CleanIpAddressType.IPV4
                                    "IPV6" -> CleanIpAddressType.IPV6
                                    "DOMAIN" -> CleanIpAddressType.DOMAIN
                                    else -> null
                                }
                                Button(
                                    onClick = { onProbeCleanIpPool(targetFilterType) },
                                    enabled = !isProbingCleanIps && cleanIpPool.isNotEmpty(),
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("probe_clean_ips_btn"),
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = Color(0xFF041A24)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = if (isProbingCleanIps) {
                                            if (isPersian) "در حال پایش..." else "Probing..."
                                        } else {
                                            if (isPersian) "پایش سلامت (TCP/443)" else "Probe Health"
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                OutlinedButton(
                                    onClick = onPromoteHealthyCleanIps,
                                    enabled = !isBusy && healthyCleanCount > 0,
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("promote_healthy_clean_ips_btn"),
                                    border = BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.7f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldGreen),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = if (isPersian) "ارسال سالم‌ها به استخر" else "Promote Healthy",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = { showBulkCleanIpDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("admin_bulk_clean_ip_btn"),
                                border = BorderStroke(1.dp, MetallicGold.copy(alpha = 0.65f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MetallicGold),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isPersian) "افزودن رنج /24 و IP دسته‌جمعی" else "Bulk Import IPs / CIDR /24",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                if (showCleanIpPoolList && cleanIpPool.isNotEmpty()) {
                    val filteredCleanIps = cleanIpPool.filter { item ->
                        when (cleanIpFilter) {
                            "IPV4" -> item.addressType == CleanIpAddressType.IPV4.name
                            "IPV6" -> item.addressType == CleanIpAddressType.IPV6.name
                            "DOMAIN" -> item.addressType == CleanIpAddressType.DOMAIN.name
                            "HEALTHY" -> item.healthStatus == CleanIpHealthStatus.HEALTHY.name
                            else -> true
                        }
                    }.take(60)

                    items(filteredCleanIps, key = { "clean_${it.host}" }) { item ->
                        val statusEnum = item.healthStatusEnum()
                        val badgeColor = when (statusEnum) {
                            CleanIpHealthStatus.HEALTHY -> EmeraldGreen
                            CleanIpHealthStatus.DEGRADED -> StatusWarningAmber
                            CleanIpHealthStatus.UNHEALTHY, CleanIpHealthStatus.QUARANTINED -> StatusOfflineRed
                            CleanIpHealthStatus.UNTESTED -> TextGraySecondary
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(BorderStroke(0.8.dp, badgeColor.copy(alpha = 0.45f)), RoundedCornerShape(10.dp)),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF131823))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.host,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextWhitePrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val pingText = if (item.latencyMs > 0) "${item.latencyMs}ms" else "--"
                                    Text(
                                        text = "${item.addressTypeEnum().labelEn} • ${if (isPersian) statusEnum.labelFa else statusEnum.labelEn} • Ping: $pingText",
                                        fontSize = 10.sp,
                                        color = badgeColor
                                    )
                                }
                                Switch(
                                    checked = item.isAvailable,
                                    onCheckedChange = { checked -> onToggleCleanIpAvailability(item, checked) },
                                    colors = SwitchDefaults.colors(checkedThumbColor = EmeraldGreen)
                                )
                            }
                        }
                    }
                }

                if (endpointInventory.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(BorderStroke(1.dp, BorderGoldMuted), RoundedCornerShape(14.dp)),
                            colors = CardDefaults.cardColors(containerColor = CardSurfaceDark)
                        ) {
                            Text(
                                text = if (isPersian) {
                                    "مخزن مرکزی IP اختصاصی خالی است. نودهای واقعی را اضافه یا از سرورها وارد کنید (بدون نگهداری رمزهای خصوصی)."
                                } else {
                                    "Central endpoint inventory is empty. Add or import healthy endpoints (no private credentials stored)."
                                },
                                fontSize = 12.sp,
                                color = TextGraySecondary,
                                modifier = Modifier.padding(18.dp)
                            )
                        }
                    }
                } else {
                    items(endpointInventory, key = { it.endpointId.ifBlank { it.host + it.port } }) { ep ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    BorderStroke(
                                        1.dp,
                                        if (ep.healthy && ep.enabled) BorderGoldMuted else StatusOfflineRed.copy(alpha = 0.6f)
                                    ),
                                    RoundedCornerShape(12.dp)
                                ),
                            colors = CardDefaults.cardColors(containerColor = CardSurfaceDark)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${ep.name} (${ep.countryCode}) • Tier ${ep.priorityTier}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextWhitePrimary
                                    )
                                    Text(
                                        text = "${ep.protocol.uppercase()} • ${ep.host}:${ep.port} • Score ${ep.stabilityScore}%",
                                        fontSize = 10.sp,
                                        color = TextGraySecondary
                                    )
                                    Text(
                                        text = if (ep.assignedUserId.isNotBlank()) {
                                            if (isPersian) "تخصیص اختصاصی به: ${ep.assignedUserId}" else "Assigned to: ${ep.assignedUserId}"
                                        } else {
                                            if (isPersian) "وضعیت: آزاد و آماده تخصیص" else "Status: Unassigned Pool"
                                        },
                                        fontSize = 10.sp,
                                        color = if (ep.assignedUserId.isNotBlank()) ElectricCyan else EmeraldGreen
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (ep.assignedUserId.isNotBlank()) {
                                        OutlinedButton(
                                            onClick = { onRecordEndpointFailure(ep) },
                                            enabled = !isBusy,
                                            modifier = Modifier.height(48.dp),
                                            border = BorderStroke(1.dp, StatusWarningAmber.copy(alpha = 0.7f)),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusWarningAmber)
                                        ) {
                                            Text(text = if (isPersian) "جایگزینی" else "Failover", fontSize = 10.sp)
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = if (isPersian) "سالم" else "Healthy", fontSize = 9.sp, color = TextGraySecondary)
                                        Switch(
                                            checked = ep.healthy,
                                            onCheckedChange = { newHealthy ->
                                                onToggleEndpointHealth(ep, ep.enabled, newHealthy)
                                            },
                                            colors = SwitchDefaults.colors(checkedThumbColor = EmeraldGreen)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            AdminSubSection.SHORTAGES_LOGS -> {
                item {
                    Text(
                        text = if (isPersian) "هشدارهای کمبود ظرفیت (${shortageAlerts.size})" else "Capacity Shortage Alerts (${shortageAlerts.size})",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = StatusWarningAmber,
                        modifier = Modifier.testTag("admin_shortage_alerts_section")
                    )
                }

                if (shortageAlerts.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = CardSurfaceDark)
                        ) {
                            Text(
                                text = if (isPersian) "هیچ کمبود ظرفیتی گزارش نشده است." else "No inventory shortages detected.",
                                fontSize = 12.sp,
                                color = TextGraySecondary,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                } else {
                    items(shortageAlerts, key = { it.alertId }) { alert ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(BorderStroke(1.dp, if (alert.resolved) BorderGoldMuted else StatusOfflineRed), RoundedCornerShape(12.dp)),
                            colors = CardDefaults.cardColors(containerColor = CardSurfaceDark)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "User: ${alert.userId} • ${alert.allocatedCount}/${alert.requestedCount} (کمبود: ${alert.shortfallCount})",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (alert.resolved) EmeraldGreen else StatusOfflineRed
                                    )
                                    Text(text = alert.reason, fontSize = 10.sp, color = TextGraySecondary)
                                }
                                if (!alert.resolved) {
                                    OutlinedButton(
                                        onClick = { onResolveShortage(alert.alertId) },
                                        modifier = Modifier.height(48.dp)
                                    ) {
                                        Text(text = if (isPersian) "بستن" else "Resolve", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isPersian) "گزارش ممیزی چرخش‌های روزانه و دستی (${rotationLogs.size})" else "Rotation Audit Logs (${rotationLogs.size})",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MetallicGold,
                        modifier = Modifier.testTag("admin_rotation_logs_section")
                    )
                }

                items(rotationLogs.take(20), key = { it.logId.ifBlank { it.cycleKey + it.triggerMode + it.details.hashCode() } }) { log ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(BorderStroke(0.8.dp, BorderGoldMuted), RoundedCornerShape(10.dp)),
                        colors = CardDefaults.cardColors(containerColor = CardSurfaceDark)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${log.triggerMode} • ${log.cycleKey}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MetallicGold
                                )
                                Text(
                                    text = log.status,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (log.status == "SUCCESS") EmeraldGreen else StatusWarningAmber
                                )
                            }
                            Text(text = log.details, fontSize = 11.sp, color = TextWhitePrimary)
                        }
                    }
                }
            }

            AdminSubSection.RELEASES_REFERRALS -> {
                item {
                    AdminEcosystemTabContent(
                        rewardRequests = adminRewardRequests,
                        releases = adminReleases,
                        cryptoInvoices = adminCryptoInvoices,
                        referralBusy = referralBusy,
                        releaseBusy = releaseBusy,
                        isPersian = isPersian,
                        onReviewReferralReward = onReviewReferralReward,
                        onUploadApkRelease = onUploadApkRelease,
                        onToggleReleasePublish = onToggleReleasePublish,
                        onRefreshEcosystem = onRefreshEcosystem,
                        apkUploadProgress = apkUploadProgress,
                        apkUploadError = apkUploadError,
                        backgroundBackendError = null,
                        onUploadApkReleaseUri = onUploadApkReleaseUri,
                        onRetryBackgroundSync = onRetryBackgroundSync,
                        onDismissBackgroundError = onDismissBackgroundError
                    )
                }
            }
        }
    }

    // Grant / Activate Subscription Modal
    if (showGrantModal) {
        AlertDialog(
            onDismissRequest = { showGrantModal = false },
            containerColor = CardSurfaceDark,
            modifier = Modifier.testTag("admin_grant_subscription_dialog"),
            title = {
                Text(
                    text = if (isPersian) "فعالسازی / اعطای اشتراک جدید" else "Activate User Subscription",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MetallicGold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (registeredUsers.isNotEmpty()) {
                        Text(
                            text = if (isPersian) "انتخاب سریع کاربر ثبت‌نام‌شده:" else "Quick-Select Registered User:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElectricCyan
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .testTag("grant_registered_users_chips"),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            registeredUsers.forEach { user ->
                                val selected = grantUid == user.userId
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        grantUid = user.userId
                                        grantEmail = user.email
                                    },
                                    label = {
                                        val labelName = user.displayName.ifBlank { user.email.substringBefore("@").ifBlank { user.userId } }
                                        Text(
                                            text = "$labelName (${user.email.ifBlank { user.userId }})",
                                            fontSize = 10.sp
                                        )
                                    }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = grantUid,
                        onValueChange = { grantUid = it },
                        label = { Text(if (isPersian) "شناسه کاربر (UID)" else "Target User UID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("grant_uid_input"),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MetallicGold, unfocusedBorderColor = BorderGoldMuted, focusedTextColor = TextWhitePrimary, unfocusedTextColor = TextWhitePrimary)
                    )
                    OutlinedTextField(
                        value = grantEmail,
                        onValueChange = { grantEmail = it },
                        label = { Text(if (isPersian) "ایمیل کاربر" else "User Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("grant_email_input"),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MetallicGold, unfocusedBorderColor = BorderGoldMuted, focusedTextColor = TextWhitePrimary, unfocusedTextColor = TextWhitePrimary)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SubscriptionPlanId.entries.forEach { plan ->
                            FilterChip(
                                selected = grantPlan == plan,
                                onClick = { grantPlan = plan },
                                label = { Text(if (isPersian) "${plan.durationDays} روز (${plan.dailyAllocationLimit} IP)" else plan.titleEn, fontSize = 11.sp) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                qrActivationTarget = grantUid.trim() to grantPlan
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("admin_generate_activation_qr_btn"),
                            border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.75f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricCyan)
                        ) {
                            Icon(imageVector = Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = if (isPersian) "ساخت QR و لینک" else "Generate QR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (grantUid.isNotBlank()) {
                            onActivateSubscription(grantUid.trim(), grantEmail.trim(), grantPlan, null)
                            showGrantModal = false
                            grantUid = ""
                            grantEmail = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen, contentColor = Color(0xFF021B10))
                ) {
                    Text(text = if (isPersian) "تایید و تخصیص IP" else "Activate & Allocate", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showGrantModal = false }) {
                    Text(text = if (isPersian) "انصراف" else "Cancel", color = TextGraySecondary)
                }
            }
        )
    }

    // Strictly IP-Only Endpoint Entry Modal (No Port / CC / Protocol Boilerplate)
    if (showAddEndpointModal) {
        AlertDialog(
            onDismissRequest = { showAddEndpointModal = false },
            containerColor = CardSurfaceDark,
            modifier = Modifier.testTag("admin_add_ip_only_dialog"),
            title = {
                Text(
                    text = if (isPersian) "افزودن IP اختصاصی (فقط آدرس IP)" else "Add Endpoints (IP-Only)",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MetallicGold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (isPersian) {
                            "فقط آدرس IP (IPv4، IPv6 یا رنج /24) را وارد کنید — بدون نیاز به پورت یا پروتکل (پورت 443 و چرخش DE/TR خودکار تنظیم می‌شود)."
                        } else {
                            "Enter IP addresses only (IPv4, IPv6, or /24 CIDR). Port 443 & TLS routing are applied automatically."
                        },
                        fontSize = 11.sp,
                        color = TextGraySecondary
                    )
                    OutlinedTextField(
                        value = ipOnlyInputText,
                        onValueChange = { ipOnlyInputText = it },
                        label = { Text(if (isPersian) "فقط آدرس IP (تکی یا چند خطی)" else "IP Address(es) Only") },
                        placeholder = { Text("172.66.161.168\n104.19.104.48\n2606:4700:3031::6815:fb6", fontSize = 11.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .testTag("admin_add_ip_only_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MetallicGold,
                            unfocusedBorderColor = BorderGoldMuted,
                            focusedTextColor = TextWhitePrimary,
                            unfocusedTextColor = TextWhitePrimary
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = ipOnlyInputText.trim()
                        if (trimmed.isNotBlank()) {
                            onAddIpOnlyEndpoints(trimmed)
                            // Also notify single-endpoint listeners when adding a single host
                            val firstHost = trimmed.lines().firstOrNull()?.trim().orEmpty()
                            if (firstHost.isNotBlank() && !firstHost.contains("/")) {
                                onUpsertEndpoint(
                                    ManagedEndpoint(
                                        name = "DE-$firstHost",
                                        country = "Germany",
                                        countryCode = "DE",
                                        host = firstHost,
                                        port = 443,
                                        protocol = "vless",
                                        transport = "ws",
                                        security = "tls",
                                        enabled = true,
                                        healthy = true,
                                        priorityTier = 3,
                                        stabilityScore = 95
                                    )
                                )
                            }
                            showAddEndpointModal = false
                            ipOnlyInputText = ""
                        }
                    },
                    modifier = Modifier.testTag("admin_add_ip_only_confirm_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = Color(0xFF1E1700))
                ) {
                    Text(text = if (isPersian) "ثبت IP" else "Save IPs", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddEndpointModal = false }) {
                    Text(text = if (isPersian) "انصراف" else "Cancel", color = TextGraySecondary)
                }
            }
        )
    }

    // Activation QR Code & Deep Link Share Dialog
    qrActivationTarget?.let { (targetUid, targetPlan) ->
        val refCode = remember(targetUid, targetPlan) {
            "TMS-${(System.currentTimeMillis() % 900000L) + 100000L}"
        }
        val activationUrl = remember(targetUid, targetPlan, refCode) {
            SubscriptionActivationLinkEngine.buildActivationUrl(
                planId = targetPlan.id,
                targetUserId = targetUid.ifBlank { null },
                referenceCode = refCode,
                actorId = "admin_console"
            )
        }
        AlertDialog(
            onDismissRequest = { qrActivationTarget = null },
            containerColor = CardSurfaceDark,
            modifier = Modifier.testTag("admin_activation_qr_dialog"),
            title = {
                Text(
                    text = if (isPersian) "کد QR و لینک فعال‌سازی تک‌ضرب" else "1-Tap Activation QR & Link",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = ElectricCyan
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ActivationQrCodeBox(
                        payload = activationUrl,
                        size = 196.dp,
                        modifier = Modifier.testTag("admin_activation_qr_canvas")
                    )
                    Text(
                        text = if (isPersian) {
                            "پلن: ${targetPlan.titleFa} • کد ارجاع: $refCode"
                        } else {
                            "Plan: ${targetPlan.titleEn} • Ref: $refCode"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MetallicGold
                    )
                    Text(
                        text = activationUrl,
                        fontSize = 10.sp,
                        color = TextGraySecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(activationUrl))
                            },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("admin_copy_activation_link_btn"),
                            border = BorderStroke(1.dp, ElectricCyan),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricCyan)
                        ) {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = if (isPersian) "کپی لینک" else "Copy", fontSize = 11.sp)
                        }
                        Button(
                            onClick = {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    putExtra(Intent.EXTRA_TEXT, activationUrl)
                                    type = "text/plain"
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                runCatching {
                                    context.startActivity(Intent.createChooser(sendIntent, "TMS VPN Activation"))
                                }
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen, contentColor = Color(0xFF021B10))
                        ) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = if (isPersian) "ارسال لینک" else "Share", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { qrActivationTarget = null }) {
                    Text(text = if (isPersian) "بستن" else "Close", color = MetallicGold)
                }
            }
        )
    }

    // Upgrade / Downgrade Target Subscription Modal
    changePlanTarget?.let { targetSub ->
        AlertDialog(
            onDismissRequest = { changePlanTarget = null },
            containerColor = CardSurfaceDark,
            title = {
                Text(
                    text = if (isPersian) "تغییر پلن اشتراک کاربر" else "Change Subscription Plan",
                    color = MetallicGold,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SubscriptionPlanId.entries.forEach { plan ->
                        Button(
                            onClick = {
                                onChangePlan(targetSub, plan)
                                changePlanTarget = null
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GoldContainer, contentColor = MetallicGold)
                        ) {
                            Text(
                                text = if (isPersian) "${plan.titleFa} (${plan.dailyAllocationLimit} IP/روز)" else "${plan.titleEn} (${plan.dailyAllocationLimit} IPs/day)",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { changePlanTarget = null }) {
                    Text(text = if (isPersian) "بستن" else "Close", color = TextGraySecondary)
                }
            }
        )
    }

    // Per-User IP Allocation History Inspection Modal
    if (!inspectedHistoryUserId.isNullOrBlank()) {
        AlertDialog(
            onDismissRequest = { onInspectUserAllocationHistory(null) },
            containerColor = CardSurfaceDark,
            title = {
                Text(
                    text = if (isPersian) "تاریخچه تخصیص IP کاربر" else "User IP Allocation History",
                    color = MetallicGold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "UID: $inspectedHistoryUserId",
                        fontSize = 11.sp,
                        color = ElectricCyan
                    )
                    if (inspectedAllocationHistory.isEmpty()) {
                        Text(
                            text = if (isPersian) "سابقه‌ای برای این کاربر ثبت نشده است." else "No allocation history recorded for this user.",
                            fontSize = 12.sp,
                            color = TextGraySecondary
                        )
                    } else {
                        inspectedAllocationHistory.take(12).forEach { rec ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF141821))
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = "${rec.action} • ${rec.endpointName.ifBlank { rec.endpointId }} (v${rec.allocationVersion})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (rec.action == "ALLOCATED") EmeraldGreen else StatusWarningAmber
                                    )
                                    Text(
                                        text = "${rec.host}:${rec.port} • ${rec.cycleKey}",
                                        fontSize = 10.sp,
                                        color = TextWhitePrimary
                                    )
                                    if (rec.reason.isNotBlank()) {
                                        Text(text = rec.reason, fontSize = 10.sp, color = TextGraySecondary)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { onInspectUserAllocationHistory(null) }) {
                    Text(text = if (isPersian) "بستن" else "Close", color = MetallicGold)
                }
            }
        )
    }

    if (showBulkCleanIpDialog) {
        AlertDialog(
            onDismissRequest = { showBulkCleanIpDialog = false },
            containerColor = CardSurfaceDark,
            modifier = Modifier.testTag("admin_bulk_clean_ip_dialog"),
            title = {
                Text(
                    text = if (isPersian) "افزودن رنج /24 و IPهای تمیز دسته‌جمعی" else "Bulk Import Clean IPs & /24 Ranges",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = ElectricCyan
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (isPersian) {
                            "هر خط یک IPv4، IPv6، دامنه یا رنج CIDR (مثلاً 104.16.190.0/24) وارد کنید:"
                        } else {
                            "Enter one IPv4, IPv6, domain, or CIDR /24 range per line (e.g. 104.16.190.0/24):"
                        },
                        fontSize = 11.sp,
                        color = TextGraySecondary
                    )
                    OutlinedTextField(
                        value = bulkCleanIpText,
                        onValueChange = { bulkCleanIpText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .testTag("admin_bulk_clean_ip_input"),
                        placeholder = { Text("104.16.190.0/24\n172.67.127.156\n2606:4700:3037::6815:2826", fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricCyan,
                            unfocusedBorderColor = BorderGoldMuted,
                            focusedTextColor = TextWhitePrimary,
                            unfocusedTextColor = TextWhitePrimary
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (bulkCleanIpText.isNotBlank()) {
                            onImportBulkCleanIps(bulkCleanIpText)
                            bulkCleanIpText = ""
                            showBulkCleanIpDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = Color(0xFF041A24))
                ) {
                    Text(text = if (isPersian) "افزودن به مخزن" else "Import to Pool", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkCleanIpDialog = false }) {
                    Text(text = if (isPersian) "انصراف" else "Cancel", color = TextGraySecondary)
                }
            }
        )
    }
}
