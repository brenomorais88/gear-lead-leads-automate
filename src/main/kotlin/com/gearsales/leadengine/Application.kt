package com.gearsales.leadengine

import com.gearsales.leadengine.plugins.configureDatabase
import com.gearsales.leadengine.plugins.configureRouting
import com.gearsales.leadengine.plugins.configureSerialization
import com.gearsales.leadengine.plugins.configureSystemEvents
import com.gearsales.leadengine.plugins.configureTemplating
import com.gearsales.leadengine.plugins.configureWhatsAppIntegration
import io.ktor.server.application.Application
import io.ktor.server.netty.EngineMain

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    configureTemplating()
    configureDatabase()
    configureSystemEvents()
    configureWhatsAppIntegration()
    configureSerialization()
    configureRouting()
}
