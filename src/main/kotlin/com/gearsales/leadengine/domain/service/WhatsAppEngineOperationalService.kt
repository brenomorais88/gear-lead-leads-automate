package com.gearsales.leadengine.domain.service

import com.gearsales.leadengine.config.WhatsAppAppConfig
import com.gearsales.leadengine.config.WhatsAppEffectiveConfig
import com.gearsales.leadengine.database.repositories.LeadMessageCampaignRepository
import com.gearsales.leadengine.database.repositories.WhatsAppEngineCampaignPointer
import com.gearsales.leadengine.domain.model.LeadCampaignStatus
import com.gearsales.leadengine.domain.model.WhatsAppEngineStatus
import com.gearsales.leadengine.web.dto.WhatsAppEngineStatusResponse
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Métricas agregadas usadas para decidir o estado do motor (testável sem DB).
 */
data class WhatsAppEngineMetricsInput(
    val sentToday: Long,
    val dailyLimit: Int,
    val servicePaused: Boolean,
    val pendingCount: Long,
    val sendingCount: Long,
    val failedCount: Long,
    val scheduledCount: Long,
    val pendingWithoutSchedule: Long,
    val pendingScheduledFuture: Long,
    val eligibleCount: Long,
    val nextFuture: Pair<LocalDateTime, WhatsAppEngineCampaignPointer>?,
    val sendingPtr: WhatsAppEngineCampaignPointer?,
    val eligiblePtr: WhatsAppEngineCampaignPointer?,
)

object WhatsAppEngineStatusResolver {

    fun resolve(
        now: LocalDateTime,
        metrics: WhatsAppEngineMetricsInput,
        misconfigurationSummary: String?,
    ): Pair<WhatsAppEngineStatus, String> {
        if (misconfigurationSummary != null) {
            return WhatsAppEngineStatus.MISCONFIGURED to
                "Configuração inválida: $misconfigurationSummary"
        }
        if (metrics.servicePaused) {
            return WhatsAppEngineStatus.PAUSED to "Serviço pausado manualmente. Retome pelo botão abaixo ou em /whatsapp/config."
        }
        if (metrics.sendingCount > 0) {
            val p = metrics.sendingPtr
            val detail = if (p != null) {
                "Processando campanha #${p.campaignId} (lead #${p.leadId}" +
                    (p.batchId?.let { ", lote #$it" } ?: "") + ")."
            } else {
                "Processando envio de campanha."
            }
            return WhatsAppEngineStatus.PROCESSING to detail
        }
        val remaining = (metrics.dailyLimit - metrics.sentToday).coerceAtLeast(0).toInt()
        val backlog = metrics.pendingCount + metrics.sendingCount
        if (remaining <= 0 && backlog > 0) {
            return WhatsAppEngineStatus.DAILY_LIMIT_REACHED to
                "Limite diário atingido: ${metrics.sentToday} de ${metrics.dailyLimit} mensagens enviadas hoje; fila aguarda próximo dia."
        }
        if (metrics.pendingWithoutSchedule > 0) {
            return WhatsAppEngineStatus.PENDING_WITHOUT_SCHEDULE to
                "Existem campanhas pendentes sem agendamento válido; use “Disparar lote” para entrar na fila."
        }
        if (remaining > 0 && metrics.eligibleCount > 0) {
            return WhatsAppEngineStatus.READY_TO_SEND to
                "Pronto para enviar a próxima campanha (há itens elegíveis e quota disponível)."
        }
        if (remaining > 0 && metrics.pendingScheduledFuture > 0 && metrics.eligibleCount == 0L) {
            val slot = metrics.nextFuture
            val mins = slot?.let { ChronoUnit.MINUTES.between(now, it.first).coerceAtLeast(0) }
            val tail = if (slot != null && mins != null) {
                " Próximo disparo previsto em aproximadamente $mins minutos."
            } else {
                ""
            }
            return WhatsAppEngineStatus.WAITING_NEXT_SEND to
                ("Aguardando o horário programado para o próximo envio.$tail").trim()
        }
        if (metrics.pendingCount == 0L && metrics.sendingCount == 0L) {
            val quotaNote = if (remaining <= 0) {
                " Cota de hoje esgotada; nada na fila."
            } else {
                ""
            }
            return WhatsAppEngineStatus.IDLE to
                ("Ocioso: nenhuma campanha pendente ou programada.$quotaNote").trim()
        }
        return WhatsAppEngineStatus.IDLE to
            "Ocioso: nenhuma ação imediata necessária."
    }
}

