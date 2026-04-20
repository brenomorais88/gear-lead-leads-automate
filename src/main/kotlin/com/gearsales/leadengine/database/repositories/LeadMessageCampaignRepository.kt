package com.gearsales.leadengine.database.repositories

import com.gearsales.leadengine.database.tables.DailyBatchesTable
import com.gearsales.leadengine.database.tables.LeadMessageCampaignsTable
import com.gearsales.leadengine.database.tables.LeadsTable
import com.gearsales.leadengine.domain.model.LeadCampaignStatus
import com.gearsales.leadengine.domain.model.LeadMessageCampaignRecord
import com.gearsales.leadengine.domain.model.WhatsAppFailureCategory
import com.gearsales.leadengine.domain.model.leadCampaignStatusFromStored
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNotNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.random.Random

data class StaggeredScheduleSlot(
    val campaignId: Long,
    val scheduledAt: LocalDateTime,
    val delayAfterPreviousMinutes: Int,
)

data class BatchStaggeredScheduleResult(
    val slots: List<StaggeredScheduleSlot>,
)

/**
 * Linha campanha + lead (para leitura/API).
 */
/** Ponteiro mínimo para exibir próxima campanha / processamento no status do motor. */
data class WhatsAppEngineCampaignPointer(
    val campaignId: Long,
    val leadId: Long,
    val batchId: Long?,
)

data class CampaignLeadJoinRow(
    val campaignId: Long,
    val batchId: Long?,
    val leadId: Long,
    val templateName: String,
    val templateLanguage: String,
    val status: LeadCampaignStatus,
    val waMessageId: String?,
    val attemptNumber: Int,
    val followupStep: Int,
    val sentAt: LocalDateTime?,
    val deliveredAt: LocalDateTime?,
    val readAt: LocalDateTime?,
    val respondedAt: LocalDateTime?,
    val failedAt: LocalDateTime?,
    val failureReason: String?,
    val failureCategory: WhatsAppFailureCategory?,
    val scheduledAt: LocalDateTime?,
    val lastAttemptAt: LocalDateTime?,
    val nomeFantasia: String?,
    val razaoSocial: String,
    val telefoneOriginal: String?,
    val telefoneNormalizado: String?,
    val leadStatus: String,
    val leadRespondeu: Boolean,
    val leadInteressado: Boolean,
)

class LeadMessageCampaignRepository {

    fun createPending(
        leadId: Long,
        batchId: Long,
        templateName: String,
        templateLanguage: String,
        now: LocalDateTime,
    ): Long = dbQuery {
        val inserted = LeadMessageCampaignsTable.insert {
            it[LeadMessageCampaignsTable.leadId] = EntityID(leadId, LeadsTable)
            it[LeadMessageCampaignsTable.batchId] = EntityID(batchId, DailyBatchesTable)
            it[LeadMessageCampaignsTable.templateName] = templateName
            it[LeadMessageCampaignsTable.templateLanguage] = templateLanguage
            it[LeadMessageCampaignsTable.status] = LeadCampaignStatus.PENDING.name
            it[LeadMessageCampaignsTable.createdAt] = now
            it[LeadMessageCampaignsTable.updatedAt] = now
        } get LeadMessageCampaignsTable.id
        inserted.value
    }

    fun findByBatchId(batchId: Long): List<LeadMessageCampaignRecord> = dbQuery {
        val bid = EntityID(batchId, DailyBatchesTable)
        LeadMessageCampaignsTable.selectAll()
            .where { LeadMessageCampaignsTable.batchId eq bid }
            .orderBy(LeadMessageCampaignsTable.id, SortOrder.ASC)
            .map { it.toRecord() }
    }

    fun listCampaignsWithLeadForBatch(batchId: Long): List<CampaignLeadJoinRow> = dbQuery {
        val bid = EntityID(batchId, DailyBatchesTable)
        (LeadMessageCampaignsTable innerJoin LeadsTable)
            .selectAll()
            .where { LeadMessageCampaignsTable.batchId eq bid }
            .orderBy(LeadMessageCampaignsTable.id, SortOrder.ASC)
            .map { it.toCampaignLeadJoinRow() }
    }

