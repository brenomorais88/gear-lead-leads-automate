package com.gearsales.leadengine.web.routes

import com.gearsales.leadengine.domain.model.LeadCampaignStatus
import com.gearsales.leadengine.plugins.whatsappCampaignReadService
import com.gearsales.leadengine.web.dto.ApiErrorResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.application
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

private const val DEFAULT_CAMPAIGN_PAGE_LIMIT = 50
private const val MAX_CAMPAIGN_PAGE_LIMIT = 200

fun Route.whatsAppReadRoutes() {
    get("/api/campaigns") {
        val statusParam = call.request.queryParameters["status"]?.trim()?.takeIf { it.isNotEmpty() }
        val status = statusParam?.let { s ->
            LeadCampaignStatus.entries.find { it.name.equals(s, ignoreCase = true) }
                ?: run {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiErrorResponse("Parâmetro status inválido: $s"),
                    )
                    return@get
                }
        }
        val batchIdRaw = call.request.queryParameters["batchId"]?.trim()?.takeIf { it.isNotEmpty() }
        val batchId = batchIdRaw?.let { raw ->
            raw.toLongOrNull() ?: run {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiErrorResponse("Parâmetro batchId inválido"),
                )
                return@get
            }
        }
        val limit = call.request.queryParameters["limit"]?.toIntOrNull()
            ?.coerceIn(1, MAX_CAMPAIGN_PAGE_LIMIT)
            ?: DEFAULT_CAMPAIGN_PAGE_LIMIT
        val offset = call.request.queryParameters["offset"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val loja = call.request.queryParameters["loja"]?.trim()?.takeIf { it.isNotEmpty() }
        val body = call.application.whatsappCampaignReadService().listCampaigns(
            status = status,
            batchId = batchId,
            lojaContains = loja,
            limit = limit,
            offset = offset,
        )
        call.respond(HttpStatusCode.OK, body)
    }
}
