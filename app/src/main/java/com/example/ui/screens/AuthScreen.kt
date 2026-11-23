package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.LocalVpnConfigRepository
import com.example.data.local.RegisteredUserEntity
import com.example.data.security.LocalUserAuthManager
import com.example.ui.components.CyberAmbientBackdrop
import com.example.ui.components.CyberGlassCard
import com.example.ui.components.TmsLogoEmblem
import com.example.ui.theme.DeepGraphite
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.GlassBorderCyan
import com.example.ui.theme.GlassBorderSubtle
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.PremiumTeal
import com.example.ui.theme.SecondarySurface
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.StatusConnected
import com.example.ui.theme.StatusError
import com.example.ui.theme.TextCyberPrimary
import com.example.ui.theme.TextCyberSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val localAuthManager = remember(context) { LocalUserAuthManager(context) }

    var isSignUpMode by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var displayNameInput by remember { mutableStateOf("") }
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (LocalUserAuthManager.restoreOfflineSession(context) != null) {
            onAuthSuccess()
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        CyberAmbientBackdrop(accentColor = ElectricCyan) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                CyberGlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 460.dp),
                    cornerRadius = 24.dp,
                    accentColor = ElectricCyan.copy(alpha = 0.55f),
                    elevated = true,
                    contentPadding = 24.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        TmsLogoEmblem(accentColor = ElectricCyan, size = 68.dp)

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "TMS VPN",
                            fontFamily = SpaceGroteskFontFamily,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextCyberPrimary,
                            letterSpacing = 1.6.sp
                        )

                        Text(
                            text = "TMS NEXUS AI • QUANTUM TUNNEL",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElectricCyan,
                            letterSpacing = 1.6.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = if (isSignUpMode) "ایجاد حساب کاربری TMS VPN" else "ورود با ایمیل و رمز عبور",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextCyberPrimary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "احراز هویت امن، یکپارچه و پرسرعت بدون نیاز به حساب گوگل — تمامی اشتراک‌ها و نودهای اختصاصی آلمان/ترکیه بلافاصله فعال می‌شوند.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextCyberSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 19.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Segmented Mode Toggle: Login vs Sign Up
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(SecondarySurface)
                                .border(BorderStroke(1.dp, GlassBorderSubtle), RoundedCornerShape(14.dp))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(false to "ورود به حساب", true to "ثبت‌نام جدید").forEach { (signUpFlag, label) ->
                                val selected = isSignUpMode == signUpFlag
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (selected) ElectricCyan.copy(alpha = 0.22f)
                                            else androidx.compose.ui.graphics.Color.Transparent
                                        )
                                        .border(
                                            width = if (selected) 1.dp else 0.dp,
                                            color = if (selected) ElectricCyan else androidx.compose.ui.graphics.Color.Transparent,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            isSignUpMode = signUpFlag
                                            errorMessage = null
                                        }
                                        .padding(vertical = 10.dp)
                                        .testTag(if (signUpFlag) "auth_mode_register_tab" else "auth_mode_login_tab"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (selected) ElectricCyan else TextCyberSecondary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        AnimatedVisibility(visible = isSignUpMode) {
                            OutlinedTextField(
                                value = displayNameInput,
                                onValueChange = { displayNameInput = it },
                                label = { Text("نام و نام خانوادگی (نمایشی)") },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Filled.Person, contentDescription = null, tint = PremiumTeal)
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 10.dp)
                                    .testTag("auth_name_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ElectricCyan,
                                    unfocusedBorderColor = GlassBorderSubtle,
                                    focusedTextColor = TextCyberPrimary,
                                    unfocusedTextColor = TextCyberPrimary
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        OutlinedTextField(
                            value = emailInput,
                            onValueChange = { emailInput = it },
                            label = { Text("نشانی ایمیل (Email)") },
                            leadingIcon = {
                                Icon(imageVector = Icons.Filled.Email, contentDescription = null, tint = ElectricCyan)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_email_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricCyan,
                                unfocusedBorderColor = GlassBorderSubtle,
                                focusedTextColor = TextCyberPrimary,
                                unfocusedTextColor = TextCyberPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text("رمز عبور (حداقل ۶ کاراکتر)") },
                            leadingIcon = {
                                Icon(imageVector = Icons.Filled.Lock, contentDescription = null, tint = ElectricCyan)
                            },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = "نمایش یا مخفی‌سازی رمز",
                                        tint = TextCyberSecondary
                                    )
                                }
                            },
                            singleLine = true,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_password_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricCyan,
                                unfocusedBorderColor = GlassBorderSubtle,
                                focusedTextColor = TextCyberPrimary,
                                unfocusedTextColor = TextCyberPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        if (errorMessage != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = errorMessage.orEmpty(),
                                color = StatusError,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.testTag("auth_error_text")
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                errorMessage = null
                                isSubmitting = true
                                val res = localAuthManager.signInOrRegisterOfflineEmail(
                                    rawEmail = emailInput,
                                    rawPassword = passwordInput,
                                    rawDisplayName = displayNameInput,
                                    requireExplicitSignUp = isSignUpMode
                                )
                                val signedUser = res.user
                                if (res.isSuccess && signedUser != null) {
                                    coroutineScope.launch {
                                        val now = System.currentTimeMillis()
                                        withContext(Dispatchers.IO) {
                                            runCatching {
                                                LocalVpnConfigRepository(context.applicationContext)
                                                    .upsertRegisteredUser(
                                                        RegisteredUserEntity(
                                                            userId = signedUser.uid,
                                                            displayName = signedUser.username,
                                                            email = signedUser.email,
                                                            role = signedUser.role,
                                                            maxDevices = 10,
                                                            dataLimitBytes = 500.0 * 1024 * 1024 * 1024,
                                                            subscriptionStatus = if (signedUser.isAdmin) "active" else "expired",
                                                            createdAtMs = now,
                                                            updatedAtMs = now
                                                        )
                                                    )
                                            }
                                        }
                                        isSubmitting = false
                                        onAuthSuccess()
                                    }
                                } else {
                                    isSubmitting = false
                                    errorMessage = res.errorMessage
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("auth_email_submit_button"),
                            shape = RoundedCornerShape(14.dp),
                            enabled = !isSubmitting,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricCyan,
                                contentColor = DeepGraphite
                            )
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = DeepGraphite,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = if (isSignUpMode) "ثبت‌نام و ورود به برنامه" else "ورود با ایمیل و رمز عبور",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(ElevatedSurface.copy(alpha = 0.75f))
                                .border(BorderStroke(1.dp, GlassBorderCyan.copy(alpha = 0.4f)), RoundedCornerShape(14.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.VerifiedUser,
                                    contentDescription = null,
                                    tint = StatusConnected,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "اتصال مستقیم ایمیل و رمز عبور با رمزنگاری PBKDF2-SHA256",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextCyberPrimary
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Shield,
                                    contentDescription = null,
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "پشتیبانی کامل از مخزن IP تمیز (۳۳۰+ IP)، نودهای آلمان/ترکیه و اشتراک‌ها",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextCyberSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