    fun countCampaigns(): Long = dbQuery {
        LeadMessageCampaignsTable.selectAll().count()
    }

    fun countCampaignsWithStatus(status: LeadCampaignStatus): Long = dbQuery {
        LeadMessageCampaignsTable.selectAll()
            .where { LeadMessageCampaignsTable.status eq status.name }
            .count()
    }

    fun countCampaignsCreatedOnLocalDate(day: LocalDate, zone: ZoneId): Long = dbQuery {
        val start = day.atStartOfDay(zone).toLocalDateTime()
        val end = day.plusDays(1).atStartOfDay(zone).toLocalDateTime()
        LeadMessageCampaignsTable.selectAll()
            .where {
                LeadMessageCampaignsTable.createdAt greaterEq start and
                    (LeadMessageCampaignsTable.createdAt less end)
            }
            .count()
    }

    /**
     * Lista campanhas com dados do lead; filtros opcionais; [limit] máximo coerced.
     */
    fun listCampaignsWithLeadFiltered(
        status: LeadCampaignStatus?,
        batchId: Long?,
        lojaContains: String?,
        limit: Int,
        offset: Int,
    ): Pair<List<CampaignLeadJoinRow>, Long> = dbQuery {
        val base = (LeadMessageCampaignsTable innerJoin LeadsTable)
        val conditions = mutableListOf<Op<Boolean>>()
        status?.let { conditions += (LeadMessageCampaignsTable.status eq it.name) }
        batchId?.let { bid ->
            conditions += (LeadMessageCampaignsTable.batchId eq EntityID(bid, DailyBatchesTable))
        }
        sanitizeLojaSearch(lojaContains)?.let { pattern ->
            conditions += (
                (LeadsTable.nomeFantasia like pattern) or
                    (LeadsTable.razaoSocial like pattern)
                )
        }
        val whereClause: Op<Boolean> = when (conditions.size) {
            0 -> Op.TRUE
            1 -> conditions.first()
            else -> conditions.reduce { acc, op -> acc and op }
        }
        val total = base.selectAll().where { whereClause }.count()
        val rows = base.selectAll()
            .where { whereClause }
            .orderBy(LeadMessageCampaignsTable.id, SortOrder.DESC)
            .limit(limit)
            .offset(offset.toLong())
            .map { it.toCampaignLeadJoinRow() }
        rows to total
    }

    /**
     * Campanhas com envio aceito pela API Meta neste dia civil (timezone [zone]), via [sentAt].
     */
    fun countSuccessfulSendsOnLocalDate(day: LocalDate, zone: ZoneId): Long = dbQuery {
        val start = day.atStartOfDay(zone).toLocalDateTime()
        val end = day.plusDays(1).atStartOfDay(zone).toLocalDateTime()
        LeadMessageCampaignsTable.selectAll()
            .where {
                LeadMessageCampaignsTable.sentAt.isNotNull() and
                    (LeadMessageCampaignsTable.sentAt greaterEq start) and
                    (LeadMessageCampaignsTable.sentAt less end)
            }
            .count()
    }

    fun findLatestSuccessfulSentAt(): LocalDateTime? = dbQuery {
        LeadMessageCampaignsTable.select(LeadMessageCampaignsTable.sentAt)
            .where { LeadMessageCampaignsTable.sentAt.isNotNull() }
            .orderBy(LeadMessageCampaignsTable.sentAt, SortOrder.DESC)
            .limit(1)
            .firstOrNull()
            ?.get(LeadMessageCampaignsTable.sentAt)
    }

    fun findPendingByBatchId(batchId: Long): List<LeadMessageCampaignRecord> = dbQuery {
        val bid = EntityID(batchId, DailyBatchesTable)
        LeadMessageCampaignsTable.selectAll()
            .where {
                (LeadMessageCampaignsTable.batchId eq bid) and
                    (LeadMessageCampaignsTable.status eq LeadCampaignStatus.PENDING.name)
            }
            .orderBy(LeadMessageCampaignsTable.id, SortOrder.ASC)
            .map { it.toRecord() }
    }

