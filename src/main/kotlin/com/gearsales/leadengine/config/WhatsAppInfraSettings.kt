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

            return WhatsAppInfraSettings(
                apiBaseUrl = apiBaseUrl,
                apiVersion = apiVersion,
                accessToken = accessToken,
                businessAccountId = businessAccountId,
                webhookVerifyToken = webhookVerifyToken,
                workerPollIntervalSeconds = workerPoll.coerceAtLeast(5L),
                processingStaleSeconds = stale.coerceAtLeast(60L),
                quotaDeferMinutes = quotaDefer.coerceAtLeast(1),
            )
        }
    }
}

/**
 * Valores operacionais usados apenas para seed inicial da tabela [whatsapp_settings] (fallback .env).
 */
data class WhatsAppOperationalSeed(
    val phoneNumberId: String,
    val defaultTemplateName: String,
    val defaultTemplateLanguage: String,
    val dailySendLimit: Int,
    val sendDelayMinMinutes: Int,
    val sendDelayMaxMinutes: Int,
    val servicePaused: Boolean = false,
) {
    companion object {
        private val log = LoggerFactory.getLogger(WhatsAppOperationalSeed::class.java)

        fun loadFromEnvironment(config: ApplicationConfig): WhatsAppOperationalSeed {
            fun env(name: String): String? = AppEnv.get(name)
            fun cfg(path: String): String? =
                config.propertyOrNull(path)?.getString()?.takeIf { it.isNotBlank() }

            val phoneNumberId = env("WHATSAPP_PHONE_NUMBER_ID") ?: cfg("whatsapp.phoneNumberId").orEmpty()
            val defaultTemplateName = env("WHATSAPP_DEFAULT_TEMPLATE_NAME")
                ?: cfg("whatsapp.defaultTemplateName")
                ?: "gear_lead_intro_v1"
            val defaultTemplateLanguage = env("WHATSAPP_DEFAULT_TEMPLATE_LANGUAGE")
                ?: cfg("whatsapp.defaultTemplateLanguage")
                ?: "pt_BR"
            val dailySendLimit = env("WHATSAPP_DAILY_SEND_LIMIT")?.toIntOrNull()
                ?: cfg("whatsapp.dailySendLimit")?.toIntOrNull()
                ?: 100

            var delayMin = env("WHATSAPP_SEND_DELAY_MIN_MINUTES")?.toIntOrNull()
                ?: cfg("whatsapp.sendDelayMinMinutes")?.toIntOrNull()
                ?: 5
            var delayMax = env("WHATSAPP_SEND_DELAY_MAX_MINUTES")?.toIntOrNull()
                ?: cfg("whatsapp.sendDelayMaxMinutes")?.toIntOrNull()
                ?: 30
            if (delayMax < delayMin) {
                log.warn(
                    "WHATSAPP_SEND_DELAY: max < min no ambiente; invertendo antes do seed no banco.",
                )
                val t = delayMin
                delayMin = delayMax
                delayMax = t
            }
            delayMin = delayMin.coerceAtLeast(0)
            delayMax = delayMax.coerceAtLeast(delayMin)

            return WhatsAppOperationalSeed(
                phoneNumberId = phoneNumberId.trim(),
                defaultTemplateName = defaultTemplateName.trim(),
                defaultTemplateLanguage = defaultTemplateLanguage.trim(),
                dailySendLimit = dailySendLimit,
                sendDelayMinMinutes = delayMin,
                sendDelayMaxMinutes = delayMax,
                servicePaused = false,
            )
        }
    }
}
