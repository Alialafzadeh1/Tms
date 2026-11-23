package com.example.data.subscription

import com.example.data.model.PaymentStatus
import com.example.data.model.SubscriptionHistoryEvent
import com.example.data.model.SubscriptionPlanId
import com.example.data.model.SubscriptionStatus
import com.example.data.model.UserSubscription
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.ceil

/**
 * Pure server-time anchored subscription lifecycle engine.
 * Device local wall-clock time is NEVER trusted for entitlement decisions;
 * callers supply [trustedServerNowMs] synced from server Timestamp + monotonic elapsedRealtime delta.
 */
object SubscriptionLifecycleEngine {

    const val MILLIS_PER_DAY = 86_400_000L

    fun utcCycleKey(trustedServerNowMs: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return fmt.format(Date(trustedServerNowMs))
    }

    fun isEntitled(
        subscription: UserSubscription?,
        trustedServerNowMs: Long
    ): Boolean {
        if (subscription == null) return false
        if (subscription.statusEnum() != SubscriptionStatus.ACTIVE) return false
        if (subscription.paymentStatusEnum() != PaymentStatus.CONFIRMED) return false
        val expMs = subscription.expirationDate?.toDate()?.time ?: return false
        return expMs > trustedServerNowMs
    }

    fun remainingDays(
        subscription: UserSubscription?,
        trustedServerNowMs: Long
    ): Int {
        if (subscription == null) return 0
        val status = subscription.statusEnum()
        if (status == SubscriptionStatus.CANCELLED || status == SubscriptionStatus.SUSPENDED) {
            return 0
        }
        val expMs = subscription.expirationDate?.toDate()?.time ?: return 0
        val diff = expMs - trustedServerNowMs
        if (diff <= 0L) return 0
        return ceil(diff.toDouble() / MILLIS_PER_DAY.toDouble()).toInt().coerceAtLeast(1)
    }

    fun evaluateExpiration(
        subscription: UserSubscription,
        trustedServerNowMs: Long
    ): Pair<UserSubscription, SubscriptionHistoryEvent?> {
        val expMs = subscription.expirationDate?.toDate()?.time ?: return subscription to null
        if (subscription.statusEnum() == SubscriptionStatus.ACTIVE && expMs <= trustedServerNowMs) {
            val expired = subscription.copy(
                status = SubscriptionStatus.EXPIRED.name,
                updatedAt = timestampOf(trustedServerNowMs)
            )
            val event = SubscriptionHistoryEvent(
                subscriptionId = expired.subscriptionId,
                userId = expired.userId,
                action = "EXPIRED",
                fromPlanId = expired.planId,
                toPlanId = expired.planId,
                statusAfter = SubscriptionStatus.EXPIRED.name,
                actorId = "SYSTEM",
                details = "Subscription reached server expiration timestamp",
                createdAt = timestampOf(trustedServerNowMs)
            )
            return expired to event
        }
        return subscription to null
    }

    fun activateAfterPaymentConfirmation(
        subscriptionId: String,
        userId: String,
        userEmail: String,
        plan: SubscriptionPlanId,
        actorId: String,
        trustedServerNowMs: Long,
        existingSubscription: UserSubscription? = null
    ): Pair<UserSubscription, SubscriptionHistoryEvent> {
        val startMs = trustedServerNowMs
        val expMs = startMs + plan.durationDays * MILLIS_PER_DAY
        val version = (existingSubscription?.currentAllocationVersion ?: 0)
        val updated = UserSubscription(
            subscriptionId = subscriptionId,
            userId = userId,
            userEmail = userEmail,
            planId = plan.id,
            planDurationDays = plan.durationDays,
            endpointAllocationLimit = plan.dailyAllocationLimit,
            startDate = timestampOf(startMs),
            expirationDate = timestampOf(expMs),
            status = SubscriptionStatus.ACTIVE.name,
            paymentStatus = PaymentStatus.CONFIRMED.name,
            lastDailyRotation = existingSubscription?.lastDailyRotation,
            lastRotationCycleKey = existingSubscription?.lastRotationCycleKey ?: "",
            currentAllocationVersion = version,
            createdAt = existingSubscription?.createdAt ?: timestampOf(startMs),
            updatedAt = timestampOf(trustedServerNowMs)
        )
        val event = SubscriptionHistoryEvent(
            subscriptionId = subscriptionId,
            userId = userId,
            action = "ACTIVATED",
            fromPlanId = existingSubscription?.planId ?: "",
            toPlanId = plan.id,
            statusAfter = SubscriptionStatus.ACTIVE.name,
            actorId = actorId,
            details = "Activated ${plan.titleEn} (${plan.durationDays}d, ${plan.dailyAllocationLimit} endpoints/day)",
            createdAt = timestampOf(trustedServerNowMs)
        )
        return updated to event
    }

