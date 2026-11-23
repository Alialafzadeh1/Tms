package com.example.data.subscription

import com.example.data.model.AllocatedEndpointRecord
import com.example.data.model.AllocationHistoryRecord
import com.example.data.model.ConfigOrigin
import com.example.data.model.ManagedEndpoint
import com.example.data.model.PaymentStatus
import com.example.data.model.SubscriptionPlanId
import com.example.data.model.SubscriptionStatus
import com.example.data.model.UserSubscription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionAndAllocationEngineTest {

    private val baseTrustedNowMs = 1_795_000_000_000L // Fixed server epoch

    private fun makeEndpoints(
        count: Int,
        priorityTier: Int = 3,
        protocol: String = "vless",
        healthy: Boolean = true,
        enabled: Boolean = true
    ): List<ManagedEndpoint> = (1..count).map { idx ->
        ManagedEndpoint(
            endpointId = "ep_${priorityTier}_$idx",
            name = "Dedicated Node $priorityTier-$idx",
            country = "Germany",
            countryCode = "DE",
            host = "198.51.100.$idx",
            port = 443,
            protocol = protocol,
            transport = "ws",
            security = "tls",
            sni = "cdn.tmsvpn.net",
            pathOrServiceName = "/ws",
            enabled = enabled,
            healthy = healthy,
            priorityTier = priorityTier,
            latencyMs = 20 + idx,
            stabilityScore = 95
        )
    }

    @Test
    fun planSpecificationsMatchExactDurationsDailyLimitsAndPriorities() {
        assertEquals(30, SubscriptionPlanId.ONE_MONTH.durationDays)
        assertEquals(5, SubscriptionPlanId.ONE_MONTH.dailyAllocationLimit)
        assertEquals(1, SubscriptionPlanId.ONE_MONTH.priorityLevel)

        assertEquals(90, SubscriptionPlanId.THREE_MONTHS.durationDays)
        assertEquals(15, SubscriptionPlanId.THREE_MONTHS.dailyAllocationLimit)
        assertEquals(2, SubscriptionPlanId.THREE_MONTHS.priorityLevel)

        assertEquals(365, SubscriptionPlanId.ONE_YEAR.durationDays)
        assertEquals(50, SubscriptionPlanId.ONE_YEAR.dailyAllocationLimit)
        assertEquals(3, SubscriptionPlanId.ONE_YEAR.priorityLevel)
    }

    @Test
    fun subscriptionLifecycleEnforcesTrustedServerTimeAndNeverActivatesUnconfirmedPlans() {
        val unconfirmed = UserSubscription(
            subscriptionId = "sub_u1",
            userId = "u1",
            planId = SubscriptionPlanId.ONE_MONTH.id,
            planDurationDays = 30,
            endpointAllocationLimit = 5,
            startDate = SubscriptionLifecycleEngine.timestampOf(baseTrustedNowMs),
            expirationDate = SubscriptionLifecycleEngine.timestampOf(baseTrustedNowMs + 30 * SubscriptionLifecycleEngine.MILLIS_PER_DAY),
            status = SubscriptionStatus.PENDING_PAYMENT.name,
            paymentStatus = PaymentStatus.UNPAID.name
        )
        assertFalse(SubscriptionLifecycleEngine.isEntitled(unconfirmed, baseTrustedNowMs))

        val (activated, actEvent) = SubscriptionLifecycleEngine.activateAfterPaymentConfirmation(
            subscriptionId = "sub_u1",
            userId = "u1",
            userEmail = "u1@test.com",
            plan = SubscriptionPlanId.ONE_MONTH,
            actorId = "admin_1",
            trustedServerNowMs = baseTrustedNowMs
        )
        assertTrue(SubscriptionLifecycleEngine.isEntitled(activated, baseTrustedNowMs))
        assertEquals(30, SubscriptionLifecycleEngine.remainingDays(activated, baseTrustedNowMs))
        assertEquals("ACTIVATED", actEvent.action)

        // Advance server time by 31 days -> expired regardless of device clock
        val after31Days = baseTrustedNowMs + 31L * SubscriptionLifecycleEngine.MILLIS_PER_DAY
        assertFalse(SubscriptionLifecycleEngine.isEntitled(activated, after31Days))
        val (expiredSub, expEvent) = SubscriptionLifecycleEngine.evaluateExpiration(activated, after31Days)
        assertEquals(SubscriptionStatus.EXPIRED.name, expiredSub.status)
        assertNotNull(expEvent)

        // Upgrade from 1M to 3M
        val (upgraded, upEvent) = SubscriptionLifecycleEngine.changePlan(
            existing = activated,
            newPlan = SubscriptionPlanId.THREE_MONTHS,
            actorId = "admin_1",
            trustedServerNowMs = baseTrustedNowMs + 5L * SubscriptionLifecycleEngine.MILLIS_PER_DAY
        )
        assertEquals(SubscriptionPlanId.THREE_MONTHS.id, upgraded.planId)
        assertEquals(15, upgraded.endpointAllocationLimit)
        assertEquals("UPGRADED", upEvent.action)

        // Renew extends from current expiration
        val (renewed, renewEvent) = SubscriptionLifecycleEngine.renew(
            existing = upgraded,
            plan = SubscriptionPlanId.THREE_MONTHS,
            actorId = "admin_1",
            trustedServerNowMs = baseTrustedNowMs + 10L * SubscriptionLifecycleEngine.MILLIS_PER_DAY
        )
        assertTrue(SubscriptionLifecycleEngine.remainingDays(renewed, baseTrustedNowMs + 10L * SubscriptionLifecycleEngine.MILLIS_PER_DAY) >= 170)
        assertEquals("RENEWED", renewEvent.action)
    }

    @Test
    fun concurrentBatchAllocationEnforcesUserExclusivityAndPrivateCredentialIsolation() {
        val inventory = makeEndpoints(25, priorityTier = 3)
        val (subHigh, _) = SubscriptionLifecycleEngine.activateAfterPaymentConfirmation(
            subscriptionId = "sub_high",
            userId = "user_high",
            userEmail = "high@test.com",
            plan = SubscriptionPlanId.THREE_MONTHS, // 15 IPs
            actorId = "admin",
            trustedServerNowMs = baseTrustedNowMs
        )
        val (subBasic, _) = SubscriptionLifecycleEngine.activateAfterPaymentConfirmation(
            subscriptionId = "sub_basic",
            userId = "user_basic",
            userEmail = "basic@test.com",
            plan = SubscriptionPlanId.ONE_MONTH, // 5 IPs
            actorId = "admin",
            trustedServerNowMs = baseTrustedNowMs
        )

        val result = DedicatedIpAllocationEngine.runDailyRotationBatch(
            subscriptions = listOf(subBasic, subHigh),
            inventory = inventory,
            existingAllocationsByUser = emptyMap(),
            credentialsByUser = mapOf(
                "user_high" to "cred-token-high-111",
                "user_basic" to "cred-token-basic-222"
            ),
            triggeredByUid = "admin",
            triggerMode = "MANUAL_ALL",
            trustedServerNowMs = baseTrustedNowMs,
            forceManual = false
        )

        val highAllocs = result.allocationsByUser["user_high"].orEmpty()
        val basicAllocs = result.allocationsByUser["user_basic"].orEmpty()

        assertEquals(15, highAllocs.size)
        assertEquals(5, basicAllocs.size)
        assertTrue(result.shortageAlerts.isEmpty())

        // Zero overlapping endpoints across concurrent users
        val highIds = highAllocs.map { it.endpointId }.toSet()
        val basicIds = basicAllocs.map { it.endpointId }.toSet()
        assertTrue((highIds intersect basicIds).isEmpty())

        // User-specific credential preserved & converted to SUBSCRIPTION_ASSIGNED VpnConfig
        assertTrue(highAllocs.all { it.credentialToken == "cred-token-high-111" })
        assertTrue(basicAllocs.all { it.credentialToken == "cred-token-basic-222" })
        assertEquals(ConfigOrigin.SUBSCRIPTION_ASSIGNED.key, basicAllocs.first().toVpnConfig().origin)
    }

    @Test
    fun dailyRotationIsIdempotentWithinSameUtcCycle() {
        val inventory = makeEndpoints(10, priorityTier = 2)
        val (sub, _) = SubscriptionLifecycleEngine.activateAfterPaymentConfirmation(
            subscriptionId = "sub_1",
            userId = "u1",
            userEmail = "u1@test.com",
            plan = SubscriptionPlanId.ONE_MONTH,
            actorId = "admin",
            trustedServerNowMs = baseTrustedNowMs
        )

        val firstRun = DedicatedIpAllocationEngine.allocateOrRotateForUser(
            subscription = sub,
            inventory = inventory,
            existingAllocations = emptyList(),
            userCredentialToken = "token-u1",
            trustedServerNowMs = baseTrustedNowMs
        )
        assertFalse(firstRun.skippedIdempotent)
        assertEquals(5, firstRun.activeAllocations.size)
        val firstVersion = firstRun.updatedSubscription.currentAllocationVersion

        // Re-run in same UTC day without force -> strictly idempotent
        val secondRun = DedicatedIpAllocationEngine.allocateOrRotateForUser(
            subscription = firstRun.updatedSubscription,
            inventory = firstRun.updatedInventory,
            existingAllocations = firstRun.activeAllocations,
            userCredentialToken = "token-u1",
            trustedServerNowMs = baseTrustedNowMs + 60_000L,
            forceManualRotation = false
        )
        assertTrue(secondRun.skippedIdempotent)
        assertEquals(firstVersion, secondRun.updatedSubscription.currentAllocationVersion)
        assertEquals(firstRun.activeAllocations.map { it.endpointId }, secondRun.activeAllocations.map { it.endpointId })
    }

    @Test
    fun unhealthyReplacementAndShortageAlertsDoNotFabricateEndpointsAndPreserveActiveTunnel() {
        val inventory = makeEndpoints(5, priorityTier = 3).map { ep ->
            if (ep.endpointId == "ep_3_5") ep.copy(healthy = false) else ep
        }
        val (sub, _) = SubscriptionLifecycleEngine.activateAfterPaymentConfirmation(
            subscriptionId = "sub_1",
            userId = "u1",
            userEmail = "u1@test.com",
            plan = SubscriptionPlanId.ONE_MONTH, // Needs 5, pool only has 4 healthy
            actorId = "admin",
            trustedServerNowMs = baseTrustedNowMs
        )
        val prevAlloc: List<AllocatedEndpointRecord> = emptyList()

        val outcome = DedicatedIpAllocationEngine.allocateOrRotateForUser(
            subscription = sub,
            inventory = inventory,
            existingAllocations = prevAlloc,
            userCredentialToken = "token-u1",
            activeConnectedEndpointId = "ep_3_1",
            trustedServerNowMs = baseTrustedNowMs
        )

        // Only 4 healthy endpoints assigned; broken ep_3_5 excluded; shortage reported honestly
        assertEquals(4, outcome.activeAllocations.size)
        assertFalse(outcome.activeAllocations.any { it.endpointId == "ep_3_5" })
        assertNotNull(outcome.shortageAlert)
        assertEquals(5, outcome.shortageAlert?.requestedCount)
        assertEquals(4, outcome.shortageAlert?.allocatedCount)
        assertEquals(1, outcome.shortageAlert?.shortfallCount)

        // Verify active tunnel preservation when next day runs and ep_3_1 is connected
        val nextDayMs = baseTrustedNowMs + SubscriptionLifecycleEngine.MILLIS_PER_DAY
        val extraHealthy = outcome.updatedInventory + makeEndpoints(3, priorityTier = 2)
        val rotatedOutcome = DedicatedIpAllocationEngine.allocateOrRotateForUser(
            subscription = outcome.updatedSubscription,
            inventory = extraHealthy,
            existingAllocations = outcome.activeAllocations,
            userCredentialToken = "token-u1",
            activeConnectedEndpointId = "ep_3_1",
            trustedServerNowMs = nextDayMs
        )
        assertTrue(rotatedOutcome.activeAllocations.any { it.endpointId == "ep_3_1" })
        assertEquals(5, rotatedOutcome.activeAllocations.size)
        assertNull(rotatedOutcome.shortageAlert)

        // Verify failure cooldown temporarily excludes repeatedly failing endpoint
        var failing = inventory.first()
        failing = DedicatedIpAllocationEngine.recordEndpointFailure(failing, baseTrustedNowMs)
        failing = DedicatedIpAllocationEngine.recordEndpointFailure(failing, baseTrustedNowMs + 1000)
        assertTrue((failing.cooldownUntil?.toDate()?.time ?: 0L) > baseTrustedNowMs)
    }

    @Test
    fun oneYearPlanAllocatesUpToFiftyEndpointsWithAllocationHistoryAndZeroDoubleAssignment() {
        val inventory = makeEndpoints(60, priorityTier = 3)
        val (sub1Y, _) = SubscriptionLifecycleEngine.activateAfterPaymentConfirmation(
            subscriptionId = "sub_1y",
            userId = "vip_user",
            userEmail = "vip@test.com",
            plan = SubscriptionPlanId.ONE_YEAR, // 50 IPs/day
            actorId = "admin_1",
            trustedServerNowMs = baseTrustedNowMs
        )
        val (sub1M, _) = SubscriptionLifecycleEngine.activateAfterPaymentConfirmation(
            subscriptionId = "sub_1m",
            userId = "std_user",
            userEmail = "std@test.com",
            plan = SubscriptionPlanId.ONE_MONTH, // 5 IPs/day
            actorId = "admin_1",
            trustedServerNowMs = baseTrustedNowMs
        )

        val batch = DedicatedIpAllocationEngine.runDailyRotationBatch(
            subscriptions = listOf(sub1M, sub1Y),
            inventory = inventory,
            existingAllocationsByUser = emptyMap(),
            credentialsByUser = mapOf("vip_user" to "tok-vip", "std_user" to "tok-std"),
            triggeredByUid = "admin_1",
            triggerMode = "SCHEDULED_DAILY",
            trustedServerNowMs = baseTrustedNowMs,
            forceManual = false
        )

        val vipAllocs = batch.allocationsByUser["vip_user"].orEmpty()
        val stdAllocs = batch.allocationsByUser["std_user"].orEmpty()
        assertEquals(50, vipAllocs.size)
        assertEquals(5, stdAllocs.size)
        assertTrue(DedicatedIpAllocationEngine.verifyZeroDoubleAssignment(batch.allocationsByUser))

        val vipHistory: List<AllocationHistoryRecord> = batch.allocationHistoryByUser["vip_user"].orEmpty()
        assertEquals(50, vipHistory.count { it.action == "ALLOCATED" })
        assertTrue(vipHistory.all { it.userId == "vip_user" && it.actorId == "admin_1" })

        // Verify revocation emits REVOKED_EXPIRED allocationHistoryEvents
        val expiredSub = sub1Y.copy(status = SubscriptionStatus.SUSPENDED.name)
        val revokeOutcome = DedicatedIpAllocationEngine.allocateOrRotateForUser(
            subscription = expiredSub,
            inventory = batch.updatedInventory,
            existingAllocations = vipAllocs,
            userCredentialToken = "tok-vip",
            trustedServerNowMs = baseTrustedNowMs + 60_000L,
            forceManualRotation = true,
            actorId = "admin_1"
        )
        assertTrue(revokeOutcome.activeAllocations.isEmpty())
        assertEquals(50, revokeOutcome.allocationHistoryEvents.count { it.action == "REVOKED_EXPIRED" })
    }
}
