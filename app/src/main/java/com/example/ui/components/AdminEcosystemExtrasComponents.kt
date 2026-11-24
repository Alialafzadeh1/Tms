package com.example.ui.components

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppReleaseInfo
import com.example.data.model.CryptoInvoiceStatus
import com.example.data.model.CryptoPaymentInvoice
import com.example.data.model.ReferralRewardRequest
import com.example.data.model.ReferralRewardStatus
import com.example.data.subscription.ReferralAndCryptoCalculator
import com.example.ui.theme.BorderGoldHighlight
import com.example.ui.theme.BorderGoldMuted
import com.example.ui.theme.CardSurfaceDark
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MetallicGold
import com.example.ui.theme.StatusOfflineRed
import com.example.ui.theme.StatusWarningAmber
import com.example.ui.theme.TextGraySecondary
import com.example.ui.theme.TextWhitePrimary

@Composable
fun AdminEcosystemTabContent(
    rewardRequests: List<ReferralRewardRequest>,
    releases: List<AppReleaseInfo>,
    cryptoInvoices: List<CryptoPaymentInvoice>,
    referralBusy: Boolean,
    releaseBusy: Boolean,
    isPersian: Boolean,
    onReviewReferralReward: (requestId: String, approve: Boolean, note: String) -> Unit,
    onUploadApkRelease: (fileName: String, bytes: ByteArray, versionName: String, versionCode: Int, notes: String, publish: Boolean) -> Unit,
    onToggleReleasePublish: (releaseId: String, publish: Boolean) -> Unit,
    onRefreshEcosystem: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var rewardNote by remember { mutableStateOf("") }

    var pickedFileName by remember { mutableStateOf("") }
    var pickedApkBytes by remember { mutableStateOf<ByteArray?>(null) }
    var versionNameInput by remember { mutableStateOf("") }
    var versionCodeInput by remember { mutableStateOf("") }
    var releaseNotesInput by remember { mutableStateOf("") }
    var publishImmediately by remember { mutableStateOf(true) }

    val apkPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val resolver = context.contentResolver
                val name = resolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIdx >= 0) cursor.getString(nameIdx) else "tms-vpn.apk"
                } ?: "tms-vpn.apk"
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null && bytes.isNotEmpty()) {
                    pickedFileName = name
                    pickedApkBytes = bytes
                }
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Refresh Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isPersian) "پاداش معرفی، نسخه‌های APK و فاکتورهای کریپتو" else "Referral Rewards, APK Releases & Crypto",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MetallicGold,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onRefreshEcosystem,
                modifier = Modifier.testTag("admin_refresh_ecosystem_btn")
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh", tint = MetallicGold)
            }
        }

        // Section 1: Referral Reward Requests Queue
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(BorderStroke(1.dp, BorderGoldHighlight), RoundedCornerShape(14.dp))
                .testTag("admin_referral_rewards_section"),
            colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.CardGiftcard, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isPersian) "درخواست‌های پاداش معرفی (${rewardRequests.size})" else "Referral Reward Requests (${rewardRequests.size})",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElectricCyan
                    )
                }

                if (rewardRequests.isEmpty()) {
                    Text(
                        text = if (isPersian) "درخواستی برای بررسی ثبت نشده است." else "No referral reward requests recorded yet.",
                        fontSize = 11.sp,
                        color = TextGraySecondary
                    )
                } else {
                    OutlinedTextField(
                        value = rewardNote,
                        onValueChange = { rewardNote = it },
                        label = { Text(if (isPersian) "یادداشت مدیر (اختیاری)" else "Admin review note (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MetallicGold,
                            unfocusedBorderColor = BorderGoldMuted,
                            focusedTextColor = TextWhitePrimary,
                            unfocusedTextColor = TextWhitePrimary
                        )
                    )

                    rewardRequests.forEach { req ->
                        val statusColor = when (req.status) {
                            ReferralRewardStatus.APPROVED -> EmeraldGreen
                            ReferralRewardStatus.PENDING -> StatusWarningAmber
                            ReferralRewardStatus.REJECTED -> StatusOfflineRed
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF131823), RoundedCornerShape(10.dp))
                                .border(BorderStroke(0.8.dp, statusColor.copy(alpha = 0.45f)), RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${req.userEmail.ifBlank { req.userId }} • +${req.rewardGb} GB (${req.milestoneThreshold} refs)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextWhitePrimary
                                    )
                                    Text(
                                        text = if (isPersian) req.status.titleFa else req.status.titleEn,
                                        fontSize = 10.sp,
                                        color = statusColor
                                    )
                                }
                            }
                            if (req.status == ReferralRewardStatus.PENDING) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { onReviewReferralReward(req.requestId, true, rewardNote) },
                                        enabled = !referralBusy,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("admin_approve_reward_${req.requestId}"),
                                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen, contentColor = Color(0xFF021B10))
                                    ) {
                                        Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = if (isPersian) "تایید" else "Approve", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(
                                        onClick = { onReviewReferralReward(req.requestId, false, rewardNote) },
                                        enabled = !referralBusy,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("admin_reject_reward_${req.requestId}"),
                                        border = BorderStroke(1.dp, StatusOfflineRed.copy(alpha = 0.7f)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusOfflineRed)
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
            }
        }

        // Section 2: APK Release Upload & R2 Management
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(BorderStroke(1.dp, MetallicGold.copy(alpha = 0.65f)), RoundedCornerShape(14.dp))
                .testTag("admin_apk_releases_section"),
            colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.SystemUpdate, contentDescription = null, tint = MetallicGold, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isPersian) "انتشار و مدیریت فایل APK (Cloudflare R2)" else "APK Release Management (R2)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MetallicGold
                    )
                }

                OutlinedButton(
                    onClick = { apkPickerLauncher.launch(arrayOf("application/vnd.android.package-archive", "*/*")) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("admin_pick_apk_btn"),
                    border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.7f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricCyan),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (pickedFileName.isNotBlank()) {
                            "$pickedFileName (${ReferralAndCryptoCalculator.formatFileSize(pickedApkBytes?.size?.toLong() ?: 0L)})"
                        } else {
                            if (isPersian) "انتخاب فایل .apk از حافظه" else "Select .apk File"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = versionNameInput,
                        onValueChange = { versionNameInput = it },
                        label = { Text("Version (e.g. 2.5.0)") },
                        modifier = Modifier.weight(1f).testTag("admin_apk_version_name_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MetallicGold,
                            unfocusedBorderColor = BorderGoldMuted,
                            focusedTextColor = TextWhitePrimary,
                            unfocusedTextColor = TextWhitePrimary
                        )
                    )
                    OutlinedTextField(
                        value = versionCodeInput,
                        onValueChange = { input -> versionCodeInput = input.filter { it.isDigit() } },
                        label = { Text("Code (e.g. 25)") },
                        modifier = Modifier.weight(1f).testTag("admin_apk_version_code_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MetallicGold,
                            unfocusedBorderColor = BorderGoldMuted,
                            focusedTextColor = TextWhitePrimary,
                            unfocusedTextColor = TextWhitePrimary
                        )
                    )
                }

                OutlinedTextField(
                    value = releaseNotesInput,
                    onValueChange = { releaseNotesInput = it },
                    label = { Text(if (isPersian) "توضیحات نسخه جدید (Changelog)" else "Release Notes") },
                    modifier = Modifier.fillMaxWidth().testTag("admin_apk_release_notes_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MetallicGold,
                        unfocusedBorderColor = BorderGoldMuted,
                        focusedTextColor = TextWhitePrimary,
                        unfocusedTextColor = TextWhitePrimary
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isPersian) "انتشار عمومی فوری پس از آپلود" else "Publish immediately for users",
                        fontSize = 11.sp,
                        color = TextWhitePrimary
                    )
                    Switch(
                        checked = publishImmediately,
                        onCheckedChange = { publishImmediately = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = EmeraldGreen)
                    )
                }

                Button(
                    onClick = {
                        val bytes = pickedApkBytes ?: return@Button
                        val vName = versionNameInput.trim()
                        val vCode = versionCodeInput.trim().toIntOrNull() ?: 1
                        if (vName.isNotBlank()) {
                            onUploadApkRelease(
                                pickedFileName.ifBlank { "tms-vpn-$vName.apk" },
                                bytes,
                                vName,
                                vCode,
                                releaseNotesInput.trim(),
                                publishImmediately
                            )
                        }
                    },
                    enabled = !releaseBusy && pickedApkBytes != null && versionNameInput.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("admin_upload_apk_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = Color(0xFF1E1700)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (releaseBusy) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF1E1700))
                    } else {
                        Text(text = if (isPersian) "آپلود و ثبت نسخه در سرور" else "Upload Release to Server", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                releases.forEach { rel ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF131823), RoundedCornerShape(10.dp))
                            .border(BorderStroke(0.7.dp, BorderGoldMuted), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "v${rel.versionName} (${rel.versionCode}) • ${ReferralAndCryptoCalculator.formatFileSize(rel.fileSizeBytes)}",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhitePrimary
                            )
                            Text(
                                text = (if (isPersian) "دانلود: ${rel.downloadCount}" else "Downloads: ${rel.downloadCount}") +
                                    if (rel.releaseNotes.isNotBlank()) " • ${rel.releaseNotes}" else "",
                                fontSize = 10.sp,
                                color = TextGraySecondary
                            )
                        }
                        Switch(
                            checked = rel.isPublished,
                            onCheckedChange = { publish -> onToggleReleasePublish(rel.releaseId, publish) },
                            enabled = !releaseBusy,
                            modifier = Modifier.testTag("admin_toggle_release_${rel.releaseId}"),
                            colors = SwitchDefaults.colors(checkedThumbColor = EmeraldGreen)
                        )
                    }
                }
            }
        }

        // Section 3: Crypto Invoices Overview
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(BorderStroke(1.dp, BorderGoldMuted), RoundedCornerShape(14.dp))
                .testTag("admin_crypto_invoices_section"),
            colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.CurrencyBitcoin, contentDescription = null, tint = MetallicGold, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isPersian) "گزارش فاکتورهای رمزارزی (${cryptoInvoices.size})" else "Crypto Invoices Audit (${cryptoInvoices.size})",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MetallicGold
                    )
                }

                if (cryptoInvoices.isEmpty()) {
                    Text(
                        text = if (isPersian) "هنوز فاکتور رمزارزی ثبت نشده است." else "No crypto invoices recorded yet.",
                        fontSize = 11.sp,
                        color = TextGraySecondary
                    )
                } else {
                    cryptoInvoices.take(20).forEach { inv ->
                        val badgeColor = when (inv.status) {
                            CryptoInvoiceStatus.CONFIRMED -> EmeraldGreen
                            CryptoInvoiceStatus.PENDING, CryptoInvoiceStatus.CONFIRMING -> StatusWarningAmber
                            CryptoInvoiceStatus.OVERPAID -> ElectricCyan
                            CryptoInvoiceStatus.AMBIGUOUS -> MetallicGold
                            else -> StatusOfflineRed
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF131823), RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${inv.userId.take(10)} • ${inv.expectedAmountCrypto} ${inv.asset.symbol}",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextWhitePrimary
                                )
                                Text(
                                    text = "Tx: ${inv.txHash.ifBlank { "--" }}",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 10.sp,
                                    color = TextGraySecondary
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .background(badgeColor.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
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
    }
}
