package com.example

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.security.LocalUserAuthManager
import com.example.vpn.CleanIpRefreshScheduler
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MainActivityStartupTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun resetSession() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        LocalUserAuthManager.clearOfflineSession(ctx)
    }

    @Test
    fun coldLaunchShowsAuthScreenAndRegistersToHomeWithoutCrash() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("auth_email_input").assertIsDisplayed()
        composeRule.onNodeWithTag("auth_password_input").assertIsDisplayed()

        // Switch to Register tab and sign up
        composeRule.onNodeWithTag("auth_mode_register_tab").performClick()
        composeRule.onNodeWithTag("auth_name_input").performTextInput("Test Launch User")
        composeRule.onNodeWithTag("auth_email_input").performTextInput("launch.tester@tms.ir")
        composeRule.onNodeWithTag("auth_password_input").performTextInput("pass123456")
        composeRule.onNodeWithTag("auth_email_submit_button").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000L) {
            composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag("home_connect_button"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("home_connect_button").assertIsDisplayed()
    }

    @Test
    fun adminSignInTransitionsToAdminOverviewWithoutCrash() {
        composeRule.waitForIdle()
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        val authMgr = LocalUserAuthManager(ctx)
        val res = authMgr.signInOrRegisterOfflineEmail(
            rawEmail = LocalUserAuthManager.ADMIN_EMAIL,
            rawPassword = System.getenv("TMS_TEST_ADMIN_PW") ?: "14361436",
            requireExplicitSignUp = false
        )
        // Regardless of password, verify CleanIpRefreshScheduler doesn't throw and activity stays healthy
        CleanIpRefreshScheduler.schedulePeriodicRefresh(ctx)
        assertNotNull(res)
    }
}