    fun findPendingUnscheduledByBatchId(batchId: Long): List<LeadMessageCampaignRecord> = dbQuery {
        val bid = EntityID(batchId, DailyBatchesTable)
        LeadMessageCampaignsTable.selectAll()
            .where {
                (LeadMessageCampaignsTable.batchId eq bid) and
                    (LeadMessageCampaignsTable.status eq LeadCampaignStatus.PENDING.name) and
                    LeadMessageCampaignsTable.scheduledAt.isNull()
            }
            .orderBy(LeadMessageCampaignsTable.id, SortOrder.ASC)
            .map { it.toRecord() }
    }

    fun countPendingScheduledByBatchId(batchId: Long): Int = dbQuery {
        val bid = EntityID(batchId, DailyBatchesTable)
        LeadMessageCampaignsTable.selectAll()
            .where {
                (LeadMessageCampaignsTable.batchId eq bid) and
                    (LeadMessageCampaignsTable.status eq LeadCampaignStatus.PENDING.name) and
                    LeadMessageCampaignsTable.scheduledAt.isNotNull()
            }
            .count()
            .toInt()
    }

    /**
     * Agenda [scheduledAt] em cadeia com atrasos aleatórios (minutos) entre itens; primeira campanha em [startTime].
     */
    fun schedulePendingUnscheduledStaggered(
        batchId: Long,
        startTime: LocalDateTime,
        minDelayMinutes: Int,
        maxDelayMinutes: Int,
        now: LocalDateTime,
    ): BatchStaggeredScheduleResult = dbQuery {
        val unscheduled = findPendingUnscheduledByBatchId(batchId)
        if (unscheduled.isEmpty()) {
            return@dbQuery BatchStaggeredScheduleResult(emptyList())
        }
        val lo = minOf(minDelayMinutes, maxDelayMinutes).coerceAtLeast(0)
        val hi = maxOf(minDelayMinutes, maxDelayMinutes).coerceAtLeast(lo)
        val slots = mutableListOf<StaggeredScheduleSlot>()
        var current = startTime
        unscheduled.forEachIndexed { index, row ->
            val delayAfterPrev = if (index == 0) {
                0
            } else {
                Random.nextInt(lo, hi + 1)
            }
            if (index > 0) {
                current = current.plusMinutes(delayAfterPrev.toLong())
            }
            LeadMessageCampaignsTable.update({ LeadMessageCampaignsTable.id eq row.id }) {
                it[LeadMessageCampaignsTable.scheduledAt] = current
                it[LeadMessageCampaignsTable.updatedAt] = now
            }
            slots.add(StaggeredScheduleSlot(row.id, current, delayAfterPrev))
        }
        BatchStaggeredScheduleResult(slots)
    }

    private fun eligibleDispatchWhere(now: LocalDateTime, staleBefore: LocalDateTime): Op<Boolean> =
        (LeadMessageCampaignsTable.status eq LeadCampaignStatus.PENDING.name) and
            LeadMessageCampaignsTable.scheduledAt.isNotNull() and
            (LeadMessageCampaignsTable.scheduledAt lessEq now) and
            (
                LeadMessageCampaignsTable.processingStartedAt.isNull() or
                    (LeadMessageCampaignsTable.processingStartedAt less staleBefore)
                )

    fun countEligibleForDispatch(now: LocalDateTime, staleBefore: LocalDateTime): Long = dbQuery {
        LeadMessageCampaignsTable.selectAll()
            .where { eligibleDispatchWhere(now, staleBefore) }
            .count()
    }

    fun peekEligibleForDispatch(now: LocalDateTime, staleBefore: LocalDateTime): WhatsAppEngineCampaignPointer? = dbQuery {
        LeadMessageCampaignsTable.selectAll()
            .where { eligibleDispatchWhere(now, staleBefore) }
            .orderBy(LeadMessageCampaignsTable.scheduledAt, SortOrder.ASC)
            .orderBy(LeadMessageCampaignsTable.id, SortOrder.ASC)
            .limit(1)
            .firstOrNull()
            ?.toEnginePointer()
    }

