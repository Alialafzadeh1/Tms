package com.example.security

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.AppReleaseInfo
import com.example.data.model.BackendErrorCategory
import com.example.data.model.BackendResult
import com.example.data.model.BackgroundBackendError
import com.example.data.model.classifyBackgroundError
import com.example.data.network.ApkDownloadUrlValidator
import com.example.data.network.TmsBackendApiClient
import com.example.ui.TmsVpnApp
import com.example.ui.screens.UserProfileSettingsScreen
import com.example.ui.screens.UserSubscriptionScreen
import com.example.ui.theme.TmsVpnTheme
import com.example.ui.viewmodel.TmsVpnViewModel
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.auth
import com.sun.net.httpserver.HttpServer
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
import java.io.ByteArrayInputStream
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp-xxhdpi")
class ClientSecurityAndReliabilityPatchTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Application

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
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
        context.getSharedPreferences("tms_admin_security_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        runCatching { Firebase.auth.signOut() }
    }

    // --- TASK 1: ADMIN PIN SECURITY ---

    @Test
    fun adminPinConfiguredNeverBypassesOnBlankPinAndRevalidatesSensitiveActions() = runBlocking {
        val server = startBackendServer(
            sessionStatus = 200,
            sessionAdmin = true
        )
        try {
            val viewModel = TmsVpnViewModel(context)
            viewModel.testAuthenticatedUid = "verified_admin_uid"
            viewModel.setBackendClientForTesting(
                TmsBackendApiClient(
                    baseUrl = "http://127.0.0.1:${server.address.port}",
                    tokenProvider = { "valid_admin_token" }
                )
            )
            viewModel.syncBackendEcosystemOnceForTest()
            assertTrue(viewModel.isCurrentUserAdmin())

            // Configure PIN for the verified admin
            viewModel.configureInitialAdminPin("748291")
            assertTrue(viewModel.hasConfiguredAdminPin())
            assertTrue(viewModel.adminPinConfigured.value)
            assertTrue(viewModel.isUnlocked.value)
            assertNotNull(viewModel.activeSession.value)

            // Exit/lock admin session
            viewModel.exitAdminSpace()
            assertFalse(viewModel.isInAdminSpace.value)
            assertFalse(viewModel.isUnlocked.value)
            assertFalse(viewModel.isAdminSessionAuthorized())

            // Blank PIN MUST NEVER bypass configured PIN verification
            val blankResult = viewModel.enterAdminSpace("")
            assertFalse(blankResult)
            assertFalse(viewModel.isInAdminSpace.value)
            assertFalse(viewModel.isUnlocked.value)
            assertTrue(viewModel.showAdminPinPrompt.value)
            assertFalse(viewModel.isAdminSessionAuthorized())

            // Wrong PIN must also keep admin space locked
            val wrongResult = viewModel.enterAdminSpace("000000")
            assertFalse(wrongResult)
            assertFalse(viewModel.isInAdminSpace.value)
            assertFalse(viewModel.isUnlocked.value)

            // Valid PIN grants Admin Space access with cryptographic session token
            val validResult = viewModel.enterAdminSpace("748291")
            assertTrue(validResult)
            assertTrue(viewModel.isInAdminSpace.value)
            assertTrue(viewModel.isUnlocked.value)
            assertFalse(viewModel.showAdminPinPrompt.value)
            assertTrue(viewModel.isAdminSessionAuthorized())

            // Locking session immediately revokes authorization for admin actions
            viewModel.lockAdminSession()
            assertFalse(viewModel.isUnlocked.value)
            assertNull(viewModel.activeSession.value)
            assertFalse(viewModel.isAdminSessionAuthorized())
            viewModel.clearUiMessage()
            viewModel.adminToggleReleasePublish("rel_1", true)
            assertTrue(viewModel.uiMessage.value is com.example.ui.viewmodel.UiMessage.Error)

            // Non-admin user cannot unlock even if they know the local PIN
            viewModel.testAuthenticatedUid = null
            assertFalse(viewModel.verifyAdminPin("748291"))
            assertFalse(viewModel.enterAdminSpace("748291"))
            assertFalse(viewModel.isInAdminSpace.value)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun adminSpaceLocksAllTabsInUiWhenPinIsLocked() = runBlocking {
        val server = startBackendServer(
            sessionStatus = 200,
            sessionAdmin = true
        )
        try {
            val viewModel = TmsVpnViewModel(context)
            viewModel.testAuthenticatedUid = "verified_admin_uid"
            viewModel.setBackendClientForTesting(
                TmsBackendApiClient(
                    baseUrl = "http://127.0.0.1:${server.address.port}",
                    tokenProvider = { "valid_admin_token" }
                )
            )
            viewModel.syncBackendEcosystemOnceForTest()
            viewModel.configureInitialAdminPin("748291")
            assertTrue(viewModel.hasConfiguredAdminPin())
            assertTrue(viewModel.enterAdminSpace("748291"))

            composeRule.setContent {
                TmsVpnTheme {
                    TmsVpnApp(
                        currentUserId = "verified_admin_uid",
                        onSignOut = {},
                        viewModel = viewModel
                    )
                }
            }
            composeRule.waitForIdle()

            // When unlocked, Admin Space Overview/Tabs are accessible and gate screen is absent
            assertEquals(0, composeRule.onAllNodesWithTag("admin_locked_gate_screen").fetchSemanticsNodes().size)

            // Lock session while inside Admin Space -> all admin tabs must be gated immediately
            viewModel.lockAdminSession()
            composeRule.waitForIdle()

            composeRule.onNodeWithTag("admin_locked_gate_screen").assertIsDisplayed()
            composeRule.onNodeWithTag("admin_gate_pin_input").assertIsDisplayed()
            composeRule.onNodeWithTag("admin_gate_unlock_button").assertIsDisplayed()

            // Unlock via Gate UI restores Admin Space access
            composeRule.onNodeWithTag("admin_gate_pin_input").performTextInput("748291")
            composeRule.onNodeWithTag("admin_gate_unlock_button").performClick()
            composeRule.waitForIdle()

            assertEquals(0, composeRule.onAllNodesWithTag("admin_locked_gate_screen").fetchSemanticsNodes().size)
            assertTrue(viewModel.isUnlocked.value)
        } finally {
            server.stop(0)
        }
    }

    // --- TASK 2: APK DOWNLOAD URL VALIDATION ---

    @Test
    fun apkDownloadUrlValidatorAcceptsOnlyHttpsOfficialHost() {
        assertTrue(
            ApkDownloadUrlValidator.isValid(
                "https://tms-vpn-bot-core.24z1qne0u9xs.workers.dev/api/app/releases/rel_25/download"
            )
        )
        assertTrue(
            ApkDownloadUrlValidator.isValid(
                "https://TMS-VPN-BOT-CORE.24Z1QNE0U9XS.WORKERS.DEV/releases/app.apk?token=abc"
            )
        )

        val rejectedUrls = listOf(
            "",
            "   ",
            "not-a-url",
            "http://tms-vpn-bot-core.24z1qne0u9xs.workers.dev/api/app/releases/rel_25/download",
            "https://evil.com/tms.apk",
            "https://tms-vpn-bot-core.24z1qne0u9xs.workers.dev.evil.com/tms.apk",
            "https://fake.tms-vpn-bot-core.24z1qne0u9xs.workers.dev/tms.apk",
            "https://tms-vpn-bot-core.24z1qne0u9xs.workers.dev@evil.com/tms.apk",
            "https://tms-vpn-bot-core.24z1qne0u9xs.workers.dev:8443/tms.apk",
            "https://tms-vpn-bot-core.24z1qne0u9xs.workers.dev",
            "javascript:alert(1)",
            "file:///sdcard/Download/evil.apk",
            "content://com.evil.provider/apk"
        )
        rejectedUrls.forEach { badUrl ->
            assertFalse("Expected URL to be rejected: $badUrl", ApkDownloadUrlValidator.isValid(badUrl))
        }
    }

    @Test
    fun profileScreenBlocksUntrustedApkUrlAndLaunchesOnlyApprovedUrl() {
        var downloadCallbacks = 0
        var invalidUrlError = ""

        val maliciousRelease = AppReleaseInfo(
            releaseId = "rel_bad",
            versionName = "9.9.9",
            versionCode = 99,
            fileSizeBytes = 10_000_000L,
            downloadUrl = "http://evil-mirror.example.org/malware.apk",
            isPublished = true
        )

        composeRule.setContent {
            TmsVpnTheme {
                UserProfileSettingsScreen(
                    currentUserId = "u_1",
                    userProfile = null,
                    isPersian = false,
                    onToggleLanguage = {},
                    onOpenRadar = {},
                    onSignOut = {},
                    latestRelease = maliciousRelease,
                    onDownloadApkRelease = { downloadCallbacks++ },
                    onInvalidDownloadUrl = { invalidUrlError = it }
                )
            }
        }

        composeRule.onNodeWithTag("profile_apk_download_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals(0, downloadCallbacks)
        assertTrue(invalidUrlError.isNotBlank())
        assertNull(Shadows.shadowOf(context).nextStartedActivity)
        composeRule.onNodeWithTag("profile_apk_url_error").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun profileScreenLaunchesActionViewForApprovedHttpsDownloadUrl() {
        var downloadCallbacks = 0
        val legitimateUrl = "https://tms-vpn-bot-core.24z1qne0u9xs.workers.dev/api/app/releases/rel_25/download"
        val validRelease = AppReleaseInfo(
            releaseId = "rel_25",
            versionName = "2.5.0",
            versionCode = 25,
            fileSizeBytes = 24_000_000L,
            downloadUrl = legitimateUrl,
            isPublished = true
        )

        composeRule.setContent {
            TmsVpnTheme {
                UserProfileSettingsScreen(
                    currentUserId = "u_1",
                    userProfile = null,
                    isPersian = false,
                    onToggleLanguage = {},
                    onOpenRadar = {},
                    onSignOut = {},
                    latestRelease = validRelease,
                    onDownloadApkRelease = { downloadCallbacks++ }
                )
            }
        }

        composeRule.onNodeWithTag("profile_apk_download_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals(1, downloadCallbacks)
        val startedIntent = Shadows.shadowOf(context).nextStartedActivity
        assertNotNull(startedIntent)
        assertEquals(Intent.ACTION_VIEW, startedIntent.action)
        assertEquals(legitimateUrl, startedIntent.dataString)
    }

    // --- TASK 3: STREAMING APK UPLOAD RELIABILITY ---

    @Test
    fun streamingApkUploadStreamsChunksReportsProgressAndHandlesLargePayloadLimits() = runBlocking {
        val uploadedBodySizes = AtomicInteger(0)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/admin/releases/upload") { ex ->
            val allBytes = ex.requestBody.readBytes()
            uploadedBodySizes.set(allBytes.size)
            val body = """
                {
                  "release":{
                    "releaseId":"rel_stream_1",
                    "versionName":"2.6.0",
                    "versionCode":26,
                    "releaseNotes":"Streamed release",
                    "fileSizeBytes":524288,
                    "downloadUrl":"https://tms-vpn-bot-core.24z1qne0u9xs.workers.dev/api/app/releases/rel_stream_1/download",
                    "isPublished":true
                  }
                }
            """.trimIndent().toByteArray()
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        server.start()

        try {
            val client = TmsBackendApiClient(
                baseUrl = "http://127.0.0.1:${server.address.port}",
                tokenProvider = { "admin_token" }
            )
            val payload = ByteArray(512 * 1024) { (it % 127).toByte() }
            val progressSnapshots = mutableListOf<Long>()

            val res = client.adminUploadApkReleaseStream(
                fileName = "tms-vpn-2.6.0.apk",
                fileSizeBytes = payload.size.toLong(),
                versionName = "2.6.0",
                versionCode = 26,
                releaseNotes = "Streamed release",
                publishImmediately = true,
                streamProvider = { ByteArrayInputStream(payload) },
                onProgress = { uploaded, _ -> progressSnapshots.add(uploaded) }
            )

            assertTrue(res is BackendResult.Success)
            assertEquals("rel_stream_1", (res as BackendResult.Success).data.releaseId)
            assertTrue(uploadedBodySizes.get() >= payload.size)
            assertTrue(progressSnapshots.size >= 2)
            assertEquals(payload.size.toLong(), progressSnapshots.last())

            // Oversized APK (> MAX_APK_UPLOAD_BYTES) rejected cleanly before network write
            val oversized = client.adminUploadApkReleaseStream(
                fileName = "huge.apk",
                fileSizeBytes = TmsBackendApiClient.MAX_APK_UPLOAD_BYTES + 1024L,
                versionName = "9.0.0",
                versionCode = 90,
                releaseNotes = "Too large",
                publishImmediately = false,
                streamProvider = { ByteArrayInputStream(ByteArray(1)) }
            )
            assertTrue(oversized is BackendResult.HttpError)
            assertEquals(413, (oversized as BackendResult.HttpError).httpCode)
        } finally {
            server.stop(0)
        }
    }

    // --- TASK 4: BACKGROUND API ERROR VISIBILITY ---

    @Test
    fun backgroundSyncSurfacesAuthServerNetworkErrorsPreservesStateAndIgnoresLatestRelease404() = runBlocking {
        assertNull(
            classifyBackgroundError(
                operation = "latest_release",
                result = BackendResult.HttpError(404, "NOT_FOUND", "No release"),
                isPersian = false
            )
        )
        assertEquals(
            BackendErrorCategory.AUTHENTICATION,
            classifyBackgroundError("trial_status", BackendResult.Unauthenticated, false)?.category
        )
        assertEquals(
            BackendErrorCategory.SERVER,
            classifyBackgroundError("trial_status", BackendResult.HttpError(500, "INTERNAL", "DB fail"), false)?.category
        )
        assertEquals(
            BackendErrorCategory.NETWORK,
            classifyBackgroundError("trial_status", BackendResult.NetworkError("timeout"), false)?.category
        )

        val trialHttpCode = AtomicInteger(200)
        val sessionHttpCode = AtomicInteger(200)
        val server = startBackendServer(
            sessionStatusProvider = { sessionHttpCode.get() },
            trialStatusProvider = { trialHttpCode.get() },
            latestReleaseStatusProvider = { 404 }
        )

        try {
            val viewModel = TmsVpnViewModel(context)
            viewModel.testAuthenticatedUid = "user_sync_1"
            viewModel.setBackendClientForTesting(
                TmsBackendApiClient(
                    baseUrl = "http://127.0.0.1:${server.address.port}",
                    tokenProvider = { "valid_user_token" }
                )
            )

            // 1. Initial sync succeeds cleanly even with 404 on /api/app/releases/latest
            viewModel.syncBackendEcosystemOnceForTest()
            assertNull(viewModel.backgroundBackendError.value)
            assertNotNull(viewModel.freeTrialStatus.value)
            assertNotNull(viewModel.referralDashboard.value)
            assertNull(viewModel.uiMessage.value)

            // 2. Temporary 500 server error surfaces SERVER error, preserves data, avoids snackbar
            trialHttpCode.set(500)
            viewModel.syncBackendEcosystemOnceForTest()
            assertEquals(BackendErrorCategory.SERVER, viewModel.backgroundBackendError.value?.category)
            assertNotNull(viewModel.freeTrialStatus.value)
            assertNotNull(viewModel.referralDashboard.value)
            assertNull(viewModel.uiMessage.value)

            // 3. 403 auth error surfaces AUTHENTICATION error cleanly
            trialHttpCode.set(200)
            sessionHttpCode.set(403)
            viewModel.syncBackendEcosystemOnceForTest()
            assertEquals(BackendErrorCategory.AUTHENTICATION, viewModel.backgroundBackendError.value?.category)
            assertNull(viewModel.uiMessage.value)

            // 4. Network outage surfaces NETWORK error while keeping cached data intact
            server.stop(0)
            viewModel.syncBackendEcosystemOnceForTest()
            assertEquals(BackendErrorCategory.NETWORK, viewModel.backgroundBackendError.value?.category)
            assertNotNull(viewModel.freeTrialStatus.value)
            assertNotNull(viewModel.referralDashboard.value)
            assertNull(viewModel.uiMessage.value)
        } finally {
            runCatching { server.stop(0) }
        }
    }

    @Test
    fun backgroundErrorBannerShowsRetryAndDismissControlsOnSubscriptionScreen() {
        var retried = false
        var dismissed = false
        composeRule.setContent {
            TmsVpnTheme {
                UserSubscriptionScreen(
                    userProfile = null,
                    isPersian = false,
                    backgroundBackendError = BackgroundBackendError(
                        category = BackendErrorCategory.NETWORK,
                        message = "Connection timed out while refreshing referrals",
                        operation = "referral_dashboard"
                    ),
                    onRefreshBackend = { retried = true },
                    onDismissBackgroundError = { dismissed = true }
                )
            }
        }

        composeRule.onNodeWithTag("backend_sync_error_banner").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("backend_sync_retry_button").performClick()
        composeRule.onNodeWithTag("backend_sync_dismiss_button").performClick()
        assertTrue(retried)
        assertTrue(dismissed)
    }

    private fun startBackendServer(
        sessionStatus: Int = 200,
        sessionAdmin: Boolean = false,
        sessionStatusProvider: () -> Int = { sessionStatus },
        trialStatusProvider: () -> Int = { 200 },
        latestReleaseStatusProvider: () -> Int = { 404 }
    ): HttpServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/app/session") { ex ->
            val code = sessionStatusProvider()
            val body = if (code == 200) {
                """
                    {
                      "uid":"u_sync",
                      "email":"admin@tms.ir",
                      "isAdmin":$sessionAdmin,
                      "role":${if (sessionAdmin) "\"SUPER_ADMIN\"" else "\"USER\""},
                      "referralCode":"TMS-TEST1",
                      "serverTimeMs":1763950000000
                    }
                """.trimIndent().toByteArray()
            } else {
                """{"error":"FORBIDDEN","message":"Access denied"}""".toByteArray()
            }
            ex.sendResponseHeaders(code, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        server.createContext("/api/app/trial/status") { ex ->
            val code = trialStatusProvider()
            val body = if (code == 200) {
                """{"trial":{"eligible":true,"active":false,"cooldownRemainingSeconds":0}}""".toByteArray()
            } else {
                """{"error":"INTERNAL_ERROR","message":"D1 query failed"}""".toByteArray()
            }
            ex.sendResponseHeaders(code, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        server.createContext("/api/app/referrals") { ex ->
            val body = """{"referralCode":"TMS-TEST1","qualifiedCount":0,"referrals":[],"rewardRequests":[]}""".toByteArray()
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        server.createContext("/api/app/payments/crypto/invoices") { ex ->
            val body = """{"invoices":[]}""".toByteArray()
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        server.createContext("/api/admin/payments/crypto/invoices") { ex ->
            val body = """{"invoices":[]}""".toByteArray()
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        server.createContext("/api/app/releases/latest") { ex ->
            val code = latestReleaseStatusProvider()
            val body = if (code == 200) {
                """{"release":{"releaseId":"rel_1","versionName":"1.0","versionCode":1,"downloadUrl":"https://tms-vpn-bot-core.24z1qne0u9xs.workers.dev/api/app/releases/rel_1/download","isPublished":true}}""".toByteArray()
            } else {
                """{"error":"NOT_FOUND","message":"No published release"}""".toByteArray()
            }
            ex.sendResponseHeaders(code, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        server.createContext("/api/admin/referrals/rewards") { ex ->
            val body = """{"requests":[]}""".toByteArray()
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        server.createContext("/api/admin/releases") { ex ->
            val body = """{"releases":[]}""".toByteArray()
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        server.start()
        return server
    }
}
