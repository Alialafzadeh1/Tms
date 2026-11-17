package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ==========================================================
// TMS VPN — PREMIUM CYBER TECHNOLOGY COLOR SYSTEM
// ==========================================================

// Primary Backgrounds & Surfaces
val DeepGraphite = Color(0xFF080B10)
val SecondarySurface = Color(0xFF101720)
val ElevatedSurface = Color(0xFF17212C)
val GlassCardSurface = Color(0xCC111924)
val GlassCardElevated = Color(0xD917212C)

// Core Cyber Accents
val ElectricCyan = Color(0xFF00D9FF)
val PremiumTeal = Color(0xFF00BFA6)
val MetallicSilver = Color(0xFFB8C5D1)
val SilverHighlight = Color(0xFFE2EBF3)
val DarkCyanContainer = Color(0xFF092A36)
val DarkTealContainer = Color(0xFF082D28)

// Semantic Status Colors
val StatusConnected = Color(0xFF00D6A0)
val StatusConnecting = Color(0xFFFFB547)
val StatusDisconnected = Color(0xFF8995A3)
val StatusError = Color(0xFFFF5263)

// Text Hierarchy
val TextCyberPrimary = Color(0xFFF2F6FA)
val TextCyberSecondary = Color(0xFF8995A3)
val TextCyberSilver = Color(0xFFB8C5D1)

// Hairline & Metallic Glass Borders
val GlassBorderSubtle = Color(0xFF223040)
val GlassBorderMetallic = Color(0xFF334659)
val GlassBorderCyan = Color(0xFF0088A3)

// ==========================================================
// COMPATIBILITY ALIASES (Harmonizes Admin & Existing Screens)
// ==========================================================
val DeepBlackBackground = DeepGraphite
val ObsidianSurface = SecondarySurface
val CardSurfaceDark = SecondarySurface
val CardSurfaceElevated = ElevatedSurface

val MetallicGold = ElectricCyan
val BrightGold = SilverHighlight
val DarkGold = PremiumTeal
val GoldContainer = DarkCyanContainer
val OnGoldContainer = Color(0xFFB8F4FF)

val EmeraldGreen = StatusConnected
val EmeraldGreenGlow = PremiumTeal
val DarkEmerald = DarkTealContainer
val EmeraldContainer = DarkTealContainer

val TextWhitePrimary = TextCyberPrimary
val TextGraySecondary = TextCyberSecondary
val TextGoldMuted = MetallicSilver

val BorderGoldMuted = GlassBorderSubtle
val BorderGoldHighlight = GlassBorderCyan
val StatusOfflineRed = StatusError
val StatusWarningAmber = StatusConnecting
