package com.gearsales.leadengine.web.viewmodels

import com.gearsales.leadengine.domain.model.LeadInteractionRecord
import com.gearsales.leadengine.domain.model.LeadInteractionTypes
import java.time.format.DateTimeFormatter

data class LeadInteractionViewModel(
    val createdAt: String,
    val tipo: String,
    val resultado: String?,
    val nota: String?,
) {
    companion object {
        private val fmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

        fun from(record: LeadInteractionRecord): LeadInteractionViewModel {
            val tipoLabel = when (record.interactionType) {
                LeadInteractionTypes.STATUS_CHANGE -> "Mudança de status"
                LeadInteractionTypes.EDIT_LEAD -> "Edição manual"
                LeadInteractionTypes.OBSERVATION -> "Observações"
                else -> record.interactionType
            }
            return LeadInteractionViewModel(
                createdAt = record.createdAt.format(fmt),
                tipo = tipoLabel,
                resultado = record.result,
                nota = record.note,
            )
        }
    }
}
