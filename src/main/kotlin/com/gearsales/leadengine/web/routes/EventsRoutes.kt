package com.gearsales.leadengine.web.routes

import com.gearsales.leadengine.database.repositories.SystemEventRepository
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.thymeleaf.ThymeleafContent
import kotlin.math.ceil

private const val EVENTS_PAGE_SIZE = 200

fun Route.eventsRoutes() {
    get("/events") {
        val repo = SystemEventRepository()
        val page = call.request.queryParameters["page"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val limit = EVENTS_PAGE_SIZE
        val offset = page * limit
        val events = repo.listRecent(limit = limit, offset = offset)
        val total = repo.countAll()
        val totalPages = if (total <= 0L) 0 else ceil(total.toDouble() / limit).toInt()
        val hasPrev = page > 0
        val hasNext = totalPages > 0 && page + 1 < totalPages

        val model = mapOf(
            "title" to "Eventos",
            "events" to events,
            "total" to total,
            "page" to page,
            "totalPages" to totalPages,
            "hasPrev" to hasPrev,
            "hasNext" to hasNext,
            "prevPage" to (page - 1).coerceAtLeast(0),
            "nextPage" to (page + 1),
        )
        call.respond(ThymeleafContent("events", model))
    }
}
