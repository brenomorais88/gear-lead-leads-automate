package com.gearsales.leadengine.config

import io.ktor.server.config.ApplicationConfig
import org.slf4j.LoggerFactory

/**
 * Configuração que permanece em .env / application.yaml (segredos e infraestrutura).
 * Não inclui phone/template/limite/delay operacionais (vêm do banco).
 */
data class WhatsAppInfraSettings(
    val apiBaseUrl: String,
    val apiVersion: String,
    val accessToken: String?,
    val businessAccountId: String,
    val webhookVerifyToken: String?,
    val workerPollIntervalSeconds: Long,
    val processingStaleSeconds: Long,
    val quotaDeferMinutes: Int,
    val inboundNotifyRecipients: String? = null,
    val inboundNotifyTemplateName: String? = null,
    val inboundNotifyTemplateLanguage: String? = null,
    val inboundNotifyBodyTemplate: String? = null,
) {
    companion object {
        private val log = LoggerFactory.getLogger(WhatsAppInfraSettings::class.java)

        fun load(config: ApplicationConfig): WhatsAppInfraSettings {
            fun env(name: String): String? = AppEnv.get(name)
            fun cfg(path: String): String? =
                config.propertyOrNull(path)?.getString()?.takeIf { it.isNotBlank() }

            val apiBaseUrl = env("WHATSAPP_API_BASE_URL")
                ?: cfg("whatsapp.apiBaseUrl")
                ?: "https://graph.facebook.com"
            val apiVersion = env("WHATSAPP_API_VERSION")
                ?: cfg("whatsapp.apiVersion")
                ?: "v21.0"
            val accessToken = env("WHATSAPP_ACCESS_TOKEN") ?: cfg("whatsapp.accessToken")
            val businessAccountId = env("WHATSAPP_BUSINESS_ACCOUNT_ID")
                ?: cfg("whatsapp.businessAccountId")
                ?: ""
            val webhookVerifyToken = env("WHATSAPP_WEBHOOK_VERIFY_TOKEN")
                ?: cfg("whatsapp.webhookVerifyToken")

            val workerPoll = env("WHATSAPP_WORKER_POLL_SECONDS")?.toLongOrNull()
                ?: cfg("whatsapp.workerPollIntervalSeconds")?.toLongOrNull()
                ?: 30L
            val stale = env("WHATSAPP_PROCESSING_STALE_SECONDS")?.toLongOrNull()
                ?: cfg("whatsapp.processingStaleSeconds")?.toLongOrNull()
                ?: 600L
            val quotaDefer = env("WHATSAPP_QUOTA_DEFER_MINUTES")?.toIntOrNull()
                ?: cfg("whatsapp.quotaDeferMinutes")?.toIntOrNull()
                ?: 15
            val inboundNotifyRecipients = env("WHATSAPP_INBOUND_NOTIFY_RECIPIENTS")
                ?: cfg("whatsapp.inboundNotifyRecipients")
            val inboundNotifyTemplateName = env("WHATSAPP_INBOUND_NOTIFY_TEMPLATE_NAME")
                ?: cfg("whatsapp.inboundNotifyTemplateName")
            val inboundNotifyTemplateLanguage = env("WHATSAPP_INBOUND_NOTIFY_TEMPLATE_LANGUAGE")
                ?: cfg("whatsapp.inboundNotifyTemplateLanguage")
            val inboundNotifyBodyTemplate = env("WHATSAPP_INBOUND_NOTIFY_BODY_TEMPLATE")
                ?: cfg("whatsapp.inboundNotifyBodyTemplate")

            return WhatsAppInfraSettings(
                apiBaseUrl = apiBaseUrl,
                apiVersion = apiVersion,
                accessToken = accessToken,
                businessAccountId = businessAccountId,
                webhookVerifyToken = webhookVerifyToken,
                workerPollIntervalSeconds = workerPoll.coerceAtLeast(5L),
                processingStaleSeconds = stale.coerceAtLeast(60L),
                quotaDeferMinutes = quotaDefer.coerceAtLeast(1),
                inboundNotifyRecipients = inboundNotifyRecipients,
                inboundNotifyTemplateName = inboundNotifyTemplateName,
                inboundNotifyTemplateLanguage = inboundNotifyTemplateLanguage,
                inboundNotifyBodyTemplate = inboundNotifyBodyTemplate,
            )
        }
    }
}

/**
 * Valores usados apenas na **criação** da linha inicial em [whatsapp_settings] quando o banco está vazio.
 * Fonte: `application.yaml` opcional; **não** lê variáveis operacionais do `.env` (evita duplicar a UI/banco).
 */
data class WhatsAppOperationalSeed(
    val phoneNumberId: String,
    val defaultTemplateName: String,
    val defaultTemplateLanguage: String,
    val dailySendLimit: Int,
    val sendDelayMinMinutes: Int,
    val sendDelayMaxMinutes: Int,
    val batchSize: Int,
    val executionStartTime: String,
    val executionEndTime: String,
    val servicePaused: Boolean = false,
) {
    companion object {
        private val log = LoggerFactory.getLogger(WhatsAppOperationalSeed::class.java)

        fun loadFromYamlOnly(config: ApplicationConfig): WhatsAppOperationalSeed {
            fun cfg(path: String): String? =
                config.propertyOrNull(path)?.getString()?.takeIf { it.isNotBlank() }

            val phoneNumberId = cfg("whatsapp.phoneNumberId").orEmpty()
            val defaultTemplateName = cfg("whatsapp.defaultTemplateName") ?: "gear_lead_intro_v1"
            val defaultTemplateLanguage = cfg("whatsapp.defaultTemplateLanguage") ?: "pt_BR"
            val dailySendLimit = cfg("whatsapp.dailySendLimit")?.toIntOrNull() ?: 100

            var delayMin = cfg("whatsapp.sendDelayMinMinutes")?.toIntOrNull() ?: 5
            var delayMax = cfg("whatsapp.sendDelayMaxMinutes")?.toIntOrNull() ?: 30
            if (delayMax < delayMin) {
                log.warn("whatsapp seed: delay max < min no yaml; invertendo antes de gravar no banco.")
                val t = delayMin
                delayMin = delayMax
                delayMax = t
            }
            delayMin = delayMin.coerceAtLeast(0)
            delayMax = delayMax.coerceAtLeast(delayMin)

            val batchSize = cfg("whatsapp.batchSize")?.toIntOrNull()?.coerceAtLeast(1) ?: 20
            val executionStartTime = cfg("whatsapp.executionStartTime") ?: "00:00"
            val executionEndTime = cfg("whatsapp.executionEndTime") ?: "23:59"

            return WhatsAppOperationalSeed(
                phoneNumberId = phoneNumberId.trim(),
                defaultTemplateName = defaultTemplateName.trim(),
                defaultTemplateLanguage = defaultTemplateLanguage.trim(),
                dailySendLimit = dailySendLimit,
                sendDelayMinMinutes = delayMin,
                sendDelayMaxMinutes = delayMax,
                batchSize = batchSize,
                executionStartTime = executionStartTime.trim(),
                executionEndTime = executionEndTime.trim(),
                servicePaused = false,
            )
        }
    }
}
