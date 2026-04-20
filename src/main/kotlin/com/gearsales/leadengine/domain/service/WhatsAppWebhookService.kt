package com.gearsales.leadengine.domain.service

import com.gearsales.leadengine.config.WhatsAppInfraSettings
import com.gearsales.leadengine.config.WhatsAppAppConfig
import com.gearsales.leadengine.database.repositories.LeadInteractionRepository
import com.gearsales.leadengine.database.repositories.LeadMessageCampaignRepository
import com.gearsales.leadengine.database.repositories.LeadRepository
import com.gearsales.leadengine.domain.model.InteractionDirection
import com.gearsales.leadengine.domain.model.LeadCampaignStatus
import com.gearsales.leadengine.domain.model.WhatsAppFailureCategory
import com.gearsales.leadengine.domain.model.LeadInteractionTypes
import com.gearsales.leadengine.whatsapp.webhook.dto.WhatsAppWebhookInboundMessage
import com.gearsales.leadengine.whatsapp.webhook.dto.WhatsAppWebhookRoot
import com.gearsales.leadengine.whatsapp.webhook.dto.WhatsAppWebhookStatusItem
import com.gearsales.leadengine.whatsapp.cloudapi.WhatsAppCloudApiClient
import com.gearsales.leadengine.whatsapp.cloudapi.WhatsAppCloudHttpResult
import com.gearsales.leadengine.plugins.SystemEvents
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking

