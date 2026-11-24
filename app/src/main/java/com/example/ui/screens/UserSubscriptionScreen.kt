package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AllocatedEndpointRecord
import com.example.data.model.AllocationHistoryRecord
import com.example.data.model.PaymentRequestRecord
import com.example.data.model.SubscriptionHistoryEvent
import com.example.data.model.SubscriptionPlanId
import com.example.data.model.SubscriptionStatus
import com.example.data.model.UserProfile
import com.example.data.model.UserSubscription
import com.example.data.subscription.SubscriptionLifecycleEngine
import com.example.ui.components.CyberGlassCard
import com.example.ui.components.formatCyberBytes
import com.example.ui.theme.DeepGraphite
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.GlassBorderCyan
import com.example.ui.theme.GlassBorderMetallic
import com.example.ui.theme.GlassBorderSubtle
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MetallicSilver
import com.example.ui.theme.PremiumTeal
import com.example.ui.theme.SecondarySurface
import com.example.ui.theme.SilverHighlight
import com.example.ui.theme.StatusConnected
import com.example.ui.theme.StatusConnecting
import com.example.ui.theme.StatusError
import com.example.ui.theme.TextCyberPrimary
import com.example.ui.theme.TextCyberSecondary
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.QrCodeScanner
import com.example.data.model.BackgroundBackendError
import com.example.data.model.CryptoAsset
import com.example.data.model.CryptoPaymentInvoice
import com.example.data.model.FreeTrialStatus
import com.example.data.model.ReferralDashboardData
import com.example.data.subscription.ActivationQrCodeBox
import com.example.data.subscription.SubscriptionActivationLinkEngine
import com.example.ui.components.BackgroundBackendErrorBanner
import com.example.ui.components.CryptoInvoicesHistoryCard
import com.example.ui.components.CryptoPaymentSheetDialog
import com.example.ui.components.FreeTrialCard
import com.example.ui.components.ReferralDashboardCard

