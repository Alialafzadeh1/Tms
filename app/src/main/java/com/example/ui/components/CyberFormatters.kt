package com.example.ui.components

import com.example.data.model.VpnConfig
import com.example.data.model.VpnServer
import java.util.Locale

fun formatCyberSpeed(bytesPerSec: Long): String {
    return when {
        bytesPerSec >= 1024L * 1024L -> String.format(Locale.US, "%.2f MB/s", bytesPerSec / (1024.0 * 1024.0))
        bytesPerSec >= 1024L -> String.format(Locale.US, "%.1f KB/s", bytesPerSec / 1024.0)
        else -> "$bytesPerSec B/s"
    }
}

fun formatCyberBytes(bytes: Long): String {
    return when {
        bytes >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}

fun formatCyberDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%02d:%02d", m, s)
    }
}

fun inferCountryMeta(config: VpnConfig?, servers: List<VpnServer>): Triple<String, String, String> {
    if (config == null) return Triple("🌐", "--", "Global")
    val matchingServer = servers.firstOrNull {
        it.host.equals(config.server, ignoreCase = true) ||
            it.name.equals(config.name, ignoreCase = true)
    }
    if (matchingServer != null) {
        val code = matchingServer.countryCode.uppercase().ifBlank { "GL" }
        return Triple(countryFlagForCode(code), code, matchingServer.country.ifBlank { code })
    }
    val text = "${config.name} ${config.server}".lowercase(Locale.US)
    val code = when {
        text.contains("de") || text.contains("germany") || text.contains("frankfurt") || text.contains("آلمان") -> "DE"
        text.contains("nl") || text.contains("netherlands") || text.contains("amsterdam") || text.contains("هلند") -> "NL"
        text.contains("fi") || text.contains("finland") || text.contains("helsinki") || text.contains("فنلاند") -> "FI"
        text.contains("us") || text.contains("usa") || text.contains("america") || text.contains("آمریکا") -> "US"
        text.contains("gb") || text.contains("uk") || text.contains("london") || text.contains("انگلیس") -> "GB"
        text.contains("fr") || text.contains("france") || text.contains("paris") || text.contains("فرانسه") -> "FR"
        text.contains("tr") || text.contains("turkey") || text.contains("istanbul") || text.contains("ترکیه") -> "TR"
        text.contains("ae") || text.contains("dubai") || text.contains("امارات") -> "AE"
        text.contains("sg") || text.contains("singapore") || text.contains("سنگاپور") -> "SG"
        text.contains("ir") || text.contains("iran") || text.contains("ایران") -> "IR"
        else -> "GL"
    }
    val label = when (code) {
        "DE" -> "Germany"
        "NL" -> "Netherlands"
        "FI" -> "Finland"
        "US" -> "United States"
        "GB" -> "United Kingdom"
        "FR" -> "France"
        "TR" -> "Turkey"
        "AE" -> "UAE"
        "SG" -> "Singapore"
        "IR" -> "Iran Relay"
        else -> config.protocol.uppercase(Locale.US)
    }
    return Triple(countryFlagForCode(code), code, label)
}

fun countryFlagForCode(code: String): String = when (code.uppercase(Locale.US)) {
    "DE" -> "🇩🇪"
    "NL" -> "🇳🇱"
    "FI" -> "🇫🇮"
    "US" -> "🇺🇸"
    "GB", "UK" -> "🇬🇧"
    "FR" -> "🇫🇷"
    "TR" -> "🇹🇷"
    "AE" -> "🇦🇪"
    "SG" -> "🇸🇬"
    "IR" -> "🇮🇷"
    "CA" -> "🇨🇦"
    "JP" -> "🇯🇵"
    else -> "🌐"
}

fun mapCoordinatesForCode(code: String): Pair<Float, Float> = when (code.uppercase(Locale.US)) {
    "US", "CA" -> 0.22f to 0.36f
    "GB" -> 0.45f to 0.25f
    "FR" -> 0.47f to 0.29f
    "NL" -> 0.49f to 0.26f
    "DE" -> 0.51f to 0.28f
    "FI" -> 0.54f to 0.20f
    "TR" -> 0.56f to 0.34f
    "IR" -> 0.61f to 0.39f
    "AE" -> 0.61f to 0.45f
    "SG" -> 0.77f to 0.55f
    "JP" -> 0.85f to 0.36f
    else -> 0.50f to 0.32f
}
