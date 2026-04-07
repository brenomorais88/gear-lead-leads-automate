package com.gearsales.leadengine.domain.service

import com.gearsales.leadengine.config.WhatsAppAppConfig
import com.gearsales.leadengine.database.repositories.LeadMessageCampaignRepository
import com.gearsales.leadengine.web.dto.SendBatchCampaignResponse
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class SendBatchCampaignService(
    private val whatsappConfig: WhatsAppAppConfig,
    private val campaignRepository: LeadMessageCampaignRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun scheduleBatchSend(batchId: Long): SendBatchCampaignResponse {
        val eff = whatsappConfig.effective()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val dailyLimit = eff.dailySendLimit
        val alreadyBefore = campaignRepository.countSuccessfulSendsOnLocalDate(today, zone)
        val remainingAfter = (dailyLimit - alreadyBefore).coerceAtLeast(0).toInt()

        val pendingAll = campaignRepository.findPendingByBatchId(batchId)
        val totalPending = pendingAll.size
        val alreadyScheduled = campaignRepository.countPendingScheduledByBatchId(batchId)
        val unscheduled = campaignRepository.findPendingUnscheduledByBatchId(batchId)

        if (unscheduled.isEmpty()) {
            log.info(
                "WA batch schedule: batchId={} action=none totalPending={} alreadyScheduled={}",
                batchId,
                totalPending,
                alreadyScheduled,
            )
            return SendBatchCampaignResponse(
                batchId = batchId,
                dailyLimit = dailyLimit,
                alreadySentToday = alreadyBefore.toInt(),
                remainingQuotaToday = remainingAfter,
                totalPendingInBatch = totalPending,
                newlyScheduledCount = 0,
                alreadyScheduledPendingCount = alreadyScheduled,
                firstScheduledAt = null,
                lastScheduledAt = null,
                summaryMessage = when {
                    totalPending == 0 -> "Não há campanhas pendentes neste lote."
                    else -> "Todas as campanhas pendentes já estão na fila de envio. Aguarde o processamento automático."
                },
            )
        }

        val now = LocalDateTime.now()
        val result = campaignRepository.schedulePendingUnscheduledStaggered(
            batchId = batchId,
            startTime = now,
            minDelayMinutes = eff.sendDelayMinMinutes,
            maxDelayMinutes = eff.sendDelayMaxMinutes,
            now = now,
        )

        val first = result.slots.firstOrNull()?.scheduledAt?.toString()
        val last = result.slots.lastOrNull()?.scheduledAt?.toString()

        log.info(
            "WA batch schedule: batchId={} newlyScheduled={} delayMinMin={} delayMaxMin={} firstAt={} lastAt={}",
            batchId,
            result.slots.size,
            eff.sendDelayMinMinutes,
            eff.sendDelayMaxMinutes,
            first,
            last,
        )
        result.slots.forEach { slot ->
            log.info(
                "WA batch schedule slot: batchId={} campaignId={} scheduledAt={} delayAfterPrevMin={}",
                batchId,
                slot.campaignId,
                slot.scheduledAt,
                slot.delayAfterPreviousMinutes,
            )
        }

        return SendBatchCampaignResponse(
            batchId = batchId,
            dailyLimit = dailyLimit,
            alreadySentToday = alreadyBefore.toInt(),
            remainingQuotaToday = remainingAfter,
            totalPendingInBatch = totalPending,
            newlyScheduledCount = result.slots.size,
            alreadyScheduledPendingCount = alreadyScheduled + result.slots.size,
            firstScheduledAt = first,
            lastScheduledAt = last,
            summaryMessage = "${result.slots.size} campanha(s) entraram na fila. A primeira pode ser processada em seguida; as demais respeitam intervalos aleatórios de ${eff.sendDelayMinMinutes}-${eff.sendDelayMaxMinutes} min.",
        )
    }
}