@Composable
fun UserSubscriptionScreen(
    userProfile: UserProfile?,
    isPersian: Boolean,
    modifier: Modifier = Modifier,
    subscription: UserSubscription? = null,
    allocatedEndpoints: List<AllocatedEndpointRecord> = emptyList(),
    allocationHistory: List<AllocationHistoryRecord> = emptyList(),
    history: List<SubscriptionHistoryEvent> = emptyList(),
    paymentRequests: List<PaymentRequestRecord> = emptyList(),
    trustedNowMs: Long = System.currentTimeMillis(),
    isOfflineCached: Boolean = false,
    isBusy: Boolean = false,
    onSubmitPlanRequest: (plan: SubscriptionPlanId, actionType: String, referenceCode: String, note: String) -> Unit = { _, _, _, _ -> },
    onConnectEndpoint: (AllocatedEndpointRecord) -> Unit = {},
    onActivateByLink: (String) -> Unit = {},
    freeTrialStatus: FreeTrialStatus? = null,
    trialBusy: Boolean = false,
    cryptoInvoices: List<CryptoPaymentInvoice> = emptyList(),
    activeCryptoInvoice: CryptoPaymentInvoice? = null,
    cryptoBusy: Boolean = false,
    referralDashboard: ReferralDashboardData? = null,
    referralBusy: Boolean = false,
    telegramLinked: Boolean = false,
    onActivateFreeTrial: () -> Unit = {},
    onCreateCryptoInvoice: (SubscriptionPlanId, CryptoAsset) -> Unit = { _, _ -> },
    onVerifyCryptoInvoice: (invoiceId: String, txHash: String) -> Unit = { _, _ -> },
    onSelectCryptoInvoice: (CryptoPaymentInvoice) -> Unit = {},
    onDismissCryptoInvoice: () -> Unit = {},
    onClaimReferralReward: (Int) -> Unit = {},
    onRefreshBackend: () -> Unit = {},
    backgroundBackendError: BackgroundBackendError? = null,
    onDismissBackgroundError: () -> Unit = {}
) {
    val context = LocalContext.current
    var pendingPlanModal by remember { mutableStateOf<Pair<SubscriptionPlanId, String>?>(null) }
    var paymentRefCode by remember { mutableStateOf("") }
    var paymentNote by remember { mutableStateOf("") }
    var showLinkActivationModal by remember { mutableStateOf(false) }
    var activationLinkInput by remember { mutableStateOf("") }
    var showCryptoDialog by remember { mutableStateOf(false) }
    var cryptoInitialPlan by remember { mutableStateOf(SubscriptionPlanId.ONE_MONTH) }

    val isEntitled = SubscriptionLifecycleEngine.isEntitled(subscription, trustedNowMs)
    val remainingDays = SubscriptionLifecycleEngine.remainingDays(subscription, trustedNowMs)
    val activePlan = subscription?.planEnum()

    val subStatus = subscription?.statusEnum()
    val statusColor = when (subStatus) {
        SubscriptionStatus.ACTIVE -> if (isEntitled) StatusConnected else StatusError
        SubscriptionStatus.PENDING_PAYMENT -> StatusConnecting
        SubscriptionStatus.EXPIRED, SubscriptionStatus.SUSPENDED, SubscriptionStatus.CANCELLED -> StatusError
        null -> MetallicSilver
    }
    val statusLabel = when {
        subStatus == null -> if (isPersian) "بدون اشتراک تاییدشده" else "NO ACTIVE PLAN"
        subStatus == SubscriptionStatus.ACTIVE && !isEntitled -> if (isPersian) "منقضی‌شده (زمان سرور)" else "EXPIRED"
        else -> if (isPersian) subStatus.titleFa else subStatus.name
    }

    val healthyEndpointCount = allocatedEndpoints.count { it.healthy && it.allocationStatus == "ACTIVE" }
    val unhealthyEndpointCount = allocatedEndpoints.count { !it.healthy || it.allocationStatus != "ACTIVE" }
    val dailyLimit = subscription?.endpointAllocationLimit ?: 0

    val latestPendingRequest = paymentRequests.firstOrNull {
        it.status == "PENDING_PAYMENT" || it.status == "PENDING_VERIFICATION"
    }

    val usedBytes = userProfile?.dataUsageBytes?.toLong()?.coerceAtLeast(0L) ?: 0L
    val limitBytes = userProfile?.dataLimitBytes?.toLong()?.coerceAtLeast(1L) ?: 0L
    val remainingBytes = (limitBytes - usedBytes).coerceAtLeast(0L)
    val usageProgress = if (limitBytes > 0L) (usedBytes.toFloat() / limitBytes.toFloat()).coerceIn(0f, 1f) else 0f

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("user_subscription_screen"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Brushed Metallic Membership Card (Current Subscription Summary)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF172230), Color(0xFF0E1620), Color(0xFF13242E))
                        )
                    )
                    .border(
                        BorderStroke(
                            1.2.dp,
                            Brush.linearGradient(
                                listOf(
                                    ElectricCyan.copy(alpha = 0.7f),
                                    SilverHighlight.copy(alpha = 0.2f),
                                    PremiumTeal.copy(alpha = 0.6f)
                                )
                            )
                        ),
                        RoundedCornerShape(22.dp)
                    )
                    .padding(18.dp)
                    .testTag("current_subscription_card")
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VerifiedUser,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (subscription != null && activePlan != null) {
                                        if (isPersian) activePlan.titleFa else activePlan.titleEn
                                    } else {
                                        if (isPersian) "وضعیت اشتراک TMS VPN" else "TMS VPN Subscription"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextCyberPrimary
                                )
                                Text(
                                    text = userProfile?.email?.takeIf { it.isNotBlank() }
                                        ?: subscription?.userEmail?.takeIf { it.isNotBlank() }
                                        ?: (if (isPersian) "حساب کاربری فعال" else "Active Account"),
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 11.sp,
                                    color = MetallicSilver
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(statusColor.copy(alpha = 0.16f))
                                .border(BorderStroke(1.dp, statusColor.copy(alpha = 0.55f)), RoundedCornerShape(999.dp))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                .testTag("subscription_status_badge")
                        ) {
                            Text(
                                text = statusLabel,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusColor
                            )
                        }
                    }

                    // Lifecycle Metrics Grid (Duration, Activation, Expiration, Remaining Days)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DeepGraphite.copy(alpha = 0.7f))
                            .border(BorderStroke(0.6.dp, GlassBorderSubtle), RoundedCornerShape(12.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SubscriptionMetricColumn(
                            label = if (isPersian) "مدت پلن" else "Duration",
                            value = if (subscription != null) "${subscription.planDurationDays}d" else "--",
                            accent = TextCyberPrimary
                        )
                        SubscriptionMetricColumn(
                            label = if (isPersian) "فعالسازی" else "Activated",
                            value = formatSubDate(subscription?.startDate),
                            accent = MetallicSilver
                        )
                        SubscriptionMetricColumn(
                            label = if (isPersian) "انقضا" else "Expires",
                            value = formatSubDate(subscription?.expirationDate),
                            accent = MetallicSilver
                        )
                        SubscriptionMetricColumn(
                            label = if (isPersian) "باقیمانده" else "Remaining",
                            value = if (isPersian) "$remainingDays روز" else "${remainingDays}d",
                            accent = if (remainingDays > 5) StatusConnected else if (remainingDays > 0) StatusConnecting else StatusError
                        )
                    }

                    // Daily Dedicated IP Quota & Endpoint Health Summary
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ElevatedSurface.copy(alpha = 0.75f))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isPersian) "سهمیه روزانه IP اختصاصی" else "Daily Dedicated Endpoint Quota",
                                fontSize = 11.sp,
                                color = TextCyberSecondary
                            )
                            Text(
                                text = if (dailyLimit > 0) {
                                    if (isPersian) "${allocatedEndpoints.size} از $dailyLimit نود تخصیص‌یافته امروز"
                                    else "${allocatedEndpoints.size} / $dailyLimit allocated today"
                                } else {
                                    if (isPersian) "نیازمند اشتراک تاییدشده" else "Requires confirmed plan"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElectricCyan
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = if (isPersian) "سلامت نودها" else "Endpoint Health",
                                fontSize = 10.sp,
                                color = TextCyberSecondary
                            )
                            Text(
                                text = if (isPersian) "$healthyEndpointCount سالم • $unhealthyEndpointCount ناپایدار" else "$healthyEndpointCount OK • $unhealthyEndpointCount degraded",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (healthyEndpointCount > 0) StatusConnected else MetallicSilver
                            )
                        }
                    }

                    // Traffic Consumption & Devices Quota (preserved)
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isPersian) "حجم باقیمانده" else "Remaining Traffic",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextCyberSecondary
                            )
                            Text(
                                text = if (limitBytes > 0L) "${formatCyberBytes(remainingBytes)} / ${formatCyberBytes(limitBytes)}" else "-- / --",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElectricCyan
                            )
                        }

                        LinearProgressIndicator(
                            progress = { 1f - usageProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(999.dp)),
                            color = ElectricCyan,
                            trackColor = ElevatedSurface
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = (if (isPersian) "مصرف‌شده: " else "Used: ") + formatCyberBytes(usedBytes),
                                fontSize = 11.sp,
                                color = MetallicSilver
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Filled.Devices, contentDescription = null, tint = PremiumTeal, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (userProfile != null) "${userProfile.deviceCount} / ${userProfile.maxDevices}" else "-- / --",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PremiumTeal
                                )
                            }
                        }
                    }

                    // Action Buttons for Active/Existing Subscription (Renew / Upgrade)
                    if (subscription != null && activePlan != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    pendingPlanModal = activePlan to "RENEW"
                                },
                                enabled = !isBusy,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("renew_subscription_button"),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, ElectricCyan),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricCyan)
                            ) {
                                Icon(imageVector = Icons.Filled.Autorenew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = if (isPersian) "تمدید پلن" else "Renew", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            val nextTier = when (activePlan) {
                                SubscriptionPlanId.ONE_MONTH -> SubscriptionPlanId.THREE_MONTHS
                                SubscriptionPlanId.THREE_MONTHS -> SubscriptionPlanId.ONE_YEAR
                                SubscriptionPlanId.ONE_YEAR -> SubscriptionPlanId.ONE_YEAR
                            }
                            if (activePlan != SubscriptionPlanId.ONE_YEAR) {
                                Button(
                                    onClick = {
                                        pendingPlanModal = nextTier to "UPGRADE"
                                    },
                                    enabled = !isBusy,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("upgrade_subscription_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PremiumTeal, contentColor = DeepGraphite)
                                ) {
                                    Icon(imageVector = Icons.Filled.TrendingUp, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = if (isPersian) "ارتقا پلن" else "Upgrade", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = { showLinkActivationModal = true },
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("user_activate_by_link_btn"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.65f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricCyan)
                    ) {
                        Icon(imageVector = Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isPersian) "فعال‌سازی با لینک / کد QR" else "Activate via Link / QR",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            if (backgroundBackendError != null) {
                BackgroundBackendErrorBanner(
                    error = backgroundBackendError,
                    isPersian = isPersian,
                    onRetry = onRefreshBackend,
                    onDismiss = onDismissBackgroundError
                )
            }

            // 1-Day Free Trial (7-Day Server-Enforced Cooldown)
            FreeTrialCard(
                trialStatus = freeTrialStatus,
                trustedNowMs = trustedNowMs,
                isBusy = trialBusy,
                isPersian = isPersian,
                onActivateFreeTrial = onActivateFreeTrial
            )

            // Pending Server Verification Notice (Only shown when unconfirmed payment request exists)
            if (latestPendingRequest != null) {
                val reqPlan = SubscriptionPlanId.fromId(latestPendingRequest.planId)
                CyberGlassCard(
                    accentColor = StatusConnecting,
                    contentPadding = 14.dp,
                    modifier = Modifier.testTag("pending_payment_notice_card")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Filled.HourglassTop, contentDescription = null, tint = StatusConnecting, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isPersian) {
                                    "درخواست «${reqPlan.titleFa}» در انتظار تایید سرور/پرداخت است"
                                } else {
                                    "Request for ${reqPlan.titleEn} is PENDING_PAYMENT"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = StatusConnecting
                            )
                            Text(
                                text = if (isPersian) {
                                    "کد پیگیری: ${latestPendingRequest.referenceCode.ifBlank { "ثبت‌شده" }} • تا پیش از تایید معتبر سرور، پلن فعال نمایش داده نمی‌شود."
                                } else {
                                    "Ref: ${latestPendingRequest.referenceCode.ifBlank { "Submitted" }} • Plan will not show active until server confirmation."
                                },
                                fontSize = 11.sp,
                                color = TextCyberSecondary
                            )
                        }
                    }
                }
            }

            // 3-Way Purchase Options Hub (In-App Plan Request + Rubika Channel + Telegram Channel)
            CyberGlassCard(
                accentColor = PremiumTeal.copy(alpha = 0.55f),
                contentPadding = 14.dp,
                modifier = Modifier.testTag("subscription_purchase_hub_card")
            ) {
                Text(
                    text = if (isPersian) "روش‌های خرید و تهیه اشتراک" else "How to Purchase a Subscription",
                    style = MaterialTheme.typography.titleSmall,
                    color = PremiumTeal
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isPersian) {
                        "می‌توانید به‌صورت مستقیم از داخل برنامه سفارش دهید یا از طریق کانال‌های رسمی روبیکا و تلگرام اشتراک تهیه کرده و با لینک/کد QR فعال کنید:"
                    } else {
                        "Order directly inside the app below, or purchase via our official Rubika / Telegram channels:"
                    },
                    fontSize = 11.sp,
                    color = TextCyberSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        pendingPlanModal = (activePlan ?: SubscriptionPlanId.THREE_MONTHS) to "NEW"
                    },
                    enabled = !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("sub_buy_in_app_btn"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = DeepGraphite)
                ) {
                    Icon(imageVector = Icons.Filled.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isPersian) "خرید مستقیم از داخل برنامه" else "Buy Directly In-App",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        cryptoInitialPlan = activePlan ?: SubscriptionPlanId.ONE_MONTH
                        showCryptoDialog = true
                    },
                    enabled = !isBusy && !cryptoBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("crypto_pay_open_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, com.example.ui.theme.MetallicGold),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = com.example.ui.theme.MetallicGold)
                ) {
                    Icon(imageVector = Icons.Filled.CurrencyBitcoin, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isPersian) "پرداخت رمزارزی (USDT TRC20 / TRX)" else "Pay with Crypto (USDT TRC20 / TRX)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { openExternalUrl(context, "https://rubika.ir/TMS_Time") },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("sub_buy_rubika_btn"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PremiumTeal, contentColor = DeepGraphite)
                    ) {
                        Icon(imageVector = Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isPersian) "خرید از روبیکا" else "Rubika",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    OutlinedButton(
                        onClick = { openExternalUrl(context, "https://t.me/TMS_TIME") },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("sub_buy_telegram_btn"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ElectricCyan)
                    ) {
                        Icon(imageVector = Icons.Filled.OpenInNew, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isPersian) "خرید از تلگرام" else "Telegram",
                            color = ElectricCyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Premium Plan Cards (1 Month: 300k / 3 Months: 650k / 1 Year: 1,700k Toman)
            CyberGlassCard(contentPadding = 14.dp) {
                Text(
                    text = if (isPersian) "پلن‌های اشتراک IP اختصاصی" else "Dedicated IP Subscription Plans",
                    style = MaterialTheme.typography.titleMedium,
                    color = ElectricCyan
                )
                Text(
                    text = if (isPersian) {
                        "تخصیص ایزوله و چرخش روزانه خودکار • پرداخت مستقیم، ربات تلگرام یا رمزارز"
                    } else {
                        "Daily isolated IP rotation quota per plan • Official Toman & Crypto checkout"
                    },
                    fontSize = 11.sp,
                    color = TextCyberSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SubscriptionPlanId.entries.forEach { plan ->
                        val isCurrentActive = isEntitled && activePlan == plan
                        val actionType = when {
                            activePlan == null || !isEntitled -> "NEW"
                            plan.priorityLevel > activePlan.priorityLevel -> "UPGRADE"
                            plan.priorityLevel < activePlan.priorityLevel -> "DOWNGRADE"
                            else -> "RENEW"
                        }
                        val accent = when (plan) {
                            SubscriptionPlanId.ONE_MONTH -> ElectricCyan
                            SubscriptionPlanId.THREE_MONTHS -> PremiumTeal
                            SubscriptionPlanId.ONE_YEAR -> StatusConnected
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(ElevatedSurface.copy(alpha = 0.85f))
                                .border(
                                    BorderStroke(if (isCurrentActive) 1.5.dp else 0.9.dp, if (isCurrentActive) accent else GlassBorderSubtle),
                                    RoundedCornerShape(16.dp)
                                )
                                .padding(14.dp)
                                .testTag("plan_card_${plan.id}")
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (isPersian) plan.titleFa else plan.titleEn,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = accent
                                        )
                                        Text(
                                            text = if (isPersian) plan.serverTierFa else plan.serverTierEn,
                                            fontSize = 11.sp,
                                            color = TextCyberSecondary
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(accent.copy(alpha = 0.14f))
                                            .border(BorderStroke(0.8.dp, accent.copy(alpha = 0.5f)), RoundedCornerShape(10.dp))
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = if (isPersian) "${plan.dailyAllocationLimit} IP / روز" else "${plan.dailyAllocationLimit} IPs/day",
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = accent
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = if (isPersian) plan.formattedPriceTomanFa else plan.formattedPriceTomanEn,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = com.example.ui.theme.MetallicGold,
                                            modifier = Modifier.testTag("plan_price_${plan.id}")
                                        )
                                        Text(
                                            text = if (isPersian) {
                                                "مدت: ${plan.durationDays} روز • ${plan.priorityLabelFa}"
                                            } else {
                                                "Duration: ${plan.durationDays}d • ${plan.priorityLabelEn}"
                                            },
                                            fontSize = 10.sp,
                                            color = MetallicSilver
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        OutlinedButton(
                                            onClick = {
                                                cryptoInitialPlan = plan
                                                showCryptoDialog = true
                                            },
                                            enabled = !isBusy && !cryptoBusy,
                                            modifier = Modifier
                                                .height(48.dp)
                                                .testTag("crypto_plan_button_${plan.id}"),
                                            border = BorderStroke(1.dp, com.example.ui.theme.MetallicGold.copy(alpha = 0.7f)),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = com.example.ui.theme.MetallicGold),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp)
                                        ) {
                                            Text(text = "USDT/TRX", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Button(
                                            onClick = { pendingPlanModal = plan to actionType },
                                            enabled = !isBusy,
                                            modifier = Modifier
                                                .height(48.dp)
                                                .testTag("select_plan_button_${plan.id}"),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isCurrentActive) SecondarySurface else accent,
                                                contentColor = if (isCurrentActive) accent else DeepGraphite
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp)
                                        ) {
                                            Text(
                                                text = when (actionType) {
                                                    "RENEW" -> if (isPersian) "تمدید" else "Renew"
                                                    "UPGRADE" -> if (isPersian) "ارتقا" else "Upgrade"
                                                    "DOWNGRADE" -> if (isPersian) "تغییر پلن" else "Downgrade"
                                                    else -> if (isPersian) "ثبت درخواست" else "Request Plan"
                                                },
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Dedicated Allocated Endpoints List
            CyberGlassCard(
                accentColor = ElectricCyan.copy(alpha = 0.3f),
                contentPadding = 14.dp,
                modifier = Modifier.testTag("allocated_endpoints_section")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Filled.Dns, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isPersian) "نودهای اختصاصی تخصیص‌یافته امروز" else "Allocated Dedicated Endpoints",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextCyberPrimary
                        )
                    }
                    Text(
                        text = if (subscription != null) "v${subscription.currentAllocationVersion}" else "v0",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        color = ElectricCyan
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (allocatedEndpoints.isEmpty()) {
                    Text(
                        text = if (isPersian) {
                            "هنوز IP اختصاصی برای دوره فعلی تخصیص نیافته است. پس از تایید اشتراک، چرخش روزانه سرور نودهای سالم شما را منتشر می‌کند."
                        } else {
                            "No dedicated endpoints allocated yet. Endpoints appear once subscription is confirmed by backend."
                        },
                        fontSize = 11.sp,
                        color = TextCyberSecondary
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        allocatedEndpoints.forEach { ep ->
                            val healthColor = if (ep.healthy) StatusConnected else StatusError
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(DeepGraphite.copy(alpha = 0.75f))
                                    .border(BorderStroke(0.8.dp, healthColor.copy(alpha = 0.45f)), RoundedCornerShape(10.dp))
                                    .padding(10.dp)
                                    .testTag("allocated_endpoint_item_${ep.endpointId}"),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(healthColor)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${ep.endpointName} (${ep.countryCode})",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextCyberPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${ep.protocol.uppercase()} • ${ep.host}:${ep.port}" +
                                                if (ep.latencyMs > 0) " • ${ep.latencyMs}ms" else "",
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontSize = 10.sp,
                                            color = TextCyberSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Button(
                                    onClick = { onConnectEndpoint(ep) },
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("connect_allocated_${ep.endpointId}"),
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = DeepGraphite),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp)
                                ) {
                                    Icon(imageVector = Icons.Filled.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = if (isPersian) "اتصال" else "Connect", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Recent Crypto Invoices History
            CryptoInvoicesHistoryCard(
                invoices = cryptoInvoices,
                isPersian = isPersian,
                onSelectInvoice = { inv ->
                    onSelectCryptoInvoice(inv)
                    showCryptoDialog = true
                }
            )

            // Referral Program & Cumulative Data Rewards
            ReferralDashboardCard(
                dashboard = referralDashboard,
                telegramLinked = telegramLinked,
                isBusy = referralBusy,
                isPersian = isPersian,
                onClaimMilestone = onClaimReferralReward,
                onRefresh = onRefreshBackend
            )

            // Subscription History Timeline
            CyberGlassCard(
                contentPadding = 14.dp,
                modifier = Modifier.testTag("subscription_history_section")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Filled.History, contentDescription = null, tint = PremiumTeal, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isPersian) "تاریخچه اشتراک و تغییرات" else "Subscription History",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextCyberPrimary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                if (history.isEmpty()) {
                    Text(
                        text = if (isPersian) "رویدادی برای نمایش وجود ندارد." else "No subscription events recorded yet.",
                        fontSize = 11.sp,
                        color = TextCyberSecondary
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        history.take(10).forEach { ev ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ElevatedSurface.copy(alpha = 0.65f))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${ev.action} • ${SubscriptionPlanId.fromId(ev.toPlanId).let { if (isPersian) it.titleFa else it.titleEn }}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextCyberPrimary
                                    )
                                    if (ev.details.isNotBlank()) {
                                        Text(
                                            text = ev.details,
                                            fontSize = 10.sp,
                                            color = TextCyberSecondary
                                        )
                                    }
                                }
                                Text(
                                    text = formatSubDate(ev.createdAt),
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 10.sp,
                                    color = MetallicSilver
                                )
                            }
                        }
                    }
                }
            }

            // Dedicated IP Allocation & Rotation History Timeline
            CyberGlassCard(
                accentColor = PremiumTeal.copy(alpha = 0.3f),
                contentPadding = 14.dp,
                modifier = Modifier.testTag("allocation_history_section")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Filled.Dns, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isPersian) "تاریخچه تخصیص و چرخش IP اختصاصی" else "IP Allocation & Rotation History",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextCyberPrimary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                if (allocationHistory.isEmpty()) {
                    Text(
                        text = if (isPersian) "هنوز رویداد تخصیص IP ثبت نشده است." else "No IP allocation events recorded yet.",
                        fontSize = 11.sp,
                        color = TextCyberSecondary
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        allocationHistory.take(15).forEach { rec ->
                            val actionColor = when (rec.action) {
                                "ALLOCATED" -> StatusConnected
                                "ROTATED_OUT" -> MetallicSilver
                                "REPLACED_FAILED", "COOLDOWN_EXCLUDED" -> StatusConnecting
                                else -> StatusError
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ElevatedSurface.copy(alpha = 0.65f))
                                    .border(BorderStroke(0.6.dp, actionColor.copy(alpha = 0.35f)), RoundedCornerShape(8.dp))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${rec.action} • ${rec.endpointName.ifBlank { rec.endpointId }} (v${rec.allocationVersion})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = actionColor
                                    )
                                    if (rec.host.isNotBlank()) {
                                        Text(
                                            text = "${rec.protocol.uppercase()} • ${rec.host}:${rec.port}",
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontSize = 10.sp,
                                            color = TextCyberPrimary
                                        )
                                    }
                                    if (rec.reason.isNotBlank()) {
                                        Text(
                                            text = rec.reason,
                                            fontSize = 10.sp,
                                            color = TextCyberSecondary
                                        )
                                    }
                                }
                                Text(
                                    text = rec.cycleKey.ifBlank { formatSubDate(rec.createdAt) },
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 10.sp,
                                    color = MetallicSilver
                                )
                            }
                        }
                    }
                }
            }

            // Official Channels (Telegram & Rubika) — Preserved & Restyled
            CyberGlassCard(
                accentColor = ElectricCyan.copy(alpha = 0.45f),
                elevated = true,
                contentPadding = 16.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Filled.Campaign, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isPersian) "کانال‌های رسمی TMS Time" else "Official TMS Time Channels",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextCyberPrimary
                            )
                            Text(
                                text = if (isPersian) "پشتیبانی پرداخت، تمدید و کانفیگ‌های اضطراری" else "Payment verification & backup emergency configs",
                                style = MaterialTheme.typography.bodySmall,
                                color = PremiumTeal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { openExternalUrl(context, "https://t.me/TMS_TIME") },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = DeepGraphite)
                    ) {
                        Icon(imageVector = Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = if (isPersian) "تلگرام" else "Telegram", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { openExternalUrl(context, "https://rubika.ir/TMS_Time") },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ElevatedSurface, contentColor = SilverHighlight),
                        border = BorderStroke(1.dp, GlassBorderMetallic)
                    ) {
                        Icon(imageVector = Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = if (isPersian) "روبیکا" else "Rubika", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val clipText = "Telegram: https://t.me/TMS_TIME\nRubika: https://rubika.ir/TMS_Time"
                            clipboard?.setPrimaryClip(ClipData.newPlainText("TMS Channels", clipText))
                            Toast.makeText(context, if (isPersian) "لینک کانال‌ها کپی شد" else "Channel links copied", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, GlassBorderSubtle)
                    ) {
                        Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = null, tint = MetallicSilver, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = if (isPersian) "کپی لینک‌ها" else "Copy Links", color = MetallicSilver)
                    }

                    OutlinedButton(
                        onClick = {
                            val shareText = "TMS VPN Official Channels:\nhttps://t.me/TMS_TIME\nhttps://rubika.ir/TMS_Time"
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, shareText)
                                type = "text/plain"
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(Intent.createChooser(intent, "TMS VPN"))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, GlassBorderCyan)
                    ) {
                        Icon(imageVector = Icons.Filled.Share, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = if (isPersian) "اشتراک‌گذاری" else "Share", color = ElectricCyan)
                    }
                }
            }
        }
    }

    // Honest Payment Verification Request Modal (Never fabricates active confirmation)
    pendingPlanModal?.let { (plan, actionType) ->
        AlertDialog(
            onDismissRequest = { pendingPlanModal = null },
            containerColor = SecondarySurface,
            modifier = Modifier.testTag("payment_request_dialog"),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Filled.Shield, contentDescription = null, tint = ElectricCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isPersian) "ثبت درخواست اشتراک (${plan.titleFa})" else "Request ${plan.titleEn}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElectricCyan
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (isPersian) {
                            "این درخواست با وضعیت «در انتظار تایید (PENDING_PAYMENT)» به سرور ارسال می‌شود. اشتراک و ${plan.dailyAllocationLimit} IP اختصاصی روزانه تنها پس از تایید مدیر/درگاه معتبر فعال خواهند شد."
                        } else {
                            "Submitted as PENDING_PAYMENT. Plan and ${plan.dailyAllocationLimit} daily endpoints activate only after trusted server verification."
                        },
                        fontSize = 11.sp,
                        color = TextCyberSecondary
                    )

                    OutlinedTextField(
                        value = paymentRefCode,
                        onValueChange = { paymentRefCode = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("payment_reference_input"),
                        label = { Text(if (isPersian) "شماره پیگیری / ارجاع پرداخت (اختیاری)" else "Payment Reference Code (optional)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricCyan,
                            unfocusedBorderColor = GlassBorderSubtle,
                            focusedTextColor = TextCyberPrimary,
                            unfocusedTextColor = TextCyberPrimary
                        )
                    )

                    OutlinedTextField(
                        value = paymentNote,
                        onValueChange = { paymentNote = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (isPersian) "توضیحات برای مدیر" else "Note for administrator") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricCyan,
                            unfocusedBorderColor = GlassBorderSubtle,
                            focusedTextColor = TextCyberPrimary,
                            unfocusedTextColor = TextCyberPrimary
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSubmitPlanRequest(plan, actionType, paymentRefCode, paymentNote)
                        paymentRefCode = ""
                        paymentNote = ""
                        pendingPlanModal = null
                    },
                    enabled = !isBusy,
                    modifier = Modifier.testTag("submit_payment_request_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = DeepGraphite)
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = DeepGraphite)
                    } else {
                        Text(text = if (isPersian) "ارسال برای تایید سرور" else "Submit Pending Request", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPlanModal = null }) {
                    Text(text = if (isPersian) "انصراف" else "Cancel", color = MetallicSilver)
                }
            }
        )
    }

    if (showLinkActivationModal) {
        val parsedPreview = remember(activationLinkInput) {
            SubscriptionActivationLinkEngine.parseActivationUrl(activationLinkInput)
        }
        AlertDialog(
            onDismissRequest = { showLinkActivationModal = false },
            containerColor = SecondarySurface,
            modifier = Modifier.testTag("user_activate_link_dialog"),
            title = {
                Text(
                    text = if (isPersian) "فعال‌سازی اشتراک با لینک یا کد" else "Activate Subscription via Link",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = ElectricCyan
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (isPersian) {
                            "لینک فعال‌سازی دریافتی از مدیر (tmsvpn://activate?...) را وارد کنید:"
                        } else {
                            "Paste your activation deep link (tmsvpn://activate?...):"
                        },
                        fontSize = 11.sp,
                        color = TextCyberSecondary
                    )
                    OutlinedTextField(
                        value = activationLinkInput,
                        onValueChange = { activationLinkInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("user_activate_link_input"),
                        label = { Text("tmsvpn://activate?...") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricCyan,
                            unfocusedBorderColor = GlassBorderSubtle,
                            focusedTextColor = TextCyberPrimary,
                            unfocusedTextColor = TextCyberPrimary
                        )
                    )
                    if (parsedPreview != null) {
                        val previewPlan = SubscriptionPlanId.fromId(parsedPreview.planId)
                        Text(
                            text = if (isPersian) {
                                "پلن شناسایی‌شده: ${previewPlan.titleFa} (${previewPlan.dailyAllocationLimit} IP/روز)"
                            } else {
                                "Detected plan: ${previewPlan.titleEn} (${previewPlan.dailyAllocationLimit} IPs/day)"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = StatusConnected
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (activationLinkInput.isNotBlank()) {
                            onActivateByLink(activationLinkInput.trim())
                            activationLinkInput = ""
                            showLinkActivationModal = false
                        }
                    },
                    enabled = !isBusy && activationLinkInput.isNotBlank(),
                    modifier = Modifier.testTag("user_submit_activation_link_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = DeepGraphite)
                ) {
                    Text(text = if (isPersian) "فعال‌سازی" else "Activate", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLinkActivationModal = false }) {
                    Text(text = if (isPersian) "انصراف" else "Cancel", color = MetallicSilver)
                }
            }
        )
    }

    if (showCryptoDialog || activeCryptoInvoice != null) {
        CryptoPaymentSheetDialog(
            initialPlan = cryptoInitialPlan,
            activeInvoice = activeCryptoInvoice,
            trustedNowMs = trustedNowMs,
            isBusy = cryptoBusy,
            isPersian = isPersian,
            onCreateInvoice = onCreateCryptoInvoice,
            onVerifyInvoice = onVerifyCryptoInvoice,
            onDismiss = {
                showCryptoDialog = false
                onDismissCryptoInvoice()
            }
        )
    }
}

@Composable
private fun SubscriptionMetricColumn(
    label: String,
    value: String,
    accent: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 10.sp, color = TextCyberSecondary)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = accent
        )
    }
}

private fun formatSubDate(ts: Timestamp?): String {
    if (ts == null) return "--"
    return SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date(ts.toDate().time))
}

private fun openExternalUrl(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, e.localizedMessage ?: "Cannot open link", Toast.LENGTH_SHORT).show()
    }
}
