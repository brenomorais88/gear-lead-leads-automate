package com.gearsales.leadengine.domain.service

import com.gearsales.leadengine.config.WhatsAppAppConfig
import com.gearsales.leadengine.database.repositories.LeadInteractionRepository
import com.gearsales.leadengine.database.repositories.LeadMessageCampaignRepository
import com.gearsales.leadengine.database.repositories.LeadRepository
import com.gearsales.leadengine.domain.model.InteractionDirection
import com.gearsales.leadengine.domain.model.LeadCampaignStatus
import com.gearsales.leadengine.domain.model.LeadInteractionTypes
import com.gearsales.leadengine.domain.model.WhatsAppFailureCategory
import com.gearsales.leadengine.whatsapp.cloudapi.WhatsAppCloudApiClient
import com.gearsales.leadengine.whatsapp.cloudapi.WhatsAppCloudHttpResult
import com.gearsales.leadengine.whatsapp.cloudapi.WhatsAppInvalidRecipientDetector
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.UUID

sealed class SendWhatsAppTemplateResult {
    data class Success(val waMessageId: String) : SendWhatsAppTemplateResult()
    data class Failure(val reason: String) : SendWhatsAppTemplateResult()
}

class SendWhatsAppTemplateService(
    private val whatsappConfig: WhatsAppAppConfig,
    private val apiClient: WhatsAppCloudApiClient,
    private val campaignRepository: LeadMessageCampaignRepository,
    private val leadRepository: LeadRepository,
    private val interactionRepository: LeadInteractionRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun sendForCampaign(
        campaignId: Long,
        trigger: SendTrigger = SendTrigger.API_DIRECT,
    ): SendWhatsAppTemplateResult {
        val requestId = UUID.randomUUID().toString().take(8)
        val eff = whatsappConfig.effective()
        try {
            val campaign = campaignRepository.findById(campaignId)
                ?: run {
                    log.warn("WA send {}: campaign not found campaignId={}", requestId, campaignId)
                    return SendWhatsAppTemplateResult.Failure("Campanha não encontrada")
                }
            if (campaign.status != LeadCampaignStatus.PENDING && campaign.status != LeadCampaignStatus.SENDING) {
                log.info(
                    "WA send {}: skip non-PENDING campaignId={} status={} trigger={}",
                    requestId,
                    campaignId,
                    campaign.status,
                    trigger,
                )
                return SendWhatsAppTemplateResult.Failure(
                    "Somente campanhas PENDING ou em envio (SENDING) podem ser processadas (status atual: ${campaign.status})",
                )
            }

            val lead = leadRepository.findById(campaign.leadId)
                ?: return SendWhatsAppTemplateResult.Failure("Lead não encontrado")

            val lojaNome = lead.nomeFantasia?.trim()?.takeIf { it.isNotEmpty() } ?: lead.razaoSocial.trim()
            val phone = lead.telefoneNormalizado.orEmpty()

            log.info(
                "WA send {} start: trigger={} campaignId={} batchId={} leadId={} loja={} telefone={} template={} lang={}",
                requestId,
                trigger,
                campaignId,
                campaign.batchId,
                lead.id,
                lojaNome,
                maskPhone(phone),
                campaign.templateName,
                campaign.templateLanguage,
            )

            val now = LocalDateTime.now()
            val toDigits = phone.filter { it.isDigit() }
            if (toDigits.isBlank() || toDigits.length < 10) {
                val reason = "Telefone ausente ou inválido para envio"
                log.warn(
                    "WA send {} INVALID_PHONE: campaignId={} leadId={} telefoneNorm={}",
                    requestId,
                    campaignId,
                    lead.id,
                    maskPhone(phone),
                )
                transaction {
                    campaignRepository.updateAfterSendFailure(
                        campaignId,
                        now,
                        reason,
                        WhatsAppFailureCategory.INVALID_PHONE,
                        now,
                    )
                    leadRepository.markLeadPhoneInvalid(lead.id, now)
                }
                tryInsertInteraction(requestId, campaignId, lead.id) {
                    interactionRepository.insert(
                        leadId = lead.id,
                        interactionType = LeadInteractionTypes.WHATSAPP_SEND_FAILED,
                        result = WhatsAppFailureCategory.INVALID_PHONE.name,
                        note = reason,
                        direction = InteractionDirection.OUTBOUND,
                        metadataJson = """{"campaignId":$campaignId,"reason":"invalid_phone","requestId":"$requestId"}""",
                    )
                }
                logPersist(campaignId, lead.id, "FAILED", reason)
                return SendWhatsAppTemplateResult.Failure(reason)
            }

            val storeName = lojaNome
            val templateName = campaign.templateName.ifBlank { eff.defaultTemplateName }
            val languageCode = campaign.templateLanguage.ifBlank { eff.defaultTemplateLanguage }

            try {
                eff.requireSendCredentials()
            } catch (e: IllegalArgumentException) {
                val reason = e.message ?: "Configuração WhatsApp incompleta"
                log.warn("WA send {} AUTH_ERROR: campaignId={} {}", requestId, campaignId, reason)
                transaction {
                    campaignRepository.updateAfterSendFailure(
                        campaignId,
                        now,
                        reason,
                        WhatsAppFailureCategory.AUTH_ERROR,
                        now,
                    )
                }
                tryInsertInteraction(requestId, campaignId, lead.id) {
                    interactionRepository.insert(
                        leadId = lead.id,
                        interactionType = LeadInteractionTypes.WHATSAPP_SEND_FAILED,
                        result = WhatsAppFailureCategory.AUTH_ERROR.name,
                        note = reason,
                        direction = InteractionDirection.OUTBOUND,
                        metadataJson = """{"campaignId":$campaignId,"reason":"missing_credentials","requestId":"$requestId"}""",
                    )
                }
                logPersist(campaignId, lead.id, "FAILED", reason)
                return SendWhatsAppTemplateResult.Failure(reason)
            }

            log.info(
                "WA send {} Meta request: endpoint={} phoneNumberId={} to={} template={} lang={} bodyParamLen={}",
                requestId,
                eff.messagesEndpointUrl(),
                maskId(eff.phoneNumberId),
                maskPhone(toDigits),
                templateName,
                languageCode,
                storeName.length,
            )

            val httpResult = apiClient.sendTemplateMessage(
                toDigits = toDigits,
                templateName = templateName,
                languageCode = languageCode,
                bodyParameterText = storeName,
                requestId = requestId,
            )

            return when (httpResult) {
                is WhatsAppCloudHttpResult.Success -> {
                    val messageId = httpResult.body.messages?.firstOrNull()?.id
                    log.info(
                        "WA send {} Meta response: http=200 wamid={} rawSnippet={}",
                        requestId,
                        messageId,
                        httpResult.logSnippet(),
                    )
                    if (messageId.isNullOrBlank()) {
                        val reason = "Resposta da API sem wamid"
                        log.error("WA send {} INTERNAL_ERROR: {}", requestId, reason)
                        transaction {
                            campaignRepository.updateAfterSendFailure(
                                campaignId,
                                now,
                                reason,
                                WhatsAppFailureCategory.INTERNAL_ERROR,
                                now,
                            )
                        }
                        tryInsertInteraction(requestId, campaignId, lead.id) {
                            interactionRepository.insert(
                                leadId = lead.id,
                                interactionType = LeadInteractionTypes.WHATSAPP_SEND_FAILED,
                                result = WhatsAppFailureCategory.INTERNAL_ERROR.name,
                                note = reason,
                                direction = InteractionDirection.OUTBOUND,
                                metadataJson = """{"campaignId":$campaignId,"http":"success_no_id","requestId":"$requestId"}""",
                            )
                        }
                        logPersist(campaignId, lead.id, "FAILED", reason)
                        SendWhatsAppTemplateResult.Failure(reason)
                    } else {
                        val followUpAt = now.plusDays(2)
                        transaction {
                            campaignRepository.updateAfterSendSuccess(campaignId, messageId, now, now)
                            leadRepository.updateLeadAfterSuccessfulTemplateSend(lead.id, now, followUpAt)
                        }
                        tryInsertInteraction(requestId, campaignId, lead.id) {
                            interactionRepository.insert(
                                leadId = lead.id,
                                interactionType = LeadInteractionTypes.WHATSAPP_TEMPLATE_SENT,
                                result = templateName,
                                note = null,
                                direction = InteractionDirection.OUTBOUND,
                                externalMessageId = messageId,
                                metadataJson = """{"campaignId":$campaignId,"language":"$languageCode","requestId":"$requestId"}""",
                            )
                        }
                        logPersist(campaignId, lead.id, "SENT", messageId)
                        SendWhatsAppTemplateResult.Success(messageId)
                    }
                }

                is WhatsAppCloudHttpResult.MissingCredentials -> {
                    val reason = "Credenciais WhatsApp ausentes"
                    log.warn("WA send {} AUTH_ERROR: {}", requestId, reason)
                    transaction {
                        campaignRepository.updateAfterSendFailure(
                            campaignId,
                            now,
                            reason,
                            WhatsAppFailureCategory.AUTH_ERROR,
                            now,
                        )
                    }
                    tryInsertInteraction(requestId, campaignId, lead.id) {
                        interactionRepository.insert(
                            leadId = lead.id,
                            interactionType = LeadInteractionTypes.WHATSAPP_SEND_FAILED,
                            result = WhatsAppFailureCategory.AUTH_ERROR.name,
                            note = reason,
                            direction = InteractionDirection.OUTBOUND,
                            metadataJson = """{"campaignId":$campaignId,"reason":"missing_credentials","requestId":"$requestId"}""",
                        )
                    }
                    logPersist(campaignId, lead.id, "FAILED", reason)
                    SendWhatsAppTemplateResult.Failure(reason)
                }

                is WhatsAppCloudHttpResult.ApiError -> {
                    val lower = httpResult.rawBody.lowercase()
                    val invalidRecipient =
                        WhatsAppInvalidRecipientDetector.isLikelyInvalidRecipient(httpResult.parsed, lower)
                    val (readable, category) = WhatsAppApiFailureClassifier.classify(
                        httpResult.statusCode,
                        httpResult.parsed,
                        httpResult.rawBody,
                        invalidRecipient,
                    )
                    log.warn(
                        "WA send {} {}: campaignId={} leadId={} http={} telefone={} metaMsg={} bodySnippet={}",
                        requestId,
                        category,
                        campaignId,
                        lead.id,
                        httpResult.statusCode,
                        maskPhone(toDigits),
                        httpResult.parsed?.message,
                        httpResult.rawBody.take(2000),
                    )
                    transaction {
                        campaignRepository.updateAfterSendFailure(
                            campaignId,
                            now,
                            readable,
                            category,
                            now,
                        )
                        if (invalidRecipient) {
                            leadRepository.markLeadPhoneInvalid(lead.id, now)
                        }
                    }
                    tryInsertInteraction(requestId, campaignId, lead.id) {
                        interactionRepository.insert(
                            leadId = lead.id,
                            interactionType = LeadInteractionTypes.WHATSAPP_SEND_FAILED,
                            result = category.name,
                            note = readable,
                            direction = InteractionDirection.OUTBOUND,
                            externalMessageId = null,
                            metadataJson = buildFailureMetadataJson(
                                campaignId = campaignId,
                                statusCode = httpResult.statusCode,
                                code = httpResult.parsed?.code,
                                invalidRecipient = invalidRecipient,
                                fbtraceId = httpResult.parsed?.fbtraceId,
                                category = category.name,
                                requestId = requestId,
                            ),
                        )
                    }
                    logPersist(campaignId, lead.id, "FAILED", readable)
                    SendWhatsAppTemplateResult.Failure(readable)
                }
            }
        } finally {
            campaignRepository.clearProcessingLock(campaignId, LocalDateTime.now())
        }
    }

    /**
     * Interaction é secundária: falha aqui não pode derrubar o commit da campanha (evita rollback + loop no worker).
     */
    private fun tryInsertInteraction(
        requestId: String,
        campaignId: Long,
        leadId: Long,
        insertBlock: () -> Unit,
    ) {
        runCatching { insertBlock() }
            .onFailure { e ->
                log.warn(
                    "WA send {} falha secundária ao gravar interaction (campanha já persistida): campaignId={} leadId={}",
                    requestId,
                    campaignId,
                    leadId,
                    e,
                )
            }
    }

    private fun logPersist(campaignId: Long, leadId: Long, status: String, detail: String) {
        log.info("WA send persist: campaignId={} leadId={} outcome={} detail={}", campaignId, leadId, status, detail.take(200))
    }

    private fun maskPhone(p: String): String {
        val d = p.filter { it.isDigit() }
        if (d.length < 4) return "***"
        return "***${d.takeLast(4)}"
    }

    private fun maskId(id: String?): String = id?.takeLast(4)?.let { "***$it" } ?: "null"

    private fun WhatsAppCloudHttpResult.Success.logSnippet(): String =
        runCatching { "messages=${body.messages?.size}" }.getOrDefault("(parse)")

    private fun buildFailureMetadataJson(
        campaignId: Long,
        statusCode: Int?,
        code: Int?,
        invalidRecipient: Boolean,
        fbtraceId: String?,
        category: String,
        requestId: String,
    ): String {
        val sc = statusCode?.toString() ?: "null"
        val c = code?.toString() ?: "null"
        val inv = if (invalidRecipient) "true" else "false"
        val trace = fbtraceId
            ?.replace("\\", "\\\\")
            ?.replace("\"", "\\\"")
            ?.let { ",\"fbtraceId\":\"$it\"" }
            .orEmpty()
        return """{"campaignId":$campaignId,"httpStatus":$sc,"errorCode":$c,"invalidRecipient":$inv,"category":"$category","requestId":"$requestId"$trace}"""
    }
}
