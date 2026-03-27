package com.gearsales.leadengine.domain.model

import java.time.LocalDateTime

data class LeadInteractionRecord(
    val id: Long,
    val leadId: Long,
    val interactionType: String,
    val result: String?,
    val note: String?,
    val createdAt: LocalDateTime,
)