    fun countPendingWithoutSchedule(): Long = dbQuery {
        LeadMessageCampaignsTable.selectAll()
            .where {
                (LeadMessageCampaignsTable.status eq LeadCampaignStatus.PENDING.name) and
                    LeadMessageCampaignsTable.scheduledAt.isNull()
            }
            .count()
    }

    fun countPendingScheduledStrictlyAfter(now: LocalDateTime): Long = dbQuery {
        LeadMessageCampaignsTable.selectAll()
            .where {
                (LeadMessageCampaignsTable.status eq LeadCampaignStatus.PENDING.name) and
                    LeadMessageCampaignsTable.scheduledAt.isNotNull() and
                    (LeadMessageCampaignsTable.scheduledAt greater now)
            }
            .count()
    }

    fun countPendingWithSchedule(): Long = dbQuery {
        LeadMessageCampaignsTable.selectAll()
            .where {
                (LeadMessageCampaignsTable.status eq LeadCampaignStatus.PENDING.name) and
                    LeadMessageCampaignsTable.scheduledAt.isNotNull()
            }
            .count()
    }

    /** Próxima janela futura (para “aguardando próximo envio”). */
    fun peekNextFutureScheduledPending(now: LocalDateTime): Pair<LocalDateTime, WhatsAppEngineCampaignPointer>? = dbQuery {
        val row = LeadMessageCampaignsTable.selectAll()
            .where {
                (LeadMessageCampaignsTable.status eq LeadCampaignStatus.PENDING.name) and
                    LeadMessageCampaignsTable.scheduledAt.isNotNull() and
                    (LeadMessageCampaignsTable.scheduledAt greater now)
            }
            .orderBy(LeadMessageCampaignsTable.scheduledAt, SortOrder.ASC)
            .orderBy(LeadMessageCampaignsTable.id, SortOrder.ASC)
            .limit(1)
            .firstOrNull() ?: return@dbQuery null
        val at = row[LeadMessageCampaignsTable.scheduledAt]!!
        at to row.toEnginePointer()
    }

    fun peekSendingCampaign(): WhatsAppEngineCampaignPointer? = dbQuery {
        LeadMessageCampaignsTable.selectAll()
            .where { LeadMessageCampaignsTable.status eq LeadCampaignStatus.SENDING.name }
            .orderBy(LeadMessageCampaignsTable.id, SortOrder.ASC)
            .limit(1)
            .firstOrNull()
            ?.toEnginePointer()
    }

    /**
     * Reserva uma campanha PENDING com [scheduledAt] <= [now] e lock expirado ou ausente.
     */
    fun tryClaimNextDispatchableCampaign(now: LocalDateTime, staleBefore: LocalDateTime): Long? = dbQuery {
        val eligibleWhere = eligibleDispatchWhere(now, staleBefore)
        val candidate = LeadMessageCampaignsTable.selectAll()
            .where { eligibleWhere }
            .orderBy(LeadMessageCampaignsTable.scheduledAt, SortOrder.ASC)
            .orderBy(LeadMessageCampaignsTable.id, SortOrder.ASC)
            .limit(1)
            .firstOrNull() ?: return@dbQuery null
        val id = candidate[LeadMessageCampaignsTable.id].value
        val n = LeadMessageCampaignsTable.update(
            {
                (LeadMessageCampaignsTable.id eq id) and eligibleWhere
            },
        ) {
            it[LeadMessageCampaignsTable.status] = LeadCampaignStatus.SENDING.name
            it[LeadMessageCampaignsTable.processingStartedAt] = now
            it[LeadMessageCampaignsTable.lastAttemptAt] = now
            it[LeadMessageCampaignsTable.updatedAt] = now
        }
        if (n == 0) null else id
    }

