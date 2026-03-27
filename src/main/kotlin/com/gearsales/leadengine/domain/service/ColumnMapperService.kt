package com.gearsales.leadengine.domain.service

import org.slf4j.LoggerFactory
import java.text.Normalizer
import java.util.Locale

data class ColumnHeaderMapping(
    val fieldToColumnIndex: Map<String, Int>,
    val colunasMapeadas: Map<String, String>,
    val colunasNaoReconhecidas: List<String>,
)

class ColumnMapperService {

    private val log = LoggerFactory.getLogger(ColumnMapperService::class.java)

    private val normalizedAliasToField: Map<String, String> = buildMap {
        fun reg(field: String, vararg labels: String) {
            for (label in labels) {
                val k = normalizeHeader(label)
                if (k.isNotEmpty()) put(k, field)
            }
        }
        reg("cnpj", "CNPJ", "cnpj")
        reg("razaoSocial", "Razão Social", "Razao Social", "razao social")
        reg("nomeFantasia", "Nome Fantasia", "nome fantasia", "fantasia")
        reg("telefone", "Telefone", "telefone", "Celular", "celular", "Fone", "WhatsApp", "whatsapp")
        reg("email", "E-mail", "Email", "email")
        reg("endereco", "Endereço", "Endereco", "endereco")
        reg("cidade", "Cidade", "cidade")
        reg("estado", "Estado", "UF", "uf")
        reg("dataAbertura", "Data de abertura", "Data Abertura", "abertura")
        reg("cnae", "Ramo de Atividade (CNAE)", "CNAE", "cnae", "ramo de atividade")
        reg("situacao", "Situação", "Situacao", "situacao")
        reg("porte", "Porte", "porte")
        reg("socio", "Sócio", "Socio", "socio")
        reg("capitalSocial", "Capital Social", "capital social")
        reg("tipo", "Tipo", "tipo")
    }

    fun mapHeaders(headers: List<String>): ColumnHeaderMapping {
        val fieldToColumnIndex = linkedMapOf<String, Int>()
        val unrecognized = mutableListOf<String>()

        val normalizedHeaders = headers.map { raw ->
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) {
                ""
            } else {
                normalizeHeader(trimmed)
            }
        }

        headers.forEachIndexed { index, raw ->
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) {
                unrecognized.add("(coluna vazia)")
                return@forEachIndexed
            }
            val key = normalizeHeader(trimmed)
            if (key.isEmpty()) {
                unrecognized.add(trimmed)
                return@forEachIndexed
            }
            val field = normalizedAliasToField[key]
            if (field != null) {
                if (!fieldToColumnIndex.containsKey(field)) {
                    fieldToColumnIndex[field] = index
                }
            } else {
                unrecognized.add(trimmed)
            }
        }

        log.info("ColumnMapper: headers originais={}", headers)
        log.info("ColumnMapper: headers normalizados={}", normalizedHeaders)

        if (!fieldToColumnIndex.containsKey("telefone")) {
            val picked = pickPhoneColumnIndex(headers, normalizedHeaders)
            if (picked != null) {
                val (colIdx, priorityLabel) = picked
                val rawLabel = headers.getOrNull(colIdx)?.trim().orEmpty()
                fieldToColumnIndex["telefone"] = colIdx
                unrecognized.remove(rawLabel)
                log.info(
                    "ColumnMapper: telefone mapeado por heurística (prioridade {}) para coluna '{}'",
                    priorityLabel,
                    rawLabel,
                )
            } else {
                log.info("ColumnMapper: nenhuma coluna de telefone detectada (alias exato ou heurística)")
            }
        } else {
            val telIdx = fieldToColumnIndex.getValue("telefone")
            val rawLabel = headers.getOrNull(telIdx)?.trim().orEmpty()
            log.info("ColumnMapper: telefone mapeado por alias exato para coluna '{}'", rawLabel)
        }

        val colunasMapeadas = fieldToColumnIndex.mapValues { (_, colIdx) ->
            headers.getOrNull(colIdx)?.trim().orEmpty()
        }

        return ColumnHeaderMapping(
            fieldToColumnIndex = fieldToColumnIndex,
            colunasMapeadas = colunasMapeadas,
            colunasNaoReconhecidas = unrecognized,
        )
    }

    /**
     * Prioridade menor = melhor. 1=whatsapp, 2=celular, 3=telefone, 4=fone (ex.: só "fone"), 5=contato.
     */
    private fun phoneHeuristicPriority(normalized: String): Int? {
        if (normalized.isEmpty()) return null
        if ("whatsapp" in normalized) return 1
        if ("celular" in normalized) return 2
        if ("telefone" in normalized) return 3
        if ("fone" in normalized) return 4
        if ("contato" in normalized) return 5
        return null
    }

    private fun pickPhoneColumnIndex(
        headers: List<String>,
        normalizedHeaders: List<String>,
    ): Pair<Int, String>? {
        data class Cand(val index: Int, val priority: Int, val label: String)

        val cands = mutableListOf<Cand>()
        for (i in headers.indices) {
            val raw = headers[i].trim()
            if (raw.isEmpty()) continue
            val norm = normalizedHeaders.getOrNull(i) ?: continue
            if (norm.isEmpty()) continue
            val p = phoneHeuristicPriority(norm) ?: continue
            cands.add(Cand(index = i, priority = p, label = phonePriorityLabel(p)))
        }
        if (cands.isEmpty()) return null
        val bestP = cands.minOf { it.priority }
        return cands
            .filter { it.priority == bestP }
            .minByOrNull { it.index }
            ?.let { it.index to it.label }
    }

    private fun phonePriorityLabel(priority: Int): String = when (priority) {
        1 -> "whatsapp"
        2 -> "celular"
        3 -> "telefone"
        4 -> "fone"
        5 -> "contato"
        else -> "desconhecida"
    }

    companion object {
        fun normalizeHeader(raw: String): String {
            val trimmed = raw.trim().replace(Regex("\\s+"), " ")
            val nfd = Normalizer.normalize(trimmed, Normalizer.Form.NFD)
            val withoutMarks = nfd.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            return withoutMarks.lowercase(Locale.getDefault())
        }
    }
}
