package com.gearsales.leadengine.database.tables

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

object LeadInteractionsTable : LongIdTable("lead_interactions") {
    val leadId = reference("lead_id", LeadsTable)
    val interactionType = varchar("interaction_type", 64)
    val result = varchar("result", 128).nullable()
    val note = text("note").nullable()
    val direction = varchar("direction", 16).nullable()
    val externalMessageId = varchar("external_message_id", 255).nullable()
    val metadataJson = text("metadata_json").nullable()
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
}
