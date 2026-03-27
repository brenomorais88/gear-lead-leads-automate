package com.gearsales.leadengine.domain.model

data class LeadImportPreview(
    val rows: List<LeadImportRow>,
    val totalLido: Int,
    val colunasMapeadas: Map<String, String>,
    val colunasNaoReconhecidas: List<String>,
)
