package com.gearsales.leadengine.web.routes

import com.gearsales.leadengine.database.repositories.LeadInteractionRepository
import com.gearsales.leadengine.database.repositories.LeadRepository
import com.gearsales.leadengine.domain.model.InteractionDirection
import com.gearsales.leadengine.domain.model.LeadInteractionTypes
import com.gearsales.leadengine.plugins.SystemEvents
import com.gearsales.leadengine.plugins.whatsappCloudApiClient
import com.gearsales.leadengine.whatsapp.cloudapi.WhatsAppCloudHttpResult
import io.ktor.server.application.application
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.thymeleaf.ThymeleafContent
import java.time.LocalDateTime

private val conversationsLeadRepo = LeadRepository()
private val conversationsInteractionRepo = LeadInteractionRepository()

private val whatsappInteractionTypes = setOf(
    LeadInteractionTypes.WHATSAPP_INBOUND_MESSAGE,
    LeadInteractionTypes.WHATSAPP_TEMPLATE_SENT,
    LeadInteractionTypes.WHATSAPP_SEND_FAILED,
    LeadInteractionTypes.WHATSAPP_MANUAL_SENT,
)

private data class ConversationThreadRow(
    val representativeLeadId: Long,
    val title: String,
    val razaoSocial: String,
    val telefoneDisplay: String,
    val telefoneNormalized: String?,
    val preview: String,
    val lastType: String,
    val lastDirection: String,
    val lastAt: LocalDateTime,
    val leadsCount: Int,
)

