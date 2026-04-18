package com.gearsales.leadengine.domain.model

enum class WhatsAppFailureCategory {
    INVALID_PHONE,
    AUTH_ERROR,
    TEMPLATE_ERROR,
    RATE_LIMIT,
    META_API_ERROR,
    INTERNAL_ERROR,
    UNKNOWN_ERROR,
    ;

    companion object {
        fun fromStored(value: String?): WhatsAppFailureCategory? =
            entries.find { it.name == value }
    }
}
