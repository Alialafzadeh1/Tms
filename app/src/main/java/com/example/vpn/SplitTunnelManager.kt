package com.example.vpn

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class InstalledAppItem(
    val packageName: String,
    val label: String,
    val isIranianOrBanking: Boolean
)

/**
 * Manages Per-App Split Tunneling (disallowed apps that bypass VPN) plus
 * Kill Switch and Network-Handoff Auto-Reconnect settings.
 * Uses Play-compliant launcher intent queries rather than QUERY_ALL_PACKAGES.
 */
object SplitTunnelManager {

    private const val PREFS_NAME = "tms_split_tunnel_prefs"
    private const val KEY_SPLIT_ENABLED = "split_enabled"
    private const val KEY_BYPASS_IRAN = "bypass_iran_apps"
    private const val KEY_AUTO_RECONNECT = "auto_reconnect"
    private const val KEY_KILL_SWITCH = "kill_switch"
    private const val KEY_BYPASSED_PACKAGES = "bypassed_packages"

    private val KNOWN_IRAN_PACKAGES = setOf(
        "ir.resaneh1.iptv",          // Rubika
        "cab.snapp.passenger",       // Snapp
        "com.digikala",              // Digikala
        "ir.divar",                  // Divar
        "ir.torob",                  // Torob
        "ir.nasim",                  // Bale
        "ir.eitaa.messenger",        // Eitaa
        "ir.igap.messenger",         // iGap
        "mobi.mmdt.ott",             // Soroush+
        "ir.medu.shad",              // Shad
        "com.farsitel.bazaar",       // Cafe Bazaar
        "ir.mci.ecareapp",           // MyMCI
        "com.myirancell",            // MyIrancell
        "com.asanpardakht.android",  // Asan Pardakht
        "com.sibche.aspardproject.app",
        "com.tejaratbank.mobilebank",
        "com.bmi.mobilebank",
        "com.mellat.mobilebank",
        "com.saderat.mobilebank",
        "com.sepah.mobilebank",
        "ir.tejaratbank.bam",
        "com.blubank.app",
        "ir.pasargadbank.mobilebank",
        "com.samanpr.mobilebank",
        "com.parsian.mobilebank",
        "com.ayandehbank.kilid"
    )

    private val IRAN_KEYWORD_HINTS = listOf(
        "bank", "mellat", "melli", "tejarat", "saderat", "sepah", "pasargad",
        "saman", "parsian", "ayandeh", "blubank", "wepod", "snapp", "digikala",
        "divar", "rubika", "resaneh1", "eitaa", "bale", "nasim", "shad", "bazaar",
        "ap.", "asanpardakht", "tapsi", "neshan", "balad", "shaparak", "hamrah"
    )

    @Volatile
    private var initialized = false

    private val _splitTunnelEnabled = MutableStateFlow(true)
    val splitTunnelEnabled: StateFlow<Boolean> = _splitTunnelEnabled.asStateFlow()

    private val _bypassIranAppsEnabled = MutableStateFlow(true)
    val bypassIranAppsEnabled: StateFlow<Boolean> = _bypassIranAppsEnabled.asStateFlow()

    private val _autoReconnectEnabled = MutableStateFlow(true)
    val autoReconnectEnabled: StateFlow<Boolean> = _autoReconnectEnabled.asStateFlow()

    private val _killSwitchEnabled = MutableStateFlow(false)
    val killSwitchEnabled: StateFlow<Boolean> = _killSwitchEnabled.asStateFlow()

    private val _bypassedPackages = MutableStateFlow<Set<String>>(emptySet())
    val bypassedPackages: StateFlow<Set<String>> = _bypassedPackages.asStateFlow()

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            _splitTunnelEnabled.value = prefs.getBoolean(KEY_SPLIT_ENABLED, true)
            _bypassIranAppsEnabled.value = prefs.getBoolean(KEY_BYPASS_IRAN, true)
            _autoReconnectEnabled.value = prefs.getBoolean(KEY_AUTO_RECONNECT, true)
            _killSwitchEnabled.value = prefs.getBoolean(KEY_KILL_SWITCH, false)
            _bypassedPackages.value = prefs.getStringSet(KEY_BYPASSED_PACKAGES, emptySet())?.toSet() ?: emptySet()
            initialized = true
        }
    }

    fun setSplitTunnelEnabled(context: Context, enabled: Boolean) {
        init(context)
        _splitTunnelEnabled.value = enabled
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SPLIT_ENABLED, enabled).apply()
    }

    fun setBypassIranAppsEnabled(context: Context, enabled: Boolean) {
        init(context)
        _bypassIranAppsEnabled.value = enabled
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_BYPASS_IRAN, enabled).apply()
    }

    fun setAutoReconnectEnabled(context: Context, enabled: Boolean) {
        init(context)
        _autoReconnectEnabled.value = enabled
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO_RECONNECT, enabled).apply()
    }

    fun setKillSwitchEnabled(context: Context, enabled: Boolean) {
        init(context)
        _killSwitchEnabled.value = enabled
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_KILL_SWITCH, enabled).apply()
    }

    fun togglePackageBypass(context: Context, packageName: String) {
        init(context)
        val updated = _bypassedPackages.value.toMutableSet()
        if (!updated.add(packageName)) {
            updated.remove(packageName)
        }
        _bypassedPackages.value = updated
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_BYPASSED_PACKAGES, updated).apply()
    }

    fun isAutoReconnectEnabled(context: Context): Boolean {
        init(context)
        return _autoReconnectEnabled.value
    }

    fun isKillSwitchEnabled(context: Context): Boolean {
        init(context)
        return _killSwitchEnabled.value
    }

    fun isIranianOrBankingPackage(packageName: String): Boolean {
        val lower = packageName.lowercase()
        if (lower in KNOWN_IRAN_PACKAGES) return true
        if (lower.startsWith("ir.")) return true
        return IRAN_KEYWORD_HINTS.any { lower.contains(it) }
    }

    fun getEffectiveDisallowedPackages(context: Context): Set<String> {
        init(context)
        val result = mutableSetOf(context.packageName)
        if (!_splitTunnelEnabled.value) return result

        result.addAll(_bypassedPackages.value)
        if (_bypassIranAppsEnabled.value) {
            result.addAll(KNOWN_IRAN_PACKAGES)
            getLaunchableApps(context)
                .filter { it.isIranianOrBanking }
                .forEach { result.add(it.packageName) }
        }
        return result
    }

    fun getLaunchableApps(context: Context): List<InstalledAppItem> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, 0)
            }
        } catch (_: Exception) {
            emptyList()
        }

        return resolveInfos
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == context.packageName) return@mapNotNull null
                val label = info.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: pkg
                InstalledAppItem(
                    packageName = pkg,
                    label = label,
                    isIranianOrBanking = isIranianOrBankingPackage(pkg)
                )
            }
            .distinctBy { it.packageName }
            .sortedWith(compareByDescending<InstalledAppItem> { it.isIranianOrBanking }.thenBy { it.label.lowercase() })
    }
}
