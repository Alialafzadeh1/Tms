package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

/**
 * Distinct classification for VPN configurations so deletion, sync, and rotation
 * respect strict ownership and safety permissions.
 */
enum class ConfigOrigin(
    val key: String,
    val titleFa: String,
    val titleEn: String,
    val userDeletable: Boolean,
    val adminBulkDeletable: Boolean
) {
    USER_IMPORTED(
        key = "USER_IMPORTED",
        titleFa = "واردشده توسط کاربر",
        titleEn = "User Imported",
        userDeletable = true,
        adminBulkDeletable = true
    ),
    SUBSCRIPTION_ASSIGNED(
        key = "SUBSCRIPTION_ASSIGNED",
        titleFa = "اندپوینت اشتراک اختصاصی",
        titleEn = "Subscription Endpoint",
        userDeletable = false,
        adminBulkDeletable = true
    ),
    SYSTEM_MANAGED(
        key = "SYSTEM_MANAGED",
        titleFa = "سیستمی محافظت‌شده",
        titleEn = "System Managed",
        userDeletable = false,
        adminBulkDeletable = false
    );

    companion object {
        fun fromString(raw: String?): ConfigOrigin {
            return entries.firstOrNull { it.key.equals(raw?.trim(), ignoreCase = true) }
                ?: USER_IMPORTED
        }
    }
}

enum class SubscriptionPlanId(
    val id: String,
    val titleFa: String,
    val titleEn: String,
    val durationDays: Int,
    val dailyAllocationLimit: Int,
    val priorityLevel: Int,
    val priorityLabelFa: String,
    val priorityLabelEn: String,
    val serverTierFa: String,
    val serverTierEn: String
) {
    ONE_MONTH(
        id = "plan_1m",
        titleFa = "پلن ۱ ماهه (۳۰ روزه)",
        titleEn = "1 Month Plan",
        durationDays = 30,
        dailyAllocationLimit = 5,
        priorityLevel = 1,
        priorityLabelFa = "اولویت بالا (High)",
        priorityLabelEn = "High Priority",
        serverTierFa = "اندپوینت‌های سالم پرمیوم",
        serverTierEn = "Premium healthy endpoints"
    ),
    THREE_MONTHS(
        id = "plan_3m",
        titleFa = "پلن ۳ ماهه (۹۰ روزه)",
        titleEn = "3 Months Plan",
        durationDays = 90,
        dailyAllocationLimit = 15,
        priorityLevel = 2,
        priorityLabelFa = "اولویت بسیار بالا (Higher)",
        priorityLabelEn = "Higher Priority",
        serverTierFa = "اندپوینت‌های سالم پرمیوم",
        serverTierEn = "Premium healthy endpoints"
    ),
    ONE_YEAR(
        id = "plan_1y",
        titleFa = "پلن ۱ ساله (۳۶۵ روزه)",
        titleEn = "1 Year Plan",
        durationDays = 365,
        dailyAllocationLimit = 50,
        priorityLevel = 3,
        priorityLabelFa = "بالاترین اولویت (Highest)",
        priorityLabelEn = "Highest Priority",
        serverTierFa = "بالاترین اولویت نودهای سالم",
        serverTierEn = "Highest-priority healthy endpoints"
    );

    companion object {
        fun fromId(raw: String?): SubscriptionPlanId {
            return entries.firstOrNull {
                it.id.equals(raw?.trim(), ignoreCase = true) ||
                    it.name.equals(raw?.trim(), ignoreCase = true)
            } ?: ONE_MONTH
        }
    }
}

enum class SubscriptionStatus(
    val titleFa: String,
    val titleEn: String
) {
    PENDING_PAYMENT("در انتظار تایید پرداخت", "Pending Payment"),
    ACTIVE("فعال", "Active"),
    EXPIRED("منقضی شده", "Expired"),
    SUSPENDED("معلق شده", "Suspended"),
    CANCELLED("لغو شده", "Cancelled");

    companion object {
        fun fromString(raw: String?): SubscriptionStatus {
            return entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) }
                ?: PENDING_PAYMENT
        }
    }
}

