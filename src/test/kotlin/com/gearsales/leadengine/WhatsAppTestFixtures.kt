package com.gearsales.leadengine

import com.gearsales.leadengine.config.WhatsAppAppConfig
import com.gearsales.leadengine.config.WhatsAppInfraSettings
import com.gearsales.leadengine.database.repositories.WhatsAppSettingsRepository
import java.time.LocalDateTime

fun testWhatsAppInfra(
    accessToken: String? = "test-token",
    webhookVerifyToken: String? = "gearleadengine_wh_verify_2026_local",
    workerPollIntervalSeconds: Long = 30L,
    processingStaleSeconds: Long = 600L,
    quotaDeferMinutes: Int = 15,
) = WhatsAppInfraSettings(
    apiBaseUrl = "https://graph.facebook.com",
    apiVersion = "v21.0",
    accessToken = accessToken,
    businessAccountId = "",
    webhookVerifyToken = webhookVerifyToken,
    workerPollIntervalSeconds = workerPollIntervalSeconds,
    processingStaleSeconds = processingStaleSeconds,
    quotaDeferMinutes = quotaDeferMinutes,
)

/**
 * Repositório com linha id=1 já criada (valores típicos de teste).
 */
fun testWhatsAppSettingsRepository(
    phoneNumberId: String = "999",
    dailySendLimit: Int = 50,
    sendDelayMinMinutes: Int = 5,
    sendDelayMaxMinutes: Int = 30,
    batchSize: Int = 20,
    executionStartTime: String = "00:00",
    executionEndTime: String = "23:59",
    servicePaused: Boolean = false,
    defaultTemplateName: String = "gear_lead_intro_v1",
    defaultTemplateLanguage: String = "pt_BR",
): WhatsAppSettingsRepository {
    val r = WhatsAppSettingsRepository()
    r.ensureSingletonFromSeed(
        phoneNumberId = phoneNumberId,
        defaultTemplateName = defaultTemplateName,
        defaultTemplateLanguage = defaultTemplateLanguage,
        dailySendLimit = dailySendLimit,
        sendDelayMinMinutes = sendDelayMinMinutes,
        sendDelayMaxMinutes = sendDelayMaxMinutes,
        batchSize = batchSize,
        executionStartTime = executionStartTime,
        executionEndTime = executionEndTime,
        inboundNotifyRecipients = "",
        inboundNotifyTemplateName = "",
        inboundNotifyTemplateLanguage = "pt_BR",
        inboundNotifyBodyTemplate = "",
        servicePaused = servicePaused,
        now = LocalDateTime.now(),
    )
    return r
}

fun testWhatsAppAppConfig(
    infra: WhatsAppInfraSettings = testWhatsAppInfra(),
    repository: WhatsAppSettingsRepository = testWhatsAppSettingsRepository(),
): WhatsAppAppConfig = WhatsAppAppConfig(infra, repository)
