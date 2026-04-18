package com.gearsales.leadengine.domain.service

import com.gearsales.leadengine.domain.model.WhatsAppFailureCategory
import com.gearsales.leadengine.whatsapp.cloudapi.dto.WhatsAppSendErrorBody

object WhatsAppApiFailureClassifier {

    fun classify(
        httpStatus: Int?,
        parsed: WhatsAppSendErrorBody?,
        rawBody: String,
        invalidRecipient: Boolean,
    ): Pair<String, WhatsAppFailureCategory> {
        if (invalidRecipient) {
            return brief("Meta ${httpStatus ?: "?"}: recipient / número inválido ou não permitido", WhatsAppFailureCategory.INVALID_PHONE)
        }
        val code = httpStatus ?: 0
        val metaMsg = parsed?.message?.trim()?.takeIf { it.isNotEmpty() }
        val metaCode = parsed?.code
        val base = when {
            metaMsg != null && metaCode != null -> "Meta $code (error_subcode=$metaCode): $metaMsg"
            metaMsg != null -> "Meta $code: $metaMsg"
            else -> "Meta $code: ${rawBody.trim().take(500)}"
        }
        val category = when (code) {
            401, 403 -> WhatsAppFailureCategory.AUTH_ERROR
            429 -> WhatsAppFailureCategory.RATE_LIMIT
            in 400..499 -> classify4xx(metaMsg, rawBody)
            in 500..599 -> WhatsAppFailureCategory.META_API_ERROR
            0 -> WhatsAppFailureCategory.INTERNAL_ERROR
            else -> WhatsAppFailureCategory.UNKNOWN_ERROR
        }
        return base to category
    }

    private fun classify4xx(msg: String?, raw: String): WhatsAppFailureCategory {
        val m = ((msg ?: "") + " " + raw).lowercase()
        return when {
            m.contains("template") || m.contains("template_name") -> WhatsAppFailureCategory.TEMPLATE_ERROR
            m.contains("access token") || m.contains("oauth") -> WhatsAppFailureCategory.AUTH_ERROR
            m.contains("rate") || m.contains("too many") -> WhatsAppFailureCategory.RATE_LIMIT
            m.contains("phone") || m.contains("recipient") || m.contains("not registered") -> WhatsAppFailureCategory.INVALID_PHONE
            else -> WhatsAppFailureCategory.META_API_ERROR
        }
    }

    private fun brief(text: String, cat: WhatsAppFailureCategory) = text to cat
}
