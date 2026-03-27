package com.gearsales.leadengine.domain.service

import com.gearsales.leadengine.database.repositories.LeadRepository
import com.gearsales.leadengine.domain.model.LeadImportOutcome
import com.gearsales.leadengine.domain.model.LeadImportPreview
import com.gearsales.leadengine.domain.model.LeadImportResult
import com.gearsales.leadengine.domain.model.LeadImportRow
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.InputStream

class LeadImportService(
    private val columnMapper: ColumnMapperService = ColumnMapperService(),
    private val leadRepository: LeadRepository = LeadRepository(),
    private val leadScoringService: LeadScoringService = LeadScoringService(),
) {

    private val dataFormatter = DataFormatter()
    private val maxErros = 20

    fun parse(inputStream: InputStream, filename: String): LeadImportPreview {
        requireXlsx(filename)
        XSSFWorkbook(inputStream).use { workbook ->
            return parseWorkbook(workbook)
        }
    }

    fun importAndPersist(inputStream: InputStream, filename: String): LeadImportOutcome {
        requireXlsx(filename)
        XSSFWorkbook(inputStream).use { workbook ->
            val preview = parseWorkbook(workbook)
            val result = persistRows(preview.rows)
            return LeadImportOutcome(preview = preview, result = result)
        }
    }

    private fun requireXlsx(filename: String) {
        if (!filename.endsWith(".xlsx", ignoreCase = true)) {
            throw IllegalArgumentException("Apenas arquivos .xlsx são aceitos.")
        }
    }

    private fun parseWorkbook(workbook: XSSFWorkbook): LeadImportPreview {
        val sheet = workbook.getSheetAt(0)
        val headerRow = sheet.getRow(0)
            ?: throw IllegalArgumentException("A primeira aba não contém cabeçalho.")
        val lastCell = headerRow.lastCellNum.toInt().coerceAtLeast(0)
        if (lastCell == 0) {
            throw IllegalArgumentException("Cabeçalho vazio ou inválido.")
        }
        val headers = (0 until lastCell).map { col ->
            formatCell(headerRow.getCell(col))
        }
        val mapping = columnMapper.mapHeaders(headers)
        val width = (1..sheet.lastRowNum).fold(lastCell) { acc, r ->
            val row = sheet.getRow(r) ?: return@fold acc
            maxOf(acc, row.lastCellNum.toInt().coerceAtLeast(0))
        }.coerceAtLeast(lastCell)

        val rows = mutableListOf<LeadImportRow>()
        for (r in 1..sheet.lastRowNum) {
            val row = sheet.getRow(r) ?: continue
            if (isRowEmpty(row, width)) continue
            rows.add(buildRow(row, mapping.fieldToColumnIndex))
        }

        return LeadImportPreview(
            rows = rows,
            totalLido = rows.size,
            colunasMapeadas = mapping.colunasMapeadas,
            colunasNaoReconhecidas = mapping.colunasNaoReconhecidas,
        )
    }

    private fun persistRows(rows: List<LeadImportRow>): LeadImportResult {
        return transaction {
            var totalImportado = 0
            var totalDuplicado = 0
            var totalInvalido = 0
            val erros = mutableListOf<String>()
            rows.forEachIndexed { index, row ->
                val lineNum = index + 2
                if (!isValidRow(row)) {
                    totalInvalido++
                    if (erros.size < maxErros) {
                        erros.add("Linha $lineNum: CNPJ ou razão social ausente.")
                    }
                    return@forEachIndexed
                }
                val cnpjNorm = normalizeCnpjDigits(row.cnpj!!)!!
                if (leadRepository.findByCnpj(cnpjNorm) != null) {
                    totalDuplicado++
                } else {
                    val telefoneNz = PhoneNormalizer.normalizeForWhatsApp(row.telefone)
                    val scoreResult = leadScoringService.evaluate(row, telefoneNz)
                    leadRepository.insert(
                        row = row,
                        normalizedCnpj = cnpjNorm,
                        telefoneNormalizado = telefoneNz,
                        score = scoreResult.score,
                        prioridade = scoreResult.prioridade,
                    )
                    totalImportado++
                }
            }
            LeadImportResult(
                totalLido = rows.size,
                totalImportado = totalImportado,
                totalDuplicado = totalDuplicado,
                totalInvalido = totalInvalido,
                erros = erros,
            )
        }
    }

    private fun isValidRow(row: LeadImportRow): Boolean {
        val razao = row.razaoSocial?.trim().orEmpty()
        if (razao.isEmpty()) return false
        val cnpj = normalizeCnpjDigits(row.cnpj)
        return cnpj != null
    }

    private fun normalizeCnpjDigits(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val digits = raw.filter { it.isDigit() }
        if (digits.isEmpty()) return null
        return if (digits.length > 14) digits.takeLast(14) else digits
    }

    private fun formatCell(cell: Cell?): String {
        if (cell == null) return ""
        return dataFormatter.formatCellValue(cell).trim()
    }

    private fun isRowEmpty(row: Row, maxCol: Int): Boolean {
        for (c in 0 until maxCol) {
            val v = formatCell(row.getCell(c))
            if (v.isNotEmpty()) return false
        }
        return true
    }

    private fun buildRow(row: Row, fieldToColumnIndex: Map<String, Int>): LeadImportRow {
        fun cell(field: String): String? {
            val idx = fieldToColumnIndex[field] ?: return null
            val v = formatCell(row.getCell(idx))
            return v.takeIf { it.isNotEmpty() }
        }
        return LeadImportRow(
            cnpj = cell("cnpj"),
            razaoSocial = cell("razaoSocial"),
            nomeFantasia = cell("nomeFantasia"),
            telefone = cell("telefone"),
            email = cell("email"),
            endereco = cell("endereco"),
            cidade = cell("cidade"),
            estado = cell("estado"),
            dataAbertura = cell("dataAbertura"),
            cnae = cell("cnae"),
            situacao = cell("situacao"),
            porte = cell("porte"),
            socio = cell("socio"),
            capitalSocial = cell("capitalSocial"),
            tipo = cell("tipo"),
        )
    }
}
