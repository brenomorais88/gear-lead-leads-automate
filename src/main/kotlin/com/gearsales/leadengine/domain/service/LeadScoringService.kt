package com.gearsales.leadengine.domain.service

import com.gearsales.leadengine.domain.model.LeadImportRow
import com.gearsales.leadengine.domain.model.LeadPriority
import java.text.Normalizer
import java.util.Locale

data class LeadScoreResult(
    val score: Int,
    val prioridade: LeadPriority,
)

class LeadScoringService {

    fun evaluate(row: LeadImportRow, telefoneNormalizado: String?): LeadScoreResult {
        var score = 0
        if (telefoneNormalizado != null) score += 3
        if (!row.email.isNullOrBlank()) score += 2
        if (matchesCnae4511102(row.cnae)) score += 2
        if (!row.nomeFantasia.isNullOrBlank()) score += 1
        if (!row.socio.isNullOrBlank()) score += 1
        if (isMeOrEpp(row.porte)) score += 1

        val prioridade = when {
            score >= 7 -> LeadPriority.ALTA
            score in 4..6 -> LeadPriority.MEDIA
            else -> LeadPriority.BAIXA
        }
        return LeadScoreResult(score = score, prioridade = prioridade)
    }

    private fun matchesCnae4511102(cnae: String?): Boolean {
        if (cnae.isNullOrBlank()) return false
        val digits = cnae.filter { it.isDigit() }
        return digits == TARGET_CNAE || digits.contains(TARGET_CNAE)
    }

    private fun isMeOrEpp(porte: String?): Boolean {
        if (porte.isNullOrBlank()) return false
        val n = normalizeLabel(porte)
        if (n == "me" || n == "epp") return true
        if (n.contains("microempresa") || (n.contains("micro") && n.contains("empresa"))) return true
        if (n.contains("empresa de pequeno porte") || n.contains("pequeno porte")) return true
        return false
    }

    private fun normalizeLabel(s: String): String {
        val trimmed = s.trim().lowercase(Locale.getDefault())
        val nfd = Normalizer.normalize(trimmed, Normalizer.Form.NFD)
        return nfd.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "").replace("\\s+".toRegex(), " ")
    }

    companion object {
        private const val TARGET_CNAE = "4511102"
    }
}
