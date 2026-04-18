package com.gearsales.leadengine.whatsapp.cloudapi

import com.gearsales.leadengine.whatsapp.cloudapi.dto.WhatsAppSendErrorBody

object WhatsAppInvalidRecipientDetector {

    private val invalidRecipientCodes = setOf(
        131026,
        131051,
        133010,
        131008,
    )

    private val invalidRecipientSubcodes = setOf(
        131026,
        131051,
    )

    fun isLikelyInvalidRecipient(error: WhatsAppSendErrorBody?, httpBodyLowercase: String): Boolean {
        val code = error?.code
        if (code != null && code in invalidRecipientCodes) return true
        val sub = error?.errorSubcode
        if (sub != null && sub in invalidRecipientSubcodes) return true
        val msg = error?.message?.lowercase().orEmpty()
        if (msg.contains("invalid") && (msg.contains("phone") || msg.contains("recipient") || msg.contains("user"))) {
            return true
        }
        if (msg.contains("not registered") || msg.contains("is not a valid whatsapp")) return true
        if (httpBodyLowercase.contains("invalid phone") ||
            httpBodyLowercase.contains("phone number is not a valid") ||
            httpBodyLowercase.contains("not a valid whatsapp phone number")
        ) {
            return true
        }
        return false
    }
}
