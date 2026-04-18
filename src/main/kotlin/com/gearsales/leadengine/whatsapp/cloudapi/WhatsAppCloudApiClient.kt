package com.gearsales.leadengine.whatsapp.cloudapi

import com.gearsales.leadengine.config.WhatsAppAppConfig
import com.gearsales.leadengine.whatsapp.cloudapi.dto.WhatsAppSendErrorBody
import com.gearsales.leadengine.whatsapp.cloudapi.dto.WhatsAppSendErrorEnvelope
import com.gearsales.leadengine.whatsapp.cloudapi.dto.WhatsAppSendSuccessResponse
import com.gearsales.leadengine.whatsapp.cloudapi.dto.WhatsAppTemplateBlock
import com.gearsales.leadengine.whatsapp.cloudapi.dto.WhatsAppTemplateComponent
import com.gearsales.leadengine.whatsapp.cloudapi.dto.WhatsAppTemplateLanguage
import com.gearsales.leadengine.whatsapp.cloudapi.dto.WhatsAppTemplateParameter
import com.gearsales.leadengine.whatsapp.cloudapi.dto.WhatsAppTemplateSendRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

sealed class WhatsAppCloudHttpResult {
    data class Success(val body: WhatsAppSendSuccessResponse) : WhatsAppCloudHttpResult()
    data class ApiError(
        val statusCode: Int?,
        val rawBody: String,
        val parsed: WhatsAppSendErrorBody?,
    ) : WhatsAppCloudHttpResult()

    data object MissingCredentials : WhatsAppCloudHttpResult()
}

class WhatsAppCloudApiClient(
    private val httpClient: HttpClient,
    private val json: Json,
    private val whatsappConfig: WhatsAppAppConfig,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun sendTemplateMessage(
        toDigits: String,
        templateName: String,
        languageCode: String,
        bodyParameterText: String,
        requestId: String = "noid",
    ): WhatsAppCloudHttpResult {
        val eff = whatsappConfig.effective()
        val token = eff.accessToken
        if (token.isNullOrBlank()) {
            return WhatsAppCloudHttpResult.MissingCredentials
        }
        if (eff.phoneNumberId.isBlank()) {
            return WhatsAppCloudHttpResult.MissingCredentials
        }

        val request = WhatsAppTemplateSendRequest(
            to = toDigits,
            template = WhatsAppTemplateBlock(
                name = templateName,
                language = WhatsAppTemplateLanguage(code = languageCode),
                components = listOf(
                    WhatsAppTemplateComponent(
                        type = "body",
                        parameters = listOf(
                            WhatsAppTemplateParameter(text = bodyParameterText),
                        ),
                    ),
                ),
            ),
        )

        val url = eff.messagesEndpointUrl()
        log.info(
            "WA HTTP {} -> POST url={} phoneNumberIdLast4={} toMasked={} template={} lang={} bodyParamChars={}",
            requestId,
            url,
            eff.phoneNumberId.takeLast(4),
            maskDest(toDigits),
            templateName,
            languageCode,
            bodyParameterText.length,
        )
        val response: HttpResponse = httpClient.post(url) {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(request)
        }

        val text = response.bodyAsText()
        val bodyPreview = text.take(4000)
        if (!response.status.isSuccess()) {
            log.warn(
                "WA HTTP {} <- error status={} bodyChars={} bodyPreview={}",
                requestId,
                response.status.value,
                text.length,
                bodyPreview,
            )
            val parsed = runCatching {
                json.decodeFromString(WhatsAppSendErrorEnvelope.serializer(), text).error
            }.getOrNull()
            return WhatsAppCloudHttpResult.ApiError(response.status.value, text, parsed)
        }

        val success = runCatching {
            json.decodeFromString(WhatsAppSendSuccessResponse.serializer(), text)
        }.getOrElse {
            log.warn("WA HTTP {} <- 200 but JSON parse failed preview={}", requestId, bodyPreview)
            return WhatsAppCloudHttpResult.ApiError(response.status.value, text, null)
        }
        log.info(
            "WA HTTP {} <- ok status=200 wamid={} bodyPreview={}",
            requestId,
            success.messages?.firstOrNull()?.id,
            bodyPreview.take(500),
        )
        return WhatsAppCloudHttpResult.Success(success)
    }

    private fun maskDest(digits: String): String {
        val d = digits.filter { it.isDigit() }
        if (d.length < 4) return "***"
        return "***${d.takeLast(4)}"
    }
}
