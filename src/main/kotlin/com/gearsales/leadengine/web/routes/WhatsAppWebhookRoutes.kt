package com.gearsales.leadengine.web.routes

import com.gearsales.leadengine.plugins.whatsappWebhookService
import com.gearsales.leadengine.whatsapp.webhook.dto.WhatsAppWebhookRoot
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.slf4j.LoggerFactory

private val webhookLog = LoggerFactory.getLogger("WhatsAppWebhookRoutes")

/** Rotas oficiais Meta Cloud API + alias `/whatsapp/webhook` (documentação antiga / exemplos). */
private val webhookPaths = listOf(
    "/webhooks/whatsapp",
    "/whatsapp/webhook",
)

private suspend fun ApplicationCall.handleWebhookVerification() {
    val mode = request.queryParameters["hub.mode"]
    val token = request.queryParameters["hub.verify_token"]
    val challenge = request.queryParameters["hub.challenge"]
    val out = application.whatsappWebhookService().verifyWebhook(mode, token, challenge)
    if (out == null) {
        respond(HttpStatusCode.Forbidden)
    } else {
        respondText(out, ContentType.Text.Plain, HttpStatusCode.OK)
    }
}

private suspend fun ApplicationCall.handleWebhookPayload() {
    try {
        val body = receive<WhatsAppWebhookRoot>()
        application.whatsappWebhookService().handlePayload(body)
    } catch (e: Throwable) {
        webhookLog.warn("webhook POST ignored or failed: {}", e.message)
    }
    respond(HttpStatusCode.OK)
}

fun Route.whatsAppWebhookRoutes() {
    for (path in webhookPaths) {
        get(path) { call.handleWebhookVerification() }
        post(path) { call.handleWebhookPayload() }
    }
}
