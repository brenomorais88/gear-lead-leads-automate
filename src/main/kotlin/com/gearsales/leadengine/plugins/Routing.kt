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
import io.ktor.server.http.content.staticResources
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    routing {
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