fun Route.conversationsRoutes() {
    get("/conversations") {
        val q = call.request.queryParameters["q"]?.trim()
        val previews = conversationsInteractionRepo.listRecentWhatsAppThreads(limit = 200, q = q)
        val rawRows = previews.mapNotNull { p ->
            val lead = conversationsLeadRepo.findById(p.leadId) ?: return@mapNotNull null
            val title = lead.nomeFantasia?.takeIf { it.isNotBlank() } ?: lead.razaoSocial
            val preview = p.note?.takeIf { it.isNotBlank() } ?: p.result.orEmpty()
            ConversationThreadRow(
                representativeLeadId = lead.id,
                title = title,
                razaoSocial = lead.razaoSocial,
                telefoneDisplay = (lead.telefoneOriginal ?: lead.telefoneNormalizado ?: ""),
                telefoneNormalized = lead.telefoneNormalizado,
                preview = preview,
                lastType = p.interactionType,
                lastDirection = (p.direction ?: ""),
                lastAt = p.createdAt,
                leadsCount = 1,
            )
        }
        val grouped = rawRows.groupBy { row ->
            row.telefoneNormalized?.takeIf { it.isNotBlank() } ?: "lead:${row.representativeLeadId}"
        }.values.map { group ->
            val newest = group.maxByOrNull { it.lastAt }!!
            val phoneKey = newest.telefoneNormalized
            val count = if (phoneKey.isNullOrBlank()) {
                group.size
            } else {
                conversationsLeadRepo.listByTelefoneNormalizado(phoneKey).size
            }
            newest.copy(leadsCount = count)
        }.sortedByDescending { it.lastAt }

        val rows = grouped.map { row ->
            mapOf(
                "leadId" to row.representativeLeadId,
                "title" to row.title,
                "razaoSocial" to row.razaoSocial,
                "telefone" to row.telefoneDisplay,
                "preview" to row.preview,
                "lastType" to row.lastType,
                "lastDirection" to row.lastDirection,
                "lastAt" to row.lastAt.toString(),
                "leadsCount" to row.leadsCount,
            )
        }
        call.respond(
            ThymeleafContent(
                "conversations",
                mapOf(
                    "title" to "Conversas",
                    "q" to (q ?: ""),
                    "threads" to rows,
                ),
            ),
        )
    }

    get("/conversations/{id}") {
        val id = call.parameters["id"]?.toLongOrNull()
        if (id == null) {
            call.respondText("Identificador inválido.", status = io.ktor.http.HttpStatusCode.BadRequest)
            return@get
        }
        val lead = conversationsLeadRepo.findById(id)
        if (lead == null) {
            call.respondText("Lead não encontrado.", status = io.ktor.http.HttpStatusCode.NotFound)
            return@get
        }
        val conversationLeads = lead.telefoneNormalizado
            ?.takeIf { it.isNotBlank() }
            ?.let { conversationsLeadRepo.listByTelefoneNormalizado(it) }
            ?.takeIf { it.isNotEmpty() }
            ?: listOf(lead)
        val leadIds = conversationLeads.map { it.id }
        val all = leadIds.flatMap { conversationsInteractionRepo.listByLeadId(it) }
        val messages = all
            .sortedBy { it.createdAt }
            .filter { it.interactionType in whatsappInteractionTypes }
            .map {
                val direction = it.direction ?: if (it.interactionType == LeadInteractionTypes.WHATSAPP_INBOUND_MESSAGE) {
                    InteractionDirection.INBOUND
                } else {
                    InteractionDirection.OUTBOUND
                }
                mapOf(
                    "id" to it.id,
                    "direction" to direction,
                    "type" to it.interactionType,
                    "leadId" to it.leadId,
                    "text" to (it.note?.takeIf { n -> n.isNotBlank() } ?: it.result.orEmpty()),
                    "createdAt" to it.createdAt.toString(),
                )
            }
        val status = call.request.queryParameters["status"].orEmpty()
        val leadNames = conversationLeads.map {
            it.nomeFantasia?.takeIf { n -> n.isNotBlank() } ?: it.razaoSocial
        }
        val primaryName = leadNames.firstOrNull() ?: (lead.nomeFantasia?.takeIf { it.isNotBlank() } ?: lead.razaoSocial)
        call.respond(
            ThymeleafContent(
                "conversation-detail",
                mapOf(
                    "title" to "Conversa",
                    "leadId" to lead.id,
                    "leadName" to primaryName,
                    "leadPhone" to (lead.telefoneNormalizado ?: lead.telefoneOriginal ?: ""),
                    "leadNames" to leadNames,
                    "leadCount" to conversationLeads.size,
                    "messages" to messages,
                    "status" to status,
                ),
            ),
        )
    }

    post("/conversations/{id}/send") {
        val id = call.parameters["id"]?.toLongOrNull()
        if (id == null) {
            call.respondText("Identificador inválido.", status = io.ktor.http.HttpStatusCode.BadRequest)
            return@post
        }
        val lead = conversationsLeadRepo.findById(id)
        if (lead == null) {
            call.respondText("Lead não encontrado.", status = io.ktor.http.HttpStatusCode.NotFound)
            return@post
        }
        val to = lead.telefoneNormalizado?.trim().orEmpty()
        if (to.isEmpty()) {
            call.respondRedirect("/conversations/$id?status=no_phone", permanent = false)
            return@post
        }
        val msg = call.receiveParameters()["message"]?.trim().orEmpty()
        if (msg.isBlank()) {
            call.respondRedirect("/conversations/$id?status=empty", permanent = false)
            return@post
        }

        when (val out = call.application.whatsappCloudApiClient().sendTextMessage(to, msg, requestId = "manual-$id")) {
            is WhatsAppCloudHttpResult.Success -> {
                val wamid = out.body.messages?.firstOrNull()?.id
                conversationsInteractionRepo.insert(
                    leadId = id,
                    interactionType = LeadInteractionTypes.WHATSAPP_MANUAL_SENT,
                    result = "text",
                    note = msg,
                    direction = InteractionDirection.OUTBOUND,
                    externalMessageId = wamid,
                )
                SystemEvents.info("WHATSAPP_MANUAL", "Mensagem manual enviada para lead $id")
                call.respondRedirect("/conversations/$id?status=sent", permanent = false)
            }
            is WhatsAppCloudHttpResult.ApiError -> {
                val details = out.parsed?.message ?: out.rawBody.take(300)
                conversationsInteractionRepo.insert(
                    leadId = id,
                    interactionType = LeadInteractionTypes.WHATSAPP_SEND_FAILED,
                    result = "manual_text",
                    note = details,
                    direction = InteractionDirection.OUTBOUND,
                )
                SystemEvents.warn("WHATSAPP_MANUAL", "Falha ao enviar manual para lead $id", details)
                call.respondRedirect("/conversations/$id?status=error", permanent = false)
            }
            WhatsAppCloudHttpResult.MissingCredentials -> {
                call.respondRedirect("/conversations/$id?status=missing_credentials", permanent = false)
            }
        }
    }
}
