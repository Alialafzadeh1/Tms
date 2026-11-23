package com.example.security

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.ai.NexusAiScope
import com.example.data.ai.NexusChatMessage
import com.example.data.ai.NexusContextSanitizer
import com.example.data.ai.NexusDiagnosticReport
import com.example.data.local.CleanIpAddressType
import com.example.data.local.CleanIpEntity
import com.example.data.local.CleanIpHealthStatus
import com.example.data.model.AdminRole
import com.example.data.model.InventoryShortageAlert
import com.example.data.model.ManagedEndpoint
import com.example.data.model.SubscriptionPlanId
import com.example.data.model.SubscriptionStatus
import com.example.data.model.UserSubscription
import com.example.data.model.VpnConfig
import com.example.data.model.VpnConnectionState
import com.example.data.model.VpnServer
import com.example.data.model.VpnSessionStats
import com.example.data.security.AdminSecurityManager
import com.example.data.security.AuthAttemptResult
import com.example.ui.screens.NexusAssistantModal
import com.example.ui.theme.TmsVpnTheme
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AdminSecurityAndNexusIsolationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `admin security manager persists changed pin across restarts and rejects default bypass`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("tms_admin_security_prefs", Context.MODE_PRIVATE).edit().clear().commit()

        val manager1 = AdminSecurityManager(context)
        val initialToken = manager1.getRecoveryToken()
        assertTrue(initialToken.startsWith("TMS-REC-"))
        assertNotEquals("TMS-RECOVERY-2026-ALPHA-99", initialToken)
        assertFalse(manager1.isCustomPinConfigured())

        // Initial default PIN works once
        val (initialVerify, _) = manager1.verifyAdminCredential("admin_1", AdminRole.SUPER_ADMIN, "14361436", "TestDevice")
        assertTrue(initialVerify is AuthAttemptResult.Success)

        // Change PIN
        val (changed, _) = manager1.changeAdminPin("admin_1", AdminRole.SUPER_ADMIN, "14361436", "98765432")
        assertTrue(changed)

        // Re-instantiate AdminSecurityManager (simulates cold app restart)
        val manager2 = AdminSecurityManager(context)
        assertTrue(manager2.isCustomPinConfigured())

        // Old/default 14361436 MUST NOT bypass after custom PIN is saved
        val (bypassAttempt, _) = manager2.verifyAdminCredential("admin_1", AdminRole.SUPER_ADMIN, "14361436", "TestDevice")
        assertTrue(bypassAttempt is AuthAttemptResult.InvalidCredentials)

        // New custom PIN succeeds
        val (newPinAttempt, _) = manager2.verifyAdminCredential("admin_1", AdminRole.SUPER_ADMIN, "98765432", "TestDevice")
        assertTrue(newPinAttempt is AuthAttemptResult.Success)

        // Recovery token rotates after emergency reset
        val (resetOk, _) = manager2.emergencyResetPinWithRecoveryToken(initialToken, "55667788")
        assertTrue(resetOk)
        val rotatedToken = manager2.getRecoveryToken()
        assertNotEquals(initialToken, rotatedToken)
    }

    @Test
    fun `admin security manager triggers 5 minute lockout after 5 invalid attempts`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("tms_admin_security_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        val manager = AdminSecurityManager(context)

        repeat(4) {
            val (res, _) = manager.verifyAdminCredential("admin_1", AdminRole.SUPER_ADMIN, "00000000", "TestDevice")
            assertTrue(res is AuthAttemptResult.InvalidCredentials)
        }
        val (fifth, _) = manager.verifyAdminCredential("admin_1", AdminRole.SUPER_ADMIN, "00000000", "TestDevice")
        assertTrue(fifth is AuthAttemptResult.LockedOut)
        assertTrue(manager.isLockedOut().first)
    }

    @Test
    fun `nexus user sanitizer isolates user data and redacts sensitive secrets`() {
        val secretUuid = "9f8e7d6c-SECRET-USER-UUID-1234"
        val secretRaw = "vless://$secretUuid@de.tmsvpn.net:443?type=ws#Test"
        val selectedCfg = VpnConfig(
            id = "cfg_1",
            userId = "user_alpha",
            name = "My Clean Node",
            protocol = "vless",
            server = "de.tmsvpn.net",
            port = 443,
            uuidOrPassword = secretUuid,
            transport = "ws",
            security = "tls",
            sni = "cdn.example.com",
            rawConfig = secretRaw,
            pingMs = 74,
            status = "online"
        )
        val sub = UserSubscription(
            subscriptionId = "sub_alpha",
            userId = "user_alpha",
            planId = SubscriptionPlanId.THREE_MONTHS.id,
            status = SubscriptionStatus.ACTIVE.name,
            endpointAllocationLimit = 15
        )

        val userCtx = NexusContextSanitizer.buildUserSafeContext(
            vpnState = VpnConnectionState.CONNECTED,
            selectedConfig = selectedCfg,
            userConfigCount = 4,
            onlineConfigCount = 3,
            sessionStats = VpnSessionStats(engineMode = "Xray REALITY/WS"),
            healthReport = null,
            leakCheckReport = null,
            ispTuningReport = null,
            userSubscription = sub,
            allocatedEndpointCount = 15
        )

        assertTrue(userCtx.contains("scope: USER_ASSISTANT"))
        assertTrue(userCtx.contains("My Clean Node"))
        assertFalse("Raw UUID must NEVER enter AI prompt context", userCtx.contains(secretUuid))
        assertFalse("Raw vless link must NEVER enter AI prompt context", userCtx.contains(secretRaw))
        assertFalse("User context must NEVER include admin operations markers", userCtx.contains("ADMIN_COPILOT"))

        val adminCtx = NexusContextSanitizer.buildAdminOperationsContext(
            servers = listOf(VpnServer(id = "s1", name = "DE-Core", countryCode = "DE", host = "1.2.3.4", port = 443, status = "online", latencyMs = 60)),
            configs = listOf(selectedCfg),
            endpointInventory = listOf(ManagedEndpoint(endpointId = "ep1", host = "5.6.7.8", enabled = true, healthy = true)),
            cleanIpPool = listOf(
                CleanIpEntity(
                    host = "69.48.218.1",
                    addressType = CleanIpAddressType.IPV4.name,
                    healthStatus = CleanIpHealthStatus.HEALTHY.name,
                    latencyMs = 52
                )
            ),
            subscriptions = listOf(sub),
            shortageAlerts = listOf(InventoryShortageAlert(alertId = "al1", planId = "plan_3m", requestedCount = 15, allocatedCount = 10, shortfallCount = 5)),
            rotationLogs = emptyList()
        )

        assertTrue(adminCtx.contains("ADMIN_COPILOT"))
        assertTrue(adminCtx.contains("69.48.218.1"))
        assertTrue(adminCtx.contains("unresolved_shortage_alerts: 1"))
        assertFalse("Admin prompt must redact user UUID", adminCtx.contains(secretUuid))
    }

    @Test
    fun `nexus assistant modal renders tabs diagnostics error transparently and support form`() {
        var diagnosticsTriggered = false
        composeRule.setContent {
            TmsVpnTheme {
                NexusAssistantModal(
                    isPersian = true,
                    messages = listOf(
                        NexusChatMessage(id = "m1", isFromUser = true, text = "سلام وضعیت چطوره؟", scope = NexusAiScope.USER_ASSISTANT),
                        NexusChatMessage(id = "m2", isFromUser = false, text = "اتصال شما روی VLESS پایدار است.", scope = NexusAiScope.USER_ASSISTANT)
                    ),
                    isStreaming = false,
                    diagnosticReport = NexusDiagnosticReport(
                        summaryFa = "تونل فعال و بدون نشت DNS است",
                        severity = "LOW",
                        rootCause = "کیفیت مسیر مناسب است",
                        recommendedActions = listOf("نیازی به تغییر نیست"),
                        suggestedTransport = "vless+ws+tls",
                        shouldTriggerIspTune = false,
                        shouldTriggerFailover = false,
                        confidencePercent = 94
                    ),
                    diagnosticLoading = false,
                    diagnosticError = "App Check token unavailable in offline test",
                    tickets = emptyList(),
                    ticketBusy = false,
                    observeTicketReplies = { flowOf(emptyList()) },
                    onSendChatMessage = {},
                    onClearChat = {},
                    onRunDiagnostics = { diagnosticsTriggered = true },
                    onTriggerIspAutoTune = {},
                    onTriggerFailover = {},
                    onSubmitTicket = { _, _, _, _ -> },
                    onReplyTicket = { _, _ -> },
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithTag("nexus_assistant_modal").assertIsDisplayed()
        composeRule.onNodeWithTag("nexus_chat_input").assertIsDisplayed()

        // Switch to Diagnostics tab
        composeRule.onNodeWithTag("nexus_tab_diagnostics").performClick()
        composeRule.onNodeWithTag("nexus_diagnostic_error_card").assertIsDisplayed()
        composeRule.onNodeWithTag("nexus_diagnostic_report_card").assertIsDisplayed()
        composeRule.onNodeWithTag("nexus_run_diagnostics_button").performClick()
        assertTrue(diagnosticsTriggered)

        // Switch to Support & KB tab
        composeRule.onNodeWithTag("nexus_tab_support").performClick()
        composeRule.onNodeWithTag("ticket_subject_input").assertIsDisplayed()
        composeRule.onNodeWithTag("ticket_submit_button").assertIsDisplayed()
        assertEquals(true, diagnosticsTriggered)
    }
}
