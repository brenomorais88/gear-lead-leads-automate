package com.gearsales.leadengine.domain.service

import com.gearsales.leadengine.database.tables.DailyBatchLeadsTable
import com.gearsales.leadengine.database.tables.DailyBatchesTable
import com.gearsales.leadengine.database.tables.LeadInteractionsTable
import com.gearsales.leadengine.database.tables.LeadMessageCampaignsTable
import com.gearsales.leadengine.database.tables.LeadsTable
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

/**
 * Remove dados operacionais (leads, lotes, campanhas, interações), mantendo [whatsapp_settings].
 */
class OperationalDataPurgeService {
    private val log = LoggerFactory.getLogger(javaClass)

    fun purgeAllOperationalData() = transaction {
        val campaigns = LeadMessageCampaignsTable.deleteAll()
        val interactions = LeadInteractionsTable.deleteAll()
        val batchLinks = DailyBatchLeadsTable.deleteAll()
        val batches = DailyBatchesTable.deleteAll()
        val leads = LeadsTable.deleteAll()
        log.warn(
            "operational_purge: removidos campaigns={} interactions={} batchLinks={} batches={} leads={}",
            campaigns,
            interactions,
            batchLinks,
            batches,
            leads,
        )
    }
}
