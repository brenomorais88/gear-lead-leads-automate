package com.gearsales.leadengine.whatsapp.cloudapi.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WhatsAppTemplateSendRequest(
    @SerialName("messaging_product") val messagingProduct: String = "whatsapp",
    val to: String,
    val type: String = "template",
    val template: WhatsAppTemplateBlock,
)

@Serializable
data class WhatsAppTemplateBlock(
    val name: String,
    val language: WhatsAppTemplateLanguage,
    val components: List<WhatsAppTemplateComponent> = emptyList(),
)

@Serializable
data class WhatsAppTemplateLanguage(
    val code: String,
)

@Serializable
data class WhatsAppTemplateComponent(
    val type: String,
    val parameters: List<WhatsAppTemplateParameter> = emptyList(),
)

@Serializable
data class WhatsAppTemplateParameter(
    val type: String = "text",
    val text: String,
)

@Serializable
data class WhatsAppSendSuccessResponse(
    @SerialName("messaging_product") val messagingProduct: String? = null,
    val contacts: List<WhatsAppSendContact>? = null,
    val messages: List<WhatsAppSendMessageId>? = null,
)

@Serializable
data class WhatsAppSendContact(
    val input: String? = null,
    @SerialName("wa_id") val waId: String? = null,
)

@Serializable
data class WhatsAppSendMessageId(
    val id: String? = null,
)

@Serializable
data class WhatsAppSendErrorEnvelope(
    val error: WhatsAppSendErrorBody? = null,
)

@Serializable
data class WhatsAppSendErrorBody(
    val message: String? = null,
    val type: String? = null,
    val code: Int? = null,
    @SerialName("error_subcode") val errorSubcode: Int? = null,
    @SerialName("fbtrace_id") val fbtraceId: String? = null,
)
