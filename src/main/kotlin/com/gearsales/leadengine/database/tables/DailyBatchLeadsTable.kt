package com.gearsales.leadengine.database.tables

import org.jetbrains.exposed.dao.id.LongIdTable

object DailyBatchLeadsTable : LongIdTable("daily_batch_leads") {
    val batchId = reference("batch_id", DailyBatchesTable)
    val leadId = reference("lead_id", LeadsTable)
}
