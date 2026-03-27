package com.gearsales.leadengine.database.repositories

import com.gearsales.leadengine.database.tables.DailyBatchLeadsTable
import com.gearsales.leadengine.database.tables.DailyBatchesTable
import com.gearsales.leadengine.database.tables.LeadsTable
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

data class BatchListRow(
    val id: Long,
    val createdAt: LocalDateTime,
    val totalLeads: Int,
)

class BatchRepository {

    fun insertBatch(totalLeads: Int): Long = dbQuery {
        (DailyBatchesTable.insert {
            it[DailyBatchesTable.totalLeads] = totalLeads
        } get DailyBatchesTable.id).value
    }

    fun linkLeads(batchId: Long, leadIds: List<Long>) = dbQuery {
        val bid = EntityID(batchId, DailyBatchesTable)
        for (lid in leadIds) {
            DailyBatchLeadsTable.insert {
                it[DailyBatchLeadsTable.batchId] = bid
                it[DailyBatchLeadsTable.leadId] = EntityID(lid, LeadsTable)
            }
        }
    }

    fun listAll(): List<BatchListRow> = dbQuery {
        DailyBatchesTable.selectAll()
            .orderBy(DailyBatchesTable.id, SortOrder.DESC)
            .map { it.toBatchListRow() }
    }

    fun findById(id: Long): BatchListRow? = dbQuery {
        DailyBatchesTable.selectAll()
            .where { DailyBatchesTable.id eq id }
            .firstOrNull()
            ?.toBatchListRow()
    }

    fun findLeadIdsForBatch(batchId: Long): List<Long> = dbQuery {
        val bid = EntityID(batchId, DailyBatchesTable)
        DailyBatchLeadsTable.selectAll()
            .where { DailyBatchLeadsTable.batchId eq bid }
            .orderBy(DailyBatchLeadsTable.id, SortOrder.ASC)
            .map { it[DailyBatchLeadsTable.leadId].value }
    }

    private fun ResultRow.toBatchListRow() = BatchListRow(
        id = this[DailyBatchesTable.id].value,
        createdAt = this[DailyBatchesTable.createdAt],
        totalLeads = this[DailyBatchesTable.totalLeads],
    )

    private companion object {
        private fun <T> dbQuery(block: () -> T): T {
            return if (TransactionManager.currentOrNull() != null) {
                block()
            } else {
                transaction { block() }
            }
        }
    }
}
