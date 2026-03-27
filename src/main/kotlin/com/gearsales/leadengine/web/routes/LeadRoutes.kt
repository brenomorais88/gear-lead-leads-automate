package com.gearsales.leadengine.web.routes

import io.ktor.http.ContentType
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.thymeleaf.ThymeleafContent

fun Route.leadRoutes() {
    get("/leads") {
        call.respond(ThymeleafContent("leads", mapOf("title" to "Leads")))
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
