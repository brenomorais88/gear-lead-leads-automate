package com.gearsales.leadengine.domain.service

import com.gearsales.leadengine.config.WhatsAppAppConfig
import com.gearsales.leadengine.database.repositories.LeadMessageCampaignRepository
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Processa no máximo uma campanha por chamada: claim, respeita cota diária, envia, libera lock.
 */
class CampaignDispatchProcessor(
    private val whatsappConfig: WhatsAppAppConfig,
    private val campaignRepository: LeadMessageCampaignRepository,
    private val sendWhatsAppTemplateService: SendWhatsAppTemplateService,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val zone: ZoneId = ZoneId.systemDefault()
    @Volatile
    private var lastPauseLogAtMs: Long = 0L

    suspend fun processNextEligible(trigger: SendTrigger): Boolean {
        val eff = whatsappConfig.effective()
        if (eff.servicePaused) {
            val t = System.currentTimeMillis()
            if (t - lastPauseLogAtMs > 30_000L) {
                log.info("WA dispatch: serviço pausado; não processando novas campanhas neste ciclo")
                lastPauseLogAtMs = t
            }
            return false
        }
        val now = LocalDateTime.now()
        val staleBefore = now.minusSeconds(eff.processingStaleSeconds)
        val campaignId = campaignRepository.tryClaimNextDispatchableCampaign(now, staleBefore) ?: return false

        try {
            val today = LocalDate.now(zone)
            val sentToday = campaignRepository.countSuccessfulSendsOnLocalDate(today, zone)
            if (sentToday >= eff.dailySendLimit) {
                log.info(
                    "WA dispatch skip quota: campaignId={} sentToday={} dailyLimit={} deferMin={}",
                    campaignId,
                    sentToday,
                    eff.dailySendLimit,
                    eff.quotaDeferMinutes,
                )
                campaignRepository.deferCampaignScheduledTime(
                    campaignId,
                    now.plusMinutes(eff.quotaDeferMinutes.toLong()),
                    LocalDateTime.now(),
                )
                return true
            }

            when (val r = sendWhatsAppTemplateService.sendForCampaign(campaignId, trigger)) {
                is SendWhatsAppTemplateResult.Success -> {
                    log.info("WA dispatch success: campaignId={} wamid={} trigger={}", campaignId, r.waMessageId, trigger)
                }
                is SendWhatsAppTemplateResult.Failure -> {
                    log.warn("WA dispatch failure: campaignId={} trigger={} reason={}", campaignId, trigger, r.reason)
                }
            }
            return true
        } catch (e: Exception) {
            log.error("WA dispatch INTERNAL_ERROR: campaignId={} trigger={}", campaignId, trigger, e)
            campaignRepository.clearProcessingLock(campaignId, LocalDateTime.now())
            return true
        }
    }
}
