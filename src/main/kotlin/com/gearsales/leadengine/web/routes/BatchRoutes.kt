package com.gearsales.leadengine.web.routes

import com.gearsales.leadengine.database.repositories.BatchRepository
import com.gearsales.leadengine.database.repositories.LeadRepository
import com.gearsales.leadengine.domain.service.DailyBatchService
import com.gearsales.leadengine.plugins.prepareBatchCampaignService
import com.gearsales.leadengine.plugins.sendBatchCampaignService
import com.gearsales.leadengine.plugins.whatsappCampaignReadService
import com.gearsales.leadengine.web.dto.ApiErrorResponse
import com.gearsales.leadengine.web.viewmodels.BatchLeadRowViewModel
import com.gearsales.leadengine.web.viewmodels.DailyBatchViewModel
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.application.application
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.thymeleaf.ThymeleafContent
import java.time.format.DateTimeFormatter

private val batchLeadRepository = LeadRepository()
private val batchRepository = BatchRepository()
private val dailyBatchService = DailyBatchService(batchLeadRepository, batchRepository)

private val batchDateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

fun Route.batchRoutes() {
    get("/batches") {
        val msg = call.request.queryParameters["msg"].orEmpty()
        val batches = batchRepository.listAll().map { row ->
            DailyBatchViewModel(
                id = row.id,
                createdAt = row.createdAt.format(batchDateFmt),
                totalLeads = row.totalLeads,
            )
        }
        call.respond(
            ThymeleafContent(
                "batches",
                mapOf(
                    "title" to "Lotes",
                    "batches" to batches,
                    "msg" to msg,
                ),
            ),
        )
    }
    post("/batches/generate") {
        val result = dailyBatchService.generateBatch()
        if (result == null) {
            call.respondRedirect("/batches?msg=sem_elegiveis", permanent = false)
        } else {
            call.respondRedirect("/batches/${result.batchId}", permanent = false)
        }
    }
    get("/batches/{id}/campaigns") {
        val id = call.parameters["id"]?.toLongOrNull()
        if (id == null) {
            call.respond(HttpStatusCode.BadRequest, ApiErrorResponse("Identificador de lote inválido"))
            return@get
        }
        if (batchRepository.findById(id) == null) {
            call.respond(HttpStatusCode.NotFound, ApiErrorResponse("Lote não encontrado"))
            return@get
        }
        call.respond(
            HttpStatusCode.OK,
            call.application.whatsappCampaignReadService().batchCampaigns(id),
        )
    }
    get("/batches/{id}/whatsapp-summary") {
        val id = call.parameters["id"]?.toLongOrNull()
        if (id == null) {
            call.respond(HttpStatusCode.BadRequest, ApiErrorResponse("Identificador de lote inválido"))
            return@get
        }
        val summary = call.application.whatsappCampaignReadService().batchWhatsappSummary(id)
        if (summary == null) {
            call.respond(HttpStatusCode.NotFound, ApiErrorResponse("Lote não encontrado"))
            return@get
        }
        call.respond(HttpStatusCode.OK, summary)
    }
    get("/batches/{id}") {
        val id = call.parameters["id"]?.toLongOrNull()
        if (id == null) {
            call.respondText("Identificador inválido.", status = HttpStatusCode.BadRequest)
            return@get
        }
        val row = batchRepository.findById(id)
        if (row == null) {
            call.respondText("Lote não encontrado.", status = HttpStatusCode.NotFound)
            return@get
        }
        val leadIds = batchRepository.findLeadIdsForBatch(id)
        val leadRecords = batchLeadRepository.findByIdsOrdered(leadIds)
        val readSvc = call.application.whatsappCampaignReadService()
        val batchCampaigns = readSvc.batchCampaigns(id)
        val latestCampaignByLead = batchCampaigns.campaigns
            .groupBy { it.leadId }
            .mapValues { (_, rows) -> rows.maxBy { it.campaignId } }
        val leadRows = leadRecords.map { lead ->
            BatchLeadRowViewModel.from(lead, latestCampaignByLead[lead.id])
        }
        val batchVm = DailyBatchViewModel(
            id = row.id,
            createdAt = row.createdAt.format(batchDateFmt),
            totalLeads = row.totalLeads,
            leads = leadRows,
        )
        val whatsappSummary = readSvc.batchWhatsappSummary(id)!!
        call.respond(
            ThymeleafContent(
                "batch-detail",
                mapOf(
                    "title" to "Lote #$id",
                    "batch" to batchVm,
                    "whatsappSummary" to whatsappSummary,
                ),
            ),
        )
    }
    post("/batches/{id}/prepare-send") {
        val id = call.parameters["id"]?.toLongOrNull()
        if (id == null) {
            call.respond(HttpStatusCode.BadRequest, ApiErrorResponse("Identificador de lote inválido"))
            return@post
        }
        if (batchRepository.findById(id) == null) {
            call.respond(HttpStatusCode.NotFound, ApiErrorResponse("Lote não encontrado"))
            return@post
        }
        val result = call.application.prepareBatchCampaignService().prepare(id)
        call.respond(HttpStatusCode.OK, result)
    }
    post("/batches/{id}/send") {
        val id = call.parameters["id"]?.toLongOrNull()
        if (id == null) {
            call.respond(HttpStatusCode.BadRequest, ApiErrorResponse("Identificador de lote inválido"))
            return@post
        }
        if (batchRepository.findById(id) == null) {
            call.respond(HttpStatusCode.NotFound, ApiErrorResponse("Lote não encontrado"))
            return@post
        }
        val result = call.application.sendBatchCampaignService().scheduleBatchSend(id)
        call.respond(HttpStatusCode.OK, result)
    }
}
