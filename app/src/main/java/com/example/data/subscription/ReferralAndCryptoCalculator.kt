package com.example.data.subscription

import com.example.data.model.ReferralMilestone
import com.example.data.model.ReferralRewardRequest
import com.example.data.model.ReferralRewardStatus
import java.text.NumberFormat
import java.util.Locale

/**
 * Pure domain calculator for cumulative independent referral milestones (max 12.5 GB),
 * trial cooldown formatting, Toman formatting, and release file size formatting.
 */
object ReferralAndCryptoCalculator {

    private data class MilestoneRule(
        val threshold: Int,
        val rewardGb: Double,
        val titleFa: String,
        val titleEn: String
    )

    private val RULES = listOf(
        MilestoneRule(
            threshold = 2,
            rewardGb = 1.0,
            titleFa = "۲ دعوت تاییدشده → +۱ گیگابایت",
            titleEn = "2 Qualified Referrals → +1.0 GB"
        ),
        MilestoneRule(
            threshold = 4,
            rewardGb = 1.5,
            titleFa = "۴ دعوت تاییدشده → +۱.۵ گیگابایت",
            titleEn = "4 Qualified Referrals → +1.5 GB"
        ),
        MilestoneRule(
            threshold = 10,
            rewardGb = 10.0,
            titleFa = "۱۰ دعوت تاییدشده → +۱۰ گیگابایت",
            titleEn = "10 Qualified Referrals → +10.0 GB"
        )
    )

    const val MAX_CUMULATIVE_GB = 12.5

    fun evaluateMilestones(
        qualifiedCount: Int,
        existingRequests: List<ReferralRewardRequest>
    ): List<ReferralMilestone> {
        val safeCount = qualifiedCount.coerceAtLeast(0)
        return RULES.map { rule ->
            val unlocked = safeCount >= rule.threshold
            val matchingRequest = existingRequests
                .filter { it.milestoneThreshold == rule.threshold }
                .maxByOrNull { it.createdAtMs }
            val status = matchingRequest?.status
            val canClaim = unlocked && (status == null || status == ReferralRewardStatus.REJECTED)
            ReferralMilestone(
                thresholdCount = rule.threshold,
                rewardGb = rule.rewardGb,
                titleFa = rule.titleFa,
                titleEn = rule.titleEn,
                isUnlocked = unlocked,
                rewardStatus = status,
                canClaim = canClaim
            )
        }
    }

    fun calculateTotalUnlockedGb(qualifiedCount: Int): Double {
        val safeCount = qualifiedCount.coerceAtLeast(0)
        return RULES.filter { safeCount >= it.threshold }.sumOf { it.rewardGb }
    }

    fun formatToman(amount: Long, isPersian: Boolean): String {
        val formatted = NumberFormat.getNumberInstance(Locale.US).format(amount)
        return if (isPersian) "$formatted تومان" else "$formatted Toman"
    }

    fun formatDurationCountdown(seconds: Long, isPersian: Boolean): String {
        val safe = seconds.coerceAtLeast(0L)
        val days = safe / 86_400L
        val hours = (safe % 86_400L) / 3600L
        val mins = (safe % 3600L) / 60L
        return if (isPersian) {
            when {
                days > 0 -> "$days روز و $hours ساعت"
                hours > 0 -> "$hours ساعت و $mins دقیقه"
                else -> "$mins دقیقه"
            }
        } else {
            when {
                days > 0 -> "${days}d ${hours}h"
                hours > 0 -> "${hours}h ${mins}m"
                else -> "${mins}m"
            }
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "--"
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        return if (mb >= 1.0) {
            String.format(Locale.US, "%.2f MB", mb)
        } else {
            String.format(Locale.US, "%.1f KB", bytes.toDouble() / 1024.0)
        }
    }
}
