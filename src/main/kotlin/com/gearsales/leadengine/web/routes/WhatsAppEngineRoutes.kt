package com.gearsales.leadengine.web.routes

import com.gearsales.leadengine.plugins.whatsAppEngineOperationalService
import com.gearsales.leadengine.plugins.whatsappSettingsAdminService
import com.gearsales.leadengine.web.dto.WhatsAppOperationalDataResetRequest
import com.gearsales.leadengine.web.dto.WhatsAppSettingsApiResponse
import com.gearsales.leadengine.web.dto.WhatsAppSettingsUpdateRequest
import com.gearsales.leadengine.web.dto.WhatsAppSettingsValidationErrorResponse
import com.gearsales.leadengine.domain.service.WhatsAppSettingsUpdateResult
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.application
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put

fun Route.whatsAppEngineRoutes() {
    get("/whatsapp/engine-status") {
        call.respond(
            HttpStatusCode.OK,
            call.application.whatsAppEngineOperationalService().getStatus(),
        )
    }

    get("/whatsapp/settings") {
        val rec = call.application.whatsappSettingsAdminService().getCurrent()
        call.respond(HttpStatusCode.OK, WhatsAppSettingsApiResponse.from(rec))
    }

    put("/whatsapp/settings") {
        val body = call.receive<WhatsAppSettingsUpdateRequest>()
        when (val r = call.application.whatsappSettingsAdminService().update(body)) {
            is WhatsAppSettingsUpdateResult.Ok ->
                call.respond(HttpStatusCode.OK, WhatsAppSettingsApiResponse.from(r.record))
            is WhatsAppSettingsUpdateResult.Invalid ->
                call.respond(
                    HttpStatusCode.BadRequest,
                    WhatsAppSettingsValidationErrorResponse(errors = r.errors),
                )
        }
    }

    post("/whatsapp/settings/pause") {
        val rec = call.application.whatsappSettingsAdminService().pause()
        call.respond(HttpStatusCode.OK, WhatsAppSettingsApiResponse.from(rec))
    }

    post("/whatsapp/settings/resume") {
        val rec = call.application.whatsappSettingsAdminService().resume()
        call.respond(HttpStatusCode.OK, WhatsAppSettingsApiResponse.from(rec))
    }

    post("/whatsapp/settings/reset-operational-data") {
        val body = call.receive<WhatsAppOperationalDataResetRequest>()
        when (val r = call.application.whatsappSettingsAdminService().purgeOperationalData(body)) {
            is WhatsAppSettingsUpdateResult.Ok ->
                call.respond(HttpStatusCode.OK, WhatsAppSettingsApiResponse.from(r.record))
            is WhatsAppSettingsUpdateResult.Invalid ->
                call.respond(
                    HttpStatusCode.BadRequest,
                    WhatsAppSettingsValidationErrorResponse(errors = r.errors),
                )
        }
    }
}
