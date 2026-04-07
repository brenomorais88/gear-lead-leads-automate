package com.gearsales.leadengine.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

/** Uma única linha (`id = 1`) com configuração operacional do motor WhatsApp. */
object WhatsappSettingsTable : Table("whatsapp_settings") {
    val id = integer("id")
    val phoneNumberId = varchar("phone_number_id", 64)
    val defaultTemplateName = varchar("default_template_name", 128)
    val defaultTemplateLanguage = varchar("default_template_language", 16)
    val dailySendLimit = integer("daily_send_limit")
    val sendDelayMinMinutes = integer("send_delay_min_minutes")
    val sendDelayMaxMinutes = integer("send_delay_max_minutes")
    val servicePaused = bool("service_paused").default(false)
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)

    override val primaryKey = PrimaryKey(id)
}