class WhatsAppWebhookService(
    private val infra: WhatsAppInfraSettings,
    private val campaignRepository: LeadMessageCampaignRepository,
    private val leadRepository: LeadRepository,
    private val interactionRepository: LeadInteractionRepository,
    private val apiClient: WhatsAppCloudApiClient? = null,
    private val whatsappConfig: WhatsAppAppConfig? = null,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val zone: ZoneId = ZoneId.systemDefault()
    private data class InboundNotificationPayload(
        val leadId: Long,
        val leadName: String,
        val leadPhone: String,
        val inboundText: String,
    )

    fun verifyWebhook(mode: String?, token: String?, challenge: String?): String? {
        if (mode != "subscribe") return null
        val expected = infra.webhookVerifyToken?.trim().orEmpty()
        if (expected.isEmpty()) return null
        if (token != expected) return null
        if (challenge.isNullOrBlank()) return null
        return challenge
    }

    fun handlePayload(root: WhatsAppWebhookRoot) {
        val inboundNotifications = mutableListOf<InboundNotificationPayload>()
        if (root.entry.isEmpty()) {
            log.debug("whatsapp webhook: payload sem entry")
        }
        for (entry in root.entry) {
            for (change in entry.changes) {
                val value = change.value ?: continue
                for (st in value.statuses) {
                    try {
                        transaction { handleStatusUpdate(st) }
                    } catch (e: Exception) {
                        log.warn("whatsapp webhook status: {}", e.message)
                    }
                }
                for (msg in value.messages) {
                    try {
                        transaction {
                            handleInboundMessage(msg)?.let { inboundNotifications += it }
                        }
                    } catch (e: Exception) {
                        log.warn("whatsapp webhook inbound: {}", e.message)
                    }
                }
            }
        }
        dispatchInboundNotifications(inboundNotifications)
    }

    private fun handleStatusUpdate(st: WhatsAppWebhookStatusItem) {
        val waId = st.id?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val campaign = campaignRepository.findByWaMessageId(waId) ?: return
        val now = LocalDateTime.now()
        val eventTime = parseMetaTimestamp(st.timestamp) ?: now
        val statusStr = st.status?.lowercase()?.trim() ?: return
        val current = campaignRepository.findById(campaign.id) ?: return

        when (statusStr) {
            "sent" -> {
                if (current.status in SKIP_SENT_IF_ALREADY) return
                campaignRepository.patchCampaignFromWebhook(
                    campaign.id,
                    LeadCampaignStatus.SENT,
                    now,
                    sentAt = eventTime,
                )
                SystemEvents.info(
                    category = "WHATSAPP_STATUS",
                    summary = "Webhook status SENT para campanha ${campaign.id}",
                )
            }
            "delivered" -> {
                if (current.status in SKIP_DELIVERED) return
                campaignRepository.patchCampaignFromWebhook(
                    campaign.id,
                    LeadCampaignStatus.DELIVERED,
                    now,
                    deliveredAt = eventTime,
                )
                SystemEvents.info(
                    category = "WHATSAPP_STATUS",
                    summary = "Webhook status DELIVERED para campanha ${campaign.id}",
                )
            }
            "read" -> {
                if (current.status in SKIP_READ) return
                campaignRepository.patchCampaignFromWebhook(
                    campaign.id,
                    LeadCampaignStatus.READ,
                    now,
                    readAt = eventTime,
                )
                SystemEvents.info(
                    category = "WHATSAPP_STATUS",
                    summary = "Webhook status READ para campanha ${campaign.id}",
                )
            }
            "failed" -> {
                val reason = st.errors.firstOrNull().let { e ->
                    listOfNotNull(e?.code?.toString(), e?.title, e?.message)
                        .joinToString(": ")
                        .ifBlank { "delivery_failed" }
                }
                campaignRepository.patchCampaignFromWebhook(
                    campaign.id,
                    LeadCampaignStatus.FAILED,
                    now,
                    failedAt = eventTime,
                    failureReason = reason,
                    failureCategoryStored = WhatsAppFailureCategory.META_API_ERROR.name,
                )
                SystemEvents.warn(
                    category = "WHATSAPP_STATUS",
                    summary = "Webhook status FAILED para campanha ${campaign.id}",
                    details = reason,
                )
            }
        }
    }

    private fun handleInboundMessage(msg: WhatsAppWebhookInboundMessage): InboundNotificationPayload? {
        val from = msg.from?.trim()?.takeIf { it.isNotEmpty() } ?: run {
            log.warn("whatsapp webhook inbound: mensagem sem campo from (type={})", msg.type)
            return null
        }
        val contextWaMessageId = msg.context?.id?.trim()?.takeIf { it.isNotEmpty() }
        val contextCampaign = contextWaMessageId?.let { campaignRepository.findByWaMessageId(it) }
        val phoneCandidates = phoneCandidatesForInbound(from)
        val phoneCampaign =
            if (contextCampaign == null) {
                campaignRepository.findLatestOutboundCampaignByPhoneCandidates(phoneCandidates)
            } else {
                null
            }
        val lead =
            contextCampaign?.let { campaign ->
                leadRepository.findById(campaign.leadId)
            } ?: phoneCampaign?.let { campaign ->
                leadRepository.findById(campaign.leadId)
            } ?: leadRepository.findFirstMatchingWhatsAppFrom(from)
        if (lead == null) {
            log.warn(
                "whatsapp webhook inbound: sem lead para from={} contextId={} candidates={}",
                maskWaFrom(from),
                maskWaId(contextWaMessageId),
                phoneCandidates.size,
            )
            return null
        }
        val now = LocalDateTime.now()
        val eventTime = parseMetaTimestamp(msg.timestamp) ?: now
        val note = extractInboundBody(msg)?.take(MAX_NOTE_CHARS)

        leadRepository.applyInboundWhatsAppReply(lead.id, now)

        val latest =
            contextCampaign
                ?: phoneCampaign
                ?: campaignRepository.findLatestOutboundCampaignForLead(lead.id)
        if (latest != null && latest.status != LeadCampaignStatus.RESPONDED) {
            campaignRepository.markCampaignResponded(latest.id, eventTime, now)
        }

        val typeEsc = (msg.type ?: "").replace("\\", "\\\\").replace("\"", "\\\"")
        val tsEsc = (msg.timestamp ?: "").replace("\\", "\\\\").replace("\"", "\\\"")
        val metadataJson = """{"type":"$typeEsc","timestamp":"$tsEsc"}"""

        interactionRepository.insert(
            leadId = lead.id,
            interactionType = LeadInteractionTypes.WHATSAPP_INBOUND_MESSAGE,
            result = msg.type,
            note = note,
            direction = InteractionDirection.INBOUND,
            externalMessageId = msg.id,
            metadataJson = metadataJson,
        )
        log.info(
            "whatsapp webhook inbound: registrado para leadId={} type={} noteLen={}",
            lead.id,
            msg.type,
            note?.length ?: 0,
        )
        SystemEvents.info(
            category = "WHATSAPP_INBOUND",
            summary = "Resposta inbound registrada para lead ${lead.id}",
            details = note?.take(180),
        )
        return InboundNotificationPayload(
            leadId = lead.id,
            leadName = lead.nomeFantasia?.takeIf { it.isNotBlank() } ?: lead.razaoSocial,
            leadPhone = lead.telefoneOriginal ?: lead.telefoneNormalizado.orEmpty(),
            inboundText = note ?: "(mensagem sem texto)",
        )
    }

    private fun dispatchInboundNotifications(items: List<InboundNotificationPayload>) {
        if (items.isEmpty()) return
        val client = apiClient ?: return
        val appCfg = whatsappConfig ?: return
        val recipients = parseRecipients(infra.inboundNotifyRecipients)
        if (recipients.isEmpty()) return
        val eff = appCfg.effective()
        val templateName = infra.inboundNotifyTemplateName?.trim().takeIf { !it.isNullOrBlank() }
            ?: eff.defaultTemplateName
        val templateLanguage = infra.inboundNotifyTemplateLanguage?.trim().takeIf { !it.isNullOrBlank() }
            ?: eff.defaultTemplateLanguage
        items.forEach { p ->
            val bodyParam = renderNotifyBody(
                leadName = p.leadName,
                leadPhone = p.leadPhone,
                inboundText = p.inboundText,
            )
            recipients.forEach { to ->
                val out = runBlocking {
                    client.sendTemplateMessage(
                        toDigits = to,
                        templateName = templateName,
                        languageCode = templateLanguage,
                        bodyParameterText = bodyParam,
                        requestId = "inbound-notify-${p.leadId}",
                    )
                }
                when (out) {
                    is WhatsAppCloudHttpResult.Success -> {
                        SystemEvents.info(
                            category = "WHATSAPP_NOTIFY",
                            summary = "Notificação de resposta enviada para $to",
                            details = "lead=${p.leadId}",
                        )
                    }
                    is WhatsAppCloudHttpResult.ApiError -> {
                        val d = out.parsed?.message ?: out.rawBody.take(300)
                        log.warn("whatsapp notify failed to={} lead={} detail={}", to, p.leadId, d)
                        SystemEvents.warn(
                            category = "WHATSAPP_NOTIFY",
                            summary = "Falha ao notificar resposta para $to",
                            details = d,
                        )
                    }
                    WhatsAppCloudHttpResult.MissingCredentials -> {
                        log.warn("whatsapp notify skipped: missing credentials")
                    }
                }
            }
        }
    }

    private fun parseRecipients(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(',', ';', '\n', '\t', ' ')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { it.filter(Char::isDigit) }
            .filter { it.length >= 12 }
            .distinct()
    }

    private fun renderNotifyBody(
        leadName: String,
        leadPhone: String,
        inboundText: String,
    ): String {
        val fallback = "Lead: $leadName | Tel: $leadPhone | Msg: $inboundText"
        val tpl = infra.inboundNotifyBodyTemplate?.trim().takeIf { !it.isNullOrBlank() } ?: fallback
        return tpl
            .replace("{{lead_name}}", leadName)
            .replace("{{lead_phone}}", leadPhone)
            .replace("{{message}}", inboundText)
            .take(400)
    }

    /** Log seguro: só dígitos finais do wa_id. */
    private fun maskWaFrom(from: String): String {
        val d = from.filter { it.isDigit() }
        if (d.length <= 4) return "****"
        return "…${d.takeLast(4)}"
    }

    private fun maskWaId(id: String?): String {
        val v = id?.trim().orEmpty()
        if (v.isEmpty()) return "null"
        if (v.length <= 8) return "…$v"
        return "…${v.takeLast(8)}"
    }

    private fun phoneCandidatesForInbound(waFrom: String): List<String> {
        val digits = waFrom.filter { it.isDigit() }
        if (digits.isEmpty()) return emptyList()
        return buildSet {
            add(digits)
            PhoneNormalizer.normalizeForWhatsApp(waFrom)?.let { add(it) }
            PhoneNormalizer.normalizeForWhatsApp(digits)?.let { add(it) }
            if (digits.length == 11) add("55$digits")
            if (digits.length == 10) add("55$digits")
        }.filter { it.isNotBlank() }.toList()
    }

    private fun extractInboundBody(msg: WhatsAppWebhookInboundMessage): String? =
        when (msg.type?.lowercase()) {
            "text" -> msg.text?.body?.trim()?.takeIf { it.isNotEmpty() }
            else -> null
        }

    private fun parseMetaTimestamp(raw: String?): LocalDateTime? {
        if (raw.isNullOrBlank()) return null
        val sec = raw.toLongOrNull() ?: return null
        return Instant.ofEpochSecond(sec).atZone(zone).toLocalDateTime()
    }

    private companion object {
        private val SKIP_SENT_IF_ALREADY = setOf(
            LeadCampaignStatus.SENT,
            LeadCampaignStatus.DELIVERED,
            LeadCampaignStatus.READ,
            LeadCampaignStatus.RESPONDED,
        )
        private val SKIP_DELIVERED = setOf(
            LeadCampaignStatus.DELIVERED,
            LeadCampaignStatus.READ,
            LeadCampaignStatus.RESPONDED,
            LeadCampaignStatus.FAILED,
        )
        private val SKIP_READ = setOf(
            LeadCampaignStatus.READ,
            LeadCampaignStatus.FAILED,
            LeadCampaignStatus.RESPONDED,
        )
        private const val MAX_NOTE_CHARS = 16_000
    }
}
