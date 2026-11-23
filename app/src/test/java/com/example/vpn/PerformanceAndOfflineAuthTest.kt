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
import com.example.data.security.LocalUserAuthManager
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.UserProfileSettingsScreen
import com.example.ui.theme.TmsVpnTheme
import com.example.ui.viewmodel.TmsVpnViewModel
import com.example.vpn.engine.PacketBufferPool
import com.example.vpn.engine.XrayCoreEngine
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
import java.util.concurrent.atomic.AtomicBoolean

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
        LocalUserAuthManager.clearOfflineSession(context)
        context.getSharedPreferences("tms_local_intranet_auth", Context.MODE_PRIVATE)
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
    }

    @After
    fun tearDown() {
        PacketBufferPool.clear()
        LocalUserAuthManager.clearOfflineSession(context)
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

        // Re-acquiring pooled tiers should record hits and drain pools
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

        // Oversized allocations bypass the pool cleanly
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
            // Idempotent concurrent call reuses existing preparation cleanly
            XrayCoreEngine.preloadNativeAssetsAsync(context, scope)
            deferred.await()

            assertTrue(XrayCoreEngine.isAssetsPrepared())
            assertTrue(assetsDir.exists())
            assertTrue(assetsDir.isDirectory)
            // Idempotent safety check for centralized native runtime guard
            assertFalse(XrayCoreEngine.ensureNativeRuntimeInitialized(context))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun offlineIntranetEmailAuthValidatesRegistersHashesAndRestoresSession() {
        val manager = LocalUserAuthManager(context)

        // Reject invalid email and short password
        val badEmail = manager.signInOrRegisterOfflineEmail("not-an-email", "secret123")
        assertFalse(badEmail.isSuccess)
        assertNotNull(badEmail.errorMessage)

        val shortPw = manager.signInOrRegisterOfflineEmail("iran.user@example.com", "123")
        assertFalse(shortPw.isSuccess)
        assertNotNull(shortPw.errorMessage)

        // Register first-time offline user
        val registerRes = manager.signInOrRegisterOfflineEmail("Iran.User@Example.com", "S3cr3tPass!")
        assertTrue(registerRes.isSuccess)
        assertTrue(registerRes.isNewAccount)
        val user = registerRes.user
        assertNotNull(user)
        assertTrue(user!!.uid.startsWith("intranet_"))
        assertFalse(user.isOfflineIntranetMode)
        assertEquals("iran.user@example.com", user.email)
        assertTrue(
            LocalUserAuthManager.getAllRegisteredPrefProfiles(context)
                .any { it.email == "iran.user@example.com" }
        )

        // Plaintext password must never exist in SharedPreferences
        val rawPrefs = context.getSharedPreferences("tms_local_intranet_auth", Context.MODE_PRIVATE).all
        val allValues = rawPrefs.values.joinToString("|")
        assertFalse(allValues.contains("S3cr3tPass!"))

        // Wrong password rejected for existing account
        val wrongPw = manager.signInOrRegisterOfflineEmail("iran.user@example.com", "WrongPass99")
        assertFalse(wrongPw.isSuccess)
        assertTrue(wrongPw.errorMessage?.contains("اشتباه") == true)

        // Re-login with valid password succeeds as returning account
        val reloginRes = manager.signInOrRegisterOfflineEmail("iran.user@example.com", "S3cr3tPass!")
        assertTrue(reloginRes.isSuccess)
        assertFalse(reloginRes.isNewAccount)
        assertEquals(user.uid, reloginRes.user?.uid)

        // Restoring session recovers user; clearing removes it
        val restored = LocalUserAuthManager.restoreOfflineSession(context)
        assertNotNull(restored)
        assertEquals(user.uid, restored?.uid)

        LocalUserAuthManager.clearOfflineSession(context)
        assertNull(LocalUserAuthManager.offlineUserFlow.value)
        assertNull(LocalUserAuthManager.restoreOfflineSession(context))
    }

    @Test
    fun authScreenAllowsNationalIntranetEmailAndPasswordSignIn() {
        val authSucceeded = AtomicBoolean(false)

        composeRule.setContent {
            TmsVpnTheme {
                AuthScreen(
                    onAuthSuccess = { authSucceeded.set(true) }
                )
            }
        }

        composeRule.waitForIdle()
        assertEquals(0, composeRule.onAllNodesWithTag("auth_google_sign_in_button").fetchSemanticsNodes().size)
        composeRule.onNodeWithTag("auth_mode_login_tab").assertIsDisplayed()
        composeRule.onNodeWithTag("auth_mode_register_tab").assertIsDisplayed()

        // Switch to Registration tab and sign up with Display Name, Email & Password
        composeRule.onNodeWithTag("auth_mode_register_tab").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("auth_name_input").performScrollTo().performTextInput("Ali Rezaei")
        composeRule.onNodeWithTag("auth_email_input").performScrollTo().performTextInput("ali.intranet@tms.ir")
        composeRule.onNodeWithTag("auth_password_input").performScrollTo().performTextInput("Pass@2026")
        composeRule.onNodeWithTag("auth_email_submit_button").performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 5000) { authSucceeded.get() }
        assertTrue(authSucceeded.get())
        val activeUser = LocalUserAuthManager.offlineUserFlow.value
        assertNotNull(activeUser)
        assertEquals("Ali Rezaei", activeUser?.username)
        assertEquals("ali.intranet@tms.ir", activeUser?.email)
        assertFalse(activeUser?.isOfflineIntranetMode == true)
        assertFalse(activeUser?.isAdmin == true)
        assertEquals("USER", activeUser?.role)
    }

    @Test
    fun adminEmailRequiresExactPasswordAndAutoOpensFullAdminPanel() = runBlocking {
        val manager = LocalUserAuthManager(context)

        // Wrong password for reserved admin email must always be rejected
        val wrongPw = manager.signInOrRegisterOfflineEmail("alafzadehali@gmail.com", "wrong1234")
        assertFalse(wrongPw.isSuccess)
        assertNull(LocalUserAuthManager.offlineUserFlow.value)

        // Non-admin user stays strictly in User Space and cannot unlock Admin even with old PIN
        val regularRes = manager.signInOrRegisterOfflineEmail("regular.user@example.com", "Pass1234!")
        assertTrue(regularRes.isSuccess)
        assertFalse(regularRes.user?.isAdmin == true)

        val viewModel = TmsVpnViewModel(context)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertFalse(viewModel.isInAdminSpace.value)
        assertFalse(viewModel.enterAdminSpace("14361436"))
        assertFalse(viewModel.isInAdminSpace.value)

        // Exact admin email + password grants SUPER_ADMIN and opens full Admin Control Center
        LocalUserAuthManager.clearOfflineSession(context)
        val adminRes = manager.signInOrRegisterOfflineEmail("alafzadehali@gmail.com", "@ALI1384alii")
        assertTrue(adminRes.isSuccess)
        assertTrue(adminRes.user?.isAdmin == true)
        assertEquals("SUPER_ADMIN", adminRes.user?.role)

        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue(viewModel.isCurrentUserAdmin())
        assertTrue(viewModel.isInAdminSpace.value)
        assertTrue(viewModel.isUnlocked.value)

        // A freshly created ViewModel for this admin session opens Admin Control Center immediately on frame 0
        val freshAdminViewModel = TmsVpnViewModel(context)
        assertTrue(freshAdminViewModel.isInAdminSpace.value)
        assertTrue(freshAdminViewModel.isUnlocked.value)
    }

    @Test
    fun userProfileSettingsScreenHasNoAdminPortalButton() {
        composeRule.setContent {
            TmsVpnTheme {
                UserProfileSettingsScreen(
                    currentUserId = "regular-uid",
                    userProfile = null,
                    isPersian = true,
                    onToggleLanguage = {},
                    onOpenRadar = {},
                    onSignOut = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("profile_sign_out_button").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithTag("profile_admin_portal_button").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("ورود به پنل کنترل مدیریت", substring = true).fetchSemanticsNodes().size)
    }
}
