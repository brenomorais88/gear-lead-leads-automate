package com.gearsales.leadengine.config

/**
 * Visão efetiva usada em runtime: infra (.env) + operacional (banco).
 */
data class WhatsAppEffectiveConfig(
    val apiBaseUrl: String,
    val apiVersion: String,
    val accessToken: String?,
    val businessAccountId: String,
    val webhookVerifyToken: String?,
    val workerPollIntervalSeconds: Long,
    val processingStaleSeconds: Long,
    val quotaDeferMinutes: Int,
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
) {
    fun messagesEndpointUrl(): String {
        val base = apiBaseUrl.trimEnd('/')
        val ver = apiVersion.trim().removePrefix("/").removeSuffix("/")
        val id = phoneNumberId.trim()
        return "$base/$ver/$id/messages"
    }

    fun requireSendCredentials() {
        require(!accessToken.isNullOrBlank()) {
            "WHATSAPP_ACCESS_TOKEN is required to send WhatsApp messages"
        }
        require(phoneNumberId.isNotBlank()) {
            "Phone Number ID é obrigatório (configure em /whatsapp/config ou GET/PUT /whatsapp/settings)"
        }
    }
}
