package com.example.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.example.data.model.AdminRole
import com.example.data.model.AllocatedEndpointRecord
import com.example.data.model.AllocationHistoryRecord
import com.example.data.model.ConfigOrigin
import com.example.data.model.InventoryShortageAlert
import com.example.data.model.ManagedEndpoint
import com.example.data.model.PaymentRequestRecord
import com.example.data.model.RotationAuditLog
import com.example.data.model.SubscriptionPlanId
import com.example.data.model.SubscriptionStatus
import com.example.data.model.UserProfile
import com.example.data.model.VpnConfig
import com.example.data.subscription.ConfigDeletionContext
import com.example.data.subscription.ConfigDeletionPolicy
import com.example.data.subscription.SubscriptionLifecycleEngine
import com.example.ui.screens.AdminSubscriptionScreen
import com.example.ui.screens.ConfigManagementScreen
import com.example.ui.screens.UserSubscriptionScreen
import com.example.ui.theme.TmsVpnTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SubscriptionAndConfigUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val nowMs = 1_795_000_000_000L

    @Test
    fun configManagementScreenSupportsSearchStatusFilterMultiSelectAndProtectedConfirmation() {
        val configs = listOf(
            VpnConfig(
                id = "usr_1",
                userId = "alice",
                name = "Frankfurt Custom",
                protocol = "vless",
                server = "de1.tmsvpn.net",
                port = 443,
                status = "online",
                pingMs = 45,
                origin = ConfigOrigin.USER_IMPORTED.key
            ),
            VpnConfig(
                id = "sub_1",
                userId = "alice",
                name = "Dedicated Helsinki",
                protocol = "trojan",
                server = "fi1.tmsvpn.net",
                port = 443,
                status = "online",
                pingMs = 38,
                origin = ConfigOrigin.SUBSCRIPTION_ASSIGNED.key
            ),
            VpnConfig(
                id = "sys_1",
                userId = "alice",
                name = "System Core Protected",
                protocol = "vless",
                server = "core.tmsvpn.net",
                port = 443,
                status = "offline",
                origin = ConfigOrigin.SYSTEM_MANAGED.key
            )
        )

        var selectedIds = setOf("usr_1")
        var deleteSelectedTriggered = false

        composeRule.setContent {
            TmsVpnTheme {
                ConfigManagementScreen(
                    configs = configs,
                    currentRole = AdminRole.SUPER_ADMIN,
                    isPersian = true,
                    isPinging = false,
                    onImportConfig = {},
                    onImportBatch = {},
                    onSelectForVpn = {},
                    onPingConfig = {},
                    onDeleteConfig = {},
                    onToggleFavorite = {},
                    onQuickAudit = {},
                    selectedConfigIds = selectedIds,
                    onToggleSelection = { id ->
                        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                    },
                    onSelectAll = { ids -> selectedIds = ids.toSet() },
                    onDeselectAll = { selectedIds = emptySet() },
                    onDeleteSelected = { deleteSelectedTriggered = true },
                    onDeleteAllPermitted = {},
                    previewBulkDeletion = { ids ->
                        ConfigDeletionPolicy.planBulkDeletion(
                            allConfigs = configs,
                            targetIds = ids,
                            context = ConfigDeletionContext(
                                actorUserId = "alice",
                                isAdminActor = true,
                                hasAdminManageConfigsPermission = true
                            )
                        )
                    }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("config_management_list")
            .performScrollToNode(hasTestTag("bulk_action_bar"))
        composeRule.onNodeWithTag("bulk_action_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("selected_configs_count").assertTextContains("1", substring = true)

        // Filter by search query
        composeRule.onNodeWithTag("config_management_list")
            .performScrollToNode(hasTestTag("config_search_input"))
        composeRule.onNodeWithTag("config_search_input").performTextInput("Frankfurt")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("config_management_list")
            .performScrollToNode(hasTestTag("config_card_usr_1"))
        composeRule.onNodeWithTag("config_card_usr_1").assertIsDisplayed()

        // Trigger Delete Selected -> opens explicit confirmation dialog
        composeRule.onNodeWithTag("config_management_list")
            .performScrollToNode(hasTestTag("bulk_action_bar"))
        composeRule.onNodeWithTag("delete_selected_configs_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("confirm_bulk_delete_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_bulk_delete_button").performClick()
        composeRule.waitForIdle()
        assertTrue(deleteSelectedTriggered)
    }

    @Test
    fun userSubscriptionScreenDisplaysPlansAndSubmitsHonestPendingPayment() {
        val (activeSub, historyEvent) = SubscriptionLifecycleEngine.activateAfterPaymentConfirmation(
            subscriptionId = "sub_alice",
            userId = "alice",
            userEmail = "alice@example.com",
            plan = SubscriptionPlanId.THREE_MONTHS,
            actorId = "admin",
            trustedServerNowMs = nowMs
        )
        val allocated = listOf(
            AllocatedEndpointRecord(
                allocationId = "alloc_1",
                userId = "alice",
                subscriptionId = "sub_alice",
                endpointId = "ep_1",
                endpointName = "Berlin Edge #1",
                country = "Germany",
                countryCode = "DE",
                host = "198.51.100.1",
                port = 443,
                protocol = "vless",
                credentialToken = "tok-1",
                rawConfig = "vless://tok-1@198.51.100.1:443#Berlin1",
                allocationStatus = "ACTIVE",
                healthy = true,
                latencyMs = 44
            )
        )

        val allocHistory = listOf(
            AllocationHistoryRecord(
                historyId = "hist_ui_1",
                userId = "alice",
                subscriptionId = "sub_alice",
                planId = SubscriptionPlanId.THREE_MONTHS.id,
                endpointId = "ep_1",
                endpointName = "Berlin Edge #1",
                host = "198.51.100.1",
                port = 443,
                protocol = "vless",
                action = "ALLOCATED",
                allocationVersion = 1,
                cycleKey = "2026-11-18",
                reason = "Initial cycle allocation",
                actorId = "admin"
            )
        )

        var submittedPlan: SubscriptionPlanId? = null
        var submittedRef = ""

        composeRule.setContent {
            TmsVpnTheme {
                UserSubscriptionScreen(
                    userProfile = UserProfile(
                        userId = "alice",
                        displayName = "Alice",
                        email = "alice@example.com",
                        subscriptionStatus = "active",
                        dataUsageBytes = 5_000_000.0,
                        dataLimitBytes = 50_000_000_000.0
                    ),
                    subscription = activeSub,
                    allocatedEndpoints = allocated,
                    allocationHistory = allocHistory,
                    history = listOf(historyEvent),
                    paymentRequests = emptyList(),
                    trustedNowMs = nowMs,
                    isOfflineCached = true,
                    isBusy = false,
                    isPersian = true,
                    onSubmitPlanRequest = { plan, _, ref, _ ->
                        submittedPlan = plan
                        submittedRef = ref
                    },
                    onConnectEndpoint = {}
                )
            }
        }

        composeRule.waitForIdle()
        assertEquals(0, composeRule.onAllNodesWithTag("subscription_offline_banner").fetchSemanticsNodes().size)
        composeRule.onNodeWithTag("current_subscription_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("subscription_status_badge", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNode(
            androidx.compose.ui.test.hasAnyAncestor(hasTestTag("subscription_status_badge")) and
                androidx.compose.ui.test.hasText("فعال", substring = true),
            useUnmergedTree = true
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("plan_card_plan_1m").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("plan_card_plan_3m").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("plan_card_plan_1y").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("allocated_endpoint_item_ep_1").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("allocation_history_section").performScrollTo().assertIsDisplayed()
        composeRule.onNode(
            androidx.compose.ui.test.hasAnyAncestor(hasTestTag("allocation_history_section")) and
                androidx.compose.ui.test.hasText("ALLOCATED", substring = true),
            useUnmergedTree = true
        ).assertIsDisplayed()

        // Submit an honest PENDING_PAYMENT request for 1Y plan
        composeRule.onNodeWithTag("select_plan_button_plan_1y").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("payment_request_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("payment_reference_input").performTextInput("PAY-REF-9001")
        composeRule.onNodeWithTag("submit_payment_request_button").performClick()
        composeRule.waitForIdle()

        assertEquals(SubscriptionPlanId.ONE_YEAR, submittedPlan)
        assertEquals("PAY-REF-9001", submittedRef)
    }

    @Test
    fun adminSubscriptionScreenRendersPaymentsInventoryShortagesAndManualRotation() {
        val pendingReq = PaymentRequestRecord(
            paymentId = "pay_1",
            userId = "bob",
            userEmail = "bob@example.com",
            planId = SubscriptionPlanId.ONE_MONTH.id,
            actionType = "NEW",
            referenceCode = "TRX-777",
            status = SubscriptionStatus.PENDING_PAYMENT.name
        )
        val endpoint = ManagedEndpoint(
            endpointId = "ep_de_1",
            name = "Munich VIP",
            country = "Germany",
            countryCode = "DE",
            host = "198.51.100.99",
            port = 443,
            protocol = "vless",
            enabled = true,
            healthy = true,
            priorityTier = 3
        )
        val shortage = InventoryShortageAlert(
            alertId = "short_1",
            userId = "bob",
            subscriptionId = "sub_bob",
            planId = SubscriptionPlanId.ONE_YEAR.id,
            requestedCount = 50,
            allocatedCount = 12,
            shortfallCount = 38,
            cycleKey = "2026-11-18",
            reason = "Pool needs 38 more healthy endpoints"
        )
        val log = RotationAuditLog(
            logId = "log_1",
            cycleKey = "2026-11-18",
            triggerMode = "MANUAL_ALL",
            triggeredByUid = "admin",
            targetUserCount = 2,
            rotatedUsersCount = 2,
            replacedEndpointsCount = 4,
            shortageCount = 1,
            status = "PARTIAL_SHORTAGE"
        )

        var approvedPaymentId = ""
        var manualAllTriggered = false

        composeRule.setContent {
            TmsVpnTheme {
                AdminSubscriptionScreen(
                    subscriptions = emptyList(),
                    endpointInventory = listOf(endpoint),
                    paymentRequests = listOf(pendingReq),
                    shortageAlerts = listOf(shortage),
                    rotationLogs = listOf(log),
                    selectedRotationUserIds = emptySet(),
                    trustedNowMs = nowMs,
                    isBusy = false,
                    isPersian = true,
                    onToggleSelectUser = {},
                    onSelectAllUsers = {},
                    onClearUserSelection = {},
                    onActivateSubscription = { _, _, _, payId -> approvedPaymentId = payId.orEmpty() },
                    onRejectPayment = { _, _ -> },
                    onRenewSubscription = {},
                    onChangePlan = { _, _ -> },
                    onUpdateStatus = { _, _, _ -> },
                    onUpsertEndpoint = {},
                    onImportFromNodes = {},
                    onToggleEndpointHealth = { _, _, _ -> },
                    onResolveShortage = {},
                    onRotateSingleUser = {},
                    onRotateSelectedUsers = {},
                    onRunDailyRotationAll = { manualAllTriggered = true }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("admin_subscription_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("admin_subscription_screen")
            .performScrollToNode(hasTestTag("approve_payment_pay_1"))
        composeRule.onNodeWithTag("approve_payment_pay_1").performClick()
        assertEquals("pay_1", approvedPaymentId)

        composeRule.onNodeWithTag("admin_subscription_screen")
            .performScrollToNode(hasTestTag("admin_run_daily_rotation_btn"))
        composeRule.onNodeWithTag("admin_run_daily_rotation_btn").performClick()
        assertTrue(manualAllTriggered)

        // Switch to Endpoint Pool tab
        composeRule.onNodeWithTag("admin_sub_tab_INVENTORY").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("admin_subscription_screen")
            .performScrollToNode(hasTestTag("admin_endpoint_inventory_section"))
        composeRule.onNodeWithTag("admin_endpoint_inventory_section").assertIsDisplayed()

        // Switch to Alerts & Logs tab
        composeRule.onNodeWithTag("admin_sub_tab_SHORTAGES_LOGS").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("admin_subscription_screen")
            .performScrollToNode(hasTestTag("admin_shortage_alerts_section"))
        composeRule.onNodeWithTag("admin_shortage_alerts_section").assertIsDisplayed()
        composeRule.onNodeWithTag("admin_subscription_screen")
            .performScrollToNode(hasTestTag("admin_rotation_logs_section"))
        composeRule.onNodeWithTag("admin_rotation_logs_section").assertIsDisplayed()
    }
}
