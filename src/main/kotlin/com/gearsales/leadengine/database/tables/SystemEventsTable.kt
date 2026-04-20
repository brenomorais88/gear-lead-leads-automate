package com.gearsales.leadengine.database.tables

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

object SystemEventsTable : LongIdTable("system_events") {
    val level = varchar("level", 16)
    val category = varchar("category", 64)
    val summary = varchar("summary", 255)
    val details = text("details").nullable()
    val httpMethod = varchar("http_method", 16).nullable()
    val httpPath = varchar("http_path", 512).nullable()
    val httpStatus = integer("http_status").nullable()
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
}
