package com.gearsales.leadengine.web.dto

import kotlinx.serialization.Serializable

@Serializable
data class SendBatchCampaignResponse(
    val batchId: Long,
    val dailyLimit: Int,
    val alreadySentToday: Int,
    val remainingQuotaToday: Int,
    val totalPendingInBatch: Int,
    /** Campanhas que acabaram de receber [scheduledAt] nesta chamada. */
    val newlyScheduledCount: Int,
    /** Pendentes que já tinham horário na fila antes + recém-agendadas. */
    val alreadyScheduledPendingCount: Int,
    val firstScheduledAt: String? = null,
    val lastScheduledAt: String? = null,
    val summaryMessage: String,
)
