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

open class LeadInteractionRepository {

    companion object {
        /** Limite da coluna `lead_interactions.result` (varchar). */
        const val RESULT_VARCHAR_MAX_LENGTH: Int = 128
    }

    private fun <T> dbQuery(block: () -> T): T {
        return if (TransactionManager.currentOrNull() != null) {
            block()
        } else {
            transaction { block() }
        }
    }

    private fun truncateInteractionResult(result: String?): String? {
        if (result == null) return null
        val max = RESULT_VARCHAR_MAX_LENGTH
        if (result.length <= max) return result
        return result.take(max - 1) + "…"
    }

    open fun insert(
        leadId: Long,
        interactionType: String,
        result: String?,
        note: String?,
        direction: String? = null,
        externalMessageId: String? = null,
        metadataJson: String? = null,
    ): Long = dbQuery {
        val safeResult = truncateInteractionResult(result)
        val inserted = LeadInteractionsTable.insert {
            it[LeadInteractionsTable.leadId] = EntityID(leadId, LeadsTable)
            it[LeadInteractionsTable.interactionType] = interactionType
            it[LeadInteractionsTable.result] = safeResult
            it[LeadInteractionsTable.note] = note
            it[LeadInteractionsTable.direction] = direction
            it[LeadInteractionsTable.externalMessageId] = externalMessageId
            it[LeadInteractionsTable.metadataJson] = metadataJson
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
        direction = this[LeadInteractionsTable.direction],
        externalMessageId = this[LeadInteractionsTable.externalMessageId],
        metadataJson = this[LeadInteractionsTable.metadataJson],
        createdAt = this[LeadInteractionsTable.createdAt],
    )
}