enum class PaymentStatus(
    val titleFa: String,
    val titleEn: String
) {
    UNPAID("پرداخت نشده", "Unpaid"),
    PENDING_VERIFICATION("در انتظار بررسی سرور", "Pending Verification"),
    CONFIRMED("تایید شده توسط سرور", "Confirmed"),
    FAILED("ناموفق", "Failed"),
    REFUNDED("مسترد شده", "Refunded");

    companion object {
        fun fromString(raw: String?): PaymentStatus {
            return entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) }
                ?: UNPAID
        }
    }
}

/**
 * Central subscription record stored in `/subscriptions/{subscriptionId}`.
 * Only trusted backend / verified administrators may transition entitlements.
 */
@Keep
@IgnoreExtraProperties
data class UserSubscription(
    @DocumentId val subscriptionId: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val planId: String = SubscriptionPlanId.ONE_MONTH.id,
    val planDurationDays: Int = SubscriptionPlanId.ONE_MONTH.durationDays,
    val endpointAllocationLimit: Int = SubscriptionPlanId.ONE_MONTH.dailyAllocationLimit,
    val startDate: Timestamp? = null,
    val expirationDate: Timestamp? = null,
    val status: String = SubscriptionStatus.PENDING_PAYMENT.name,
    val paymentStatus: String = PaymentStatus.UNPAID.name,
    val lastDailyRotation: Timestamp? = null,
    val lastRotationCycleKey: String = "",
    val currentAllocationVersion: Int = 0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun planEnum(): SubscriptionPlanId = SubscriptionPlanId.fromId(planId)
    fun statusEnum(): SubscriptionStatus = SubscriptionStatus.fromString(status)
    fun paymentStatusEnum(): PaymentStatus = PaymentStatus.fromString(paymentStatus)
}

/**
 * Central endpoint pool item stored in `/endpoint_inventory/{endpointId}`.
 * Contains zero user credentials (never expose UUIDs or passwords in shared inventory).
 */
@Keep
@IgnoreExtraProperties
data class ManagedEndpoint(
    @DocumentId val endpointId: String = "",
    val name: String = "",
    val country: String = "",
    val countryCode: String = "",
    val host: String = "",
    val port: Int = 443,
    val protocol: String = "vless",
    val transport: String = "ws",
    val security: String = "tls",
    val sni: String = "",
    val pathOrServiceName: String = "",
    val enabled: Boolean = true,
    val healthy: Boolean = true,
    val priorityTier: Int = 1, // 1 = High, 2 = Higher, 3 = Highest
    val latencyMs: Int = -1,
    val stabilityScore: Int = 95,
    val assignedUserId: String = "",
    val assignedSubscriptionId: String = "",
    val allocationExpiresAt: Timestamp? = null,
    val failureCount: Int = 0,
    val cooldownUntil: Timestamp? = null,
    val lastHealthCheckAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun isUnassignedOrOwnedBy(uid: String): Boolean {
        return assignedUserId.isBlank() || assignedUserId == uid
    }
}

/**
 * User-isolated private endpoint allocation record stored in
 * `/users/{userId}/allocated_endpoints/{allocationId}`.
 * Holds the user's private credential token safely isolated by user ownership.
 */
