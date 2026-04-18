package com.gearsales.leadengine.domain.model

import java.time.LocalDateTime

data class LeadMessageCampaignRecord(
    val id: Long,
    val leadId: Long,
    val batchId: Long?,
    val templateName: String,
    val templateLanguage: String,
    val status: LeadCampaignStatus,
    val waMessageId: String?,
    val attemptNumber: Int,
    val followupStep: Int,
    val sentAt: LocalDateTime?,
    val deliveredAt: LocalDateTime?,
    val readAt: LocalDateTime?,
    val respondedAt: LocalDateTime?,
    val failedAt: LocalDateTime?,
    val failureReason: String?,
    val failureCategory: WhatsAppFailureCategory?,
    val scheduledAt: LocalDateTime?,
    val lastAttemptAt: LocalDateTime?,
    val processingStartedAt: LocalDateTime?,
    val nextFollowupAt: LocalDateTime?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

fun leadCampaignStatusFromStored(value: String): LeadCampaignStatus =
    LeadCampaignStatus.entries.find { it.name == value } ?: LeadCampaignStatus.PENDING