    fun renew(
        existing: UserSubscription,
        plan: SubscriptionPlanId = existing.planEnum(),
        actorId: String,
        trustedServerNowMs: Long
    ): Pair<UserSubscription, SubscriptionHistoryEvent> {
        val currentExpMs = existing.expirationDate?.toDate()?.time ?: 0L
        val baseMs = maxOf(currentExpMs, trustedServerNowMs)
        val newExpMs = baseMs + plan.durationDays * MILLIS_PER_DAY
        val newStart = existing.startDate ?: timestampOf(trustedServerNowMs)

        val renewed = existing.copy(
            planId = plan.id,
            planDurationDays = plan.durationDays,
            endpointAllocationLimit = plan.dailyAllocationLimit,
            startDate = newStart,
            expirationDate = timestampOf(newExpMs),
            status = SubscriptionStatus.ACTIVE.name,
            paymentStatus = PaymentStatus.CONFIRMED.name,
            updatedAt = timestampOf(trustedServerNowMs)
        )
        val event = SubscriptionHistoryEvent(
            subscriptionId = existing.subscriptionId,
            userId = existing.userId,
            action = "RENEWED",
            fromPlanId = existing.planId,
            toPlanId = plan.id,
            statusAfter = SubscriptionStatus.ACTIVE.name,
            actorId = actorId,
            details = "Renewed ${plan.titleEn} (+${plan.durationDays}d)",
            createdAt = timestampOf(trustedServerNowMs)
        )
        return renewed to event
    }

    fun changePlan(
        existing: UserSubscription,
        newPlan: SubscriptionPlanId,
        actorId: String,
        trustedServerNowMs: Long
    ): Pair<UserSubscription, SubscriptionHistoryEvent> {
        val oldPlan = existing.planEnum()
        val isUpgrade = newPlan.priorityLevel >= oldPlan.priorityLevel &&
            newPlan.dailyAllocationLimit >= oldPlan.dailyAllocationLimit

        val currentExpMs = existing.expirationDate?.toDate()?.time ?: trustedServerNowMs
        val remainingMs = (currentExpMs - trustedServerNowMs).coerceAtLeast(0L)
        val adjustedExpMs = if (isUpgrade) {
            trustedServerNowMs + maxOf(remainingMs, newPlan.durationDays * MILLIS_PER_DAY)
        } else {
            // Downgrade preserves remaining term or caps at existing expiration without fabricating extra time
            if (remainingMs > 0L) currentExpMs else trustedServerNowMs + newPlan.durationDays * MILLIS_PER_DAY
        }

        val updated = existing.copy(
            planId = newPlan.id,
            planDurationDays = newPlan.durationDays,
            endpointAllocationLimit = newPlan.dailyAllocationLimit,
            expirationDate = timestampOf(adjustedExpMs),
            status = SubscriptionStatus.ACTIVE.name,
            paymentStatus = PaymentStatus.CONFIRMED.name,
            updatedAt = timestampOf(trustedServerNowMs)
        )
        val action = if (isUpgrade) "UPGRADED" else "DOWNGRADED"
        val event = SubscriptionHistoryEvent(
            subscriptionId = existing.subscriptionId,
            userId = existing.userId,
            action = action,
            fromPlanId = oldPlan.id,
            toPlanId = newPlan.id,
            statusAfter = SubscriptionStatus.ACTIVE.name,
            actorId = actorId,
            details = "$action from ${oldPlan.id} (${oldPlan.dailyAllocationLimit}/day) to ${newPlan.id} (${newPlan.dailyAllocationLimit}/day)",
            createdAt = timestampOf(trustedServerNowMs)
        )
        return updated to event
    }

    fun updateStatus(
        existing: UserSubscription,
        newStatus: SubscriptionStatus,
        actorId: String,
        reason: String,
        trustedServerNowMs: Long
    ): Pair<UserSubscription, SubscriptionHistoryEvent> {
        val updated = existing.copy(
            status = newStatus.name,
            updatedAt = timestampOf(trustedServerNowMs)
        )
        val action = when (newStatus) {
            SubscriptionStatus.SUSPENDED -> "SUSPENDED"
            SubscriptionStatus.CANCELLED -> "CANCELLED"
            SubscriptionStatus.EXPIRED -> "EXPIRED"
            SubscriptionStatus.ACTIVE -> "ACTIVATED"
            SubscriptionStatus.PENDING_PAYMENT -> "PENDING_PAYMENT"
        }
        val event = SubscriptionHistoryEvent(
            subscriptionId = existing.subscriptionId,
            userId = existing.userId,
            action = action,
            fromPlanId = existing.planId,
            toPlanId = existing.planId,
            statusAfter = newStatus.name,
            actorId = actorId,
            details = reason.ifBlank { "Status updated to ${newStatus.name}" },
            createdAt = timestampOf(trustedServerNowMs)
        )
        return updated to event
    }

    fun timestampOf(epochMs: Long): Timestamp {
        val seconds = epochMs / 1000L
        val nanos = ((epochMs % 1000L) * 1_000_000L).toInt().coerceAtLeast(0)
        return Timestamp(seconds, nanos)
    }
}
