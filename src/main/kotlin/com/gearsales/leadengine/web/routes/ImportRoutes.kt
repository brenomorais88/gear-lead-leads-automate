package com.gearsales.leadengine.web.routes

import io.ktor.http.ContentType
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.thymeleaf.ThymeleafContent

fun Route.importRoutes() {
    get("/import") {
        call.respond(ThymeleafContent("import", mapOf("title" to "Importação")))
    }
    post("/import") {
        call.respondText("Importação ainda não implementada.", ContentType.Text.Plain)
    }
}
