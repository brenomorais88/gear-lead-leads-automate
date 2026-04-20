package com.gearsales.leadengine.plugins

import com.gearsales.leadengine.database.repositories.SystemEventRepository
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import kotlin.math.min

object SystemEvents {
    @Volatile
    private var repository: SystemEventRepository? = null

    fun bind(repo: SystemEventRepository) {
        repository = repo
    }

    fun info(category: String, summary: String, details: String? = null) {
        write("INFO", category, summary, details)
    }

    fun warn(category: String, summary: String, details: String? = null) {
        write("WARN", category, summary, details)
    }

    fun error(category: String, summary: String, details: String? = null) {
        write("ERROR", category, summary, details)
    }

    fun write(
        level: String,
        category: String,
        summary: String,
        details: String? = null,
        httpMethod: String? = null,
        httpPath: String? = null,
        httpStatus: Int? = null,
    ) {
        try {
            repository?.insert(
                level = level,
                category = category,
                summary = summary,
                details = details,
                httpMethod = httpMethod,
                httpPath = httpPath,
                httpStatus = httpStatus,
            )
        } catch (_: Throwable) {
            // Não interrompe o fluxo da aplicação por falha de telemetria.
        }
    }
}

fun Application.configureSystemEvents() {
    val repo = SystemEventRepository()
    SystemEvents.bind(repo)

    intercept(ApplicationCallPipeline.Monitoring) {
        val startedAt = System.currentTimeMillis()
        var raised: Throwable? = null
        try {
            proceed()
        } catch (t: Throwable) {
            raised = t
            val details = buildString {
                append(t::class.simpleName ?: "Exception")
                if (!t.message.isNullOrBlank()) {
                    append(": ")
                    append(t.message)
                }
            }
            SystemEvents.error(
                category = "HTTP_ERROR",
                summary = "Falha em ${call.request.httpMethod.value} ${call.request.path()}",
                details = details,
            )
            throw t
        } finally {
            val elapsed = System.currentTimeMillis() - startedAt
            val status = call.response.status()?.value ?: if (raised != null) 500 else null
            val pathWithQuery = call.request.path()
            val summary =
                "${call.request.httpMethod.value} ${pathWithQuery} " +
                    "status=${status ?: "-"} dur=${elapsed}ms"
            val details = buildString {
                if (raised != null) {
                    append("exception=")
                    append(raised!!::class.simpleName ?: "Exception")
                }
            }.takeIf { it.isNotBlank() }
            val normalizedLevel = if ((status ?: 0) >= 500) "ERROR" else "INFO"
            SystemEvents.write(
                level = normalizedLevel,
                category = "HTTP_CALL",
                summary = summary.take(255),
                details = details?.let { it.substring(0, min(it.length, 2000)) },
                httpMethod = call.request.httpMethod.value,
                httpPath = pathWithQuery.take(512),
                httpStatus = status,
            )
        }
    }
}
