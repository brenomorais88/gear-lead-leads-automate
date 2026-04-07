package com.gearsales.leadengine.domain.service

import com.gearsales.leadengine.database.repositories.WhatsAppSettingsRepository
import com.gearsales.leadengine.domain.model.WhatsappSettingsRecord
import com.gearsales.leadengine.web.dto.WhatsAppSettingsUpdateRequest
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

object WhatsAppOperationalSettingsValidator {
    fun validateUpdate(
        phoneNumberId: String,
        defaultTemplateName: String,
        defaultTemplateLanguage: String,
        dailySendLimit: Int,
        sendDelayMinMinutes: Int,
        sendDelayMaxMinutes: Int,
    ): List<String> {
        val errors = mutableListOf<String>()
        if (phoneNumberId.isBlank()) errors.add("Phone Number ID não pode ser vazio.")
        if (defaultTemplateName.isBlank()) errors.add("Nome do template não pode ser vazio.")
        if (defaultTemplateLanguage.isBlank()) errors.add("Idioma do template não pode ser vazio.")
        if (dailySendLimit <= 0) errors.add("Limite diário deve ser maior que zero.")
        if (sendDelayMinMinutes < 0) errors.add("Delay mínimo deve ser maior ou igual a zero.")
        if (sendDelayMaxMinutes < sendDelayMinMinutes) {
            errors.add("Delay máximo deve ser maior ou igual ao delay mínimo.")
        }
        return errors
    }

    fun validateUpdate(req: WhatsAppSettingsUpdateRequest): List<String> = validateUpdate(
        phoneNumberId = req.phoneNumberId,
        defaultTemplateName = req.defaultTemplateName,
        defaultTemplateLanguage = req.defaultTemplateLanguage,
        dailySendLimit = req.dailySendLimit,
        sendDelayMinMinutes = req.sendDelayMinMinutes,
        sendDelayMaxMinutes = req.sendDelayMaxMinutes,
    )
}

sealed class WhatsAppSettingsUpdateResult {
    data class Ok(val record: WhatsappSettingsRecord) : WhatsAppSettingsUpdateResult()
    data class Invalid(val errors: List<String>) : WhatsAppSettingsUpdateResult()
}

class WhatsAppSettingsAdminService(
    private val repository: WhatsAppSettingsRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun getCurrent(): WhatsappSettingsRecord = repository.getSingletonRequired()

    fun update(req: WhatsAppSettingsUpdateRequest): WhatsAppSettingsUpdateResult {
        val errors = WhatsAppOperationalSettingsValidator.validateUpdate(req)
        if (errors.isNotEmpty()) return WhatsAppSettingsUpdateResult.Invalid(errors)
        val now = LocalDateTime.now()
        val rec = repository.updateOperational(
            phoneNumberId = req.phoneNumberId,
            defaultTemplateName = req.defaultTemplateName,
            defaultTemplateLanguage = req.defaultTemplateLanguage,
            dailySendLimit = req.dailySendLimit,
            sendDelayMinMinutes = req.sendDelayMinMinutes,
            sendDelayMaxMinutes = req.sendDelayMaxMinutes,
            now = now,
        )
        log.info("whatsapp_settings: atualização via API/UI concluída")
        return WhatsAppSettingsUpdateResult.Ok(rec)
    }

    fun pause(): WhatsappSettingsRecord = repository.setServicePaused(true, LocalDateTime.now())

    fun resume(): WhatsappSettingsRecord = repository.setServicePaused(false, LocalDateTime.now())
}
