package com.gearsales.leadengine.domain.service

import com.gearsales.leadengine.config.WhatsAppAppConfig
import com.gearsales.leadengine.database.repositories.BatchRepository
import com.gearsales.leadengine.database.repositories.CampaignLeadJoinRow
import com.gearsales.leadengine.database.repositories.LeadMessageCampaignRepository
import com.gearsales.leadengine.database.repositories.LeadRepository
import com.gearsales.leadengine.domain.model.LeadCampaignStatus
import com.gearsales.leadengine.web.dto.BatchCampaignRowDto
import com.gearsales.leadengine.web.dto.BatchCampaignsApiResponse
import com.gearsales.leadengine.web.dto.BatchCampaignSummaryCountsDto
import com.gearsales.leadengine.web.dto.BatchLeadCampaignItemDto
import com.gearsales.leadengine.web.dto.BatchWhatsAppSummaryResponse
import com.gearsales.leadengine.web.dto.CampaignListRowDto
import com.gearsales.leadengine.web.dto.CampaignsListApiResponse
import com.gearsales.leadengine.web.dto.WhatsAppDashboardSummaryResponse
import com.gearsales.leadengine.web.dto.WhatsAppEngineStatusResponse
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class WhatsAppCampaignReadService(
    private val whatsappConfig: WhatsAppAppConfig,
    private val campaignRepository: LeadMessageCampaignRepository,
    private val batchRepository: BatchRepository,
    private val leadRepository: LeadRepository,
    private val engineOperationalService: WhatsAppEngineOperationalService,
) {
    private val zone: ZoneId = ZoneId.systemDefault()

    fun batchCampaigns(batchId: Long): BatchCampaignsApiResponse {
        val rows = campaignRepository.listCampaignsWithLeadForBatch(batchId)
        return BatchCampaignsApiResponse(
            batchId = batchId,
            totalCampaigns = rows.size,
            campaigns = rows.map { it.toBatchCampaignRowDto() },
        )
    }

    fun listCampaigns(
        status: LeadCampaignStatus?,
        batchId: Long?,
        lojaContains: String?,
        limit: Int,
        offset: Int,
    ): CampaignsListApiResponse {
        val (rows, total) =
            campaignRepository.listCampaignsWithLeadFiltered(status, batchId, lojaContains, limit, offset)
        return CampaignsListApiResponse(
            campaigns = rows.map { it.toCampaignListRowDto() },
            total = total,
            limit = limit,
            offset = offset,
        )
    }

    fun dashboardSummary(): WhatsAppDashboardSummaryResponse {
        val eff = whatsappConfig.effective()
        val today = LocalDate.now(zone)
        val engineStatus: WhatsAppEngineStatusResponse = engineOperationalService.getStatus()
        return WhatsAppDashboardSummaryResponse(
            dailyLimit = engineStatus.dailyLimit,
            sentToday = engineStatus.sentToday,
            remainingQuotaToday = engineStatus.remainingQuotaToday,
            pendingCampaigns = campaignRepository.countCampaignsWithStatus(LeadCampaignStatus.PENDING) +
                campaignRepository.countCampaignsWithStatus(LeadCampaignStatus.SENDING),
            failedCampaigns = campaignRepository.countCampaignsWithStatus(LeadCampaignStatus.FAILED) +
                campaignRepository.countCampaignsWithStatus(LeadCampaignStatus.STOPPED),
            respondedCampaigns = campaignRepository.countCampaignsWithStatus(LeadCampaignStatus.RESPONDED),
            totalCampaigns = campaignRepository.countCampaigns(),
            totalCampaignsToday = campaignRepository.countCampaignsCreatedOnLocalDate(today, zone),
            activeTemplateName = eff.defaultTemplateName,
            activeTemplateLanguage = eff.defaultTemplateLanguage,
            engineStatus = engineStatus,
        )
    }

    fun batchWhatsappSummary(batchId: Long): BatchWhatsAppSummaryResponse? {
        val batch = batchRepository.findById(batchId) ?: return null
        val campaigns = campaignRepository.findByBatchId(batchId)
        val summary = summarizeBatchCampaigns(campaigns.map { it.status })
        val leadIds = batchRepository.findLeadIdsForBatch(batchId)
        val leads = leadRepository.findByIdsOrdered(leadIds)
        val latestByLead = campaigns
            .groupBy { it.leadId }
            .mapValues { (_, list) -> list.maxBy { it.id } }
        val leadItems = leads.map { lead ->
            val c = latestByLead[lead.id]
            BatchLeadCampaignItemDto(
                leadId = lead.id,
                lojaNome = lojaNome(lead.nomeFantasia, lead.razaoSocial),
                telefone = telefoneDisplay(lead.telefoneNormalizado, lead.telefoneOriginal),
                campaignId = c?.id,
                campaignStatus = c?.status?.name,
            )
        }
        return BatchWhatsAppSummaryResponse(
            batchId = batch.id,
            createdAt = batch.createdAt.toString(),
            totalLeads = batch.totalLeads,
            summary = summary,
            leads = leadItems,
        )
    }

    private fun summarizeBatchCampaigns(statuses: List<LeadCampaignStatus>): BatchCampaignSummaryCountsDto {
        val pending = setOf(LeadCampaignStatus.PENDING, LeadCampaignStatus.SENDING)
        val sent = setOf(LeadCampaignStatus.SENT, LeadCampaignStatus.DELIVERED, LeadCampaignStatus.READ)
        val failed = setOf(LeadCampaignStatus.FAILED, LeadCampaignStatus.STOPPED)
        return BatchCampaignSummaryCountsDto(
            campaignsCreated = statuses.size,
            pending = statuses.count { it in pending },
            sent = statuses.count { it in sent },
            failed = statuses.count { it in failed },
            responded = statuses.count { it == LeadCampaignStatus.RESPONDED },
        )
    }

    private fun CampaignLeadJoinRow.toBatchCampaignRowDto() = BatchCampaignRowDto(
        campaignId = campaignId,
        leadId = leadId,
        lojaNome = lojaNome(nomeFantasia, razaoSocial),
        telefone = telefoneDisplay(telefoneNormalizado, telefoneOriginal),
        templateName = templateName,
        templateLanguage = templateLanguage,
        status = status.name,
        waMessageId = waMessageId,
        attemptNumber = attemptNumber,
        followupStep = followupStep,
        sentAt = sentAt.toApiString(),
        deliveredAt = deliveredAt.toApiString(),
        readAt = readAt.toApiString(),
        respondedAt = respondedAt.toApiString(),
        failedAt = failedAt.toApiString(),
        failureReason = failureReason,
        failureCategory = failureCategory?.name,
        scheduledAt = scheduledAt.toApiString(),
        lastAttemptAt = lastAttemptAt.toApiString(),
        leadStatus = leadStatus,
        leadRespondeu = leadRespondeu,
        leadInteressado = leadInteressado,
    )

    private fun CampaignLeadJoinRow.toCampaignListRowDto() = CampaignListRowDto(
        campaignId = campaignId,
        batchId = batchId,
        leadId = leadId,
        lojaNome = lojaNome(nomeFantasia, razaoSocial),
        telefone = telefoneDisplay(telefoneNormalizado, telefoneOriginal),
        templateName = templateName,
        templateLanguage = templateLanguage,
        status = status.name,
        sentAt = sentAt.toApiString(),
        deliveredAt = deliveredAt.toApiString(),
        readAt = readAt.toApiString(),
        respondedAt = respondedAt.toApiString(),
        failedAt = failedAt.toApiString(),
        failureReason = failureReason,
        failureCategory = failureCategory?.name,
        scheduledAt = scheduledAt.toApiString(),
        lastAttemptAt = lastAttemptAt.toApiString(),
    )

    private fun lojaNome(nomeFantasia: String?, razaoSocial: String): String =
        nomeFantasia?.trim()?.takeIf { it.isNotEmpty() } ?: razaoSocial.trim()

    private fun telefoneDisplay(normalizado: String?, original: String?): String =
        normalizado?.trim()?.takeIf { it.isNotEmpty() }
            ?: original?.trim().orEmpty()

    private fun LocalDateTime?.toApiString(): String? = this?.toString()
}
