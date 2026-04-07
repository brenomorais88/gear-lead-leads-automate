package com.gearsales.leadengine.web.dto

import com.gearsales.leadengine.domain.model.WhatsAppEngineStatus
import kotlinx.serialization.Serializable

@Serializable
data class WhatsAppEngineStatusResponse(
    val currentStatus: WhatsAppEngineStatus,
    val statusMessage: String,
    val workerEnabled: Boolean = true,
    val servicePaused: Boolean = false,
    val now: String,
    val dailyLimit: Int,
    val sentToday: Long,
    val remainingQuotaToday: Int,
    val pendingCampaigns: Long,
    val scheduledCampaigns: Long,
    val processingCampaigns: Long,
    val failedCampaigns: Long,
    val sendDelayMinMinutes: Int,
    val sendDelayMaxMinutes: Int,
    val workerPollIntervalSeconds: Long,
    val defaultTemplateName: String? = null,
    val defaultTemplateLanguage: String? = null,
    val phoneNumberIdMasked: String? = null,
    val nextScheduledAt: String? = null,
    val nextCampaignId: Long? = null,
    val nextLeadId: Long? = null,
    val nextBatchId: Long? = null,
    val currentProcessingCampaignId: Long? = null,
    val currentProcessingLeadId: Long? = null,
    val currentProcessingBatchId: Long? = null,
    val waitingDelayMinutesRemaining: Long? = null,
    val misconfigurationReason: String? = null,
)
