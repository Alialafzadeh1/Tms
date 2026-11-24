package com.example

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.AdminRole
import com.example.data.security.CredentialValidationResult
import com.example.data.security.LocalUserAuthManager
import com.example.vpn.CleanIpRefreshScheduler
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.auth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp-xxhdpi")
class MainActivityStartupTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun resetSession() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        if (FirebaseApp.getApps(ctx).isEmpty()) {
            FirebaseApp.initializeApp(
                ctx,
                FirebaseOptions.Builder()
                    .setApplicationId("1:123456789012:android:abcdef1234567890")
                    .setApiKey("AIzaSyDummyTestApiKeyForUnitTestsOnly")
                    .setProjectId("tms-vpn-test")
                    .build()
            )
        }
        runCatching { Firebase.auth.signOut() }
    }

    @Test
    fun coldLaunchShowsFirebaseAuthScreenAndAllowsTabSwitch() {
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithTag("auth_email_input").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("auth_email_input").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("auth_password_input").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("auth_forgot_password_button").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("auth_mode_register_tab").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("auth_name_input").performScrollTo().assertIsDisplayed()

        val ctx = ApplicationProvider.getApplicationContext<Application>()
        CleanIpRefreshScheduler.schedulePeriodicRefresh(ctx)
    }

    @Test
    fun claimExtractionNeverGrantsAdminFromEmailAlone() {
        val validation = LocalUserAuthManager.validateCredentialsInput(
            rawEmail = "  User@Example.com ",
            rawPassword = "StrongPassword#1",
            rawDisplayName = "Ali",
            isSignUp = true
        )
        assertTrue(validation is CredentialValidationResult.Valid)
        val valid = validation as CredentialValidationResult.Valid
        assertEquals("user@example.com", valid.email)

        // Regular Firebase token claims -> non-admin
        val regularSession = LocalUserAuthManager.extractVerifiedClaims(
            claims = mapOf("role" to "user"),
            uid = "firebase_uid_1",
            email = "user@example.com",
            displayName = "Ali",
            emailVerified = true,
            token = "jwt_tok"
        )
        assertFalse(regularSession.isServerAdmin)
        assertNull(regularSession.verifiedAdminRole)

        // Server custom claim -> verified SUPER_ADMIN
        val adminSession = LocalUserAuthManager.extractVerifiedClaims(
            claims = mapOf("admin" to true, "role" to "SUPER_ADMIN"),
            uid = "firebase_admin_uid",
            email = "ops@example.com",
            displayName = "Admin",
            emailVerified = true,
            token = "jwt_admin_tok"
        )
        assertTrue(adminSession.isServerAdmin)
        assertEquals(AdminRole.SUPER_ADMIN, adminSession.verifiedAdminRole)
    }
}
