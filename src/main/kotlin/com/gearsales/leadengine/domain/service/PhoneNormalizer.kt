package com.gearsales.leadengine.domain.service

object PhoneNormalizer {

    /**
     * Retorna apenas dígitos no formato internacional BR para WhatsApp (55 + DDD + número),
     * ou null se inválido.
     */
    fun normalizeForWhatsApp(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val digits = raw.filter { it.isDigit() }
        if (digits.isEmpty()) return null

        return when {
            digits.startsWith("55") -> when (digits.length) {
                12, 13 -> digits
                else -> null
            }
            digits.length == 10 || digits.length == 11 -> "55$digits"
            else -> null
        }
    }
}
