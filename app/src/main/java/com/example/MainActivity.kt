package com.example

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.data.security.LocalUserAuthManager
import com.example.ui.TmsVpnApp
import com.example.ui.screens.AuthScreen
import com.example.ui.theme.DeepBlackBackground
import com.example.ui.theme.TmsVpnTheme
import com.example.util.configureAppCheck
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val pendingActivationLinkState = mutableStateOf<String?>(null)
    private val pendingReferralCodeState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mandatory App Check configuration with launch intent before UI or Firebase operations
        configureAppCheck(this, intent)
        captureDeepLinkIntent(intent)

        enableEdgeToEdge()

        setContent {
            val pendingActivationLink by pendingActivationLinkState
            val pendingReferralCode by pendingReferralCodeState
            TmsVpnTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DeepBlackBackground
                ) {
                    AppContent(
                        pendingActivationLink = pendingActivationLink,
                        onActivationLinkConsumed = { pendingActivationLinkState.value = null },
                        pendingReferralCode = pendingReferralCode,
                        onReferralCodeConsumed = { pendingReferralCodeState.value = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureDeepLinkIntent(intent)
    }

    private fun captureDeepLinkIntent(intent: Intent?) {
        val uri = intent?.data
        val data = intent?.dataString?.trim()
        if (!data.isNullOrBlank() && data.startsWith("tmsvpn://activate", ignoreCase = true)) {
            pendingActivationLinkState.value = data
        }
        val refCode = uri?.getQueryParameter("ref")?.trim()
            ?.takeIf { it.isNotBlank() }
        if (!refCode.isNullOrBlank()) {
            pendingReferralCodeState.value = refCode
        }
    }
}

private fun safeCurrentFirebaseUser(): FirebaseUser? =
    runCatching { Firebase.auth.currentUser }.getOrNull()

@Composable
fun AppContent(
    pendingActivationLink: String? = null,
    onActivationLinkConsumed: () -> Unit = {},
    pendingReferralCode: String? = null,
    onReferralCodeConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val authManager = remember(context) { LocalUserAuthManager(context) }
    var currentUser by remember { mutableStateOf(safeCurrentFirebaseUser()) }
    var pendingVpnAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingVpnAction?.invoke()
        } else {
            com.example.vpn.VpnController.updateState(
                com.example.data.model.VpnConnectionState.ERROR,
                "مجوز ایجاد تونل VPN تایید نشد / VPN permission denied"
            )
        }
        pendingVpnAction = null
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        runCatching { com.example.vpn.VpnController.syncWithService(context) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNotificationPerm = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasNotificationPerm) {
                runCatching { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
            }
        }
        withContext(Dispatchers.IO) {
            runCatching { com.example.vpn.CleanIpRefreshScheduler.schedulePeriodicRefresh(context) }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                runCatching { com.example.vpn.VpnController.syncWithService(context) }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(Unit) {
        val authInstance = runCatching { Firebase.auth }.getOrNull()
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = runCatching { auth.currentUser }.getOrNull()
        }
        runCatching { authInstance?.addAuthStateListener(listener) }
        onDispose {
            runCatching { authInstance?.removeAuthStateListener(listener) }
        }
    }

    val activeUid = currentUser?.uid
    if (activeUid == null) {
        AuthScreen(
            onAuthSuccess = {
                currentUser = safeCurrentFirebaseUser()
            }
        )
    } else {
        key(activeUid) {
            TmsVpnApp(
                currentUserId = activeUid,
                pendingActivationLink = pendingActivationLink,
                onActivationLinkConsumed = onActivationLinkConsumed,
                pendingReferralCode = pendingReferralCode,
                onReferralCodeConsumed = onReferralCodeConsumed,
                onSignOut = {
                    coroutineScope.launch {
                        withContext(NonCancellable) {
                            runCatching { authManager.logout() }
                            currentUser = null
                        }
                    }
                },
                onRequestVpnPermission = { onGranted ->
                    try {
                        val intent = VpnService.prepare(context)
                        if (intent != null) {
                            pendingVpnAction = onGranted
                            vpnPermissionLauncher.launch(intent)
                        } else {
                            onGranted()
                        }
                    } catch (e: Exception) {
                        pendingVpnAction = null
                        com.example.vpn.VpnController.updateState(
                            com.example.data.model.VpnConnectionState.ERROR,
                            "خطا در درخواست مجوز VPN: ${e.localizedMessage ?: "امکان آماده‌سازی سرویس وجود ندارد"}"
                        )
                    }
                }
            )
        }
    }
}
