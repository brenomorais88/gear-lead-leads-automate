package com.gearsales.leadengine.web.viewmodels

import com.gearsales.leadengine.domain.model.LeadRecord
import com.gearsales.leadengine.domain.service.WhatsAppMessageService
import java.time.format.DateTimeFormatter

data class LeadDetailViewModel(
    val id: Long = 0L,
    val cnpj: String = "",
    val razaoSocial: String = "",
    val nomeFantasia: String = "",
    val telefoneOriginal: String = "",
    val telefoneNormalizado: String = "",
    val email: String = "",
    val endereco: String = "",
    val cidade: String = "",
    val estado: String = "",
    val dataAbertura: String = "",
    val cnae: String = "",
    val situacao: String = "",
    val porte: String = "",
    val socio: String = "",
    val capitalSocial: String = "",
    val tipo: String = "",
    val score: Int = 0,
    val prioridade: String = "",
    val status: String = "",
    val jaFoiSorteado: Boolean = false,
    val primeiroSorteioEm: String = "",
    val quantidadeTentativas: Int = 0,
    val respondeu: Boolean = false,
    val interessado: Boolean = false,
    val observacoes: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
    val whatsappMessage: String = "",
    val whatsappLink: String = "",
    val hasWhatsApp: Boolean = false,
) {
    companion object {
        private val fmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

        fun from(record: LeadRecord): LeadDetailViewModel {
            val msg = WhatsAppMessageService.buildDefaultMessage(
                nomeFantasia = record.nomeFantasia,
                razaoSocial = record.razaoSocial,
                cidade = record.cidade,
            )
            val link = WhatsAppMessageService.buildWhatsAppLink(record.telefoneNormalizado, msg) ?: ""
            val hasWa = record.telefoneNormalizado?.isNotBlank() == true
            return LeadDetailViewModel(
                id = record.id,
                cnpj = record.cnpj,
                razaoSocial = record.razaoSocial,
                nomeFantasia = record.nomeFantasia.orEmpty(),
                telefoneOriginal = record.telefoneOriginal.orEmpty(),
                telefoneNormalizado = record.telefoneNormalizado.orEmpty(),
                email = record.email.orEmpty(),
                endereco = record.endereco.orEmpty(),
                cidade = record.cidade.orEmpty(),
                estado = record.estado.orEmpty(),
                dataAbertura = record.dataAbertura.orEmpty(),
                cnae = record.cnae.orEmpty(),
                situacao = record.situacao.orEmpty(),
                porte = record.porte.orEmpty(),
                socio = record.socio.orEmpty(),
                capitalSocial = record.capitalSocial.orEmpty(),
                tipo = record.tipo.orEmpty(),
                score = record.score,
                prioridade = record.prioridade,
                status = record.status,
                jaFoiSorteado = record.jaFoiSorteado,
                primeiroSorteioEm = record.primeiroSorteioEm?.format(fmt).orEmpty(),
                quantidadeTentativas = record.quantidadeTentativas,
                respondeu = record.respondeu,
                interessado = record.interessado,
                observacoes = record.observacoes.orEmpty(),
                createdAt = record.createdAt.format(fmt),
                updatedAt = record.updatedAt.format(fmt),
                whatsappMessage = msg,
                whatsappLink = link,
                hasWhatsApp = hasWa,
            )
        }
    }
}
