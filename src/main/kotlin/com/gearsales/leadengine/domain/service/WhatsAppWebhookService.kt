package com.gearsales.leadengine.domain.service

import com.gearsales.leadengine.config.WhatsAppInfraSettings
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
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class WhatsAppWebhookService(
    private val infra: WhatsAppInfraSettings,
    private val campaignRepository: LeadMessageCampaignRepository,
    private val leadRepository: LeadRepository,
    private val interactionRepository: LeadInteractionRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val zone: ZoneId = ZoneId.systemDefault()

    fun verifyWebhook(mode: String?, token: String?, challenge: String?): String? {
        if (mode != "subscribe") return null
        val expected = infra.webhookVerifyToken?.trim().orEmpty()
        if (expected.isEmpty()) return null
        if (token != expected) return null
        if (challenge.isNullOrBlank()) return null
        return challenge
    }

    fun handlePayload(root: WhatsAppWebhookRoot) {
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
                        transaction { handleInboundMessage(msg) }
                    } catch (e: Exception) {
                        log.warn("whatsapp webhook inbound: {}", e.message)
                    }
                }
            }
        }
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
            }
            "delivered" -> {
                if (current.status in SKIP_DELIVERED) return
                campaignRepository.patchCampaignFromWebhook(
                    campaign.id,
                    LeadCampaignStatus.DELIVERED,
                    now,
                    deliveredAt = eventTime,
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
            }
        }
    }

    private fun handleInboundMessage(msg: WhatsAppWebhookInboundMessage) {
        val from = msg.from?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val lead = leadRepository.findFirstMatchingWhatsAppFrom(from) ?: return
        val now = LocalDateTime.now()
        val eventTime = parseMetaTimestamp(msg.timestamp) ?: now
        val note = extractInboundBody(msg)?.take(MAX_NOTE_CHARS)

        leadRepository.applyInboundWhatsAppReply(lead.id, now)

        val latest = campaignRepository.findLatestOutboundCampaignForLead(lead.id)
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
