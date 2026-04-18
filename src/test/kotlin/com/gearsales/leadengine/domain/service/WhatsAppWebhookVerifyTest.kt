package com.gearsales.leadengine.domain.service

import com.gearsales.leadengine.database.repositories.LeadInteractionRepository
import com.gearsales.leadengine.database.repositories.LeadMessageCampaignRepository
import com.gearsales.leadengine.database.repositories.LeadRepository
import com.gearsales.leadengine.testWhatsAppInfra
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WhatsAppWebhookVerifyTest {

    private val service = WhatsAppWebhookService(
        infra = testWhatsAppInfra(),
        campaignRepository = LeadMessageCampaignRepository(),
        leadRepository = LeadRepository(),
        interactionRepository = LeadInteractionRepository(),
    )

    @Test
    fun verify_ok_returnsChallenge() {
        val c = service.verifyWebhook(
            mode = "subscribe",
            token = "gearleadengine_wh_verify_2026_local",
            challenge = "12345",
        )
        assertEquals("12345", c)
    }

    @Test
    fun verify_wrongToken_returnsNull() {
        assertNull(
            service.verifyWebhook(
                mode = "subscribe",
                token = "wrong",
                challenge = "x",
            ),
        )
    }

    @Test
    fun verify_wrongMode_returnsNull() {
        assertNull(
            service.verifyWebhook(
                mode = "unsubscribe",
                token = "gearleadengine_wh_verify_2026_local",
                challenge = "x",
            ),
        )
    }
}