private object WhatsAppEngineStatusTransitionLogger {
    private val last = AtomicReference<WhatsAppEngineStatus?>(null)
    private val log = LoggerFactory.getLogger("WhatsAppEngine")

    fun onComputed(status: WhatsAppEngineStatus) {
        val prev = last.getAndSet(status)
        if (prev != null && prev != status) {
            log.info("WA engine status changed: {} -> {}", prev, status)
            if (status == WhatsAppEngineStatus.MISCONFIGURED) {
                log.warn("WA engine misconfigured (ver misconfigurationReason no JSON /status)")
            }
            if (status == WhatsAppEngineStatus.PAUSED) {
                log.info("WA engine paused (disparo manualmente suspenso)")
            }
        }
    }
}

class WhatsAppEngineOperationalService(
    private val whatsappConfig: WhatsAppAppConfig,
    private val campaignRepository: LeadMessageCampaignRepository,
) {
    private val zone: ZoneId = ZoneId.systemDefault()
    private val log = LoggerFactory.getLogger(javaClass)

    fun getStatus(): WhatsAppEngineStatusResponse = try {
        computeStatusInternal()
    } catch (e: Exception) {
        log.error("WA engine status ERROR", e)
        val now = LocalDateTime.now()
        run {
            val eff = runCatching { whatsappConfig.effective() }.getOrNull()
            WhatsAppEngineStatusResponse(
                currentStatus = WhatsAppEngineStatus.ERROR,
                statusMessage = "Falha ao calcular o estado do motor: ${e.message ?: e.javaClass.simpleName}",
                workerEnabled = true,
                servicePaused = eff?.servicePaused ?: false,
                now = now.toString(),
                dailyLimit = eff?.dailySendLimit ?: 0,
                sentToday = 0,
                remainingQuotaToday = 0,
                pendingCampaigns = 0,
                scheduledCampaigns = 0,
                processingCampaigns = 0,
                failedCampaigns = 0,
                sendDelayMinMinutes = eff?.sendDelayMinMinutes ?: 0,
                sendDelayMaxMinutes = eff?.sendDelayMaxMinutes ?: 0,
                workerPollIntervalSeconds = eff?.workerPollIntervalSeconds ?: 30L,
                defaultTemplateName = eff?.defaultTemplateName,
                defaultTemplateLanguage = eff?.defaultTemplateLanguage,
                phoneNumberIdMasked = eff?.phoneNumberId?.let { maskPhoneId(it) },
                misconfigurationReason = null,
            )
        }
    }

    private fun computeStatusInternal(): WhatsAppEngineStatusResponse {
        val eff = whatsappConfig.effective()
        val now = LocalDateTime.now()
        val today = LocalDate.now(zone)
        val staleBefore = now.minusSeconds(eff.processingStaleSeconds)

        val misconfigurationReason = collectMisconfigurationReasons(eff)
        val sentToday = campaignRepository.countSuccessfulSendsOnLocalDate(today, zone)
        val dailyLimit = eff.dailySendLimit
        val remaining = (dailyLimit - sentToday).coerceAtLeast(0).toInt()

        val pendingCount = campaignRepository.countCampaignsWithStatus(LeadCampaignStatus.PENDING)
        val sendingCount = campaignRepository.countCampaignsWithStatus(LeadCampaignStatus.SENDING)
        val failedCount =
            campaignRepository.countCampaignsWithStatus(LeadCampaignStatus.FAILED) +
                campaignRepository.countCampaignsWithStatus(LeadCampaignStatus.STOPPED)
        val scheduledCount = campaignRepository.countPendingWithSchedule()
        val pendingWithoutSchedule = campaignRepository.countPendingWithoutSchedule()
        val pendingScheduledFuture = campaignRepository.countPendingScheduledStrictlyAfter(now)
        val eligibleCount = campaignRepository.countEligibleForDispatch(now, staleBefore)
        val nextFuture = campaignRepository.peekNextFutureScheduledPending(now)
        val sendingPtr = campaignRepository.peekSendingCampaign()
        val eligiblePtr = campaignRepository.peekEligibleForDispatch(now, staleBefore)

        val metrics = WhatsAppEngineMetricsInput(
            sentToday = sentToday,
            dailyLimit = dailyLimit,
            servicePaused = eff.servicePaused,
            pendingCount = pendingCount,
            sendingCount = sendingCount,
            failedCount = failedCount,
            scheduledCount = scheduledCount,
            pendingWithoutSchedule = pendingWithoutSchedule,
            pendingScheduledFuture = pendingScheduledFuture,
            eligibleCount = eligibleCount,
            nextFuture = nextFuture,
            sendingPtr = sendingPtr,
            eligiblePtr = eligiblePtr,
        )

        val (status, message) = WhatsAppEngineStatusResolver.resolve(
            now = now,
            metrics = metrics,
            misconfigurationSummary = misconfigurationReason,
        )

        WhatsAppEngineStatusTransitionLogger.onComputed(status)

        val nextFromFuture = nextFuture?.second

        val waitingMins = if (status == WhatsAppEngineStatus.WAITING_NEXT_SEND && nextFuture != null) {
            ChronoUnit.MINUTES.between(now, nextFuture.first).coerceAtLeast(0)
        } else {
            null
        }

        val nextCamp = when (status) {
            WhatsAppEngineStatus.READY_TO_SEND -> eligiblePtr
            WhatsAppEngineStatus.WAITING_NEXT_SEND -> nextFromFuture
            else -> null
        }

        val nextScheduledAtIso =
            if (status == WhatsAppEngineStatus.WAITING_NEXT_SEND) nextFuture?.first?.toString() else null

        return WhatsAppEngineStatusResponse(
            currentStatus = status,
            statusMessage = message,
            workerEnabled = true,
            servicePaused = eff.servicePaused,
            now = now.toString(),
            dailyLimit = dailyLimit,
            sentToday = sentToday,
            remainingQuotaToday = remaining,
            pendingCampaigns = pendingCount,
            scheduledCampaigns = scheduledCount,
            processingCampaigns = sendingCount,
            failedCampaigns = failedCount,
            sendDelayMinMinutes = eff.sendDelayMinMinutes,
            sendDelayMaxMinutes = eff.sendDelayMaxMinutes,
            workerPollIntervalSeconds = eff.workerPollIntervalSeconds,
            defaultTemplateName = eff.defaultTemplateName,
            defaultTemplateLanguage = eff.defaultTemplateLanguage,
            phoneNumberIdMasked = maskPhoneId(eff.phoneNumberId),
            nextScheduledAt = nextScheduledAtIso,
            nextCampaignId = nextCamp?.campaignId,
            nextLeadId = nextCamp?.leadId,
            nextBatchId = nextCamp?.batchId,
            currentProcessingCampaignId = sendingPtr?.campaignId,
            currentProcessingLeadId = sendingPtr?.leadId,
            currentProcessingBatchId = sendingPtr?.batchId,
            waitingDelayMinutesRemaining = waitingMins,
            misconfigurationReason = misconfigurationReason,
        )
    }

    private fun collectMisconfigurationReasons(eff: WhatsAppEffectiveConfig): String? {
        val reasons = mutableListOf<String>()

        if (eff.accessToken.isNullOrBlank()) {
            reasons.add("WHATSAPP_ACCESS_TOKEN ausente ou vazio")
        }
        if (eff.phoneNumberId.isBlank()) {
            reasons.add("Phone Number ID não configurado (defina em /whatsapp/config)")
        }
        if (eff.defaultTemplateName.isBlank()) {
            reasons.add("template padrão (nome) inválido ou vazio")
        }
        if (eff.defaultTemplateLanguage.isBlank()) {
            reasons.add("template padrão (idioma) inválido ou vazio")
        }
        if (eff.dailySendLimit <= 0) {
            reasons.add("limite diário deve ser maior que zero")
        }
        if (eff.sendDelayMaxMinutes < eff.sendDelayMinMinutes) {
            reasons.add("delay máximo menor que mínimo (ajuste em /whatsapp/config)")
        }

        if (reasons.isEmpty()) return null
        return reasons.joinToString("; ")
    }

    private fun maskPhoneId(id: String): String {
        val t = id.trim()
        if (t.length < 4) return "***"
        return "***${t.takeLast(4)}"
    }
}
