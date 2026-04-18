package com.gearsales.leadengine.whatsapp.webhook

import com.gearsales.leadengine.whatsapp.webhook.dto.WhatsAppWebhookRoot
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class WhatsAppWebhookJsonTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun deserialize_statusAndMessages_mvpShape() {
        val raw = """
            {
              "object": "whatsapp_business_account",
              "entry": [{
                "changes": [{
                  "field": "messages",
                  "value": {
                    "statuses": [{
                      "id": "wamid.STATUS1",
                      "status": "delivered",
                      "timestamp": "1730000000"
                    }],
                    "messages": [{
                      "from": "5511999887766",
                      "id": "wamid.IN1",
                      "timestamp": "1730000001",
                      "type": "text",
                      "text": { "body": "Oi" }
                    }]
                  }
                }]
              }]
            }
        """.trimIndent()

        val root = json.decodeFromString(WhatsAppWebhookRoot.serializer(), raw)
        assertEquals("whatsapp_business_account", root.obj)
        assertEquals(1, root.entry.size)
        val value = root.entry.first().changes.first().value
        assertNotNull(value)
        assertEquals(1, value.statuses.size)
        assertEquals("delivered", value.statuses.first().status)
        assertEquals(1, value.messages.size)
        assertEquals("text", value.messages.first().type)
        assertEquals("Oi", value.messages.first().text?.body)
    }

    @Test
    fun deserialize_partialPayload_doesNotCrash() {
        val raw = """{"entry":[],"object":"whatsapp_business_account"}"""
        val root = json.decodeFromString(WhatsAppWebhookRoot.serializer(), raw)
        assertEquals(0, root.entry.size)
    }
}
