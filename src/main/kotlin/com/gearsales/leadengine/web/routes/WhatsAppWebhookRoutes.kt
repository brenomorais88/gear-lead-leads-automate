package com.gearsales.leadengine.web.routes

import com.gearsales.leadengine.plugins.whatsappWebhookService
import com.gearsales.leadengine.whatsapp.webhook.dto.WhatsAppWebhookRoot
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.slf4j.LoggerFactory

private val webhookLog = LoggerFactory.getLogger("WhatsAppWebhookRoutes")

fun Route.whatsAppWebhookRoutes() {
    get("/webhooks/whatsapp") {
        val mode = call.request.queryParameters["hub.mode"]
        val token = call.request.queryParameters["hub.verify_token"]
        val challenge = call.request.queryParameters["hub.challenge"]
        val out = call.application.whatsappWebhookService().verifyWebhook(mode, token, challenge)
        if (out == null) {
            call.respond(HttpStatusCode.Forbidden)
        } else {
            call.respondText(out, ContentType.Text.Plain, HttpStatusCode.OK)
        }
    }
    post("/webhooks/whatsapp") {
        try {
            val body = call.receive<WhatsAppWebhookRoot>()
            call.application.whatsappWebhookService().handlePayload(body)
        } catch (e: Throwable) {
            webhookLog.warn("webhook POST ignored or failed: {}", e.message)
        }
        call.respond(HttpStatusCode.OK)
    }
}
