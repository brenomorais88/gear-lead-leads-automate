package com.gearsales.leadengine.plugins

import com.gearsales.leadengine.web.routes.batchRoutes
import com.gearsales.leadengine.web.routes.campaignsWebRoutes
import com.gearsales.leadengine.web.routes.dashboardRoutes
import com.gearsales.leadengine.web.routes.importRoutes
import com.gearsales.leadengine.web.routes.leadRoutes
import com.gearsales.leadengine.web.routes.whatsAppEngineRoutes
import com.gearsales.leadengine.web.routes.whatsAppSettingsWebRoutes
import com.gearsales.leadengine.web.routes.whatsAppReadRoutes
import com.gearsales.leadengine.web.routes.whatsAppWebhookRoutes
import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.http.content.staticResources
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.http.ContentType

fun Application.configureRouting() {
    routing {
        get("/health") {
            call.respondText("ok", ContentType.Text.Plain)
        }
        dashboardRoutes()
        importRoutes()
        leadRoutes()
        batchRoutes()
        campaignsWebRoutes()
        whatsAppReadRoutes()
        whatsAppEngineRoutes()
        whatsAppSettingsWebRoutes()
        whatsAppWebhookRoutes()
        staticResources("/static", "static")
    }
}
