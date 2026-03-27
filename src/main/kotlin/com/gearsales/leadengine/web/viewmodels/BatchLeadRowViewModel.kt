package com.gearsales.leadengine.web.viewmodels

import com.gearsales.leadengine.domain.model.LeadRecord
import com.gearsales.leadengine.domain.service.WhatsAppMessageService

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
            )
        }
    }
}
