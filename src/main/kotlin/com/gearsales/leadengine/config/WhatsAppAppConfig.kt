package com.gearsales.leadengine.config

import com.gearsales.leadengine.database.repositories.WhatsAppSettingsRepository

/**
 * Ponto de acesso à configuração WhatsApp: segredos/infra fixos + operacional lido do banco a cada [effective].
 */
class WhatsAppAppConfig(
    private val infra: WhatsAppInfraSettings,
    private val settingsRepository: WhatsAppSettingsRepository,
) {
    fun effective(): WhatsAppEffectiveConfig {
        val r = settingsRepository.getSingletonRequired()
        return WhatsAppEffectiveConfig(
            apiBaseUrl = infra.apiBaseUrl,
            apiVersion = infra.apiVersion,
            accessToken = infra.accessToken,
            businessAccountId = infra.businessAccountId,
            webhookVerifyToken = infra.webhookVerifyToken,
            workerPollIntervalSeconds = infra.workerPollIntervalSeconds,
            processingStaleSeconds = infra.processingStaleSeconds,
            quotaDeferMinutes = infra.quotaDeferMinutes,
            phoneNumberId = r.phoneNumberId,
            defaultTemplateName = r.defaultTemplateName,
            defaultTemplateLanguage = r.defaultTemplateLanguage,
            dailySendLimit = r.dailySendLimit,
            sendDelayMinMinutes = r.sendDelayMinMinutes,
            sendDelayMaxMinutes = r.sendDelayMaxMinutes,
            batchSize = r.batchSize,
            executionStartTime = r.executionStartTime,
            executionEndTime = r.executionEndTime,
            inboundNotifyRecipients = r.inboundNotifyRecipients,
            inboundNotifyTemplateName = r.inboundNotifyTemplateName,
            inboundNotifyTemplateLanguage = r.inboundNotifyTemplateLanguage,
            inboundNotifyBodyTemplate = r.inboundNotifyBodyTemplate,
            servicePaused = r.servicePaused,
        )
    }

    fun settingsRepository(): WhatsAppSettingsRepository = settingsRepository
    fun infra(): WhatsAppInfraSettings = infra
}
