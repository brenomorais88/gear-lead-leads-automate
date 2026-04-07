package com.gearsales.leadengine.domain.service

import com.gearsales.leadengine.database.repositories.WhatsAppEngineCampaignPointer
import com.gearsales.leadengine.domain.model.WhatsAppEngineStatus
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WhatsAppEngineStatusResolverTest {

    private val now = LocalDateTime.parse("2026-04-01T12:00:00")
    private val ptr = WhatsAppEngineCampaignPointer(31L, 411L, 2L)

    @Test
    fun idle_noPending() {
        val m = baseMetrics().copy(
            pendingCount = 0,
            sendingCount = 0,
            pendingWithoutSchedule = 0,
            pendingScheduledFuture = 0,
            eligibleCount = 0,
        )
        val (s, msg) = WhatsAppEngineStatusResolver.resolve(now, m, null)
        assertEquals(WhatsAppEngineStatus.IDLE, s)
        assertEquals(true, msg.contains("Ocioso", ignoreCase = true))
    }

    @Test
    fun misconfigured() {
        val m = baseMetrics()
        val (s, msg) = WhatsAppEngineStatusResolver.resolve(now, m, "WHATSAPP_ACCESS_TOKEN ausente")
        assertEquals(WhatsAppEngineStatus.MISCONFIGURED, s)
        assertEquals(true, msg.contains("Configuração", ignoreCase = true))
    }

    @Test
    fun paused_takesPrecedenceOverIdle() {
        val m = baseMetrics().copy(
            servicePaused = true,
            pendingCount = 0,
            sendingCount = 0,
        )
        val (s, msg) = WhatsAppEngineStatusResolver.resolve(now, m, null)
        assertEquals(WhatsAppEngineStatus.PAUSED, s)
        assertTrue(msg.contains("pausado", ignoreCase = true))
    }

    @Test
    fun processing() {
        val m = baseMetrics().copy(sendingCount = 1, sendingPtr = ptr)
        val (s, _) = WhatsAppEngineStatusResolver.resolve(now, m, null)
        assertEquals(WhatsAppEngineStatus.PROCESSING, s)
    }

    @Test
    fun dailyLimitReached_withBacklog() {
        val m = baseMetrics().copy(
            sentToday = 20,
            dailyLimit = 20,
            pendingCount = 3,
            pendingWithoutSchedule = 0,
            pendingScheduledFuture = 2,
            eligibleCount = 1,
        )
        val (s, msg) = WhatsAppEngineStatusResolver.resolve(now, m, null)
        assertEquals(WhatsAppEngineStatus.DAILY_LIMIT_REACHED, s)
        assertEquals(true, msg.contains("20", ignoreCase = false))
    }

    @Test
    fun pendingWithoutSchedule() {
        val m = baseMetrics().copy(
            pendingCount = 2,
            pendingWithoutSchedule = 2,
            pendingScheduledFuture = 0,
            eligibleCount = 0,
            sendingCount = 0,
        )
        val (s, _) = WhatsAppEngineStatusResolver.resolve(now, m, null)
        assertEquals(WhatsAppEngineStatus.PENDING_WITHOUT_SCHEDULE, s)
    }

    @Test
    fun readyToSend() {
        val m = baseMetrics().copy(
            pendingCount = 1,
            pendingWithoutSchedule = 0,
            pendingScheduledFuture = 0,
            eligibleCount = 1,
            eligiblePtr = ptr,
            sentToday = 0,
        )
        val (s, _) = WhatsAppEngineStatusResolver.resolve(now, m, null)
        assertEquals(WhatsAppEngineStatus.READY_TO_SEND, s)
    }

    @Test
    fun waitingNextSend() {
        val future = now.plusMinutes(17)
        val m = baseMetrics().copy(
            pendingCount = 1,
            pendingWithoutSchedule = 0,
            pendingScheduledFuture = 1,
            eligibleCount = 0,
            nextFuture = future to ptr,
            sentToday = 0,
        )
        val (s, msg) = WhatsAppEngineStatusResolver.resolve(now, m, null)
        assertEquals(WhatsAppEngineStatus.WAITING_NEXT_SEND, s)
        assertEquals(true, msg.contains("17", ignoreCase = false) || msg.contains("horário", ignoreCase = true))
    }

    private fun baseMetrics() = WhatsAppEngineMetricsInput(
        sentToday = 0,
        dailyLimit = 20,
        servicePaused = false,
        pendingCount = 0,
        sendingCount = 0,
        failedCount = 0,
        scheduledCount = 0,
        pendingWithoutSchedule = 0,
        pendingScheduledFuture = 0,
        eligibleCount = 0,
        nextFuture = null,
        sendingPtr = null,
        eligiblePtr = null,
    )
}
