package com.gearsales.leadengine.web.dto

import com.gearsales.leadengine.domain.model.WhatsappSettingsRecord
import kotlinx.serialization.Serializable

@Serializable
data class WhatsAppSettingsUpdateRequest(
    val phoneNumberId: String,
    val defaultTemplateName: String,
    val defaultTemplateLanguage: String,
    val dailySendLimit: Int,
    val sendDelayMinMinutes: Int,
    val sendDelayMaxMinutes: Int,
    val batchSize: Int = 20,
    val executionStartTime: String = "00:00",
    val executionEndTime: String = "23:59",
)

@Serializable
data class WhatsAppSettingsApiResponse(
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
    val createdAt: String,
    val updatedAt: String,
) {
    companion object {
        fun from(r: WhatsappSettingsRecord) = WhatsAppSettingsApiResponse(
            phoneNumberId = r.phoneNumberId,
            defaultTemplateName = r.defaultTemplateName,
            defaultTemplateLanguage = r.defaultTemplateLanguage,
            dailySendLimit = r.dailySendLimit,
            sendDelayMinMinutes = r.sendDelayMinMinutes,
            sendDelayMaxMinutes = r.sendDelayMaxMinutes,
            batchSize = r.batchSize,
            executionStartTime = r.executionStartTime,
            executionEndTime = r.executionEndTime,
            servicePaused = r.servicePaused,
            createdAt = r.createdAt.toString(),
            updatedAt = r.updatedAt.toString(),
        )
    }
}

@Serializable
data class WhatsAppOperationalDataResetRequest(
    val confirmPhrase: String,
)

@Serializable
data class WhatsAppSettingsValidationErrorResponse(
    val errors: List<String>,
)
