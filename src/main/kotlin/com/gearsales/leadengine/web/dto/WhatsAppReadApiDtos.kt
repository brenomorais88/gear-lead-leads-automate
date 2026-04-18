package com.gearsales.leadengine.web.dto

import kotlinx.serialization.Serializable

@Serializable
data class BatchCampaignsApiResponse(
    val batchId: Long,
    val totalCampaigns: Int,
    val campaigns: List<BatchCampaignRowDto>,
)

@Serializable
data class BatchCampaignRowDto(
    val campaignId: Long,
    val leadId: Long,
    val lojaNome: String,
    val telefone: String,
    val templateName: String,
    val templateLanguage: String,
    val status: String,
    val waMessageId: String?,
    val attemptNumber: Int,
    val followupStep: Int,
    val sentAt: String?,
    val deliveredAt: String?,
    val readAt: String?,
    val respondedAt: String?,
    val failedAt: String?,
    val failureReason: String?,
    val failureCategory: String?,
    val scheduledAt: String?,
    val lastAttemptAt: String?,
    val leadStatus: String,
    val leadRespondeu: Boolean,
    val leadInteressado: Boolean,
)

@Serializable
data class CampaignsListApiResponse(
    val campaigns: List<CampaignListRowDto>,
    val total: Long,
    val limit: Int,
    val offset: Int,
)

@Serializable
data class CampaignListRowDto(
    val campaignId: Long,
    val batchId: Long?,
    val leadId: Long,
    val lojaNome: String,
    val telefone: String,
    val templateName: String,
    val templateLanguage: String,
    val status: String,
    val sentAt: String?,
    val deliveredAt: String?,
    val readAt: String?,
    val respondedAt: String?,
    val failedAt: String?,
    val failureReason: String?,
    val failureCategory: String?,
    val scheduledAt: String?,
    val lastAttemptAt: String?,
)

@Serializable
data class WhatsAppDashboardSummaryResponse(
    val dailyLimit: Int,
    val sentToday: Long,
    val remainingQuotaToday: Int,
    val pendingCampaigns: Long,
    val failedCampaigns: Long,
    val respondedCampaigns: Long,
    val totalCampaigns: Long,
    val totalCampaignsToday: Long,
    val activeTemplateName: String,
    val activeTemplateLanguage: String,
    /** Mesmo payload que GET /whatsapp/engine-status (fonte única de verdade operacional). */
    val engineStatus: WhatsAppEngineStatusResponse,
)

@Serializable
data class BatchWhatsAppSummaryResponse(
    val batchId: Long,
    val createdAt: String,
    val totalLeads: Int,
    val summary: BatchCampaignSummaryCountsDto,
    val leads: List<BatchLeadCampaignItemDto>,
)

@Serializable
data class BatchCampaignSummaryCountsDto(
    val campaignsCreated: Int,
    val pending: Int,
    val sent: Int,
    val failed: Int,
    val responded: Int,
)

@Serializable
data class BatchLeadCampaignItemDto(
    val leadId: Long,
    val lojaNome: String,
    val telefone: String,
    val campaignId: Long?,
    val campaignStatus: String?,
)
