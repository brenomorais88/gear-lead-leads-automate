package com.gearsales.leadengine.domain.service

import com.gearsales.leadengine.config.WhatsAppAppConfig
import com.gearsales.leadengine.database.repositories.BatchRepository
import com.gearsales.leadengine.database.repositories.LeadInteractionRepository
import com.gearsales.leadengine.database.repositories.LeadMessageCampaignRepository
import com.gearsales.leadengine.database.repositories.LeadRepository
import com.gearsales.leadengine.domain.model.LeadInteractionTypes
import com.gearsales.leadengine.domain.model.LeadStatus
import com.gearsales.leadengine.web.dto.PrepareBatchSendResponse
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

object PrepareBatchSkipReason {
    const val MISSING_PHONE = "MISSING_PHONE"
    const val INVALID_NUMBER_FLAG = "INVALID_NUMBER_FLAG"
    const val LEAD_STATUS_NUMERO_INVALIDO = "LEAD_STATUS_NUMERO_INVALIDO"
    const val DUPLICATE_ACTIVE_CAMPAIGN = "DUPLICATE_ACTIVE_CAMPAIGN"
    const val LEAD_NOT_FOUND = "LEAD_NOT_FOUND"
}

class PrepareBatchCampaignService(
    private val whatsappConfig: WhatsAppAppConfig,
    private val batchRepository: BatchRepository,
    private val leadRepository: LeadRepository,
    private val campaignRepository: LeadMessageCampaignRepository,
    private val interactionRepository: LeadInteractionRepository,
) {

    fun prepare(batchId: Long): PrepareBatchSendResponse {
        val eff = whatsappConfig.effective()
        return transaction {
        val leadIds = batchRepository.findLeadIdsForBatch(batchId)
        val total = leadIds.size
        val templateName = eff.defaultTemplateName
        val templateLanguage = eff.defaultTemplateLanguage
        val now = LocalDateTime.now()
        var created = 0
        val reasons = mutableMapOf<String, Int>()

        fun bump(reason: String) {
            reasons[reason] = (reasons[reason] ?: 0) + 1
        }

        fun recordSystemSkip(leadId: Long, reason: String) {
            interactionRepository.insert(
                leadId = leadId,
                interactionType = LeadInteractionTypes.SYSTEM,
                result = reason,
                note = "Lote $batchId: campanha WhatsApp não preparada",
                metadataJson = """{"batchId":$batchId,"reason":"$reason"}""",
            )
        }

        for (leadId in leadIds) {
            val lead = leadRepository.findById(leadId)
            if (lead == null) {
                bump(PrepareBatchSkipReason.LEAD_NOT_FOUND)
                continue
            }
            if (lead.status == LeadStatus.NUMERO_INVALIDO.name) {
                bump(PrepareBatchSkipReason.LEAD_STATUS_NUMERO_INVALIDO)
                recordSystemSkip(lead.id, PrepareBatchSkipReason.LEAD_STATUS_NUMERO_INVALIDO)
                continue
            }
            if (!lead.numeroValido) {
                bump(PrepareBatchSkipReason.INVALID_NUMBER_FLAG)
                recordSystemSkip(lead.id, PrepareBatchSkipReason.INVALID_NUMBER_FLAG)
                continue
            }
            val digits = lead.telefoneNormalizado.orEmpty().filter { it.isDigit() }
            if (digits.isBlank()) {
                bump(PrepareBatchSkipReason.MISSING_PHONE)
                recordSystemSkip(lead.id, PrepareBatchSkipReason.MISSING_PHONE)
                continue
            }
            if (campaignRepository.hasActiveCampaignForLeadBatchTemplate(leadId, batchId, templateName)) {
                bump(PrepareBatchSkipReason.DUPLICATE_ACTIVE_CAMPAIGN)
                continue
            }
            campaignRepository.createPending(leadId, batchId, templateName, templateLanguage, now)
            created++
        }

        val skipped = total - created
        PrepareBatchSendResponse(
            batchId = batchId,
            totalLeadsNoLote = total,
            campaignsCreated = created,
            skipped = skipped,
            skippedReasons = reasons,
        )
        }
    }
}
