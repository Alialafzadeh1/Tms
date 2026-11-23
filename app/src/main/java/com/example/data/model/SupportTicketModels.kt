package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class SupportTicketStatus(val titleFa: String, val titleEn: String) {
    OPEN("باز", "Open"),
    AI_TRIAGED("بررسی‌شده توسط NEXUS AI", "AI Triaged"),
    ADMIN_REPLIED("پاسخ پشتیبانی", "Admin Replied"),
    RESOLVED("حل‌شده", "Resolved"),
    CLOSED("بسته‌شده", "Closed");

    companion object {
        fun fromString(raw: String?): SupportTicketStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: OPEN
    }
}

enum class SupportTicketCategory(val titleFa: String, val titleEn: String) {
    CONNECTION("مشکل اتصال / فیلتر", "Connection & Bypass"),
    SUBSCRIPTION("اشتراک و پرداخت", "Subscription & Billing"),
    DEDICATED_IP("IP اختصاصی", "Dedicated IP"),
    SPEED_LATENCY("سرعت و پینگ", "Speed & Latency"),
    GENERAL("سوال عمومی", "General");

    companion object {
        fun fromString(raw: String?): SupportTicketCategory =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: GENERAL
    }
}

enum class SupportTicketPriority(val titleFa: String, val titleEn: String) {
    LOW("کم", "Low"),
    MEDIUM("متوسط", "Medium"),
    HIGH("بالا", "High"),
    URGENT("اضطراری", "Urgent");

    companion object {
        fun fromString(raw: String?): SupportTicketPriority =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: MEDIUM
    }
}

@Keep
@IgnoreExtraProperties
data class SupportTicket(
    @DocumentId val ticketId: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val subject: String = "",
    val category: String = SupportTicketCategory.GENERAL.name,
    val priority: String = SupportTicketPriority.MEDIUM.name,
    val status: String = SupportTicketStatus.OPEN.name,
    val description: String = "",
    val aiTriageSummary: String = "",
    val lastReplyText: String = "",
    val lastReplyByRole: String = "", // USER, ADMIN
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun statusEnum(): SupportTicketStatus = SupportTicketStatus.fromString(status)
    fun categoryEnum(): SupportTicketCategory = SupportTicketCategory.fromString(category)
    fun priorityEnum(): SupportTicketPriority = SupportTicketPriority.fromString(priority)
}

@Keep
@IgnoreExtraProperties
data class SupportTicketReply(
    @DocumentId val replyId: String = "",
    val ticketId: String = "",
    val userId: String = "",
    val senderUid: String = "",
    val senderRole: String = "USER", // USER, ADMIN
    val message: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
