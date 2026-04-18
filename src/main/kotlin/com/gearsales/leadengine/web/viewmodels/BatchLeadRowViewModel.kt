package com.gearsales.leadengine.web.viewmodels

import com.gearsales.leadengine.domain.model.LeadRecord
import com.gearsales.leadengine.domain.service.WhatsAppMessageService
import com.gearsales.leadengine.web.dto.BatchCampaignRowDto

data class BatchLeadRowViewModel(
    val id: Long = 0L,
    val nomeLoja: String = "",
    val cidade: String = "",
    val estado: String = "",
    val telefoneNormalizado: String = "",
    val cnae: String = "",
    val score: Int = 0,
    val prioridade: String = "",
    val status: String = "",
    val detailUrl: String = "",
    val whatsappLink: String = "",
    val hasWhatsApp: Boolean = false,
    val campaignStatus: String = "Sem campanha",
    val campaignSentAt: String = "—",
    val campaignFailure: String = "—",
    val campaignFailureCategory: String = "—",
    val campaignScheduledAt: String = "—",
    val waMessageIdDisplay: String = "—",
    /** Texto completo para tooltip (Message ID). */
    val waMessageIdTitle: String = "",
    val respondeuWhatsApp: String = "Não",
) {
    companion object {
        fun from(record: LeadRecord): BatchLeadRowViewModel {
            val nome = record.nomeFantasia?.trim()?.takeIf { it.isNotEmpty() } ?: record.razaoSocial
            val msg = WhatsAppMessageService.buildDefaultMessage(
                nomeFantasia = record.nomeFantasia,
                razaoSocial = record.razaoSocial,
                cidade = record.cidade,
            )
            val link = WhatsAppMessageService.buildWhatsAppLink(record.telefoneNormalizado, msg) ?: ""
            val hasWa = record.telefoneNormalizado?.isNotBlank() == true
            val resp = if (record.respondeu) "Sim" else "Não"
            return BatchLeadRowViewModel(
                id = record.id,
                nomeLoja = nome,
                cidade = record.cidade.orEmpty(),
                estado = record.estado.orEmpty(),
                telefoneNormalizado = record.telefoneNormalizado.orEmpty(),
                cnae = record.cnae.orEmpty(),
                score = record.score,
                prioridade = record.prioridade,
                status = record.status,
                detailUrl = "/lead/${record.id}",
                whatsappLink = link,
                hasWhatsApp = hasWa,
                respondeuWhatsApp = resp,
            )
        }

        fun from(record: LeadRecord, campaign: BatchCampaignRowDto?): BatchLeadRowViewModel {
            val base = from(record)
            if (campaign == null) {
                return base.copy(
                    campaignStatus = "Sem campanha",
                    campaignSentAt = "—",
                    campaignFailure = "—",
                    campaignFailureCategory = "—",
                    campaignScheduledAt = "—",
                    waMessageIdDisplay = "—",
                    waMessageIdTitle = "",
                )
            }
            val fullId = campaign.waMessageId?.trim().orEmpty()
            val cat = campaign.failureCategory?.trim().orEmpty()
            return base.copy(
                campaignStatus = campaign.status,
                campaignSentAt = displayOrDash(campaign.sentAt),
                campaignFailure = displayOrDash(campaign.failureReason),
                campaignFailureCategory = cat.ifEmpty { "—" },
                campaignScheduledAt = displayOrDash(campaign.scheduledAt),
                waMessageIdDisplay = truncateWaId(campaign.waMessageId),
                waMessageIdTitle = fullId,
                respondeuWhatsApp = if (campaign.leadRespondeu) "Sim" else "Não",
            )
        }

        private fun displayOrDash(s: String?): String {
            val t = s?.trim().orEmpty()
            return t.ifEmpty { "—" }
        }

        private fun truncateWaId(id: String?): String {
            val t = id?.trim().orEmpty()
            if (t.isEmpty()) return "—"
            if (t.length <= 22) return t
            return t.take(20) + "…"
        }
    }
}