    /**
     * Libera lock de processamento. Se a campanha ainda estiver em SENDING (ex.: exceção antes de SENT/FAILED),
     * volta para PENDING para nova tentativa — evita segundo worker reinvocar o mesmo envio em paralelo.
     */
    fun clearProcessingLock(campaignId: Long, now: LocalDateTime): Boolean = dbQuery {
        val cur = LeadMessageCampaignsTable.selectAll()
            .where { LeadMessageCampaignsTable.id eq campaignId }
            .firstOrNull() ?: return@dbQuery false
        val st = cur[LeadMessageCampaignsTable.status]
        val terminal = st == LeadCampaignStatus.FAILED.name ||
            st == LeadCampaignStatus.SENT.name ||
            st == LeadCampaignStatus.DELIVERED.name ||
            st == LeadCampaignStatus.READ.name ||
            st == LeadCampaignStatus.RESPONDED.name ||
            st == LeadCampaignStatus.STOPPED.name
        val revertSendingToPending = !terminal && st == LeadCampaignStatus.SENDING.name
        LeadMessageCampaignsTable.update({ LeadMessageCampaignsTable.id eq campaignId }) {
            it[LeadMessageCampaignsTable.processingStartedAt] = null
            it[LeadMessageCampaignsTable.updatedAt] = now
            if (revertSendingToPending) {
                it[LeadMessageCampaignsTable.status] = LeadCampaignStatus.PENDING.name
            }
        } > 0
    }

    fun deferCampaignScheduledTime(campaignId: Long, newScheduledAt: LocalDateTime, now: LocalDateTime): Boolean = dbQuery {
        LeadMessageCampaignsTable.update({ LeadMessageCampaignsTable.id eq campaignId }) {
            it[LeadMessageCampaignsTable.scheduledAt] = newScheduledAt
            it[LeadMessageCampaignsTable.processingStartedAt] = null
            it[LeadMessageCampaignsTable.status] = LeadCampaignStatus.PENDING.name
            it[LeadMessageCampaignsTable.updatedAt] = now
        } > 0
    }

    /** Suporte a testes: força [scheduledAt] (ex.: alinhar múltiplas campanhas para teste de cota). */
    fun setScheduledAt(campaignId: Long, at: LocalDateTime, now: LocalDateTime): Boolean = dbQuery {
        LeadMessageCampaignsTable.update({ LeadMessageCampaignsTable.id eq campaignId }) {
            it[LeadMessageCampaignsTable.scheduledAt] = at
            it[LeadMessageCampaignsTable.updatedAt] = now
        } > 0
    }

    fun findByLeadId(leadId: Long): List<LeadMessageCampaignRecord> = dbQuery {
        val lid = EntityID(leadId, LeadsTable)
        LeadMessageCampaignsTable.selectAll()
            .where { LeadMessageCampaignsTable.leadId eq lid }
            .orderBy(LeadMessageCampaignsTable.id, SortOrder.DESC)
            .map { it.toRecord() }
    }

    /**
     * Bloqueia nova campanha se já existir uma linha (lead + batch + template) com status diferente de FAILED.
     */
    fun hasActiveCampaignForLeadBatchTemplate(
        leadId: Long,
        batchId: Long,
        templateName: String,
    ): Boolean = dbQuery {
        val lid = EntityID(leadId, LeadsTable)
        val bid = EntityID(batchId, DailyBatchesTable)
        LeadMessageCampaignsTable.selectAll()
            .where {
                (LeadMessageCampaignsTable.leadId eq lid) and
                    (LeadMessageCampaignsTable.batchId eq bid) and
                    (LeadMessageCampaignsTable.templateName eq templateName) and
                    (LeadMessageCampaignsTable.status neq LeadCampaignStatus.FAILED.name)
            }
            .count() > 0
    }

    fun updateStatus(campaignId: Long, status: LeadCampaignStatus, now: LocalDateTime): Boolean = dbQuery {
        val n = LeadMessageCampaignsTable.update({ LeadMessageCampaignsTable.id eq campaignId }) {
            it[LeadMessageCampaignsTable.status] = status.name
            it[LeadMessageCampaignsTable.updatedAt] = now
        }
        n > 0
    }

