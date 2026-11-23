package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.data.model.UserProfile
import com.example.data.security.LocalUserAuthManager
import com.example.ui.components.CyberGlassCard
import com.example.ui.theme.DeepGraphite
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.GlassBorderCyan
import com.example.ui.theme.GlassBorderMetallic
import com.example.ui.theme.GlassBorderSubtle
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MetallicSilver
import com.example.ui.theme.PremiumTeal
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.StatusConnected
import com.example.ui.theme.StatusError
import com.example.ui.theme.TextCyberPrimary
import com.example.ui.theme.TextCyberSecondary
import com.example.vpn.InstalledAppItem
import com.example.vpn.SplitTunnelManager
import com.example.vpn.VpnController
import com.example.vpn.engine.DynamicIpDnsRotator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileSettingsScreen(
    currentUserId: String,
    userProfile: UserProfile?,
    isPersian: Boolean,
    onToggleLanguage: () -> Unit,
    onOpenRadar: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenNexusAssistant: () -> Unit = {}
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        SplitTunnelManager.init(context)
    }

    val nationalIntranetMode by VpnController.nationalIntranetMode.collectAsStateWithLifecycle()
    val isAutoRotateEnabled by DynamicIpDnsRotator.isAutoRotateEnabled.collectAsStateWithLifecycle()
    val splitTunnelEnabled by SplitTunnelManager.splitTunnelEnabled.collectAsStateWithLifecycle()
    val bypassIranAppsEnabled by SplitTunnelManager.bypassIranAppsEnabled.collectAsStateWithLifecycle()
    val autoReconnectEnabled by SplitTunnelManager.autoReconnectEnabled.collectAsStateWithLifecycle()
    val killSwitchEnabled by SplitTunnelManager.killSwitchEnabled.collectAsStateWithLifecycle()
    val bypassedPackages by SplitTunnelManager.bypassedPackages.collectAsStateWithLifecycle()

    var notificationsEnabled by remember { mutableStateOf(true) }
    var showAppPickerSheet by remember { mutableStateOf(false) }
    var appSearchQuery by remember { mutableStateOf("") }
    var launchableApps by remember { mutableStateOf<List<InstalledAppItem>>(emptyList()) }

    val offlineUser by LocalUserAuthManager.offlineUserFlow.collectAsStateWithLifecycle()

    val displayName = userProfile?.displayName?.ifBlank { offlineUser?.username ?: "TMS User" }
        ?: offlineUser?.username
        ?: "TMS User"
    val email = userProfile?.email?.ifBlank { offlineUser?.email ?: currentUserId }
        ?: offlineUser?.email
        ?: currentUserId

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
            // User Identity Header Card
            CyberGlassCard(
                accentColor = ElectricCyan.copy(alpha = 0.55f),
                elevated = true,
                contentPadding = 18.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(ElevatedSurface)
                                .border(BorderStroke(1.5.dp, ElectricCyan), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = displayName.take(1).uppercase(),
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                                color = ElectricCyan
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextCyberPrimary
                            )
                            Text(
                                text = email,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.sp,
                                color = MetallicSilver
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onSignOut,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("profile_sign_out_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, StatusError.copy(alpha = 0.6f))
                    ) {
                        Icon(imageVector = Icons.Filled.Logout, contentDescription = null, tint = StatusError, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = if (isPersian) "خروج" else "Sign Out", color = StatusError, fontSize = 12.sp)
                    }
                }
            }

            // Device Management
            CyberGlassCard(contentPadding = 16.dp) {
                SectionLabel(
                    icon = Icons.Filled.Devices,
                    title = if (isPersian) "مدیریت دستگاه" else "Device Management"
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${Build.MANUFACTURER} ${Build.MODEL} • Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.sp,
                    color = TextCyberPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (userProfile != null) {
                        if (isPersian) "تعداد دستگاه فعال: ${userProfile.deviceCount} از ${userProfile.maxDevices}"
                        else "Active Device Slots: ${userProfile.deviceCount} / ${userProfile.maxDevices}"
                    } else {
                        if (isPersian) "دستگاه فعال • سهمیه ۵۰۰ گیگابایت (۱۰ دستگاه)" else "Active Device • 500 GB / 10 Devices"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = PremiumTeal
                )
            }

            // Connection Preferences
            CyberGlassCard(contentPadding = 16.dp) {
                SectionLabel(
                    icon = Icons.Filled.Hub,
                    title = if (isPersian) "تنظیمات اتصال و عبور از فیلتر" else "Connection Preferences"
                )
                Spacer(modifier = Modifier.height(8.dp))

                PreferenceToggleRow(
                    icon = Icons.Filled.Hub,
                    title = if (isPersian) "سپر نفوذ اینترنت ملی" else "National Intranet Piercer",
                    subtitle = if (isPersian) "مسیریابی اضطراری با پل داخلی" else "Domestic relay bridge mode",
                    checked = nationalIntranetMode,
                    onCheckedChange = { VpnController.toggleNationalIntranetMode() }
                )

                PreferenceToggleRow(
                    icon = Icons.Filled.Autorenew,
                    title = if (isPersian) "چرخش ۱۰ ثانیه‌ای IP و DNS" else "10s IP & DNS Auto-Hop",
                    subtitle = if (isPersian) "تطبیق دوره‌ای برای پایداری" else "Periodic anti-DPI rotation",
                    checked = isAutoRotateEnabled,
                    onCheckedChange = { DynamicIpDnsRotator.toggleAutoRotation() }
                )

                Spacer(modifier = Modifier.height(6.dp))
                OutlinedButton(
                    onClick = onOpenRadar,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, GlassBorderSubtle)
                ) {
                    Icon(imageVector = Icons.Filled.Radar, contentDescription = null, tint = PremiumTeal, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = if (isPersian) "اجرای رادار ضد فیلتر" else "Launch Anti-Filter Radar", color = PremiumTeal)
                }
            }

            // Per-App Split Tunneling, Kill Switch & Auto-Reconnect
            CyberGlassCard(
                accentColor = if (splitTunnelEnabled || killSwitchEnabled) ElectricCyan.copy(alpha = 0.45f) else GlassBorderSubtle,
                contentPadding = 16.dp
            ) {
                SectionLabel(
                    icon = Icons.Filled.AltRoute,
                    title = if (isPersian) "تفکیک برنامه‌ها و پایداری شبکه" else "Split Tunneling & Network Guard"
                )
                Spacer(modifier = Modifier.height(8.dp))

                PreferenceToggleRow(
                    icon = Icons.Filled.AltRoute,
                    title = if (isPersian) "تفکیک برنامه‌ها (Split Tunneling)" else "Per-App Split Tunneling",
                    subtitle = if (isPersian) "عبور مستقیم برنامه‌های مجاز بدون افت سرعت VPN" else "Allow selected apps to bypass VPN tunnel",
                    checked = splitTunnelEnabled,
                    onCheckedChange = { SplitTunnelManager.setSplitTunnelEnabled(context, it) }
                )

                PreferenceToggleRow(
                    icon = Icons.Filled.Apps,
                    title = if (isPersian) "مستثنی کردن اپ‌های بانکی و ایرانی" else "Bypass Iranian & Banking Apps",
                    subtitle = if (isPersian) "همراه‌بانک‌ها، اسنپ، روبیکا و دیجی‌کالا بدون VPN" else "Direct routing for local banks & Iranian services",
                    checked = bypassIranAppsEnabled,
                    onCheckedChange = { SplitTunnelManager.setBypassIranAppsEnabled(context, it) }
                )

                PreferenceToggleRow(
                    icon = Icons.Filled.Sync,
                    title = if (isPersian) "اتصال خودکار هنگام تغییر Wi-Fi / 4G" else "Auto-Reconnect on Network Handoff",
                    subtitle = if (isPersian) "تحمل جابه‌جایی آنتن و سوییچ خودکار نود" else "Preserve tunnel across Wi-Fi/LTE transitions",
                    checked = autoReconnectEnabled,
                    onCheckedChange = { SplitTunnelManager.setAutoReconnectEnabled(context, it) }
                )

                PreferenceToggleRow(
                    icon = Icons.Filled.Lock,
                    title = if (isPersian) "محافظ قطع ناگهانی (Kill Switch)" else "Session Kill Switch",
                    subtitle = if (isPersian) "جلوگیری از نشت DNS در زمان قطع ناخواسته" else "Prevent fallback leaks during tunnel drops",
                    checked = killSwitchEnabled,
                    onCheckedChange = { SplitTunnelManager.setKillSwitchEnabled(context, it) }
                )

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        launchableApps = SplitTunnelManager.getLaunchableApps(context)
                        appSearchQuery = ""
                        showAppPickerSheet = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("profile_split_tunnel_apps_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, GlassBorderCyan)
                ) {
                    Icon(imageVector = Icons.Filled.Apps, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isPersian) "انتخاب برنامه‌های مستثنی (${bypassedPackages.size})"
                        else "Manage Bypassed Apps (${bypassedPackages.size})",
                        color = ElectricCyan,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Language, Theme & Notifications
            CyberGlassCard(contentPadding = 16.dp) {
                SectionLabel(
                    icon = Icons.Filled.DarkMode,
                    title = if (isPersian) "ظاهر، زبان و اعلان‌ها" else "Appearance, Language & Alerts"
                )
                Spacer(modifier = Modifier.height(8.dp))

                PreferenceToggleRow(
                    icon = Icons.Filled.Language,
                    title = if (isPersian) "زبان فارسی (RTL)" else "Persian Language (RTL)",
                    subtitle = if (isPersian) "تغییر جهت و زبان رابط کاربری" else "Switch between Persian RTL and English LTR",
                    checked = isPersian,
                    onCheckedChange = { onToggleLanguage() }
                )

                PreferenceToggleRow(
                    icon = Icons.Filled.Notifications,
                    title = if (isPersian) "اعلان وضعیت تونل" else "Tunnel Status Notifications",
                    subtitle = if (isPersian) "نمایش وضعیت زنده در نوار اعلان" else "Persistent foreground VPN notification",
                    checked = notificationsEnabled,
                    onCheckedChange = { notificationsEnabled = it }
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isPersian) "پوسته فعال" else "Active Visual Theme",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextCyberSecondary
                    )
                    Text(
                        text = "Cyber Graphite Dark",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        color = ElectricCyan
                    )
                }
            }

            // کانال برنامه و پشتیبانی / Emergency Channels & About TMS VPN
            CyberGlassCard(
                accentColor = ElectricCyan.copy(alpha = 0.55f),
                contentPadding = 16.dp
            ) {
                SectionLabel(
                    icon = Icons.Filled.RssFeed,
                    title = if (isPersian) "کانال برنامه و پشتیبانی اضطراری" else "Official Channels & Support"
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "TMS VPN v${BuildConfig.VERSION_NAME} • Cyber Technology Edition",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 12.sp,
                    color = TextCyberPrimary
                )
                Text(
                    text = if (isPersian)
                        "حتی در شرایط قطع اینترنت و محدودیت‌های شدید، کانفیگ‌های سالم و نسخه‌های جدید در کانال‌های رسمی برنامه منتشر می‌شوند:"
                    else
                        "Even during severe internet blackouts, working configurations and releases are posted on our official channels:",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextCyberSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://rubika.ir/TMS_Time")).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, e.localizedMessage, Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("profile_channel_rubika_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PremiumTeal,
                            contentColor = DeepGraphite
                        )
                    ) {
                        Icon(imageVector = Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isPersian) "کانال روبیکا" else "Rubika",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/TMS_TIME")).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, e.localizedMessage, Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("profile_channel_telegram_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, GlassBorderCyan)
                    ) {
                        Icon(imageVector = Icons.Filled.SupportAgent, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isPersian) "کانال تلگرام" else "Telegram",
                            color = ElectricCyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onOpenNexusAssistant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("profile_open_nexus_support_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.75f))
                ) {
                    Icon(imageVector = Icons.Filled.Psychology, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isPersian) "پشتیبانی آنلاین و دستیار TMS NEXUS AI" else "Online Support & TMS NEXUS AI",
                        color = ElectricCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    if (showAppPickerSheet) {
        val filteredApps = remember(launchableApps, appSearchQuery) {
            val q = appSearchQuery.trim().lowercase()
            if (q.isEmpty()) launchableApps
            else launchableApps.filter {
                it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
            }
        }

        ModalBottomSheet(
            onDismissRequest = { showAppPickerSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = com.example.ui.theme.SecondarySurface,
            contentColor = TextCyberPrimary
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = if (isPersian) "برنامه‌های مستثنی از تونل VPN" else "Apps Bypassing VPN Tunnel",
                    style = MaterialTheme.typography.titleMedium,
                    color = ElectricCyan
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = appSearchQuery,
                    onValueChange = { appSearchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(imageVector = Icons.Filled.Search, contentDescription = null, tint = ElectricCyan) },
                    placeholder = { Text(if (isPersian) "جستجوی نام برنامه یا پکیج..." else "Search app or package...") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = GlassBorderSubtle,
                        focusedTextColor = TextCyberPrimary,
                        unfocusedTextColor = TextCyberPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        val isManuallyBypassed = bypassedPackages.contains(app.packageName)
                        val autoIranBypassed = bypassIranAppsEnabled && app.isIranianOrBanking
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(ElevatedSurface)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextCyberPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (autoIranBypassed) "${app.packageName} • ایران/بانکی" else app.packageName,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 10.sp,
                                    color = if (autoIranBypassed) PremiumTeal else TextCyberSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Switch(
                                checked = isManuallyBypassed || autoIranBypassed,
                                onCheckedChange = { SplitTunnelManager.togglePackageBypass(context, app.packageName) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = DeepGraphite,
                                    checkedTrackColor = StatusConnected
                                )
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SectionLabel(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = TextCyberPrimary)
    }
}

@Composable
private fun PreferenceToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MetallicSilver, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = TextCyberPrimary)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = TextCyberSecondary)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = DeepGraphite,
                checkedTrackColor = StatusConnected,
                uncheckedThumbColor = MetallicSilver,
                uncheckedTrackColor = ElevatedSurface
            )
        )
    }
}