@Keep
@IgnoreExtraProperties
data class AllocatedEndpointRecord(
    @DocumentId val allocationId: String = "",
    val userId: String = "",
    val subscriptionId: String = "",
    val endpointId: String = "",
    val endpointName: String = "",
    val country: String = "",
    val countryCode: String = "",
    val host: String = "",
    val port: Int = 443,
    val protocol: String = "vless",
    val transport: String = "ws",
    val security: String = "tls",
    val sni: String = "",
    val pathOrServiceName: String = "",
    val credentialToken: String = "",
    val rawConfig: String = "",
    val allocationVersion: Int = 1,
    val cycleKey: String = "",
    val allocationStatus: String = "ACTIVE", // ACTIVE, ROTATED_OUT, FAILED, REVOKED
    val healthy: Boolean = true,
    val latencyMs: Int = -1,
    val allocatedAt: Timestamp? = null,
    val expiresAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toVpnConfig(): VpnConfig {
        return VpnConfig(
            id = "sub_${subscriptionId}_${endpointId}",
            userId = userId,
            name = endpointName.ifBlank { "$countryCode • $host" },
            protocol = protocol.lowercase(),
            server = host,
            port = port,
            uuidOrPassword = credentialToken,
            transport = transport,
            security = security,
            sni = sni,
            pathOrServiceName = pathOrServiceName,
            rawConfig = rawConfig,
            pingMs = latencyMs,
            status = if (healthy) "online" else "offline",
            isFavorite = false,
            createdAt = allocatedAt,
            updatedAt = updatedAt,
            origin = ConfigOrigin.SUBSCRIPTION_ASSIGNED.key,
            subscriptionId = subscriptionId,
            endpointId = endpointId,
            allocationVersion = allocationVersion
        )
    }
}

@Keep
@IgnoreExtraProperties
data class SubscriptionHistoryEvent(
    @DocumentId val eventId: String = "",
    val subscriptionId: String = "",
    val userId: String = "",
    val action: String = "", // CREATED, ACTIVATED, RENEWED, UPGRADED, DOWNGRADED, SUSPENDED, CANCELLED, EXPIRED, ROTATED
    val fromPlanId: String = "",
    val toPlanId: String = "",
    val statusAfter: String = "",
    val actorId: String = "",
    val details: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

/**
 * Immutable audit record tracking dedicated endpoint assignments, rotations,
 * failure replacements, cooldown exclusions, and revocations under
 * `/users/{userId}/allocation_history/{historyId}`.
 */
@Keep
@IgnoreExtraProperties
data class AllocationHistoryRecord(
    @DocumentId val historyId: String = "",
    val userId: String = "",
    val subscriptionId: String = "",
    val planId: String = "",
    val endpointId: String = "",
    val endpointName: String = "",
    val host: String = "",
    val port: Int = 443,
    val protocol: String = "vless",
    val action: String = "ALLOCATED", // ALLOCATED, ROTATED_OUT, REPLACED_FAILED, REVOKED_EXPIRED, COOLDOWN_EXCLUDED
    val allocationVersion: Int = 1,
    val cycleKey: String = "",
    val reason: String = "",
    val actorId: String = "SYSTEM",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PaymentRequestRecord(
    @DocumentId val paymentId: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val subscriptionId: String = "",
    val planId: String = SubscriptionPlanId.ONE_MONTH.id,
    val actionType: String = "NEW", // NEW, RENEW, UPGRADE, DOWNGRADE
    val provider: String = "SERVER_VERIFICATION",
    val referenceCode: String = "",
    val status: String = SubscriptionStatus.PENDING_PAYMENT.name,
    val note: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    val verifiedAt: Timestamp? = null,
    val verifiedBy: String = ""
)

@Keep
@IgnoreExtraProperties
data class InventoryShortageAlert(
    @DocumentId val alertId: String = "",
    val userId: String = "",
    val subscriptionId: String = "",
    val planId: String = "",
    val requestedCount: Int = 0,
    val allocatedCount: Int = 0,
    val shortfallCount: Int = 0,
    val cycleKey: String = "",
    val reason: String = "",
    val resolved: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class RotationAuditLog(
    @DocumentId val logId: String = "",
    val cycleKey: String = "",
    val triggerMode: String = "MANUAL_SINGLE", // SCHEDULED_DAILY, MANUAL_SINGLE, MANUAL_BATCH, MANUAL_ALL, FAILOVER_REPLACE
    val triggeredByUid: String = "",
    val targetUserCount: Int = 0,
    val rotatedUsersCount: Int = 0,
    val replacedEndpointsCount: Int = 0,
    val shortageCount: Int = 0,
    val status: String = "SUCCESS", // SUCCESS, PARTIAL_SHORTAGE, SKIPPED_IDEMPOTENT, FAILED
    val details: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