    fun updateTimestamps(
        campaignId: Long,
        now: LocalDateTime,
        sentAt: LocalDateTime? = null,
        deliveredAt: LocalDateTime? = null,
        readAt: LocalDateTime? = null,
        respondedAt: LocalDateTime? = null,
        failedAt: LocalDateTime? = null,
        nextFollowupAt: LocalDateTime? = null,
    ): Boolean = dbQuery {
        val n = LeadMessageCampaignsTable.update({ LeadMessageCampaignsTable.id eq campaignId }) {
            sentAt?.let { t -> it[LeadMessageCampaignsTable.sentAt] = t }
            deliveredAt?.let { t -> it[LeadMessageCampaignsTable.deliveredAt] = t }
            readAt?.let { t -> it[LeadMessageCampaignsTable.readAt] = t }
            respondedAt?.let { t -> it[LeadMessageCampaignsTable.respondedAt] = t }
            failedAt?.let { t -> it[LeadMessageCampaignsTable.failedAt] = t }
            nextFollowupAt?.let { t -> it[LeadMessageCampaignsTable.nextFollowupAt] = t }
            it[LeadMessageCampaignsTable.updatedAt] = now
        }
        n > 0
    }

    fun findById(id: Long): LeadMessageCampaignRecord? = dbQuery {
        LeadMessageCampaignsTable.selectAll()
            .where { LeadMessageCampaignsTable.id eq id }
            .firstOrNull()
            ?.toRecord()
    }

    fun findByWaMessageId(waMessageId: String): LeadMessageCampaignRecord? = dbQuery {
        LeadMessageCampaignsTable.selectAll()
            .where { LeadMessageCampaignsTable.waMessageId eq waMessageId }
            .firstOrNull()
            ?.toRecord()
    }

    /**
     * Campanha mais recente ainda sem [respondedAt] em estado pós-envio (evita reassociar respostas antigas).
     */
    fun findLatestOutboundCampaignForLead(leadId: Long): LeadMessageCampaignRecord? = dbQuery {
        val lid = EntityID(leadId, LeadsTable)
        val statuses = listOf(
            LeadCampaignStatus.SENT.name,
            LeadCampaignStatus.DELIVERED.name,
            LeadCampaignStatus.READ.name,
        )
        LeadMessageCampaignsTable.selectAll()
            .where {
                (LeadMessageCampaignsTable.leadId eq lid) and
                    (LeadMessageCampaignsTable.status inList statuses) and
                    LeadMessageCampaignsTable.respondedAt.isNull()
            }
            .orderBy(LeadMessageCampaignsTable.id, SortOrder.DESC)
            .limit(1)
            .firstOrNull()
            ?.toRecord()
    }

    /**
     * Fallback para inbound sem `context.id`: escolhe a campanha pós-envio mais recente dentre leads
     * com telefone normalizado em [phoneCandidates].
     */
    fun findLatestOutboundCampaignByPhoneCandidates(phoneCandidates: List<String>): LeadMessageCampaignRecord? = dbQuery {
        if (phoneCandidates.isEmpty()) return@dbQuery null
        val statuses = listOf(
            LeadCampaignStatus.SENT.name,
            LeadCampaignStatus.DELIVERED.name,
            LeadCampaignStatus.READ.name,
        )
        (LeadMessageCampaignsTable innerJoin LeadsTable)
            .selectAll()
            .where {
                (LeadsTable.telefoneNormalizado inList phoneCandidates) and
                    (LeadMessageCampaignsTable.status inList statuses) and
                    LeadMessageCampaignsTable.respondedAt.isNull()
            }
            .orderBy(LeadMessageCampaignsTable.updatedAt, SortOrder.DESC)
            .orderBy(LeadMessageCampaignsTable.id, SortOrder.DESC)
            .limit(1)
            .firstOrNull()
            ?.toRecord()
    }

    fun markCampaignResponded(campaignId: Long, respondedAt: LocalDateTime, now: LocalDateTime): Boolean = dbQuery {
        val n = LeadMessageCampaignsTable.update({ LeadMessageCampaignsTable.id eq campaignId }) {
            it[LeadMessageCampaignsTable.status] = LeadCampaignStatus.RESPONDED.name
            it[LeadMessageCampaignsTable.respondedAt] = respondedAt
            it[LeadMessageCampaignsTable.updatedAt] = now
        }
        n > 0
    }

