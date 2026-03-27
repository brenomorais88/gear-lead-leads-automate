package com.gearsales.leadengine.plugins

import com.gearsales.leadengine.web.routes.batchRoutes
import com.gearsales.leadengine.web.routes.dashboardRoutes
import com.gearsales.leadengine.web.routes.importRoutes
import com.gearsales.leadengine.web.routes.leadRoutes
import io.ktor.server.application.Application
import io.ktor.server.http.content.staticResources
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    routing {
        dashboardRoutes()
        importRoutes()
        leadRoutes()
        batchRoutes()
        staticResources("/static", "static")
    }
}
