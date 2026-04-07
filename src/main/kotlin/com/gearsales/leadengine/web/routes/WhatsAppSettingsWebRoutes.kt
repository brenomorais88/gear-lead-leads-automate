package com.gearsales.leadengine.web.routes

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.thymeleaf.ThymeleafContent

fun Route.whatsAppSettingsWebRoutes() {
    /** Página HTML; `GET /whatsapp/settings` permanece reservado à API JSON. */
    get("/whatsapp/config") {
        call.respond(
            ThymeleafContent(
                "whatsapp-settings",
                mapOf(
                    "title" to "Configurações WhatsApp",
                ),
            ),
        )
    }
}
