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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.ui.TmsVpnApp
import com.example.ui.screens.AuthScreen
import com.example.ui.theme.DeepBlackBackground
import com.example.ui.theme.TmsVpnTheme
import com.example.util.configureAppCheck
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

import com.example.data.security.LocalUserAuthManager

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
    val authManager = remember { LocalUserAuthManager(context) }
    var currentSessionUser by remember { mutableStateOf(authManager.getActiveSessionUser()) }
    var pendingVpnAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingVpnAction?.invoke()
            pendingVpnAction = null
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener {
            val activeUser = authManager.getActiveSessionUser()
            if (activeUser != null) {
                currentSessionUser = activeUser
            }
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    if (currentSessionUser == null) {
        AuthScreen(
            onAuthSuccess = {
                currentSessionUser = authManager.getActiveSessionUser()
            }
        )
    } else {
        val uid = currentSessionUser!!.uid
        key(uid) {
            TmsVpnApp(
                currentUserId = uid,
                onSignOut = {
                    authManager.logout()
                    currentSessionUser = null
                },
                onRequestVpnPermission = { onGranted ->
                    val intent = VpnService.prepare(context)
                    if (intent != null) {
                        pendingVpnAction = onGranted
                        vpnPermissionLauncher.launch(intent)
                    } else {
                        onGranted()
                    }
                }
            )
        }
    }
}
