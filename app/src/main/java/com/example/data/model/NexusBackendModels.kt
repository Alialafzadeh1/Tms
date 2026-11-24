package com.example.data.model

/**
 * Typed response wrapper for Cloudflare Worker / D1 / R2 API calls.
 */
sealed interface BackendResult<out T> {
    data class Success<T>(val data: T) : BackendResult<T>
    data class HttpError(
        val httpCode: Int,
        val errorCode: String,
        val message: String
    ) : BackendResult<Nothing>
    data class NetworkError(val message: String) : BackendResult<Nothing>
    data object Unauthenticated : BackendResult<Nothing>
}

data class BackendSessionSync(
    val uid: String = "",
    val email: String = "",
    val isAdmin: Boolean = false,
    val verifiedRole: String? = null,
    val telegramLinked: Boolean = false,
    val telegramId: String? = null,
    val telegramUsername: String? = null,
    val referralCode: String = "",
    val referralShareUrl: String = "",
    val serverTimeMs: Long = 0L,
    val activePlanId: String? = null,
    val activeStatus: String? = null,
    val activeExpiresAtMs: Long? = null,
    val trialStatus: FreeTrialStatus? = null
)

enum class CryptoAsset(
    val id: String,
    val symbol: String,
    val network: String,
    val titleFa: String,
    val titleEn: String
) {
    USDT_TRC20(
        id = "USDT_TRC20",
        symbol = "USDT",
        network = "TRC20",
        titleFa = "تتر (USDT - شبکه TRC20)",
        titleEn = "Tether USDT (TRC20)"
    ),
    TRX(
        id = "TRX",
        symbol = "TRX",
        network = "TRON",
        titleFa = "ترون (TRX - شبکه بومی)",
        titleEn = "TRON (TRX Native)"
    );

    companion object {
        fun fromString(raw: String?): CryptoAsset {
            return entries.firstOrNull {
                it.id.equals(raw?.trim(), ignoreCase = true) ||
                    it.symbol.equals(raw?.trim(), ignoreCase = true)
            } ?: USDT_TRC20
        }
    }
}

enum class CryptoInvoiceStatus(
    val titleFa: String,
    val titleEn: String
) {
    PENDING("در انتظار واریز", "Pending Payment"),
    CONFIRMING("در حال تایید شبکه", "Confirming on Chain"),
    CONFIRMED("تایید و فعال‌شده", "Confirmed"),
    EXPIRED("منقضی شده", "Expired"),
    UNDERPAID("کمتر از مبلغ فاکتور", "Underpaid"),
    OVERPAID("بیشتر از مبلغ فاکتور", "Overpaid"),
    REJECTED("رد شده", "Rejected"),
    AMBIGUOUS("نیازمند بررسی دستی", "Ambiguous");

    companion object {
        fun fromString(raw: String?): CryptoInvoiceStatus {
            return entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: PENDING
        }
    }
}

data class CryptoPaymentInvoice(
    val invoiceId: String = "",
    val userId: String = "",
    val planId: String = "plan_1m",
    val asset: CryptoAsset = CryptoAsset.USDT_TRC20,
    val network: String = "TRC20",
    val depositAddress: String = "",
    val expectedAmountCrypto: String = "",
    val expectedAmountToman: Long = 0L,
    val lockedRateToman: Long = 0L,
    val status: CryptoInvoiceStatus = CryptoInvoiceStatus.PENDING,
    val txHash: String = "",
    val receivedAmountCrypto: String = "",
    val expiresAtMs: Long = 0L,
    val createdAtMs: Long = 0L,
    val statusMessage: String = ""
)

data class ReferralMilestone(
    val thresholdCount: Int,
    val rewardGb: Double,
    val titleFa: String,
    val titleEn: String,
    val isUnlocked: Boolean,
    val rewardStatus: ReferralRewardStatus? = null,
    val canClaim: Boolean = false
)

enum class ReferralRewardStatus(
    val titleFa: String,
    val titleEn: String
) {
    PENDING("در انتظار بررسی مدیر", "Pending Admin Review"),
    APPROVED("تایید و واریزشده", "Approved"),
    REJECTED("رد شده", "Rejected");

    companion object {
        fun fromString(raw: String?): ReferralRewardStatus {
            return entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: PENDING
        }
    }
}

data class ReferralItem(
    val referralId: String = "",
    val referredDisplay: String = "",
    val botStarted: Boolean = false,
    val apkDownloadClicked: Boolean = false,
    val appLoggedIn: Boolean = false,
    val qualified: Boolean = false,
    val createdAtMs: Long = 0L
)

data class ReferralRewardRequest(
    val requestId: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val milestoneThreshold: Int = 2,
    val rewardGb: Double = 1.0,
    val status: ReferralRewardStatus = ReferralRewardStatus.PENDING,
    val adminNote: String = "",
    val createdAtMs: Long = 0L,
    val reviewedAtMs: Long? = null
)

data class ReferralDashboardData(
    val referralCode: String = "",
    val referralShareUrl: String = "",
    val totalInvitedCount: Int = 0,
    val qualifiedCount: Int = 0,
    val totalUnlockedGb: Double = 0.0,
    val maxPossibleGb: Double = 12.5,
    val milestones: List<ReferralMilestone> = emptyList(),
    val referrals: List<ReferralItem> = emptyList(),
    val rewardRequests: List<ReferralRewardRequest> = emptyList()
)

data class FreeTrialStatus(
    val eligible: Boolean = false,
    val active: Boolean = false,
    val cooldownRemainingSeconds: Long = 0L,
    val activatedAtMs: Long? = null,
    val expiresAtMs: Long? = null,
    val nextEligibleAtMs: Long? = null,
    val cooldownDays: Int = 7,
    val durationHours: Int = 24,
    val lastReason: String = ""
)

data class AppReleaseInfo(
    val releaseId: String = "",
    val versionName: String = "",
    val versionCode: Int = 0,
    val releaseNotes: String = "",
    val fileSizeBytes: Long = 0L,
    val uploadedAtMs: Long = 0L,
    val downloadCount: Long = 0L,
    val downloadUrl: String = "",
    val isPublished: Boolean = false,
    val sha256: String = ""
)
