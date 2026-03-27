package com.gearsales.leadengine.domain.model

data class LeadImportResult(
    val totalLido: Int,
    val totalImportado: Int,
    val totalDuplicado: Int,
    val totalInvalido: Int,
    val erros: List<String>,
)
