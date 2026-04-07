package com.gearsales.leadengine.web.routes

import com.gearsales.leadengine.domain.model.LeadCampaignStatus
import com.gearsales.leadengine.plugins.whatsappCampaignReadService
import io.ktor.server.application.application
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.thymeleaf.ThymeleafContent
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.math.ceil

private const val PAGE_SIZE = 100

private fun enc(s: String): String = URLEncoder.encode(s, StandardCharsets.UTF_8)

private fun buildCampaignsQuery(
    page: Int,
    status: String?,
    batchIdStr: String?,
    loja: String?,
    statusInvalid: Boolean,
    batchIdInvalid: Boolean,
): String {
    val parts = mutableListOf("page=$page")
    if (!status.isNullOrEmpty() && !statusInvalid) {
        parts.add("status=${enc(status)}")
    }
    if (!batchIdStr.isNullOrEmpty() && !batchIdInvalid) {
        parts.add("batchId=${enc(batchIdStr)}")
    }
    if (!loja.isNullOrEmpty()) {
        parts.add("loja=${enc(loja)}")
    }
    return parts.joinToString("&")
}

fun Route.campaignsWebRoutes() {
    get("/campaigns") {
        val qp = call.request.queryParameters
        val statusRaw = qp["status"]?.trim()?.takeIf { it.isNotEmpty() }
        val statusParsed = statusRaw?.let { s ->
            LeadCampaignStatus.entries.find { it.name.equals(s, ignoreCase = true) }
        }
        val statusInvalid = statusRaw != null && statusParsed == null

        val batchIdStr = qp["batchId"]?.trim()?.takeIf { it.isNotEmpty() }
        val batchId = batchIdStr?.toLongOrNull()
        val batchIdInvalid = batchIdStr != null && batchId == null

        val loja = qp["loja"]?.trim()?.takeIf { it.isNotEmpty() }

        val page = qp["page"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val limit = PAGE_SIZE
        val offset = page * limit

        val body = call.application.whatsappCampaignReadService().listCampaigns(
            status = if (statusInvalid) null else statusParsed,
            batchId = if (batchIdInvalid) null else batchId,
            lojaContains = loja,
            limit = limit,
            offset = offset,
        )

        val totalPages = when {
            body.total <= 0L -> 0
            else -> ceil(body.total.toDouble() / limit).toInt()
        }

        val filterError = when {
            statusInvalid && batchIdInvalid ->
                "Status e ID de lote inválidos; esses filtros foram ignorados."
            statusInvalid -> "Status inválido; filtro de status ignorado."
            batchIdInvalid -> "ID de lote inválido; filtro de lote ignorado."
            else -> null
        }

        val prevQuery = if (page > 0) {
            buildCampaignsQuery(
                page - 1,
                statusRaw,
                batchIdStr,
                loja,
                statusInvalid,
                batchIdInvalid,
            )
        } else {
            null
        }
        val nextQuery = if (totalPages > 0 && page + 1 < totalPages) {
            buildCampaignsQuery(
                page + 1,
                statusRaw,
                batchIdStr,
                loja,
                statusInvalid,
                batchIdInvalid,
            )
        } else {
            null
        }

        val model = buildMap<String, Any> {
            put("title", "Campanhas WhatsApp")
            put("campaigns", body.campaigns)
            put("total", body.total)
            put("limit", body.limit)
            put("offset", body.offset)
            put("page", page)
            put("totalPages", totalPages)
            put("pageSize", limit)
            put("filterStatus", statusRaw ?: "")
            put("filterBatchId", batchIdStr ?: "")
            put("filterLoja", loja ?: "")
            put("filterWarning", filterError ?: "")
            put("hasPrev", prevQuery != null)
            put("prevQuery", prevQuery ?: "")
            put("hasNext", nextQuery != null)
            put("nextQuery", nextQuery ?: "")
            put("statusOptions", LeadCampaignStatus.entries.map { it.name })
        }
        call.respond(ThymeleafContent("campaigns", model))
    }
}
