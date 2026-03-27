package com.gearsales.leadengine.domain.model

data class DailyBatchResult(
    val batchId: Long,
    val totalSelecionado: Int,
    val leadIds: List<Long>,
)
