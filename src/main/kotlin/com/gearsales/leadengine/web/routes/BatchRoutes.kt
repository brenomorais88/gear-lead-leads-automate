package com.gearsales.leadengine.web.routes

import com.gearsales.leadengine.database.repositories.BatchRepository
import com.gearsales.leadengine.database.repositories.LeadRepository
import com.gearsales.leadengine.domain.service.DailyBatchService
import com.gearsales.leadengine.web.viewmodels.DailyBatchViewModel
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
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
        val leads = batchLeadRepository.findByIdsOrdered(leadIds)
        val batchVm = DailyBatchViewModel(
            id = row.id,
            createdAt = row.createdAt.format(batchDateFmt),
            totalLeads = row.totalLeads,
        )
        call.respond(
            ThymeleafContent(
                "batch-detail",
                mapOf(
                    "title" to "Lote #$id",
                    "batch" to batchVm,
                    "leads" to leads,
                ),
            ),
        )
    }
}
