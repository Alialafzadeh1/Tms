package com.example.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.MainActivity
import com.example.data.local.TmsVpnRoomDatabase
import com.example.data.security.LocalUserAuthManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp-xxhdpi")
class StartupLaunchCrashTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        LocalUserAuthManager.clearOfflineSession(context)
    }

    @After
    fun tearDown() {
        LocalUserAuthManager.clearOfflineSession(context)
    }

    @Test
    fun signedOutLaunchRendersAuthScreenWithoutCrash() {
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithTag("auth_email_submit_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("auth_email_submit_button").assertIsDisplayed()
    }

    @Test
    fun signedInStandardUserLaunchRendersUserHomeWithoutCrash() {
        val authManager = LocalUserAuthManager(context)
        val res = authManager.signInOrRegisterOfflineEmail(
            rawEmail = "startup.user@tmsvpn.ir",
            rawPassword = "UserPass#2026",
            rawDisplayName = "Startup User",
            requireExplicitSignUp = true
        )
        assertTrue(res.isSuccess)

        composeRule.activityRule.scenario.recreate()
        composeRule.waitUntil(timeoutMillis = 6_000L) {
            composeRule.onAllNodesWithTag("home_connect_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("home_connect_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("home_combo_shield_switch").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun signedInAdminLaunchRendersManagementOverviewWithoutCrash() {
        val authManager = LocalUserAuthManager(context)
        val res = authManager.signInOrRegisterOfflineEmail(
            rawEmail = LocalUserAuthManager.ADMIN_EMAIL,
            rawPassword = "@ALI1384alii",
            requireExplicitSignUp = false
        )
        assertTrue(res.isSuccess)

        composeRule.activityRule.scenario.recreate()
        composeRule.waitUntil(timeoutMillis = 6_000L) {
            composeRule.onAllNodesWithTag("admin_overview_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("admin_overview_screen").assertIsDisplayed()
    }

    @Test
    fun mismatchedLegacyV5DatabaseUpgradesCleanlyToV6WithoutCrash() = runBlocking {
        val dbName = "tms_vpn_database"
        TmsVpnRoomDatabase.clearInstanceForTests()
        context.deleteDatabase(dbName)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(5) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE IF NOT EXISTS stale_legacy_table (id TEXT PRIMARY KEY)")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )
        helper.writableDatabase.close()

        val db = TmsVpnRoomDatabase.getDatabase(context)
        assertNotNull(db)
        val cleanIps = db.cleanIpDao().getAllOnce()
        assertNotNull(cleanIps)
        TmsVpnRoomDatabase.clearInstanceForTests()
    }
}
