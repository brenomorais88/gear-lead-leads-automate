package com.gearsales.leadengine.domain.service

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object WhatsAppMessageService {

    fun buildDefaultMessage(
        nomeFantasia: String?,
        razaoSocial: String,
        cidade: String?,
    ): String {
        val nome = nomeFantasia?.trim()?.takeIf { it.isNotEmpty() } ?: razaoSocial.trim()
        val cidadeTrim = cidade?.trim()?.takeIf { it.isNotEmpty() }

        val primeiraParte = if (cidadeTrim != null) {
            "Vi a $nome aqui de $cidadeTrim e resolvi entrar em contato porque estou selecionando algumas lojas para testar gratuitamente uma plataforma que estou desenvolvendo para gestão de estoque, anúncios e operação da loja."
        } else {
            "Vi a $nome e resolvi entrar em contato porque estou selecionando algumas lojas para testar gratuitamente uma plataforma que estou desenvolvendo para gestão de estoque, anúncios e operação da loja."
        }

        return buildString {
            appendLine("Oi! Tudo bem?")
            appendLine()
            appendLine(primeiraParte)
            appendLine()
            appendLine("A ideia agora não é vender, e sim acompanhar poucas lojas de perto para evoluir o sistema com feedback real.")
            appendLine()
            appendLine("Se fizer sentido, posso te mostrar rapidinho.")
        }.trimEnd()
    }

    /**
     * [telefoneNormalizado] deve conter apenas dígitos (ex.: 5511999999999).
     * Retorna null se não houver número utilizável.
     */
    fun buildWhatsAppLink(telefoneNormalizado: String?, message: String): String? {
        if (telefoneNormalizado.isNullOrBlank()) return null
        val digits = telefoneNormalizado.filter { it.isDigit() }
        if (digits.isEmpty()) return null
        val encoded = URLEncoder.encode(message, StandardCharsets.UTF_8).replace("+", "%20")
        return "https://wa.me/$digits?text=$encoded"
    }
}
