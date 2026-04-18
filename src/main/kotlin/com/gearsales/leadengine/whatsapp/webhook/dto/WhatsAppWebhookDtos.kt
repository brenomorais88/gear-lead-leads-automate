package com.gearsales.leadengine.whatsapp.webhook.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WhatsAppWebhookRoot(
    @SerialName("object") val obj: String? = null,
    val entry: List<WhatsAppWebhookEntry> = emptyList(),
)

@Serializable
data class WhatsAppWebhookEntry(
    val id: String? = null,
    val changes: List<WhatsAppWebhookChange> = emptyList(),
)

@Serializable
data class WhatsAppWebhookChange(
    val field: String? = null,
    val value: WhatsAppWebhookValue? = null,
)

@Serializable
data class WhatsAppWebhookValue(
    @SerialName("messaging_product") val messagingProduct: String? = null,
    val metadata: WhatsAppWebhookMetadata? = null,
    val statuses: List<WhatsAppWebhookStatusItem> = emptyList(),
    val messages: List<WhatsAppWebhookInboundMessage> = emptyList(),
    val contacts: List<WhatsAppWebhookContact> = emptyList(),
)

@Serializable
data class WhatsAppWebhookMetadata(
    @SerialName("display_phone_number") val displayPhoneNumber: String? = null,
    @SerialName("phone_number_id") val phoneNumberId: String? = null,
)

@Serializable
data class WhatsAppWebhookStatusItem(
    val id: String? = null,
    val status: String? = null,
    val timestamp: String? = null,
    val errors: List<WhatsAppWebhookStatusError> = emptyList(),
    @SerialName("recipient_id") val recipientId: String? = null,
)

@Serializable
data class WhatsAppWebhookStatusError(
    val code: Int? = null,
    val title: String? = null,
    val message: String? = null,
)

@Serializable
data class WhatsAppWebhookInboundMessage(
    val from: String? = null,
    val id: String? = null,
    val timestamp: String? = null,
    val type: String? = null,
    val text: WhatsAppWebhookTextBody? = null,
)

@Serializable
data class WhatsAppWebhookTextBody(
    val body: String? = null,
)

@Serializable
data class WhatsAppWebhookContact(
    val profile: WhatsAppWebhookProfile? = null,
    @SerialName("wa_id") val waId: String? = null,
)

@Serializable
data class WhatsAppWebhookProfile(
    val name: String? = null,
)
