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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserProfile
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
import com.example.ui.theme.SilverHighlight
import com.example.ui.theme.StatusConnected
import com.example.ui.theme.StatusConnecting
import com.example.ui.theme.StatusError
import com.example.ui.theme.TextCyberPrimary
import com.example.ui.theme.TextCyberSecondary

@Composable
fun UserSubscriptionScreen(
    userProfile: UserProfile?,
    isPersian: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val usedBytes = userProfile?.dataUsageBytes?.toLong()?.coerceAtLeast(0L) ?: 0L
    val limitBytes = userProfile?.dataLimitBytes?.toLong()?.coerceAtLeast(1L) ?: 0L
    val remainingBytes = (limitBytes - usedBytes).coerceAtLeast(0L)
    val usageProgress = if (limitBytes > 0L) (usedBytes.toFloat() / limitBytes.toFloat()).coerceIn(0f, 1f) else 0f

    val statusRaw = userProfile?.subscriptionStatus?.lowercase() ?: "unverified"
    val statusColor = when (statusRaw) {
        "active" -> StatusConnected
        "trial" -> StatusConnecting
        "expired" -> StatusError
        else -> MetallicSilver
    }
    val statusLabel = when (statusRaw) {
        "active" -> if (isPersian) "فعال" else "ACTIVE"
        "trial" -> if (isPersian) "آزمایشی" else "TRIAL"
        "expired" -> if (isPersian) "منقضی شده" else "EXPIRED"
        else -> if (isPersian) "همگام‌سازی نشده" else "NOT SYNCED"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Brushed Metallic Membership Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF172230),
                                Color(0xFF0E1620),
                                Color(0xFF13242E)
                            )
                        )
                    )
                    .border(
                        BorderStroke(
                            1.2.dp,
                            Brush.linearGradient(listOf(ElectricCyan.copy(alpha = 0.7f), SilverHighlight.copy(alpha = 0.2f), PremiumTeal.copy(alpha = 0.6f)))
                        ),
                        RoundedCornerShape(22.dp)
                    )
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.VerifiedUser,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (isPersian) "وضعیت اشتراک TMS VPN" else "TMS VPN Membership",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextCyberPrimary
                                )
                                Text(
                                    text = userProfile?.email?.ifBlank { "Local Profile" } ?: if (isPersian) "حساب ابری همگام‌سازی نشده" else "Cloud account not synced",
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
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = statusLabel,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusColor
                            )
                        }
                    }

                    // Traffic Consumption Bar
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                text = if (limitBytes > 0L) "${formatCyberBytes(remainingBytes)} / ${formatCyberBytes(limitBytes)}"
                                else "-- / --",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElectricCyan
                            )
                        }

                        LinearProgressIndicator(
                            progress = { 1f - usageProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(999.dp)),
                            color = ElectricCyan,
                            trackColor = ElevatedSurface
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = if (isPersian) "سهمیه مصرف‌شده" else "Used Traffic",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextCyberSecondary
                            )
                            Text(
                                text = formatCyberBytes(usedBytes),
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextCyberPrimary
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = if (isPersian) "دستگاه‌های مجاز" else "Device Quota",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextCyberSecondary
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Filled.Devices, contentDescription = null, tint = PremiumTeal, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (userProfile != null) "${userProfile.deviceCount} / ${userProfile.maxDevices}" else "-- / --",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PremiumTeal
                                )
                            }
                        }
                    }
                }
            }

            // Renewal Information Card (Truthful — no fake payment buttons)
            CyberGlassCard(contentPadding = 16.dp) {
                Text(
                    text = if (isPersian) "اطلاعات تمدید و دریافت کانفیگ اختصاصی" else "Renewal & Provisioning Information",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextCyberPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isPersian) {
                        "تمدید اشتراک و صدور کانفیگ‌های اختصاصی VLESS با مدیریت سرور مرکزی و پشتیبانی از کانال‌های رسمی انجام می‌شود. پرداخت درون‌برنامه‌ای در این نسخه فعال نیست."
                    } else {
                        "Subscription renewals and dedicated VLESS configurations are provisioned through the administrator control panel and official TMS Time support channels."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextCyberSecondary
                )
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
                                text = if (isPersian) "کانفیگ‌های فعال حتی در زمان قطعی اینترنت" else "Active backup configs during network blackouts",
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