    fun patchCampaignFromWebhook(
        campaignId: Long,
        newStatus: LeadCampaignStatus,
        now: LocalDateTime,
        sentAt: LocalDateTime? = null,
        deliveredAt: LocalDateTime? = null,
        readAt: LocalDateTime? = null,
        failedAt: LocalDateTime? = null,
        failureReason: String? = null,
        failureCategoryStored: String? = null,
    ): Boolean = dbQuery {
        val n = LeadMessageCampaignsTable.update({ LeadMessageCampaignsTable.id eq campaignId }) {
            it[LeadMessageCampaignsTable.status] = newStatus.name
            sentAt?.let { t -> it[LeadMessageCampaignsTable.sentAt] = t }
            deliveredAt?.let { t -> it[LeadMessageCampaignsTable.deliveredAt] = t }
            readAt?.let { t -> it[LeadMessageCampaignsTable.readAt] = t }
            failedAt?.let { t -> it[LeadMessageCampaignsTable.failedAt] = t }
            failureReason?.let { r -> it[LeadMessageCampaignsTable.failureReason] = r }
            failureCategoryStored?.let { c -> it[LeadMessageCampaignsTable.failureCategory] = c }
            it[LeadMessageCampaignsTable.updatedAt] = now
        }
        n > 0
    }

    fun updateAfterSendSuccess(
        campaignId: Long,
        waMessageId: String,
        sentAt: LocalDateTime,
        now: LocalDateTime,
    ): Boolean = dbQuery {
        val n = LeadMessageCampaignsTable.update({ LeadMessageCampaignsTable.id eq campaignId }) {
            it[LeadMessageCampaignsTable.status] = LeadCampaignStatus.SENT.name
            it[LeadMessageCampaignsTable.waMessageId] = waMessageId
            it[LeadMessageCampaignsTable.sentAt] = sentAt
            it[LeadMessageCampaignsTable.failedAt] = null
            it[LeadMessageCampaignsTable.failureReason] = null
            it[LeadMessageCampaignsTable.failureCategory] = null
            it[LeadMessageCampaignsTable.processingStartedAt] = null
            it[LeadMessageCampaignsTable.lastAttemptAt] = now
            it[LeadMessageCampaignsTable.updatedAt] = now
        }
        n > 0
    }

    fun updateAfterSendFailure(
        campaignId: Long,
        failedAt: LocalDateTime,
        failureReason: String?,
        failureCategory: WhatsAppFailureCategory?,
        now: LocalDateTime,
    ): Boolean = dbQuery {
        val n = LeadMessageCampaignsTable.update({ LeadMessageCampaignsTable.id eq campaignId }) {
            it[LeadMessageCampaignsTable.status] = LeadCampaignStatus.FAILED.name
            it[LeadMessageCampaignsTable.failedAt] = failedAt
            it[LeadMessageCampaignsTable.failureReason] = failureReason
            it[LeadMessageCampaignsTable.failureCategory] = failureCategory?.name
            it[LeadMessageCampaignsTable.waMessageId] = null
            it[LeadMessageCampaignsTable.processingStartedAt] = null
            it[LeadMessageCampaignsTable.lastAttemptAt] = now
            it[LeadMessageCampaignsTable.updatedAt] = now
        }
        n > 0
    }

    private fun ResultRow.toEnginePointer(): WhatsAppEngineCampaignPointer {
        val batchRef = this[LeadMessageCampaignsTable.batchId]
        return WhatsAppEngineCampaignPointer(
            campaignId = this[LeadMessageCampaignsTable.id].value,
            leadId = this[LeadMessageCampaignsTable.leadId].value,
            batchId = batchRef?.value,
        )
    }

