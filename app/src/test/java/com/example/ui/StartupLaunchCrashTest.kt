package com.example.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.MainActivity
import com.example.data.local.TmsVpnRoomDatabase
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.auth
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
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
        runCatching { Firebase.auth.signOut() }
    }

    @Test
    fun signedOutLaunchRendersAuthScreenWithoutCrash() {
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithTag("auth_email_submit_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("auth_email_submit_button").assertIsDisplayed()
        composeRule.onNodeWithTag("auth_forgot_password_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun authScreenValidatesEmailInputWithoutOfflineBypass() {
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithTag("auth_email_input").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("auth_email_input").performScrollTo().performTextInput("invalid-email")
        composeRule.onNodeWithTag("auth_password_input").performScrollTo().performTextInput("pass123")
        composeRule.onNodeWithTag("auth_email_submit_button").performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 3_000L) {
            composeRule.onAllNodesWithTag("auth_error_text").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("auth_error_text").assertIsDisplayed()
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
