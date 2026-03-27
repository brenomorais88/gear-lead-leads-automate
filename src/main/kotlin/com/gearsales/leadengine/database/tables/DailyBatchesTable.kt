package com.gearsales.leadengine.database.tables

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

object DailyBatchesTable : LongIdTable("daily_batches") {
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val totalLeads = integer("total_leads").default(0)
}
