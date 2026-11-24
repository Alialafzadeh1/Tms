package com.example.data

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.LocalVpnConfigRepository
import com.example.data.local.LegacyAccountMigrationHelper
import com.example.data.local.TmsVpnRoomDatabase
import com.example.data.model.AppReleaseInfo
import com.example.data.model.BackendResult
import com.example.data.model.CryptoAsset
import com.example.data.model.CryptoInvoiceStatus
import com.example.data.model.CryptoPaymentInvoice
import com.example.data.model.FreeTrialStatus
import com.example.data.model.ReferralDashboardData
import com.example.data.model.ReferralRewardRequest
import com.example.data.model.ReferralRewardStatus
import com.example.data.model.SubscriptionPlanId
import com.example.data.model.UserSubscription
import com.example.data.model.VpnConfig
import com.example.data.network.TmsBackendApiClient
import com.example.data.security.LocalUserAuthManager
import com.example.data.subscription.ReferralAndCryptoCalculator
import com.example.ui.screens.UserProfileSettingsScreen
import com.example.ui.screens.UserSubscriptionScreen
import com.google.firebase.Timestamp
import com.example.ui.theme.TmsVpnTheme
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp-xxhdpi")
class NexusBackendAndReferralTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Application>()
        TmsVpnRoomDatabase.clearInstanceForTests()
        context.deleteDatabase("tms_vpn_database")
        context.getSharedPreferences("tms_legacy_account_migration", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() {
        TmsVpnRoomDatabase.clearInstanceForTests()
    }

    @Test
    fun planTomanPricesAndCumulativeReferralMilestonesCalculateCorrectly() {
        assertEquals(300_000L, SubscriptionPlanId.ONE_MONTH.priceToman)
        assertEquals(650_000L, SubscriptionPlanId.THREE_MONTHS.priceToman)
        assertEquals(1_700_000L, SubscriptionPlanId.ONE_YEAR.priceToman)
        assertEquals("۳۰۰,۰۰۰ تومان", SubscriptionPlanId.ONE_MONTH.formattedPriceTomanFa)
        assertEquals("۶۵۰,۰۰۰ تومان", SubscriptionPlanId.THREE_MONTHS.formattedPriceTomanFa)
        assertEquals("۱,۷۰۰,۰۰۰ تومان", SubscriptionPlanId.ONE_YEAR.formattedPriceTomanFa)

        // Independent cumulative milestones: 2 -> +1GB, 4 -> +1.5GB, 10 -> +10GB = 12.5GB total
        assertEquals(0.0, ReferralAndCryptoCalculator.calculateTotalUnlockedGb(1), 0.001)
        assertEquals(1.0, ReferralAndCryptoCalculator.calculateTotalUnlockedGb(2), 0.001)
        assertEquals(2.5, ReferralAndCryptoCalculator.calculateTotalUnlockedGb(4), 0.001)
        assertEquals(12.5, ReferralAndCryptoCalculator.calculateTotalUnlockedGb(10), 0.001)

        val requests = listOf(
            ReferralRewardRequest(
                requestId = "req_2",
                userId = "u1",
                milestoneThreshold = 2,
                rewardGb = 1.0,
                status = ReferralRewardStatus.APPROVED
            )
        )
        val evaluated = ReferralAndCryptoCalculator.evaluateMilestones(5, requests)
        assertEquals(3, evaluated.size)
        // Milestone 2 unlocked and already approved -> cannot re-claim
        assertTrue(evaluated[0].isUnlocked)
        assertFalse(evaluated[0].canClaim)
        // Milestone 4 unlocked and unclaimed -> claimable
        assertTrue(evaluated[1].isUnlocked)
        assertTrue(evaluated[1].canClaim)
        // Milestone 10 locked at 5 qualified referrals
        assertFalse(evaluated[2].isUnlocked)
        assertFalse(evaluated[2].canClaim)
    }

    @Test
    fun legacyAccountMigrationCopiesRoomConfigsAndSubscriptionOnceToFirebaseUid() = runBlocking {
        val repo = LocalVpnConfigRepository(context)
        val legacyUid = LocalUserAuthManager.computeLegacyIntranetUid("user@tms.ir")
        assertTrue(legacyUid.startsWith("intranet_"))

        repo.saveConfig(
            VpnConfig(
                id = "cfg_legacy_1",
                userId = legacyUid,
                name = "DE Edge Node",
                protocol = "vless",
                server = "1.2.3.4",
                port = 443
            )
        )
        val nowMs = System.currentTimeMillis()
        repo.cacheSubscription(
            sub = UserSubscription(
                subscriptionId = "sub_$legacyUid",
                userId = legacyUid,
                userEmail = "user@tms.ir",
                planId = SubscriptionPlanId.THREE_MONTHS.id,
                planDurationDays = 90,
                endpointAllocationLimit = 15,
                startDate = Timestamp(java.util.Date(nowMs)),
                expirationDate = Timestamp(java.util.Date(nowMs + 86_400_000L)),
                status = "ACTIVE",
                paymentStatus = "CONFIRMED",
                currentAllocationVersion = 2
            ),
            syncedServerNowMs = nowMs
        )

        val targetUid = "firebase_uid_xyz"
        val migratedCount1 = LegacyAccountMigrationHelper.migrateLegacyLocalDataIfNeeded(
            context = context,
            localRepo = repo,
            firebaseUid = targetUid,
            email = "user@tms.ir"
        )
        assertTrue(migratedCount1 >= 2)

        val allConfigs = repo.getAllOnce()
        assertTrue(allConfigs.any { it.userId == targetUid && it.server == "1.2.3.4" })
        val cachedTarget = repo.getCachedSubscriptionOnce(targetUid)
        assertNotNull(cachedTarget)
        assertEquals(SubscriptionPlanId.THREE_MONTHS.id, cachedTarget!!.planId)

        // Idempotent second call must not duplicate
        val migratedCount2 = LegacyAccountMigrationHelper.migrateLegacyLocalDataIfNeeded(
            context = context,
            localRepo = repo,
            firebaseUid = targetUid,
            email = "user@tms.ir"
        )
        assertEquals(0, migratedCount2)
    }

    @Test
    fun backendApiClientAttachesTokenRetriesOnceOn401AndPropagatesErrorsHonestly() = runBlocking {
        val tokenRequests = AtomicInteger(0)
        val sessionAttempts = AtomicInteger(0)

        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/app/session") { ex ->
            val auth = ex.requestHeaders.getFirst("Authorization")
            val attempt = sessionAttempts.incrementAndGet()
            if (attempt == 1 && auth == "Bearer stale_token") {
                val body = """{"error":"TOKEN_EXPIRED","message":"Expired token"}""".toByteArray()
                ex.sendResponseHeaders(401, body.size.toLong())
                ex.responseBody.use { it.write(body) }
            } else {
                val body = """
                    {
                      "uid":"u_real",
                      "email":"ali@tms.ir",
                      "isAdmin":true,
                      "role":"SUPER_ADMIN",
                      "telegramLinked":true,
                      "telegramUsername":"tmstime_user",
                      "referralCode":"TMS-ALI99",
                      "referralShareUrl":"https://t.me/TMS_TIME_BOT?start=TMS-ALI99",
                      "serverTimeMs":1763950000000
                    }
                """.trimIndent().toByteArray()
                ex.sendResponseHeaders(200, body.size.toLong())
                ex.responseBody.use { it.write(body) }
            }
        }
        server.createContext("/api/app/payments/crypto/invoices") { ex ->
            val body = """
                {
                  "invoice":{
                    "invoiceId":"inv_101",
                    "userId":"u_real",
                    "planId":"plan_1m",
                    "asset":"USDT_TRC20",
                    "network":"TRC20",
                    "depositAddress":"TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE",
                    "expectedAmountCrypto":"5.00",
                    "expectedAmountToman":300000,
                    "lockedRateToman":60000,
                    "status":"PENDING",
                    "expiresAtMs":1763951800000
                  }
                }
            """.trimIndent().toByteArray()
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        server.createContext("/api/app/trial/activate") { ex ->
            val body = """{"error":"TRIAL_COOLDOWN","message":"Cooldown active: 5 days remaining"}""".toByteArray()
            ex.sendResponseHeaders(429, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        server.start()

        try {
            val baseUrl = "http://127.0.0.1:${server.address.port}"
            val client = TmsBackendApiClient(
                baseUrl = baseUrl,
                tokenProvider = { forceRefresh ->
                    tokenRequests.incrementAndGet()
                    if (forceRefresh) "fresh_token" else "stale_token"
                }
            )

            // Verifies 401 retry with forced Firebase token refresh succeeds
            val syncRes = client.syncSession()
            assertTrue(syncRes is BackendResult.Success)
            val sync = (syncRes as BackendResult.Success).data
            assertEquals("u_real", sync.uid)
            assertTrue(sync.isAdmin)
            assertTrue(sync.telegramLinked)
            assertEquals("TMS-ALI99", sync.referralCode)
            assertEquals(2, sessionAttempts.get())

            // Verifies crypto invoice creation parsing
            val invRes = client.createCryptoInvoice("plan_1m", CryptoAsset.USDT_TRC20)
            assertTrue(invRes is BackendResult.Success)
            val inv = (invRes as BackendResult.Success).data
            assertEquals("inv_101", inv.invoiceId)
            assertEquals("5.00", inv.expectedAmountCrypto)
            assertEquals(CryptoInvoiceStatus.PENDING, inv.status)

            // Verifies 429 cooldown returns honest HttpError rather than fabricated grant
            val trialRes = client.activateFreeTrial()
            assertTrue(trialRes is BackendResult.HttpError)
            assertEquals(429, (trialRes as BackendResult.HttpError).httpCode)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun subscriptionAndProfileUiRenderPricingTrialCryptoReferralAndApkDownload() {
        var trialClicked = false
        var claimedMilestone = 0
        var apkDownloadedId = ""

        val sampleDashboard = ReferralDashboardData(
            referralCode = "TMS-7788",
            referralShareUrl = "https://t.me/TMS_TIME_BOT?start=TMS-7788",
            totalInvitedCount = 3,
            qualifiedCount = 2,
            totalUnlockedGb = 1.0,
            milestones = ReferralAndCryptoCalculator.evaluateMilestones(2, emptyList())
        )

        composeRule.setContent {
            TmsVpnTheme {
                UserSubscriptionScreen(
                    userProfile = null,
                    isPersian = true,
                    freeTrialStatus = FreeTrialStatus(eligible = true, active = false),
                    referralDashboard = sampleDashboard,
                    onActivateFreeTrial = { trialClicked = true },
                    onClaimReferralReward = { claimedMilestone = it }
                )
            }
        }

        composeRule.onNodeWithTag("trial_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("trial_activate_button").performScrollTo().performClick()
        assertTrue(trialClicked)

        composeRule.onNodeWithTag("plan_price_plan_1m").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("crypto_pay_open_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("referral_section_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("claim_milestone_2").performScrollTo().performClick()
        assertEquals(2, claimedMilestone)
    }

    @Test
    fun profileSettingsCardRendersLatestApkReleaseAndRecordsDownload() {
        var clickedReleaseId = ""
        val release = AppReleaseInfo(
            releaseId = "rel_25",
            versionName = "2.5.0",
            versionCode = 25,
            releaseNotes = "بهبود پایداری و پشتیبانی تتر TRC20",
            fileSizeBytes = 24L * 1024L * 1024L,
            downloadCount = 120L,
            downloadUrl = "https://tms-vpn-bot-core.24z1qne0u9xs.workers.dev/api/app/releases/rel_25/download",
            isPublished = true
        )

        composeRule.setContent {
            TmsVpnTheme {
                UserProfileSettingsScreen(
                    currentUserId = "u_profile",
                    userProfile = null,
                    isPersian = true,
                    isVerifiedAdmin = false,
                    latestRelease = release,
                    onDownloadApkRelease = { clickedReleaseId = it.releaseId },
                    onToggleLanguage = {},
                    onOpenRadar = {},
                    onSignOut = {}
                )
            }
        }

        composeRule.onNodeWithTag("profile_apk_update_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("profile_apk_download_button").performScrollTo().assertIsDisplayed()
    }
}
