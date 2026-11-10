package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.data.security.AuthResult
import com.example.data.security.LocalUserAuthManager
import com.example.ui.theme.BorderGoldHighlight
import com.example.ui.theme.BorderGoldMuted
import com.example.ui.theme.CardSurfaceDark
import com.example.ui.theme.DeepBlackBackground
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.MetallicGold
import com.example.ui.theme.StatusOfflineRed
import com.example.ui.theme.TextGraySecondary
import com.example.ui.theme.TextWhitePrimary
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val TAG = "AuthScreen"

enum class AuthMode {
    LOGIN,
    SIGN_UP
}

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val authManager = remember { LocalUserAuthManager(context) }
    val credentialManager = remember { CredentialManager.create(context) }

    var authMode by remember { mutableStateOf(AuthMode.LOGIN) }
    var usernameInput by remember { mutableStateOf("") }
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    fun submitAuth() {
        errorMessage = null
        successMessage = null
        isLoading = true

        coroutineScope.launch {
            val result = if (authMode == AuthMode.LOGIN) {
                authManager.login(emailInput, passwordInput)
            } else {
                authManager.register(usernameInput, emailInput, passwordInput)
            }

            isLoading = false
            when (result) {
                is AuthResult.Success -> {
                    successMessage = if (authMode == AuthMode.LOGIN) "ورود با موفقیت انجام شد" else "حساب کاربری با موفقیت ساخته شد"
                    onAuthSuccess()
                }
                is AuthResult.Error -> {
                    errorMessage = result.message
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        DeepBlackBackground,
                        Color(0xFF0F1219),
                        DeepBlackBackground
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .border(BorderStroke(1.2.dp, BorderGoldMuted), RoundedCornerShape(22.dp)),
            colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(26.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // TMS Emblem
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E1A11))
                        .border(BorderStroke(2.dp, MetallicGold), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "TMS VPN Shield",
                        tint = MetallicGold,
                        modifier = Modifier.size(40.dp)
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .size(12.dp)
                            .background(EmeraldGreen, CircleShape)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "TMS VPN",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = MetallicGold,
                    letterSpacing = 2.sp
                )

                Text(
                    text = if (authMode == AuthMode.LOGIN) "ورود به حساب کاربری مدیریت شبکه" else "ثبت نام حساب کاربری جدید",
                    fontSize = 13.sp,
                    color = TextWhitePrimary,
                    modifier = Modifier.padding(top = 4.dp),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Mode Tabs: Login vs Sign Up
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0C0E14), RoundedCornerShape(12.dp))
                        .border(BorderStroke(0.8.dp, BorderGoldMuted), RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (authMode == AuthMode.LOGIN) MetallicGold else Color.Transparent)
                            .clickable {
                                authMode = AuthMode.LOGIN
                                errorMessage = null
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "ورود (Login)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (authMode == AuthMode.LOGIN) Color(0xFF101218) else TextGraySecondary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (authMode == AuthMode.SIGN_UP) MetallicGold else Color.Transparent)
                            .clickable {
                                authMode = AuthMode.SIGN_UP
                                errorMessage = null
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "ثبت نام (Sign Up)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (authMode == AuthMode.SIGN_UP) Color(0xFF101218) else TextGraySecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Username field (only on Sign Up)
                if (authMode == AuthMode.SIGN_UP) {
                    OutlinedTextField(
                        value = usernameInput,
                        onValueChange = { usernameInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("نام کاربری (Username)") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = MetallicGold)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MetallicGold,
                            unfocusedBorderColor = BorderGoldMuted,
                            focusedTextColor = TextWhitePrimary,
                            unfocusedTextColor = TextWhitePrimary,
                            focusedLabelColor = MetallicGold,
                            unfocusedLabelColor = TextGraySecondary
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Email field
                OutlinedTextField(
                    value = emailInput,
                    onValueChange = { emailInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("آدرس ایمیل (Email)") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Email, contentDescription = null, tint = MetallicGold)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MetallicGold,
                        unfocusedBorderColor = BorderGoldMuted,
                        focusedTextColor = TextWhitePrimary,
                        unfocusedTextColor = TextWhitePrimary,
                        focusedLabelColor = MetallicGold,
                        unfocusedLabelColor = TextGraySecondary
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Password field
                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("رمز عبور (Password)") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = MetallicGold)
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                tint = TextGraySecondary
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MetallicGold,
                        unfocusedBorderColor = BorderGoldMuted,
                        focusedTextColor = TextWhitePrimary,
                        unfocusedTextColor = TextWhitePrimary,
                        focusedLabelColor = MetallicGold,
                        unfocusedLabelColor = TextGraySecondary
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { submitAuth() }),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Error / Success messaging
                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = StatusOfflineRed,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                if (successMessage != null) {
                    Text(
                        text = successMessage ?: "",
                        color = EmeraldGreen,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                // Primary Submit Button
                Button(
                    onClick = { submitAuth() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MetallicGold,
                        contentColor = Color(0xFF1E1700)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color(0xFF1E1700),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = if (authMode == AuthMode.LOGIN) "ورود به سیستم / Sign In" else "ایجاد حساب و ورود / Create Account",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Alternative: Sign in with Google
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f).height(0.6.dp).background(BorderGoldMuted))
                    Text(
                        text = "یا",
                        fontSize = 11.sp,
                        color = TextGraySecondary,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Box(modifier = Modifier.weight(1f).height(0.6.dp).background(BorderGoldMuted))
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        isLoading = true
                        errorMessage = null
                        onGoogleSignInClicked(
                            context = context,
                            credentialManager = credentialManager,
                            onAuthSuccess = {
                                isLoading = false
                                onAuthSuccess()
                            },
                            onAuthError = { err ->
                                isLoading = false
                                errorMessage = err
                            },
                            scope = coroutineScope,
                            onAuthCancelled = {
                                isLoading = false
                            }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF161A22),
                        contentColor = TextWhitePrimary
                    ),
                    border = BorderStroke(1.dp, BorderGoldMuted),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isLoading
                ) {
                    Text(
                        text = "ورود با حساب گوگل (Google Sign-In)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

private fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit
) {
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (e: Exception) {
        onAuthError("Google Sign-In configuration missing: default_web_client_id not found")
        return
    }

    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

    scope.launch {
        try {
            val result = credentialManager.getCredential(context as Activity, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                Firebase.auth.signInWithCredential(authCredential).await()
                onAuthSuccess()
            } else {
                onAuthError("نوع اعتبار دریافتی نامعتبر است")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google Sign-In flow cancelled: ${e.message}")
            onAuthCancelled()
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed", e)
            onAuthError(e.localizedMessage ?: "خطا در ورود به حساب کاربری")
        }
    }
}
