package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CardMembership
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.CardMembership
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.VpnConnectionState
import com.example.ui.theme.DeepGraphite
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.GlassBorderMetallic
import com.example.ui.theme.GlassBorderSubtle
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MetallicSilver
import com.example.ui.theme.PremiumTeal
import com.example.ui.theme.SecondarySurface
import com.example.ui.theme.SilverHighlight
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.StatusConnected
import com.example.ui.theme.StatusConnecting
import com.example.ui.theme.StatusDisconnected
import com.example.ui.theme.StatusError
import com.example.ui.theme.TextCyberPrimary
import com.example.ui.theme.TextCyberSecondary

enum class UserTabDestination(
    val route: String,
    val titleFa: String,
    val titleEn: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("user_home", "خانه", "Home", Icons.Filled.Home, Icons.Outlined.Home),
    SERVERS("user_servers", "سرورها", "Servers", Icons.Filled.Public, Icons.Outlined.Public),
    STATISTICS("user_statistics", "آمار", "Stats", Icons.Filled.Analytics, Icons.Outlined.Analytics),
    SUBSCRIPTION("user_subscription", "اشتراک", "Plan", Icons.Filled.CardMembership, Icons.Outlined.CardMembership),
    PROFILE("user_profile", "پروفایل", "Profile", Icons.Filled.Person, Icons.Outlined.Person)
}

fun statusColorFor(state: VpnConnectionState): Color = when (state) {
    VpnConnectionState.CONNECTED -> StatusConnected
    VpnConnectionState.CONNECTING, VpnConnectionState.DISCONNECTING -> StatusConnecting
    VpnConnectionState.DISCONNECTED -> StatusDisconnected
    VpnConnectionState.ERROR -> StatusError
}

/**
 * Lightweight Deep Graphite atmospheric background with subtle radial cyber lighting
 * and faint telemetry grid — optimized for mid-range GPUs (e.g. Galaxy A15).
 */
@Composable
fun CyberAmbientBackdrop(
    accentColor: Color = ElectricCyan,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DeepGraphite)
            .drawBehind {
                // Soft top ambient radial glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.11f),
                            PremiumTeal.copy(alpha = 0.04f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.22f),
                        radius = size.maxDimension * 0.58f
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.22f),
                    radius = size.maxDimension * 0.58f
                )

                // Subtle bottom corner depth sheen
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ElectricCyan.copy(alpha = 0.05f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.88f, size.height * 0.85f),
                        radius = size.maxDimension * 0.42f
                    ),
                    center = Offset(size.width * 0.88f, size.height * 0.85f),
                    radius = size.maxDimension * 0.42f
                )

                // Ultra-faint architectural cyber dot grid
                val step = 34.dp.toPx()
                val dotColor = Color(0xFF1D2A38).copy(alpha = 0.35f)
                var y = step * 0.5f
                while (y < size.height) {
                    var x = step * 0.5f
                    while (x < size.width) {
                        drawCircle(color = dotColor, radius = 0.9.dp.toPx(), center = Offset(x, y))
                        x += step
                    }
                    y += step
                }
            }
    ) {
        content()
    }
}

/**
 * Layered metallic glassmorphic card with specular top-edge bevel, optional glow accent,
 * and ripple feedback when interactive.
 */
