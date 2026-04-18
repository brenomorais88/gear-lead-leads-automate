package com.gearsales.leadengine.database.repositories

import com.gearsales.leadengine.database.tables.WhatsappSettingsTable
import com.gearsales.leadengine.domain.model.WhatsappSettingsRecord
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

private const val SINGLETON_ID = 1

class WhatsAppSettingsRepository {

    private val log = LoggerFactory.getLogger(javaClass)

    fun countRows(): Long = transaction {
        WhatsappSettingsTable.selectAll().count()
    }

    /**
     * Cria a linha única a partir do seed (yaml na primeira subida, quando o banco está vazio).
     */
    fun ensureSingletonFromSeed(
        phoneNumberId: String,
        defaultTemplateName: String,
        defaultTemplateLanguage: String,
        dailySendLimit: Int,
        sendDelayMinMinutes: Int,
        sendDelayMaxMinutes: Int,
        batchSize: Int,
        executionStartTime: String,
        executionEndTime: String,
        servicePaused: Boolean,
        now: LocalDateTime,
    ): WhatsappSettingsRecord = transaction {
        val existing = WhatsappSettingsTable.selectAll()
            .where { WhatsappSettingsTable.id eq SINGLETON_ID }
            .singleOrNull()
        if (existing != null) {
            return@transaction existing.toRecord()
        }
        WhatsappSettingsTable.insert {
            it[WhatsappSettingsTable.id] = SINGLETON_ID
            it[WhatsappSettingsTable.phoneNumberId] = phoneNumberId.trim()
            it[WhatsappSettingsTable.defaultTemplateName] = defaultTemplateName.trim()
            it[WhatsappSettingsTable.defaultTemplateLanguage] = defaultTemplateLanguage.trim()
            it[WhatsappSettingsTable.dailySendLimit] = dailySendLimit
            it[WhatsappSettingsTable.sendDelayMinMinutes] = sendDelayMinMinutes
            it[WhatsappSettingsTable.sendDelayMaxMinutes] = sendDelayMaxMinutes
            it[WhatsappSettingsTable.batchSize] = batchSize
            it[WhatsappSettingsTable.executionStartTime] = executionStartTime.trim()
            it[WhatsappSettingsTable.executionEndTime] = executionEndTime.trim()
            it[WhatsappSettingsTable.servicePaused] = servicePaused
            it[WhatsappSettingsTable.createdAt] = now
            it[WhatsappSettingsTable.updatedAt] = now
        }
        log.info(
            "whatsapp_settings: registro inicial criado (id=1) a partir do yaml — template={} lang={} limite={} delay={}-{} min batch={} janela={}-{} paused={}",
            defaultTemplateName,
            defaultTemplateLanguage,
            dailySendLimit,
            sendDelayMinMinutes,
            sendDelayMaxMinutes,
            batchSize,
            executionStartTime,
            executionEndTime,
            servicePaused,
        )
        WhatsappSettingsTable.selectAll()
            .where { WhatsappSettingsTable.id eq SINGLETON_ID }
            .single()
            .toRecord()
    }

    fun getSingletonRequired(): WhatsappSettingsRecord = transaction {
        WhatsappSettingsTable.selectAll()
            .where { WhatsappSettingsTable.id eq SINGLETON_ID }
            .singleOrNull()
            ?.toRecord()
            ?: error("whatsapp_settings: linha id=1 ausente; bootstrap não executado")
    }

    fun updateOperational(
        phoneNumberId: String,
        defaultTemplateName: String,
        defaultTemplateLanguage: String,
        dailySendLimit: Int,
        sendDelayMinMinutes: Int,
        sendDelayMaxMinutes: Int,
        batchSize: Int,
        executionStartTime: String,
        executionEndTime: String,
        now: LocalDateTime,
    ): WhatsappSettingsRecord = transaction {
        WhatsappSettingsTable.update({ WhatsappSettingsTable.id eq SINGLETON_ID }) {
            it[WhatsappSettingsTable.phoneNumberId] = phoneNumberId.trim()
            it[WhatsappSettingsTable.defaultTemplateName] = defaultTemplateName.trim()
            it[WhatsappSettingsTable.defaultTemplateLanguage] = defaultTemplateLanguage.trim()
            it[WhatsappSettingsTable.dailySendLimit] = dailySendLimit
            it[WhatsappSettingsTable.sendDelayMinMinutes] = sendDelayMinMinutes
            it[WhatsappSettingsTable.sendDelayMaxMinutes] = sendDelayMaxMinutes
            it[WhatsappSettingsTable.batchSize] = batchSize
            it[WhatsappSettingsTable.executionStartTime] = executionStartTime.trim()
            it[WhatsappSettingsTable.executionEndTime] = executionEndTime.trim()
            it[WhatsappSettingsTable.updatedAt] = now
        }
        log.info(
            "whatsapp_settings: configuração operacional atualizada (template={} lang={} limite={} delay={}-{} batch={} janela={}-{})",
            defaultTemplateName,
            defaultTemplateLanguage,
            dailySendLimit,
            sendDelayMinMinutes,
            sendDelayMaxMinutes,
            batchSize,
            executionStartTime,
            executionEndTime,
        )
        WhatsappSettingsTable.selectAll()
            .where { WhatsappSettingsTable.id eq SINGLETON_ID }
            .single()
            .toRecord()
    }

    fun setServicePaused(paused: Boolean, now: LocalDateTime): WhatsappSettingsRecord = transaction {
        WhatsappSettingsTable.update({ WhatsappSettingsTable.id eq SINGLETON_ID }) {
            it[WhatsappSettingsTable.servicePaused] = paused
            it[WhatsappSettingsTable.updatedAt] = now
        }
        if (paused) {
            log.info("whatsapp_settings: serviço de disparo PAUSADO")
        } else {
            log.info("whatsapp_settings: serviço de disparo RETOMADO")
        }
        WhatsappSettingsTable.selectAll()
            .where { WhatsappSettingsTable.id eq SINGLETON_ID }
            .single()
            .toRecord()
    }

    private fun ResultRow.toRecord() = WhatsappSettingsRecord(
        id = this[WhatsappSettingsTable.id],
        phoneNumberId = this[WhatsappSettingsTable.phoneNumberId],
        defaultTemplateName = this[WhatsappSettingsTable.defaultTemplateName],
        defaultTemplateLanguage = this[WhatsappSettingsTable.defaultTemplateLanguage],
        dailySendLimit = this[WhatsappSettingsTable.dailySendLimit],
        sendDelayMinMinutes = this[WhatsappSettingsTable.sendDelayMinMinutes],
        sendDelayMaxMinutes = this[WhatsappSettingsTable.sendDelayMaxMinutes],
        batchSize = this[WhatsappSettingsTable.batchSize],
        executionStartTime = this[WhatsappSettingsTable.executionStartTime],
        executionEndTime = this[WhatsappSettingsTable.executionEndTime],
        servicePaused = this[WhatsappSettingsTable.servicePaused],
        createdAt = this[WhatsappSettingsTable.createdAt],
        updatedAt = this[WhatsappSettingsTable.updatedAt],
    )
}
