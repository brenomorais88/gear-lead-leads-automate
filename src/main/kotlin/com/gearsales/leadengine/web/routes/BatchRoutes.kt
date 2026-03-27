package com.gearsales.leadengine.web.routes

import io.ktor.http.ContentType
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.thymeleaf.ThymeleafContent

fun Route.batchRoutes() {
    get("/batches") {
        call.respond(ThymeleafContent("batches", mapOf("title" to "Lotes")))
    }
    post("/batches/generate") {
        call.respondText("Geração de lote ainda não implementada.", ContentType.Text.Plain)
    }
    get("/batches/{id}") {
        val id = call.parameters["id"]!!
        call.respond(ThymeleafContent("batch-detail", mapOf("title" to "Lote #$id")))
    }
}