@Composable
fun CyberGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 18.dp,
    accentColor: Color? = null,
    elevated: Boolean = false,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (onClick != null && isPressed) 0.985f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "CardPressScale"
    )

    val shape = RoundedCornerShape(cornerRadius)
    val borderBrush = if (accentColor != null) {
        Brush.linearGradient(
            colors = listOf(
                accentColor.copy(alpha = 0.65f),
                GlassBorderMetallic.copy(alpha = 0.5f),
                accentColor.copy(alpha = 0.25f)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                SilverHighlight.copy(alpha = 0.16f),
                GlassBorderSubtle,
                GlassBorderSubtle.copy(alpha = 0.6f)
            )
        )
    }

    val surfaceBrush = if (elevated) {
        Brush.verticalGradient(
            colors = listOf(
                ElevatedSurface.copy(alpha = 0.94f),
                SecondarySurface.copy(alpha = 0.96f)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                SecondarySurface.copy(alpha = 0.92f),
                Color(0xFF0C1219).copy(alpha = 0.96f)
            )
        )
    }

    val clickModifier = if (onClick != null) {
        Modifier
            .minimumInteractiveComponentSize()
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = accentColor ?: ElectricCyan),
                onClick = onClick
            )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(surfaceBrush, shape)
            .border(BorderStroke(1.dp, borderBrush), shape)
            .then(clickModifier)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            content = content
        )
    }
}

/**
 * Custom geometric TMS Cyber Shield vector emblem.
 */
@Composable
fun TmsLogoEmblem(
    accentColor: Color = ElectricCyan,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(ElevatedSurface, SecondarySurface)
                )
            )
            .border(
                BorderStroke(
                    1.2.dp,
                    Brush.linearGradient(listOf(accentColor, MetallicSilver.copy(alpha = 0.4f)))
                ),
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.58f)) {
            val w = this.size.width
            val h = this.size.height
            val shieldPath = Path().apply {
                moveTo(w * 0.5f, 0f)
                lineTo(w * 0.92f, h * 0.18f)
                lineTo(w * 0.86f, h * 0.62f)
                lineTo(w * 0.5f, h)
                lineTo(w * 0.14f, h * 0.62f)
                lineTo(w * 0.08f, h * 0.18f)
                close()
            }
            drawPath(
                path = shieldPath,
                brush = Brush.verticalGradient(
                    listOf(accentColor.copy(alpha = 0.25f), PremiumTeal.copy(alpha = 0.08f))
                )
            )
            drawPath(
                path = shieldPath,
                color = accentColor,
                style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            // Inner geometric core diamond
            val corePath = Path().apply {
                moveTo(w * 0.5f, h * 0.26f)
                lineTo(w * 0.68f, h * 0.48f)
                lineTo(w * 0.5f, h * 0.72f)
                lineTo(w * 0.32f, h * 0.48f)
                close()
            }
            drawPath(
                path = corePath,
                color = SilverHighlight,
                style = Stroke(width = 1.3.dp.toPx(), join = StrokeJoin.Round)
            )
        }
    }
}

@Composable
fun CyberTopHeader(
    isPersian: Boolean,
    vpnState: VpnConnectionState,
    unreadAlertsCount: Int,
    userInitial: String,
    onOpenNotifications: () -> Unit,
    onOpenProfile: () -> Unit,
    onToggleLanguage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = statusColorFor(vpnState)
    val animatedAccent by animateColorAsState(targetValue = accent, label = "HeaderAccent")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Brand Identity
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            TmsLogoEmblem(accentColor = if (vpnState == VpnConnectionState.DISCONNECTED) ElectricCyan else animatedAccent)
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "TMS VPN",
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextCyberPrimary,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ElectricCyan.copy(alpha = 0.14f))
                            .border(BorderStroke(0.7.dp, ElectricCyan.copy(alpha = 0.45f)), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "CYBER",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElectricCyan
                        )
                    }
                }
                Text(
                    text = if (isPersian) "فناوری امنیت شبکه" else "Premium Cyber Technology",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextCyberSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Actions: Language, Notifications, Profile
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Language pill (48dp touch compliant)
            Surface(
                onClick = onToggleLanguage,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("language_toggle_button"),
                shape = RoundedCornerShape(12.dp),
                color = SecondarySurface,
                border = BorderStroke(1.dp, GlassBorderSubtle)
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isPersian) "EN" else "فا",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MetallicSilver
                    )
                }
            }

            // Notification indicator
            Box(contentAlignment = Alignment.TopEnd) {
                IconButton(
                    onClick = onOpenNotifications,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SecondarySurface)
                        .border(BorderStroke(1.dp, GlassBorderSubtle), RoundedCornerShape(12.dp))
                        .testTag("notifications_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = if (isPersian) "اعلان‌ها و رویدادها" else "Notifications",
                        tint = MetallicSilver,
                        modifier = Modifier.size(20.dp)
                    )
                }
                if (unreadAlertsCount > 0) {
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp, end = 8.dp)
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(ElectricCyan)
                    )
                }
            }

            // Profile avatar button
            Surface(
                onClick = onOpenProfile,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("header_profile_button"),
                shape = RoundedCornerShape(12.dp),
                color = ElevatedSurface,
                border = BorderStroke(1.dp, GlassBorderMetallic)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = userInitial.take(1).uppercase().ifBlank { "T" },
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = ElectricCyan
                    )
                }
            }
        }
    }
}

