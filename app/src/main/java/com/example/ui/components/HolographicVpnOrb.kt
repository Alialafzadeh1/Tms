package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.VpnConnectionState
import com.example.ui.theme.DeepGraphite
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MetallicSilver
import com.example.ui.theme.PremiumTeal
import com.example.ui.theme.SecondarySurface
import com.example.ui.theme.SilverHighlight
import com.example.ui.theme.StatusConnected
import com.example.ui.theme.StatusConnecting
import com.example.ui.theme.StatusDisconnected
import com.example.ui.theme.StatusError
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Signature 3D-inspired Holographic Energy Core for TMS VPN.
 * Built natively on hardware-accelerated Compose Canvas with 3D-projected orbital
 * rings, brushed metallic instrument bezel, off-center spherical shading,
 * and state-driven motion optimized for mid-range devices (Galaxy A15).
 */
@Composable
fun HolographicVpnOrb(
    vpnState: VpnConnectionState,
    isPersian: Boolean,
    onToggleConnection: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 256.dp
) {
    val isConnected = vpnState == VpnConnectionState.CONNECTED
    val isConnecting = vpnState == VpnConnectionState.CONNECTING
    val isDisconnecting = vpnState == VpnConnectionState.DISCONNECTING
    val isBusy = isConnecting || isDisconnecting
    val isError = vpnState == VpnConnectionState.ERROR

    val primaryGlow by animateColorAsState(
        targetValue = when (vpnState) {
            VpnConnectionState.CONNECTED -> StatusConnected
            VpnConnectionState.CONNECTING, VpnConnectionState.DISCONNECTING -> StatusConnecting
            VpnConnectionState.ERROR -> StatusError
            VpnConnectionState.DISCONNECTED -> ElectricCyan
        },
        animationSpec = tween(450),
        label = "OrbPrimaryGlow"
    )

    val secondaryAccent by animateColorAsState(
        targetValue = when (vpnState) {
            VpnConnectionState.CONNECTED -> PremiumTeal
            VpnConnectionState.CONNECTING, VpnConnectionState.DISCONNECTING -> ElectricCyan
            VpnConnectionState.ERROR -> Color(0xFFFF8A65)
            VpnConnectionState.DISCONNECTED -> MetallicSilver
        },
        animationSpec = tween(450),
        label = "OrbSecondaryAccent"
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "OrbPressScale"
    )

    // Controlled, low-overhead animation driver
    val infiniteTransition = rememberInfiniteTransition(label = "HolographicOrbMotion")
    val orbitalPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isConnecting) 3600 else if (isConnected) 9000 else 16000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "OrbitalPhase"
    )

    val energyPulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isConnecting) 900 else 2200,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "EnergyPulse"
    )

    val actionLabel = when (vpnState) {
        VpnConnectionState.CONNECTED -> if (isPersian) "قطع اتصال" else "DISCONNECT"
        VpnConnectionState.CONNECTING -> if (isPersian) "در حال اتصال..." else "CONNECTING..."
        VpnConnectionState.DISCONNECTING -> if (isPersian) "در حال قطع اتصال..." else "DISCONNECTING..."
        VpnConnectionState.ERROR -> if (isPersian) "اتصال امن" else "RETRY"
        VpnConnectionState.DISCONNECTED -> if (isPersian) "اتصال امن" else "CONNECT"
    }

    val subLabel = when (vpnState) {
        VpnConnectionState.CONNECTED -> if (isPersian) "متصل و فعال ✓" else "CONNECTED ✓"
        VpnConnectionState.DISCONNECTED -> if (isPersian) "لمس برای اتصال" else "TAP TO CONNECT"
        VpnConnectionState.ERROR -> if (isPersian) "تلاش مجدد" else "TAP TO RETRY"
        else -> null
    }

    val accessibilityLabel = when (vpnState) {
        VpnConnectionState.CONNECTED -> if (isPersian) "متصل شد، لمس برای قطع اتصال وی‌پی‌ان" else "Connected, tap to disconnect VPN"
        VpnConnectionState.CONNECTING -> if (isPersian) "در حال اتصال وی‌پی‌ان" else "Connecting VPN"
        VpnConnectionState.DISCONNECTING -> if (isPersian) "در حال قطع اتصال وی‌پی‌ان" else "Disconnecting VPN"
        else -> if (isPersian) "اتصال امن وی‌پی‌ان" else "Connect VPN"
    }

    Box(
        modifier = modifier
            .size(size)
            .scale(pressScale),
        contentAlignment = Alignment.Center
    ) {
        // Outer volumetric glow + 3D orbital rings + metallic bezel
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val radius = this.size.minDimension / 2f

            // 1. Soft atmospheric energy aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryGlow.copy(alpha = (if (isConnected) 0.28f else 0.14f) * energyPulse),
                        secondaryAccent.copy(alpha = 0.06f * energyPulse),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = radius
                ),
                center = Offset(cx, cy),
                radius = radius
            )

            // 2. Precision metallic reticle ticks around outer ring
            val tickOuter = radius * 0.92f
            val tickInner = radius * 0.87f
            for (i in 0 until 48) {
                val angleRad = (i * 7.5f) * (PI / 180.0)
                val isMajor = i % 6 == 0
                val startR = if (isMajor) tickInner - 4.dp.toPx() else tickInner
                val tickColor = if (isMajor) {
                    primaryGlow.copy(alpha = 0.65f)
                } else {
                    MetallicSilver.copy(alpha = 0.20f)
                }
                drawLine(
                    color = tickColor,
                    start = Offset(
                        cx + (startR * cos(angleRad)).toFloat(),
                        cy + (startR * sin(angleRad)).toFloat()
                    ),
                    end = Offset(
                        cx + (tickOuter * cos(angleRad)).toFloat(),
                        cy + (tickOuter * sin(angleRad)).toFloat()
                    ),
                    strokeWidth = if (isMajor) 1.6.dp.toPx() else 0.9.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // 3. Three 3D-tilted elliptical orbital rings with moving light nodes
            val orbitConfigs = listOf(
                Triple(radius * 0.84f, radius * 0.34f, 24f + orbitalPhase * 0.35f),
                Triple(radius * 0.82f, radius * 0.30f, -34f - orbitalPhase * 0.50f),
                Triple(radius * 0.78f, radius * 0.38f, 82f + orbitalPhase * 0.25f)
            )

            orbitConfigs.forEachIndexed { idx, (rx, ry, tiltDeg) ->
                val ringColor = when (idx) {
                    0 -> primaryGlow.copy(alpha = 0.55f)
                    1 -> secondaryAccent.copy(alpha = 0.42f)
                    else -> SilverHighlight.copy(alpha = 0.28f)
                }
                rotate(degrees = tiltDeg, pivot = Offset(cx, cy)) {
                    drawOval(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                Color.Transparent,
                                ringColor,
                                SilverHighlight.copy(alpha = 0.65f),
                                ringColor.copy(alpha = 0.2f),
                                Color.Transparent
                            ),
                            center = Offset(cx, cy)
                        ),
                        topLeft = Offset(cx - rx, cy - ry),
                        size = Size(rx * 2f, ry * 2f),
                        style = Stroke(width = 1.5.dp.toPx())
                    )

                    // Orbiting glowing energy node along ellipse perimeter
                    val nodeAngle = ((orbitalPhase * (if (idx % 2 == 0) 1f else -1.2f)) + idx * 120f) * (PI / 180.0)
                    val nx = cx + (rx * cos(nodeAngle)).toFloat()
                    val ny = cy + (ry * sin(nodeAngle)).toFloat()
                    drawCircle(
                        color = primaryGlow.copy(alpha = 0.35f),
                        radius = 5.dp.toPx(),
                        center = Offset(nx, ny)
                    )
                    drawCircle(
                        color = SilverHighlight,
                        radius = 2.2.dp.toPx(),
                        center = Offset(nx, ny)
                    )
                }
            }

            // 4. Active sweeping telemetry arc when connected/connecting
            if (isConnected || isBusy) {
                val arcRadius = radius * 0.74f
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(
                            Color.Transparent,
                            primaryGlow.copy(alpha = 0.85f),
                            SilverHighlight.copy(alpha = 0.9f),
                            Color.Transparent
                        ),
                        center = Offset(cx, cy)
                    ),
                    startAngle = orbitalPhase,
                    sweepAngle = if (isConnecting) 140f else 95f,
                    useCenter = false,
                    topLeft = Offset(cx - arcRadius, cy - arcRadius),
                    size = Size(arcRadius * 2f, arcRadius * 2f),
                    style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        // 5. Inner 3D Shaded Translucent Energy Core Sphere (Interactive 48dp+ target)
        val coreSize = size * 0.61f
        Box(
            modifier = Modifier
                .size(coreSize)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            ElevatedSurface,
                            SecondarySurface,
                            DeepGraphite
                        ),
                        center = Offset(0.32f, 0.28f)
                    )
                )
                .border(
                    BorderStroke(
                        2.dp,
                        Brush.linearGradient(
                            colors = listOf(
                                SilverHighlight.copy(alpha = 0.65f),
                                primaryGlow.copy(alpha = 0.85f),
                                secondaryAccent.copy(alpha = 0.35f)
                            )
                        )
                    ),
                    CircleShape
                )
                .semantics {
                    contentDescription = accessibilityLabel
                }
                .clickable(
                    enabled = !isDisconnecting,
                    interactionSource = interactionSource,
                    indication = ripple(color = primaryGlow, bounded = true),
                    role = Role.Button,
                    onClick = onToggleConnection
                )
                .testTag("vpn_connect_orb_button"),
            contentAlignment = Alignment.Center
        ) {
            // Internal geodesic wireframe & specular light highlight
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = this.size.width / 2f
                val cy = this.size.height / 2f
                val r = this.size.minDimension / 2f

                // Top-left 3D sphere specular sheen
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            SilverHighlight.copy(alpha = 0.18f),
                            primaryGlow.copy(alpha = 0.10f),
                            Color.Transparent
                        ),
                        center = Offset(cx * 0.65f, cy * 0.58f),
                        radius = r * 0.85f
                    ),
                    center = Offset(cx, cy),
                    radius = r
                )

                // Rotating internal cyber hexagon lattice
                rotate(degrees = orbitalPhase * 0.3f, pivot = Offset(cx, cy)) {
                    val hexR = r * 0.72f
                    val hexPath = Path()
                    for (i in 0..5) {
                        val a = (i * 60.0) * (PI / 180.0)
                        val px = cx + (hexR * cos(a)).toFloat()
                        val py = cy + (hexR * sin(a)).toFloat()
                        if (i == 0) hexPath.moveTo(px, py) else hexPath.lineTo(px, py)
                    }
                    hexPath.close()
                    drawPath(
                        path = hexPath,
                        color = primaryGlow.copy(alpha = if (isConnected) 0.34f else 0.16f),
                        style = Stroke(width = 1.1.dp.toPx())
                    )
                }

                // Bottom rim light reflection
                drawArc(
                    color = primaryGlow.copy(alpha = if (isError) 0.6f else 0.45f),
                    startAngle = 35f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(6.dp.toPx(), 6.dp.toPx()),
                    size = Size(this.size.width - 12.dp.toPx(), this.size.height - 12.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(42.dp),
                        color = primaryGlow,
                        trackColor = ElevatedSurface,
                        strokeWidth = 3.dp
                    )
                } else {
                    Icon(
                        imageVector = if (isConnected) Icons.Filled.PowerOff else Icons.Filled.PowerSettingsNew,
                        contentDescription = null,
                        tint = if (isConnected) StatusConnected else if (isError) StatusError else ElectricCyan,
                        modifier = Modifier.size(42.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = actionLabel,
                    fontFamily = if (isPersian) null else JetBrainsMonoFontFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = if (isPersian) 0.sp else 1.sp,
                    color = if (isConnected) StatusConnected else if (isError) StatusError else SilverHighlight
                )
                if (subLabel != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subLabel,
                        fontFamily = if (isPersian) null else JetBrainsMonoFontFamily,
                        fontSize = 9.sp,
                        color = if (isConnected) StatusConnected.copy(alpha = 0.8f) else MetallicSilver.copy(alpha = 0.75f)
                    )
                }
            }
        }
    }
}
