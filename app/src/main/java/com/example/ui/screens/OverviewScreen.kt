package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CardMembership
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.OverviewMetrics
import com.example.data.model.VpnConfig
import com.example.data.model.VpnServer
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

@Composable
fun OverviewScreen(
    metrics: OverviewMetrics,
    configs: List<VpnConfig>,
    servers: List<VpnServer>,
    isPersian: Boolean,
    onNavigateToUsers: () -> Unit = {},
    onNavigateToSubscriptions: () -> Unit = {},
    onNavigateToServers: () -> Unit = {},
    onNavigateToConfigs: () -> Unit = {}
) {
    val activeDeTrCount = configs.count {
        val hay = "${it.name} ${it.server}".lowercase()
        hay.contains("germany") || hay.contains("turkey") || hay.contains("frankfurt") ||
            hay.contains("istanbul") || hay.contains("de ") || hay.contains("tr ") ||
            hay.contains("[de]") || hay.contains("[tr]")
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("admin_overview_screen"),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Management Operations Header (Management-Only — zero personal VPN connect controls)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.2.dp, MetallicGold), RoundedCornerShape(18.dp)),
                colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(GoldContainer, CircleShape)
                                    .border(BorderStroke(1.dp, MetallicGold), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AdminPanelSettings,
                                    contentDescription = null,
                                    tint = MetallicGold,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (isPersian) "مرکز عملیات مدیریت زیرساخت" else "Infrastructure Operations Console",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MetallicGold
                                )
                                Text(
                                    text = if (isPersian) "پنل اختصاصی مدیریت کاربران، اشتراک‌ها، مخزن IP و نودها" else "Dedicated management for users, subscriptions, IP pools & servers",
                                    fontSize = 11.sp,
                                    color = TextGraySecondary
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(EmeraldContainer, RoundedCornerShape(20.dp))
                                .border(BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.6f)), RoundedCornerShape(20.dp))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Box(modifier = Modifier.size(7.dp).background(EmeraldGreen, CircleShape))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isPersian) "حالت مدیریت" else "ADMIN OPS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldGreen
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0A0C12), RoundedCornerShape(12.dp))
                            .border(BorderStroke(0.8.dp, BorderGoldMuted), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isPersian) "وضعیت همگام‌سازی و سلامت هسته" else "Sync & Infrastructure Health",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhitePrimary
                            )
                            Text(
                                text = if (isPersian) {
                                    "نودهای آلمان و ترکیه فعال: $activeDeTrCount • امتیاز سلامت: ${metrics.systemHealthScore}%"
                                } else {
                                    "DE/TR anti-DPI nodes: $activeDeTrCount • Health score: ${metrics.systemHealthScore}%"
                                },
                                fontSize = 11.sp,
                                color = TextGraySecondary
                            )
                        }
                        Box(
                            modifier = Modifier
                                .background(
                                    if (metrics.isRealBackendConnected) EmeraldContainer else Color(0xFF231D0B),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = if (metrics.isRealBackendConnected) {
                                    if (isPersian) "همگام‌سازی کامل" else "Unified Sync"
                                } else {
                                    if (isPersian) "پایگاه یکپارچه محلی" else "Unified Room DB"
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (metrics.isRealBackendConnected) EmeraldGreen else StatusWarningAmber
                            )
                        }
                    }
                }
            }
        }

        // Users & Subscriptions KPIs
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, BorderGoldMuted), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = if (isPersian) "آمار کاربران و اشتراک‌ها" else "Users & Subscription Metrics",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MetallicGold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ManagementKpiCell(
                            title = if (isPersian) "کل کاربران ثبت‌نامی" else "Registered Users",
                            value = "${metrics.registeredUsersCount}",
                            icon = Icons.Default.Group,
                            tint = ElectricCyan,
                            modifier = Modifier.weight(1f)
                        )
                        ManagementKpiCell(
                            title = if (isPersian) "اشتراک‌های فعال" else "Active Plans",
                            value = "${metrics.activeSubscriptionsCount}",
                            icon = Icons.Default.CardMembership,
                            tint = EmeraldGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ManagementKpiCell(
                            title = if (isPersian) "درخواست‌های پرداخت باز" else "Pending Payments",
                            value = "${metrics.pendingPaymentsCount}",
                            icon = Icons.Default.Payments,
                            tint = if (metrics.pendingPaymentsCount > 0) StatusWarningAmber else TextWhitePrimary,
                            modifier = Modifier.weight(1f)
                        )
                        ManagementKpiCell(
                            title = if (isPersian) "کل کانفیگ‌های سیستم" else "Total Configs",
                            value = "${metrics.totalConfigs}",
                            icon = Icons.Default.VpnKey,
                            tint = MetallicGold,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Endpoint Inventory & Clean IP Pool KPIs
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, BorderGoldMuted), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = if (isPersian) "وضعیت سرورها، استخر اختصاصی و مخزن IP تمیز" else "Servers, Dedicated Endpoints & Clean IP Pool",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MetallicGold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ManagementKpiCell(
                            title = if (isPersian) "استخر IP اختصاصی" else "Dedicated IP Pool",
                            value = "${metrics.healthyEndpointsCount} / ${metrics.totalEndpointsCount}",
                            icon = Icons.Default.Hub,
                            tint = EmeraldGreen,
                            modifier = Modifier.weight(1f)
                        )
                        ManagementKpiCell(
                            title = if (isPersian) "مخزن IPهای تمیز" else "Clean IP Pool",
                            value = "${metrics.cleanIpHealthyCount} / ${metrics.cleanIpTotalCount}",
                            icon = Icons.Default.Shield,
                            tint = ElectricCyan,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF090A0F), RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = if (isPersian) "سرورهای آنلاین" else "Online Servers", fontSize = 10.sp, color = TextGraySecondary)
                            Text(text = "${metrics.activeServers} / ${servers.size.coerceAtLeast(metrics.totalServers)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = EmeraldGreen)
                        }

                        Box(modifier = Modifier.width(1.dp).height(24.dp).background(BorderGoldMuted))

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = if (isPersian) "میانگین تاخیر" else "Avg Latency", fontSize = 10.sp, color = TextGraySecondary)
                            Text(
                                text = if (metrics.averageLatencyMs > 0) "${metrics.averageLatencyMs}ms" else "--",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MetallicGold
                            )
                        }

                        Box(modifier = Modifier.width(1.dp).height(24.dp).background(BorderGoldMuted))

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = if (isPersian) "ترافیک زیرساخت" else "Pool Bandwidth", fontSize = 10.sp, color = TextGraySecondary)
                            Text(
                                text = String.format("%.1f GB", metrics.totalBandwidthGb),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhitePrimary
                            )
                        }
                    }
                }
            }
        }

        // Quick Admin Actions
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, BorderGoldHighlight), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (isPersian) "دسترسی سریع مدیریت" else "Quick Management Shortcuts",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MetallicGold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onNavigateToSubscriptions,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("admin_overview_go_subscriptions"),
                            colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = Color(0xFF1E1700)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.CardMembership, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isPersian) "اشتراک‌ها و تخصیص IP" else "Subscriptions",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = onNavigateToUsers,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("admin_overview_go_users"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricCyan),
                            border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Group, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isPersian) "دایرکتوری کاربران" else "Users Directory",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onNavigateToServers,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("admin_overview_go_servers"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldGreen),
                            border = BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Dns, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isPersian) "مدیریت سرورها" else "Servers",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = onNavigateToConfigs,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("admin_overview_go_configs"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhitePrimary),
                            border = BorderStroke(1.dp, BorderGoldMuted),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isPersian) "مدیریت کانفیگ‌ها" else "Configs",
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

@Composable
private fun ManagementKpiCell(
    title: String,
    value: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color(0xFF0B0D14), RoundedCornerShape(10.dp))
            .border(BorderStroke(0.6.dp, BorderGoldMuted), RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(tint.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(text = title, fontSize = 10.sp, color = TextGraySecondary)
                Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = tint)
            }
        }
    }
}
