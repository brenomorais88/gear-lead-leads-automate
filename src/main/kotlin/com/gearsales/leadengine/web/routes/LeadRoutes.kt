package com.gearsales.leadengine.web.routes

import com.gearsales.leadengine.database.repositories.LeadListFilters
import com.gearsales.leadengine.database.repositories.LeadRepository
import com.gearsales.leadengine.domain.model.LeadPriority
import com.gearsales.leadengine.domain.model.LeadStatus
import io.ktor.http.ContentType
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.thymeleaf.ThymeleafContent

private val leadRepository = LeadRepository()

fun Route.leadRoutes() {
    get("/leads") {
        val q = call.request.queryParameters["q"]
        val status = call.request.queryParameters["status"]
        val prioridade = call.request.queryParameters["prioridade"]
        val leads = leadRepository.listLeads(
            LeadListFilters(
                q = q,
                status = status,
                prioridade = prioridade,
            ),
        )
        call.respond(
            ThymeleafContent(
                "leads",
                mapOf(
                    "title" to "Leads",
                    "leads" to leads,
                    "q" to (q ?: ""),
                    "statusFilter" to (status ?: ""),
                    "prioridadeFilter" to (prioridade ?: ""),
                    "statuses" to LeadStatus.entries.map { it.name },
                    "prioridades" to LeadPriority.entries.map { it.name },
                ),
            ),
        )
    }
    post("/leads/{id}/edit") {
        call.respondText("Edição de lead ainda não implementada.", ContentType.Text.Plain)
    }
    post("/leads/{id}/status") {
        call.respondText("Atualização de status ainda não implementada.", ContentType.Text.Plain)
    }
    get("/leads/{id}") {
        val id = call.parameters["id"]!!
        call.respond(ThymeleafContent("lead-detail", mapOf("title" to "Lead #$id")))
    }
}
