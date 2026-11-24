package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CryptoAsset
import com.example.data.model.CryptoInvoiceStatus
import com.example.data.model.CryptoPaymentInvoice
import com.example.data.model.FreeTrialStatus
import com.example.data.model.ReferralDashboardData
import com.example.data.model.ReferralRewardStatus
import com.example.data.model.SubscriptionPlanId
import com.example.data.subscription.ReferralAndCryptoCalculator
import com.example.ui.theme.DeepGraphite
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.GlassBorderCyan
import com.example.ui.theme.GlassBorderMetallic
import com.example.ui.theme.GlassBorderSubtle
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MetallicGold
import com.example.ui.theme.MetallicSilver
import com.example.ui.theme.PremiumTeal
import com.example.ui.theme.SecondarySurface
import com.example.ui.theme.StatusConnected
import com.example.ui.theme.StatusConnecting
import com.example.ui.theme.StatusError
import com.example.ui.theme.TextCyberPrimary
import com.example.ui.theme.TextCyberSecondary

@Composable
fun FreeTrialCard(
    trialStatus: FreeTrialStatus?,
    trustedNowMs: Long,
    isBusy: Boolean,
    isPersian: Boolean,
    onActivateFreeTrial: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isActive = trialStatus?.active == true
    val isEligible = trialStatus?.eligible ?: true
    val accent = when {
        isActive -> StatusConnected
        isEligible -> PremiumTeal
        else -> StatusConnecting
    }

    val remainingTrialSec = remember(trialStatus, trustedNowMs) {
        val exp = trialStatus?.expiresAtMs ?: return@remember 0L
        ((exp - trustedNowMs) / 1000L).coerceAtLeast(0L)
    }

    CyberGlassCard(
        accentColor = accent.copy(alpha = 0.5f),
        contentPadding = 14.dp,
        modifier = modifier.testTag("trial_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(imageVector = Icons.Filled.Timer, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = if (isPersian) "تست رایگان ۱ روزه (هر ۷ روز یکبار)" else "24-Hour Free Trial (7-Day Cooldown)",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextCyberPrimary
                    )
                    Text(
                        text = when {
                            isActive -> {
                                val left = ReferralAndCryptoCalculator.formatDurationCountdown(remainingTrialSec, isPersian)
                                if (isPersian) "تست فعال است • باقی‌مانده: $left" else "Active now • Remaining: $left"
                            }
                            trialStatus == null -> {
                                if (isPersian) "سهمیه آزمایشی ۲۴ ساعته تاییدشده توسط سرور" else "Server-enforced 24-hour full access trial"
                            }
                            isEligible -> {
                                if (isPersian) "واجد شرایط دریافت تست رایگان ۲۴ ساعته" else "Eligible to activate 24-hour trial now"
                            }
                            else -> {
                                val cool = ReferralAndCryptoCalculator.formatDurationCountdown(trialStatus.cooldownRemainingSeconds, isPersian)
                                if (isPersian) "زمان انتظار تا دریافت بعدی: $cool" else "Next trial available in $cool"
                            }
                        },
                        fontSize = 11.sp,
                        color = if (isActive) StatusConnected else TextCyberSecondary
                    )
                }
            }

            if (!isActive) {
                Button(
                    onClick = onActivateFreeTrial,
                    enabled = !isBusy && (trialStatus == null || trialStatus.eligible),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("trial_activate_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = DeepGraphite),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = DeepGraphite)
                    } else {
                        Text(
                            text = if (isPersian) "دریافت تست" else "Start Trial",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CryptoInvoicesHistoryCard(
    invoices: List<CryptoPaymentInvoice>,
    isPersian: Boolean,
    onSelectInvoice: (CryptoPaymentInvoice) -> Unit,
    modifier: Modifier = Modifier
) {
    if (invoices.isEmpty()) return
    CyberGlassCard(
        accentColor = MetallicGold.copy(alpha = 0.4f),
        contentPadding = 14.dp,
        modifier = modifier.testTag("crypto_invoices_card")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Filled.CurrencyBitcoin, contentDescription = null, tint = MetallicGold, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isPersian) "فاکتورهای رمزارزی اخیر (${invoices.size})" else "Recent Crypto Invoices (${invoices.size})",
                style = MaterialTheme.typography.titleSmall,
                color = MetallicGold
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            invoices.take(6).forEach { inv ->
                val badgeColor = cryptoStatusColor(inv.status)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(ElevatedSurface.copy(alpha = 0.78f))
                        .border(BorderStroke(0.7.dp, badgeColor.copy(alpha = 0.45f)), RoundedCornerShape(10.dp))
                        .clickable { onSelectInvoice(inv) }
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${inv.expectedAmountCrypto} ${inv.asset.symbol} (${inv.asset.network})",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextCyberPrimary
                        )
                        Text(
                            text = "${SubscriptionPlanId.fromId(inv.planId).let { if (isPersian) it.titleFa else it.titleEn }} • ${ReferralAndCryptoCalculator.formatToman(inv.expectedAmountToman, isPersian)}",
                            fontSize = 10.sp,
                            color = TextCyberSecondary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(badgeColor.copy(alpha = 0.16f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isPersian) inv.status.titleFa else inv.status.titleEn,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CryptoPaymentSheetDialog(
    initialPlan: SubscriptionPlanId,
    activeInvoice: CryptoPaymentInvoice?,
    trustedNowMs: Long,
    isBusy: Boolean,
    isPersian: Boolean,
    onCreateInvoice: (SubscriptionPlanId, CryptoAsset) -> Unit,
    onVerifyInvoice: (invoiceId: String, txHash: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedPlan by remember(initialPlan) { mutableStateOf(initialPlan) }
    var selectedAsset by remember { mutableStateOf(CryptoAsset.USDT_TRC20) }
    var txHashInput by remember(activeInvoice?.invoiceId) {
        mutableStateOf(activeInvoice?.txHash.orEmpty())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SecondarySurface,
        modifier = Modifier.testTag("crypto_payment_dialog"),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Filled.CurrencyBitcoin, contentDescription = null, tint = MetallicGold)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isPersian) "پرداخت رمزارزی (USDT TRC20 / TRX)" else "Crypto Checkout (USDT TRC20 / TRX)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MetallicGold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (activeInvoice == null) {
                    Text(
                        text = if (isPersian) {
                            "پلن و شبکه پرداخت را انتخاب کنید تا آدرس کیف‌پول رسمی و مبلغ قفل‌شده از سرور صادر شود:"
                        } else {
                            "Select plan and asset to generate a server-locked crypto invoice:"
                        },
                        fontSize = 11.sp,
                        color = TextCyberSecondary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SubscriptionPlanId.entries.forEach { plan ->
                            val selected = selectedPlan == plan
                            FilterChip(
                                selected = selected,
                                onClick = { selectedPlan = plan },
                                label = {
                                    Text(
                                        text = "${if (isPersian) plan.titleFa else plan.titleEn} • ${if (isPersian) plan.formattedPriceTomanFa else plan.formattedPriceTomanEn}",
                                        fontSize = 10.sp
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = ElectricCyan
                                )
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CryptoAsset.entries.forEach { asset ->
                            val selected = selectedAsset == asset
                            FilterChip(
                                selected = selected,
                                onClick = { selectedAsset = asset },
                                modifier = Modifier.weight(1f).testTag("crypto_asset_${asset.id}"),
                                label = {
                                    Text(
                                        text = "${asset.symbol} (${asset.network})",
                                        fontSize = 11.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MetallicGold.copy(alpha = 0.22f),
                                    selectedLabelColor = MetallicGold
                                )
                            )
                        }
                    }

                    Button(
                        onClick = { onCreateInvoice(selectedPlan, selectedAsset) },
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("crypto_create_invoice_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = DeepGraphite),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = DeepGraphite)
                        } else {
                            Text(
                                text = if (isPersian) "صدور فاکتور و نمایش آدرس ولت" else "Generate Crypto Invoice",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    val badgeColor = cryptoStatusColor(activeInvoice.status)
                    val remainingSec = ((activeInvoice.expiresAtMs - trustedNowMs) / 1000L).coerceAtLeast(0L)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "#${activeInvoice.invoiceId.take(10)}",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.sp,
                            color = TextCyberSecondary
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(badgeColor.copy(alpha = 0.16f))
                                .border(BorderStroke(0.8.dp, badgeColor.copy(alpha = 0.5f)), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("crypto_invoice_status_badge")
                        ) {
                            Text(
                                text = if (isPersian) activeInvoice.status.titleFa else activeInvoice.status.titleEn,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = badgeColor
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(DeepGraphite)
                            .border(BorderStroke(0.8.dp, GlassBorderCyan), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (isPersian) "مبلغ دقیق واریزی:" else "Exact Deposit Amount:",
                            fontSize = 10.sp,
                            color = TextCyberSecondary
                        )
                        Text(
                            text = "${activeInvoice.expectedAmountCrypto} ${activeInvoice.asset.symbol} (${activeInvoice.network})",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElectricCyan
                        )
                        Text(
                            text = "${ReferralAndCryptoCalculator.formatToman(activeInvoice.expectedAmountToman, isPersian)} • " +
                                (if (remainingSec > 0L) ReferralAndCryptoCalculator.formatDurationCountdown(remainingSec, isPersian)
                                else if (isPersian) "منقضی شده" else "Expired"),
                            fontSize = 10.sp,
                            color = MetallicSilver
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(ElevatedSurface)
                            .border(BorderStroke(0.8.dp, GlassBorderMetallic), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isPersian) "آدرس کیف‌پول مقصد:" else "Deposit Wallet Address:",
                                fontSize = 10.sp,
                                color = TextCyberSecondary
                            )
                            Text(
                                text = activeInvoice.depositAddress.ifBlank { "--" },
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.sp,
                                color = TextCyberPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = {
                                copyTextToClipboard(
                                    context,
                                    "TMS Wallet",
                                    activeInvoice.depositAddress,
                                    if (isPersian) "آدرس ولت کپی شد" else "Wallet address copied"
                                )
                            },
                            modifier = Modifier.testTag("crypto_copy_address_btn")
                        ) {
                            Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = "Copy Address", tint = ElectricCyan)
                        }
                    }

                    if (activeInvoice.statusMessage.isNotBlank()) {
                        Text(text = activeInvoice.statusMessage, fontSize = 11.sp, color = badgeColor)
                    }

                    if (activeInvoice.status != CryptoInvoiceStatus.CONFIRMED) {
                        OutlinedTextField(
                            value = txHashInput,
                            onValueChange = { txHashInput = it },
                            modifier = Modifier.fillMaxWidth().testTag("crypto_txhash_input"),
                            label = { Text(if (isPersian) "شناسه تراکنش شبکه (TxHash)" else "Transaction Hash (TxHash)") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricCyan,
                                unfocusedBorderColor = GlassBorderSubtle,
                                focusedTextColor = TextCyberPrimary,
                                unfocusedTextColor = TextCyberPrimary
                            )
                        )

                        Button(
                            onClick = { onVerifyInvoice(activeInvoice.invoiceId, txHashInput) },
                            enabled = !isBusy && txHashInput.trim().length >= 16,
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("crypto_verify_tx_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = StatusConnected, contentColor = DeepGraphite),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (isBusy) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = DeepGraphite)
                            } else {
                                Text(
                                    text = if (isPersian) "بررسی و تایید تراکنش روی بلاکچین" else "Verify Payment on Server",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = if (isPersian) "بستن" else "Close", color = MetallicSilver)
            }
        }
    )
}

@Composable
fun ReferralDashboardCard(
    dashboard: ReferralDashboardData?,
    telegramLinked: Boolean,
    isBusy: Boolean,
    isPersian: Boolean,
    onClaimMilestone: (Int) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val qualified = dashboard?.qualifiedCount ?: 0
    val milestones = remember(dashboard) {
        if (dashboard != null && dashboard.milestones.isNotEmpty()) {
            dashboard.milestones
        } else {
            ReferralAndCryptoCalculator.evaluateMilestones(qualified, dashboard?.rewardRequests.orEmpty())
        }
    }

    CyberGlassCard(
        accentColor = ElectricCyan.copy(alpha = 0.45f),
        contentPadding = 14.dp,
        modifier = modifier.testTag("referral_section_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(imageVector = Icons.Filled.GroupAdd, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = if (isPersian) "دعوت از دوستان و حجم هدیه (تا ۱۲.۵ گیگابایت)" else "Referrals & Rewards (Up to 12.5 GB)",
                        style = MaterialTheme.typography.titleSmall,
                        color = ElectricCyan
                    )
                    Text(
                        text = if (isPersian) {
                            "شرط تایید هر دعوت: استارت ربات تلگرام + دانلود APK + ورود به برنامه"
                        } else {
                            "Qualifies when invited user starts bot, downloads APK & logs into app"
                        },
                        fontSize = 10.sp,
                        color = TextCyberSecondary
                    )
                }
            }
            IconButton(onClick = onRefresh, modifier = Modifier.testTag("referral_refresh_btn")) {
                Icon(imageVector = Icons.Filled.Refresh, contentDescription = "Refresh Referrals", tint = ElectricCyan)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (dashboard != null && dashboard.referralShareUrl.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DeepGraphite.copy(alpha = 0.78f))
                    .border(BorderStroke(0.7.dp, GlassBorderCyan), RoundedCornerShape(10.dp))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isPersian) "کد دعوت شما: ${dashboard.referralCode}" else "Your Code: ${dashboard.referralCode}",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MetallicGold
                    )
                    Text(
                        text = dashboard.referralShareUrl,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 10.sp,
                        color = TextCyberSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row {
                    IconButton(
                        onClick = {
                            copyTextToClipboard(
                                context,
                                "TMS Referral",
                                dashboard.referralShareUrl,
                                if (isPersian) "لینک دعوت کپی شد" else "Referral link copied"
                            )
                        },
                        modifier = Modifier.testTag("referral_copy_link_btn")
                    ) {
                        Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = "Copy Link", tint = ElectricCyan)
                    }
                    IconButton(
                        onClick = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                putExtra(Intent.EXTRA_TEXT, dashboard.referralShareUrl)
                                type = "text/plain"
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            runCatching { context.startActivity(Intent.createChooser(sendIntent, "TMS VPN Referral")) }
                        },
                        modifier = Modifier.testTag("referral_share_link_btn")
                    ) {
                        Icon(imageVector = Icons.Filled.Share, contentDescription = "Share Link", tint = PremiumTeal)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Counters Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(ElevatedSurface.copy(alpha = 0.7f))
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = if (isPersian) "کل دعوت‌ها" else "Invited", fontSize = 10.sp, color = TextCyberSecondary)
                Text(text = "${dashboard?.totalInvitedCount ?: 0}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextCyberPrimary)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = if (isPersian) "تاییدشده" else "Qualified", fontSize = 10.sp, color = TextCyberSecondary)
                Text(text = "$qualified", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = StatusConnected)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = if (isPersian) "حجم آزادشده" else "Unlocked", fontSize = 10.sp, color = TextCyberSecondary)
                Text(
                    text = "${dashboard?.totalUnlockedGb ?: ReferralAndCryptoCalculator.calculateTotalUnlockedGb(qualified)} / 12.5 GB",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MetallicGold
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = if (isPersian) "تلگرام" else "Telegram", fontSize = 10.sp, color = TextCyberSecondary)
                Text(
                    text = if (telegramLinked) (if (isPersian) "متصل" else "Linked") else (if (isPersian) "نامتصل" else "Unlinked"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = if (telegramLinked) StatusConnected else MetallicSilver
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            milestones.forEach { m ->
                val rewardStatus = m.rewardStatus
                val statusColor = when (rewardStatus) {
                    ReferralRewardStatus.APPROVED -> StatusConnected
                    ReferralRewardStatus.PENDING -> StatusConnecting
                    ReferralRewardStatus.REJECTED -> StatusError
                    null -> if (m.isUnlocked) ElectricCyan else MetallicSilver
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DeepGraphite.copy(alpha = 0.72f))
                        .border(BorderStroke(0.7.dp, statusColor.copy(alpha = 0.45f)), RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = if (rewardStatus == ReferralRewardStatus.APPROVED) Icons.Filled.Verified else Icons.Filled.CardGiftcard,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (isPersian) m.titleFa else m.titleEn,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextCyberPrimary
                            )
                            Text(
                                text = when (rewardStatus) {
                                    ReferralRewardStatus.APPROVED -> if (isPersian) "تایید و اعمال‌شده" else "Approved & Credited"
                                    ReferralRewardStatus.PENDING -> if (isPersian) "در انتظار تایید مدیر" else "Pending Admin Review"
                                    ReferralRewardStatus.REJECTED -> if (isPersian) "رد شده — امکان درخواست مجدد" else "Rejected"
                                    null -> if (m.isUnlocked) {
                                        if (isPersian) "آماده ثبت درخواست پاداش" else "Unlocked! Ready to claim"
                                    } else {
                                        if (isPersian) "پیشرفت: $qualified / ${m.thresholdCount}" else "Progress: $qualified / ${m.thresholdCount}"
                                    }
                                },
                                fontSize = 10.sp,
                                color = statusColor
                            )
                        }
                    }

                    if (m.canClaim) {
                        Button(
                            onClick = { onClaimMilestone(m.thresholdCount) },
                            enabled = !isBusy,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("claim_milestone_${m.thresholdCount}"),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = DeepGraphite),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Text(
                                text = if (isPersian) "دریافت +${m.rewardGb}GB" else "Claim +${m.rewardGb}GB",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun cryptoStatusColor(status: CryptoInvoiceStatus) = when (status) {
    CryptoInvoiceStatus.CONFIRMED -> StatusConnected
    CryptoInvoiceStatus.PENDING, CryptoInvoiceStatus.CONFIRMING -> StatusConnecting
    CryptoInvoiceStatus.OVERPAID -> ElectricCyan
    CryptoInvoiceStatus.UNDERPAID, CryptoInvoiceStatus.EXPIRED, CryptoInvoiceStatus.REJECTED -> StatusError
    CryptoInvoiceStatus.AMBIGUOUS -> MetallicGold
}

private fun copyTextToClipboard(context: Context, label: String, text: String, toastMessage: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
}
