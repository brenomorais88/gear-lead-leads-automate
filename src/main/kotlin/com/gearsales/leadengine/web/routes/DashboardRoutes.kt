package com.gearsales.leadengine.web.routes

import com.gearsales.leadengine.database.repositories.BatchRepository
import com.gearsales.leadengine.database.repositories.LeadRepository
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.thymeleaf.ThymeleafContent

private val dashboardLeadRepository = LeadRepository()
private val dashboardBatchRepository = BatchRepository()

fun Route.dashboardRoutes() {
    get("/") {
        val stats = dashboardLeadRepository.dashboardCounts()
        val lastBatchId = dashboardBatchRepository.findLatestBatchId() ?: 0L
        call.respond(
            ThymeleafContent(
                "dashboard",
                mapOf(
                    "title" to "Dashboard",
                    "stats" to stats,
                    "lastBatchId" to lastBatchId,
                ),
            ),
        )
    }
}
