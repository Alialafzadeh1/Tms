package com.example.vpn

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.AdminRole
import com.example.data.security.CredentialValidationResult
import com.example.data.security.LocalUserAuthManager
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.UserProfileSettingsScreen
import com.example.ui.theme.TmsVpnTheme
import com.example.ui.viewmodel.TmsVpnViewModel
import com.example.vpn.engine.PacketBufferPool
import com.example.vpn.engine.XrayCoreEngine
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PerformanceAndOfflineAuthTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Application

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PacketBufferPool.clear()
        context.getSharedPreferences("tms_admin_security_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId("1:123456789012:android:abcdef1234567890")
                    .setApiKey("AIzaSyDummyTestApiKeyForUnitTestsOnly")
                    .setProjectId("tms-vpn-test")
                    .build()
            )
        }
        runCatching { Firebase.auth.signOut() }
    }

    @After
    fun tearDown() {
        PacketBufferPool.clear()
        runCatching { Firebase.auth.signOut() }
    }

    @Test
    fun packetBufferPoolReusesTieredBuffersAndRespectsBounds() {
        val small1 = PacketBufferPool.acquire(512)
        assertEquals(PacketBufferPool.SMALL_CAPACITY, small1.size)
        val medium1 = PacketBufferPool.acquire(8192)
        assertEquals(PacketBufferPool.MEDIUM_CAPACITY, medium1.size)
        val large1 = PacketBufferPool.acquire(24000)
        assertEquals(PacketBufferPool.LARGE_CAPACITY, large1.size)

        PacketBufferPool.release(small1)
        PacketBufferPool.release(medium1)
        PacketBufferPool.release(large1)

        var stats = PacketBufferPool.stats()
        assertEquals(1, stats.smallPooled)
        assertEquals(1, stats.mediumPooled)
        assertEquals(1, stats.largePooled)
        assertEquals(0L, stats.hits)

        PacketBufferPool.useBuffer(PacketBufferPool.SMALL_CAPACITY) { buf ->
            assertEquals(PacketBufferPool.SMALL_CAPACITY, buf.size)
        }
        PacketBufferPool.useBuffer(PacketBufferPool.MEDIUM_CAPACITY) { buf ->
            assertEquals(PacketBufferPool.MEDIUM_CAPACITY, buf.size)
        }
        PacketBufferPool.useBuffer(PacketBufferPool.LARGE_CAPACITY) { buf ->
            assertEquals(PacketBufferPool.LARGE_CAPACITY, buf.size)
        }

        stats = PacketBufferPool.stats()
        assertEquals(3L, stats.hits)
        assertEquals(1, stats.smallPooled)
        assertEquals(1, stats.mediumPooled)
        assertEquals(1, stats.largePooled)

        val huge = PacketBufferPool.acquire(65536)
        assertEquals(65536, huge.size)
        PacketBufferPool.release(huge)
        stats = PacketBufferPool.stats()
        assertEquals(1, stats.largePooled)
    }

    @Test
    fun xrayEnginePreloadNativeAssetsPreparesAssetDirectoryAsync() = runBlocking {
        val assetsDir = File(context.filesDir, "xray_assets")
        if (assetsDir.exists()) {
            assetsDir.deleteRecursively()
        }

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val deferred = XrayCoreEngine.preloadNativeAssetsAsync(context, scope)
            XrayCoreEngine.preloadNativeAssetsAsync(context, scope)
            deferred.await()

            assertTrue(XrayCoreEngine.isAssetsPrepared())
            assertTrue(assetsDir.exists())
            assertTrue(assetsDir.isDirectory)
            assertFalse(XrayCoreEngine.ensureNativeRuntimeInitialized(context))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun firebaseCredentialValidationAndClaimExtractionEnforceZeroTrust() {
        val badEmail = LocalUserAuthManager.validateCredentialsInput("not-an-email", "secret123")
        assertTrue(badEmail is CredentialValidationResult.Invalid)

        val shortPw = LocalUserAuthManager.validateCredentialsInput("iran.user@example.com", "123")
        assertTrue(shortPw is CredentialValidationResult.Invalid)

        val valid = LocalUserAuthManager.validateCredentialsInput("Iran.User@Example.com", "S3cr3tPass!", "Ali", isSignUp = true)
        assertTrue(valid is CredentialValidationResult.Valid)
        assertEquals("iran.user@example.com", (valid as CredentialValidationResult.Valid).email)

        // Legacy UID calculation is deterministic for Room migration only
        val legacyUid1 = LocalUserAuthManager.computeLegacyIntranetUid("Iran.User@Example.com")
        val legacyUid2 = LocalUserAuthManager.computeLegacyIntranetUid("iran.user@example.com")
        assertTrue(legacyUid1.startsWith("intranet_"))
        assertEquals(legacyUid1, legacyUid2)

        // Unprivileged claim values cannot elevate to admin
        assertNull(AdminRole.fromVerifiedClaimOrNull("user"))
        assertNull(AdminRole.fromVerifiedClaimOrNull("unknown_role"))
        assertEquals(AdminRole.AUDITOR, AdminRole.fromString("unknown_role"))
    }

    @Test
    fun authScreenShowsFirebaseEmailPasswordAndForgotPasswordWithoutGoogleBypass() {
        composeRule.setContent {
            TmsVpnTheme {
                AuthScreen(
                    onAuthSuccess = {}
                )
            }
        }

        composeRule.waitForIdle()
        assertEquals(0, composeRule.onAllNodesWithTag("auth_google_sign_in_button").fetchSemanticsNodes().size)
        composeRule.onNodeWithTag("auth_mode_login_tab").assertIsDisplayed()
        composeRule.onNodeWithTag("auth_mode_register_tab").assertIsDisplayed()
        composeRule.onNodeWithTag("auth_forgot_password_button").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("auth_email_input").performScrollTo().performTextInput("bad-email")
        composeRule.onNodeWithTag("auth_password_input").performScrollTo().performTextInput("Pass@2026")
        composeRule.onNodeWithTag("auth_email_submit_button").performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 3_000L) {
            composeRule.onAllNodesWithTag("auth_error_text").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("auth_error_text").assertIsDisplayed()
    }

    @Test
    fun nonVerifiedSessionCannotEnterAdminSpaceOrBypassWithPin() = runBlocking {
        val viewModel = TmsVpnViewModel(context)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertFalse(viewModel.isCurrentUserAdmin())
        assertFalse(viewModel.isVerifiedAdmin.value)
        assertFalse(viewModel.isInAdminSpace.value)
        assertFalse(viewModel.isUnlocked.value)
        assertFalse(viewModel.enterAdminSpace())
        assertFalse(viewModel.isInAdminSpace.value)
    }

    @Test
    fun userProfileSettingsScreenHasNoAdminPortalButtonWhenNotVerifiedAdmin() {
        composeRule.setContent {
            TmsVpnTheme {
                UserProfileSettingsScreen(
                    currentUserId = "regular-uid",
                    userProfile = null,
                    isPersian = true,
                    isVerifiedAdmin = false,
                    onToggleLanguage = {},
                    onOpenRadar = {},
                    onSignOut = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("profile_sign_out_button").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithTag("profile_admin_portal_button").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("ورود به کنسول مدیریت", substring = true).fetchSemanticsNodes().size)
    }
}
