package com.gearsales.leadengine.domain.service

import com.gearsales.leadengine.config.WhatsAppAppConfig
import com.gearsales.leadengine.database.repositories.BatchRepository
import com.gearsales.leadengine.database.repositories.LeadRepository
import com.gearsales.leadengine.domain.model.DailyBatchResult
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import kotlin.math.max

class DailyBatchService(
    private val leadRepository: LeadRepository,
    private val batchRepository: BatchRepository,
    private val whatsappConfig: WhatsAppAppConfig,
) {
    fun generateBatch(): DailyBatchResult? = transaction {
        val batchSize = whatsappConfig.effective().batchSize.coerceAtLeast(1)
        val poolLimit = max(batchSize, 100).coerceAtMost(5000)
        val pool = leadRepository.findTopEligibleForSorteio(poolLimit)
        if (pool.isEmpty()) return@transaction null
        val selected = pool.shuffled().take(batchSize)
        val agora = LocalDateTime.now()
        val batchId = batchRepository.insertBatch(selected.size)
        batchRepository.linkLeads(batchId, selected)
        leadRepository.markSorteados(selected, agora)
        DailyBatchResult(batchId, selected.size, selected)
    }
}
