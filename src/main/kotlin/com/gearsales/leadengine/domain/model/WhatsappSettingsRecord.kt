package com.gearsales.leadengine.domain.model

import java.time.LocalDateTime

data class WhatsappSettingsRecord(
    val id: Int,
    val phoneNumberId: String,
    val defaultTemplateName: String,
    val defaultTemplateLanguage: String,
    val dailySendLimit: Int,
    val sendDelayMinMinutes: Int,
    val sendDelayMaxMinutes: Int,
    val batchSize: Int,
    val executionStartTime: String,
    val executionEndTime: String,
    val servicePaused: Boolean,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)
