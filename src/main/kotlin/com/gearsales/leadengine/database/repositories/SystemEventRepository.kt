package com.gearsales.leadengine.database.repositories

import com.gearsales.leadengine.database.tables.SystemEventsTable
import com.gearsales.leadengine.domain.model.SystemEventRecord
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction

class SystemEventRepository {

    fun insert(
        level: String,
        category: String,
        summary: String,
        details: String? = null,
        httpMethod: String? = null,
        httpPath: String? = null,
        httpStatus: Int? = null,
    ): Long = dbQuery {
        val inserted = SystemEventsTable.insert {
            it[SystemEventsTable.level] = level.take(16)
            it[SystemEventsTable.category] = category.take(64)
            it[SystemEventsTable.summary] = summary.take(255)
            it[SystemEventsTable.details] = details
            it[SystemEventsTable.httpMethod] = httpMethod?.take(16)
            it[SystemEventsTable.httpPath] = httpPath?.take(512)
            it[SystemEventsTable.httpStatus] = httpStatus
        } get SystemEventsTable.id
        inserted.value
    }

    fun listRecent(limit: Int, offset: Int): List<SystemEventRecord> = dbQuery {
        val l = limit.coerceIn(1, 500)
        val o = offset.coerceAtLeast(0)
        SystemEventsTable.selectAll()
            .orderBy(SystemEventsTable.createdAt, SortOrder.DESC)
            .orderBy(SystemEventsTable.id, SortOrder.DESC)
            .limit(l)
            .offset(o.toLong())
            .map { it.toRecord() }
    }

    fun countAll(): Long = dbQuery {
        SystemEventsTable.selectAll().count()
    }

    private fun ResultRow.toRecord(): SystemEventRecord =
        SystemEventRecord(
            id = this[SystemEventsTable.id].value,
            level = this[SystemEventsTable.level],
            category = this[SystemEventsTable.category],
            summary = this[SystemEventsTable.summary],
            details = this[SystemEventsTable.details],
            httpMethod = this[SystemEventsTable.httpMethod],
            httpPath = this[SystemEventsTable.httpPath],
            httpStatus = this[SystemEventsTable.httpStatus],
            createdAt = this[SystemEventsTable.createdAt],
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
