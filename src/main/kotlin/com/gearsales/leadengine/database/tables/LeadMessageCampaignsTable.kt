package com.gearsales.leadengine.database.tables

import com.gearsales.leadengine.domain.model.LeadCampaignStatus
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

object LeadMessageCampaignsTable : LongIdTable("lead_message_campaigns") {
    val leadId = reference("lead_id", LeadsTable)
    val batchId = optReference("batch_id", DailyBatchesTable)
    val templateName = varchar("template_name", 128)
    val templateLanguage = varchar("template_language", 16)
    val status = varchar("status", 32).default(LeadCampaignStatus.PENDING.name)
    val waMessageId = varchar("wa_message_id", 255).nullable()
    val attemptNumber = integer("attempt_number").default(1)
    val followupStep = integer("followup_step").default(0)
    val sentAt = datetime("sent_at").nullable()
    val deliveredAt = datetime("delivered_at").nullable()
    val readAt = datetime("read_at").nullable()
    val respondedAt = datetime("responded_at").nullable()
    val failedAt = datetime("failed_at").nullable()
    val failureReason = text("failure_reason").nullable()
    val failureCategory = varchar("failure_category", 32).nullable()
    val scheduledAt = datetime("scheduled_at").nullable()
    val lastAttemptAt = datetime("last_attempt_at").nullable()
    val processingStartedAt = datetime("processing_started_at").nullable()
    val nextFollowupAt = datetime("next_followup_at").nullable()
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)
}
