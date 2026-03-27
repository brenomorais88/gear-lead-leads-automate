package com.gearsales.leadengine.domain.service

import com.gearsales.leadengine.database.repositories.BatchRepository
import com.gearsales.leadengine.database.repositories.LeadRepository
import com.gearsales.leadengine.domain.model.DailyBatchResult
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

class DailyBatchService(
    private val leadRepository: LeadRepository,
    private val batchRepository: BatchRepository,
) {
    fun generateBatch(): DailyBatchResult? = transaction {
        val pool = leadRepository.findTopEligibleForSorteio(100)
        if (pool.isEmpty()) return@transaction null
        val selected = pool.shuffled().take(20)
        val agora = LocalDateTime.now()
        val batchId = batchRepository.insertBatch(selected.size)
        batchRepository.linkLeads(batchId, selected)
        leadRepository.markSorteados(selected, agora)
        DailyBatchResult(batchId, selected.size, selected)
    }
}