    private fun ResultRow.toCampaignLeadJoinRow(): CampaignLeadJoinRow {
        val batchRef = this[LeadMessageCampaignsTable.batchId]
        return CampaignLeadJoinRow(
            campaignId = this[LeadMessageCampaignsTable.id].value,
            batchId = batchRef?.value,
            leadId = this[LeadMessageCampaignsTable.leadId].value,
            templateName = this[LeadMessageCampaignsTable.templateName],
            templateLanguage = this[LeadMessageCampaignsTable.templateLanguage],
            status = leadCampaignStatusFromStored(this[LeadMessageCampaignsTable.status]),
            waMessageId = this[LeadMessageCampaignsTable.waMessageId],
            attemptNumber = this[LeadMessageCampaignsTable.attemptNumber],
            followupStep = this[LeadMessageCampaignsTable.followupStep],
            sentAt = this[LeadMessageCampaignsTable.sentAt],
            deliveredAt = this[LeadMessageCampaignsTable.deliveredAt],
            readAt = this[LeadMessageCampaignsTable.readAt],
            respondedAt = this[LeadMessageCampaignsTable.respondedAt],
            failedAt = this[LeadMessageCampaignsTable.failedAt],
            failureReason = this[LeadMessageCampaignsTable.failureReason],
            failureCategory = WhatsAppFailureCategory.fromStored(this[LeadMessageCampaignsTable.failureCategory]),
            scheduledAt = this[LeadMessageCampaignsTable.scheduledAt],
            lastAttemptAt = this[LeadMessageCampaignsTable.lastAttemptAt],
            nomeFantasia = this[LeadsTable.nomeFantasia],
            razaoSocial = this[LeadsTable.razaoSocial],
            telefoneOriginal = this[LeadsTable.telefoneOriginal],
            telefoneNormalizado = this[LeadsTable.telefoneNormalizado],
            leadStatus = this[LeadsTable.status],
            leadRespondeu = this[LeadsTable.respondeu],
            leadInteressado = this[LeadsTable.interessado],
        )
    }

    private fun ResultRow.toRecord(): LeadMessageCampaignRecord {
        val batchRef = this[LeadMessageCampaignsTable.batchId]
        return LeadMessageCampaignRecord(
            id = this[LeadMessageCampaignsTable.id].value,
            leadId = this[LeadMessageCampaignsTable.leadId].value,
            batchId = batchRef?.value,
            templateName = this[LeadMessageCampaignsTable.templateName],
            templateLanguage = this[LeadMessageCampaignsTable.templateLanguage],
            status = leadCampaignStatusFromStored(this[LeadMessageCampaignsTable.status]),
            waMessageId = this[LeadMessageCampaignsTable.waMessageId],
            attemptNumber = this[LeadMessageCampaignsTable.attemptNumber],
            followupStep = this[LeadMessageCampaignsTable.followupStep],
            sentAt = this[LeadMessageCampaignsTable.sentAt],
            deliveredAt = this[LeadMessageCampaignsTable.deliveredAt],
            readAt = this[LeadMessageCampaignsTable.readAt],
            respondedAt = this[LeadMessageCampaignsTable.respondedAt],
            failedAt = this[LeadMessageCampaignsTable.failedAt],
            failureReason = this[LeadMessageCampaignsTable.failureReason],
            failureCategory = WhatsAppFailureCategory.fromStored(this[LeadMessageCampaignsTable.failureCategory]),
            scheduledAt = this[LeadMessageCampaignsTable.scheduledAt],
            lastAttemptAt = this[LeadMessageCampaignsTable.lastAttemptAt],
            processingStartedAt = this[LeadMessageCampaignsTable.processingStartedAt],
            nextFollowupAt = this[LeadMessageCampaignsTable.nextFollowupAt],
            createdAt = this[LeadMessageCampaignsTable.createdAt],
            updatedAt = this[LeadMessageCampaignsTable.updatedAt],
        )
    }

    private companion object {
        /** Padrão seguro para LIKE (% removidos do termo). */
        private fun sanitizeLojaSearch(raw: String?): String? {
            val t = raw?.trim()?.take(64) ?: return null
            if (t.isEmpty()) return null
            val safe = t.replace("%", "").replace("_", "")
            if (safe.isEmpty()) return null
            return "%$safe%"
        }

        private fun <T> dbQuery(block: () -> T): T {
            return if (TransactionManager.currentOrNull() != null) {
                block()
            } else {
                transaction { block() }
            }
        }
    }
}
