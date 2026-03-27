package com.gearsales.leadengine.database.repositories

import com.gearsales.leadengine.database.tables.LeadInteractionsTable
import com.gearsales.leadengine.database.tables.LeadsTable
import com.gearsales.leadengine.domain.model.LeadInteractionRecord
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction

class LeadInteractionRepository {

    fun insert(leadId: Long, interactionType: String, result: String?, note: String?): Long = dbQuery {
        val inserted = LeadInteractionsTable.insert {
            it[LeadInteractionsTable.leadId] = EntityID(leadId, LeadsTable)
            it[LeadInteractionsTable.interactionType] = interactionType
            it[LeadInteractionsTable.result] = result
            it[LeadInteractionsTable.note] = note
        } get LeadInteractionsTable.id
        inserted.value
    }

    fun listByLeadId(leadId: Long): List<LeadInteractionRecord> = dbQuery {
        val lid = EntityID(leadId, LeadsTable)
        LeadInteractionsTable.selectAll()
            .where { LeadInteractionsTable.leadId eq lid }
            .orderBy(LeadInteractionsTable.createdAt, SortOrder.DESC)
            .map { it.toRecord() }
    }

    private fun ResultRow.toRecord() = LeadInteractionRecord(
        id = this[LeadInteractionsTable.id].value,
        leadId = this[LeadInteractionsTable.leadId].value,
        interactionType = this[LeadInteractionsTable.interactionType],
        result = this[LeadInteractionsTable.result],
        note = this[LeadInteractionsTable.note],
        createdAt = this[LeadInteractionsTable.createdAt],
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
