package com.gearsales.leadengine.web.routes

import com.gearsales.leadengine.domain.service.LeadImportService
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.thymeleaf.ThymeleafContent
import io.ktor.utils.io.toByteArray

private val leadImportService = LeadImportService()

fun Route.importRoutes() {
    get("/import") {
        call.respond(
            ThymeleafContent(
                "import",
                mapOf(
                    "title" to "Importação",
                ),
            ),
        )
    }
    post("/import") {
        val multipart = call.receiveMultipart()
        var fileBytes: ByteArray? = null
        var originalName: String? = null
        multipart.forEachPart { part ->
            when (part) {
                is PartData.FileItem -> {
                    if (fileBytes == null) {
                        originalName = part.originalFileName
                        fileBytes = part.provider().toByteArray()
                    }
                }
                else -> Unit
            }
            part.dispose()
        }
        if (fileBytes == null || originalName.isNullOrBlank()) {
            call.respond(
                ThymeleafContent(
                    "import",
                    mapOf(
                        "title" to "Importação",
                        "error" to "Envie um arquivo .xlsx.",
                    ),
                ),
            )
            return@post
        }
        try {
            val outcome = leadImportService.importAndPersist(fileBytes!!.inputStream(), originalName!!)
            call.respond(
                ThymeleafContent(
                    "import",
                    mapOf(
                        "title" to "Importação",
                        "preview" to outcome.preview,
                        "previewRows" to outcome.preview.rows.take(10),
                        "result" to outcome.result,
                    ),
                ),
            )
        } catch (e: IllegalArgumentException) {
            call.respond(
                ThymeleafContent(
                    "import",
                    mapOf(
                        "title" to "Importação",
                        "error" to (e.message ?: "Erro ao ler o arquivo."),
                    ),
                ),
            )
        } catch (e: Exception) {
            call.respond(
                ThymeleafContent(
                    "import",
                    mapOf(
                        "title" to "Importação",
                        "error" to "Não foi possível ler o arquivo.",
                    ),
                ),
            )
        }
    }
}
