package com.example

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
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
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.example.data.security.LocalUserAuthManager
import com.example.ui.TmsVpnApp
import com.example.ui.screens.AuthScreen
import com.example.ui.theme.DeepBlackBackground
import com.example.ui.theme.TmsVpnTheme
import com.example.util.configureAppCheck
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mandatory App Check configuration with launch intent before UI or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            TmsVpnTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DeepBlackBackground
                ) {
                    AppContent()
                }
            }
        }
    }
}

@Composable
fun AppContent() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    val offlineUser by LocalUserAuthManager.offlineUserFlow.collectAsStateWithLifecycle()
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
        LocalUserAuthManager.restoreOfflineSession(context)
        com.example.vpn.VpnController.syncWithService(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                com.example.vpn.VpnController.syncWithService(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    val activeUid = currentUser?.uid ?: offlineUser?.uid
    if (activeUid == null) {
        AuthScreen(
            onAuthSuccess = {
                currentUser = Firebase.auth.currentUser
            }
        )
    } else {
        key(activeUid) {
            TmsVpnApp(
                currentUserId = activeUid,
                onSignOut = {
                    coroutineScope.launch {
                        withContext(NonCancellable) {
                            LocalUserAuthManager.clearOfflineSession(context)
                            try {
                                credentialManager.clearCredentialState(ClearCredentialStateRequest())
                            } catch (_: Exception) {
                            } finally {
                                Firebase.auth.signOut()
                                currentUser = null
                            }
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
