package com.gearsales.leadengine.domain.service

import com.gearsales.leadengine.config.WhatsAppAppConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class CampaignSendWorker(
    private val scope: CoroutineScope,
    private val whatsappConfig: WhatsAppAppConfig,
    private val processor: CampaignDispatchProcessor,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            val firstInterval = whatsappConfig.effective().workerPollIntervalSeconds
            log.info("WA campaign worker started (poll every {}s, ajustável em runtime)", firstInterval)
            while (isActive) {
                try {
                    processor.processNextEligible(SendTrigger.WORKER)
                } catch (e: Exception) {
                    log.warn("WA worker tick error: {}", e.message)
                }
                val interval = whatsappConfig.effective().workerPollIntervalSeconds
                delay(interval * 1000L)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
