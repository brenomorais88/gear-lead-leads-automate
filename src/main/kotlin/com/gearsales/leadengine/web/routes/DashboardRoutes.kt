package com.gearsales.leadengine.web.routes

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.thymeleaf.ThymeleafContent

fun Route.dashboardRoutes() {
    get("/") {
        call.respond(ThymeleafContent("dashboard", mapOf("title" to "Dashboard")))
    }
}