@Composable
fun CyberStatusPill(
    state: VpnConnectionState,
    isPersian: Boolean,
    modifier: Modifier = Modifier
) {
    val color = statusColorFor(state)
    val label = when (state) {
        VpnConnectionState.CONNECTED -> if (isPersian) "متصل و رمزنگاری‌شده" else "CONNECTED"
        VpnConnectionState.CONNECTING -> if (isPersian) "در حال اتصال..." else "CONNECTING..."
        VpnConnectionState.DISCONNECTING -> if (isPersian) "در حال قطع اتصال..." else "DISCONNECTING..."
        VpnConnectionState.ERROR -> if (isPersian) "خطای اتصال" else "CONNECTION ERROR"
        VpnConnectionState.DISCONNECTED -> if (isPersian) "آماده اتصال امن" else "STANDBY"
    }

    Row(
        modifier = modifier
            .testTag("vpn_status_pill")
            .semantics(mergeDescendants = true) {}
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.13f))
            .border(BorderStroke(1.dp, color.copy(alpha = 0.5f)), RoundedCornerShape(999.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontSize = 12.sp,
            color = color
        )
    }
}

@Composable
fun TelemetryMetricTile(
    label: String,
    value: String,
    icon: ImageVector,
    accentColor: Color = ElectricCyan,
    modifier: Modifier = Modifier
) {
    CyberGlassCard(
        modifier = modifier,
        cornerRadius = 16.dp,
        accentColor = accentColor.copy(alpha = 0.35f),
        contentPadding = 12.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = accentColor,
                    modifier = Modifier.size(15.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = TextCyberSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = value,
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = TextCyberPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Glassmorphic 5-Tab Bottom Navigation Bar with WindowInsets.navigationBars protection.
 */
@Composable
fun CyberBottomNavBar(
    currentTab: UserTabDestination,
    isPersian: Boolean,
    onSelectTab: (UserTabDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(
                BorderStroke(
                    0.8.dp,
                    Brush.horizontalGradient(
                        listOf(
                            GlassBorderSubtle,
                            ElectricCyan.copy(alpha = 0.35f),
                            GlassBorderSubtle
                        )
                    )
                ),
                RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
            ),
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        color = SecondarySurface.copy(alpha = 0.96f),
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserTabDestination.entries.forEach { tab ->
                val selected = currentTab == tab
                val scale by animateFloatAsState(
                    targetValue = if (selected) 1.04f else 1f,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    label = "NavScale"
                )
                val pillBg = if (selected) ElectricCyan.copy(alpha = 0.14f) else Color.Transparent
                val itemColor = if (selected) ElectricCyan else TextCyberSecondary

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .scale(scale)
                        .clip(RoundedCornerShape(14.dp))
                        .minimumInteractiveComponentSize()
                        .clickable { onSelectTab(tab) }
                        .padding(vertical = 4.dp)
                        .testTag("nav_tab_${tab.route}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(pillBg)
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = if (isPersian) tab.titleFa else tab.titleEn,
                            tint = itemColor,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isPersian) tab.titleFa else tab.titleEn,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = itemColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
