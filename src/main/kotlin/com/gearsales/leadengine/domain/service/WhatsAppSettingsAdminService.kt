package com.gearsales.leadengine.domain.service

import com.gearsales.leadengine.database.repositories.WhatsAppSettingsRepository
import com.gearsales.leadengine.domain.model.WhatsappSettingsRecord
import com.gearsales.leadengine.web.dto.WhatsAppOperationalDataResetRequest
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
        batchSize: Int,
        executionStartTime: String,
        executionEndTime: String,
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
        if (batchSize < 1) errors.add("Tamanho do lote (batchSize) deve ser pelo menos 1.")
        errors.addAll(
            ExecutionWindowEvaluator.validateFields(executionStartTime, executionEndTime),
        )
        return errors
    }

    fun validateUpdate(req: WhatsAppSettingsUpdateRequest): List<String> = validateUpdate(
        phoneNumberId = req.phoneNumberId,
        defaultTemplateName = req.defaultTemplateName,
        defaultTemplateLanguage = req.defaultTemplateLanguage,
        dailySendLimit = req.dailySendLimit,
        sendDelayMinMinutes = req.sendDelayMinMinutes,
        sendDelayMaxMinutes = req.sendDelayMaxMinutes,
        batchSize = req.batchSize,
        executionStartTime = req.executionStartTime,
        executionEndTime = req.executionEndTime,
    )
}

sealed class WhatsAppSettingsUpdateResult {
    data class Ok(val record: WhatsappSettingsRecord) : WhatsAppSettingsUpdateResult()
    data class Invalid(val errors: List<String>) : WhatsAppSettingsUpdateResult()
}

private const val RESET_CONFIRM_PHRASE = "APAGAR TODOS OS DADOS"

class WhatsAppSettingsAdminService(
    private val repository: WhatsAppSettingsRepository,
    private val operationalDataPurgeService: OperationalDataPurgeService,
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
            batchSize = req.batchSize.coerceAtLeast(1),
            executionStartTime = req.executionStartTime,
            executionEndTime = req.executionEndTime,
            now = now,
        )
        log.info("whatsapp_settings: atualização via API/UI concluída")
        return WhatsAppSettingsUpdateResult.Ok(rec)
    }

    fun pause(): WhatsappSettingsRecord = repository.setServicePaused(true, LocalDateTime.now())

    fun resume(): WhatsappSettingsRecord = repository.setServicePaused(false, LocalDateTime.now())

    fun purgeOperationalData(req: WhatsAppOperationalDataResetRequest): WhatsAppSettingsUpdateResult {
        if (req.confirmPhrase.trim() != RESET_CONFIRM_PHRASE) {
            return WhatsAppSettingsUpdateResult.Invalid(
                listOf(
                    "Confirmação inválida. Digite exatamente: $RESET_CONFIRM_PHRASE",
                ),
            )
        }
        operationalDataPurgeService.purgeAllOperationalData()
        log.warn("whatsapp_settings: purge operacional executado (dados de leads/lotes/campanhas removidos)")
        return WhatsAppSettingsUpdateResult.Ok(getCurrent())
    }
}
