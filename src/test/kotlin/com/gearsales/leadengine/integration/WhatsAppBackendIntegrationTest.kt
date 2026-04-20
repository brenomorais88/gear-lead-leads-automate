package com.gearsales.leadengine.integration

import com.gearsales.leadengine.config.WhatsAppAppConfig
import com.gearsales.leadengine.database.repositories.BatchRepository
import com.gearsales.leadengine.database.repositories.LeadInteractionRepository
import com.gearsales.leadengine.database.repositories.LeadMessageCampaignRepository
import com.gearsales.leadengine.database.repositories.LeadRepository
import com.gearsales.leadengine.database.tables.DailyBatchLeadsTable
import com.gearsales.leadengine.database.tables.DailyBatchesTable
import com.gearsales.leadengine.database.tables.LeadInteractionsTable
import com.gearsales.leadengine.database.tables.LeadMessageCampaignsTable
import com.gearsales.leadengine.database.tables.LeadsTable
import com.gearsales.leadengine.database.tables.WhatsappSettingsTable
import com.gearsales.leadengine.domain.model.LeadCampaignStatus
import com.gearsales.leadengine.domain.model.LeadImportRow
import com.gearsales.leadengine.domain.model.LeadPriority
import com.gearsales.leadengine.domain.model.LeadStatus
import com.gearsales.leadengine.domain.service.PhoneNormalizer
import com.gearsales.leadengine.domain.service.CampaignDispatchProcessor
import com.gearsales.leadengine.domain.service.PrepareBatchCampaignService
import com.gearsales.leadengine.domain.service.PrepareBatchSkipReason
import com.gearsales.leadengine.domain.service.SendBatchCampaignService
import com.gearsales.leadengine.domain.service.SendTrigger
import com.gearsales.leadengine.domain.service.SendWhatsAppTemplateResult
import com.gearsales.leadengine.domain.service.SendWhatsAppTemplateService
import com.gearsales.leadengine.domain.model.WhatsAppEngineStatus
import com.gearsales.leadengine.domain.service.WhatsAppCampaignReadService
import com.gearsales.leadengine.domain.service.WhatsAppEngineOperationalService
import com.gearsales.leadengine.domain.service.WhatsAppWebhookService
import com.gearsales.leadengine.testWhatsAppAppConfig
import com.gearsales.leadengine.testWhatsAppSettingsRepository
import com.gearsales.leadengine.whatsapp.cloudapi.WhatsAppCloudApiClient
import com.gearsales.leadengine.whatsapp.webhook.dto.WhatsAppWebhookRoot
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

/** Falha proposital no insert de interaction (para testar que a campanha FAILED não é revertida). */
private class FailingLeadInteractionRepository : LeadInteractionRepository() {
    override fun insert(
        leadId: Long,
        interactionType: String,
        result: String?,
        note: String?,
        direction: String?,
        externalMessageId: String?,
        metadataJson: String?,
    ): Long = throw RuntimeException("simulated interaction persistence failure")
}

private data class WhatsAppServiceBundle(
    val prepare: PrepareBatchCampaignService,
    val sendBatch: SendBatchCampaignService,
    val sendTemplate: SendWhatsAppTemplateService,
    val processor: CampaignDispatchProcessor,
    val webhook: WhatsAppWebhookService,
)

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WhatsAppBackendIntegrationTest {

    private lateinit var dataSource: HikariDataSource
    private val leadRepo = LeadRepository()
    private val batchRepo = BatchRepository()
    private val campaignRepo = LeadMessageCampaignRepository()
    private val interactionRepo = LeadInteractionRepository()

    @BeforeAll
    fun startDb() {
        dataSource = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = "jdbc:sqlite::memory:"
                driverClassName = "org.sqlite.JDBC"
                maximumPoolSize = 1
            },
        )
        Database.connect(dataSource)
        transaction {
            SchemaUtils.create(
                LeadsTable,
                DailyBatchesTable,
                DailyBatchLeadsTable,
                LeadInteractionsTable,
                LeadMessageCampaignsTable,
                WhatsappSettingsTable,
            )
        }
    }

    @AfterAll
    fun stopDb() {
        dataSource.close()
    }

    @BeforeEach
    fun wipe() {
        transaction {
            LeadMessageCampaignsTable.deleteAll()
            DailyBatchLeadsTable.deleteAll()
            DailyBatchesTable.deleteAll()
            LeadInteractionsTable.deleteAll()
            LeadsTable.deleteAll()
            WhatsappSettingsTable.deleteAll()
        }
    }

    private fun insertLead(cnpj: String, phone: String, nomeFantasia: String?): Long {
        val row = LeadImportRow(
            cnpj = cnpj,
            razaoSocial = "Razão $cnpj",
            nomeFantasia = nomeFantasia,
            telefone = phone,
            email = null,
            endereco = null,
            cidade = null,
            estado = null,
            dataAbertura = null,
            cnae = null,
            situacao = null,
            porte = null,
            socio = null,
            capitalSocial = null,
            tipo = null,
        )
        val normalized = PhoneNormalizer.normalizeForWhatsApp(phone)
            ?: fail("telefone inválido no teste")
        return leadRepo.insert(row, cnpj, normalized, 0, LeadPriority.BAIXA)
    }

    private fun buildBundle(appConfig: WhatsAppAppConfig, mockJson: String): WhatsAppServiceBundle {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val engine = MockEngine(MockEngineConfig().apply {
            addHandler {
                respond(
                    content = mockJson,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        })
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) {
                json(json)
            }
        }
        val apiClient = WhatsAppCloudApiClient(httpClient, json, appConfig)
        val sendTemplate = SendWhatsAppTemplateService(
            whatsappConfig = appConfig,
            apiClient = apiClient,
            campaignRepository = campaignRepo,
            leadRepository = leadRepo,
            interactionRepository = interactionRepo,
        )
        val prepare = PrepareBatchCampaignService(
            whatsappConfig = appConfig,
            batchRepository = batchRepo,
            leadRepository = leadRepo,
            campaignRepository = campaignRepo,
            interactionRepository = interactionRepo,
        )
        val sendBatch = SendBatchCampaignService(
            whatsappConfig = appConfig,
            campaignRepository = campaignRepo,
        )
        val processor = CampaignDispatchProcessor(
            whatsappConfig = appConfig,
            campaignRepository = campaignRepo,
            sendWhatsAppTemplateService = sendTemplate,
        )
        val webhook = WhatsAppWebhookService(
            infra = appConfig.infra(),
            campaignRepository = campaignRepo,
            leadRepository = leadRepo,
            interactionRepository = interactionRepo,
        )
        return WhatsAppServiceBundle(prepare, sendBatch, sendTemplate, processor, webhook)
    }

    private fun buildSendTemplateService(
        appConfig: WhatsAppAppConfig,
        httpStatus: HttpStatusCode,
        responseBody: String,
        interactionRepository: LeadInteractionRepository,
    ): SendWhatsAppTemplateService {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val engine = MockEngine(MockEngineConfig().apply {
            addHandler {
                respond(
                    content = responseBody,
                    status = httpStatus,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        })
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) {
                json(json)
            }
        }
        val apiClient = WhatsAppCloudApiClient(httpClient, json, appConfig)
        return SendWhatsAppTemplateService(
            whatsappConfig = appConfig,
            apiClient = apiClient,
            campaignRepository = campaignRepo,
            leadRepository = leadRepo,
            interactionRepository = interactionRepository,
        )
    }

    @Test
    fun sendForCampaign_metaApiError_whenInteractionInsertFails_campaignStaysFailed() = runBlocking {
        val errorJson =
            """{"error":{"message":"Invalid OAuth access token","type":"OAuthException","code":190}}"""
        val appConfig = testWhatsAppAppConfig()
        val sendTemplate = buildSendTemplateService(
            appConfig,
            HttpStatusCode.Unauthorized,
            errorJson,
            FailingLeadInteractionRepository(),
        )
        val prepare = PrepareBatchCampaignService(
            whatsappConfig = appConfig,
            batchRepository = batchRepo,
            leadRepository = leadRepo,
            campaignRepository = campaignRepo,
            interactionRepository = interactionRepo,
        )
        val leadId = insertLead("99999999000199", "11988887777", "Loja IX")
        val batchId = batchRepo.insertBatch(1)
        batchRepo.linkLeads(batchId, listOf(leadId))
        prepare.prepare(batchId)
        SendBatchCampaignService(appConfig, campaignRepo).scheduleBatchSend(batchId)
        val campId = campaignRepo.findByBatchId(batchId).single().id
        val now = LocalDateTime.now()
        val staleBefore = now.minusDays(1)
        val claimed = campaignRepo.tryClaimNextDispatchableCampaign(now, staleBefore)
        assertEquals(campId, claimed)

        val r = sendTemplate.sendForCampaign(campId, SendTrigger.WORKER)
        assertIs<SendWhatsAppTemplateResult.Failure>(r)

        val camp = campaignRepo.findById(campId)!!
        assertEquals(LeadCampaignStatus.FAILED, camp.status)
        assertTrue(!camp.failureReason.isNullOrBlank())
        assertEquals(0, interactionRepo.listByLeadId(leadId).size)
    }

    @Test
    fun prepareSend_duplicateBlocked_and_templateDefaults() {
        val bundle = buildBundle(testWhatsAppAppConfig(), "{}")
        val leadId = insertLead("11111111000191", "11988887777", "Loja X")
        val batchId = batchRepo.insertBatch(1)
        batchRepo.linkLeads(batchId, listOf(leadId))

        val first = bundle.prepare.prepare(batchId)
        assertEquals(1, first.campaignsCreated)
        val second = bundle.prepare.prepare(batchId)
        assertEquals(0, second.campaignsCreated)
        assertEquals(
            1,
            second.skippedReasons[PrepareBatchSkipReason.DUPLICATE_ACTIVE_CAMPAIGN],
        )

        val camp = campaignRepo.findPendingByBatchId(batchId).single()
        assertEquals("gear_lead_intro_v1", camp.templateName)
        assertEquals("pt_BR", camp.templateLanguage)
    }

    @Test
    fun sendBatch_success_updatesCampaignAndLead_andSecondSendDoesNotResend() = runBlocking {
        val successBody = """{"messaging_product":"whatsapp","messages":[{"id":"wamid.INTEG1"}]}"""
        val bundle = buildBundle(testWhatsAppAppConfig(), successBody)
        val leadId = insertLead("22222222000182", "11977776666", null)
        val batchId = batchRepo.insertBatch(1)
        batchRepo.linkLeads(batchId, listOf(leadId))
        bundle.prepare.prepare(batchId)

        val r1 = bundle.sendBatch.scheduleBatchSend(batchId)
        assertEquals(1, r1.newlyScheduledCount)
        assertTrue(bundle.processor.processNextEligible(SendTrigger.WORKER))

        val camp = campaignRepo.findByBatchId(batchId).single()
        assertEquals(LeadCampaignStatus.SENT, camp.status)
        assertEquals("wamid.INTEG1", camp.waMessageId)

        val lead = leadRepo.findById(leadId)!!
        assertEquals(LeadStatus.CONTATADO.name, lead.status)
        assertTrue(lead.proximoFollowupEm != null)

        val r2 = bundle.sendBatch.scheduleBatchSend(batchId)
        assertEquals(0, r2.newlyScheduledCount)
        assertTrue(!bundle.processor.processNextEligible(SendTrigger.WORKER))
    }

    @Test
    fun sendForCampaign_rejectsNonPending() = runBlocking {
        val successBody = """{"messaging_product":"whatsapp","messages":[{"id":"wamid.X"}]}"""
        val bundle = buildBundle(testWhatsAppAppConfig(), successBody)
        val leadId = insertLead("33333333000173", "11966665555", "Nome")
        val batchId = batchRepo.insertBatch(1)
        batchRepo.linkLeads(batchId, listOf(leadId))
        bundle.prepare.prepare(batchId)
        bundle.sendBatch.scheduleBatchSend(batchId)
        assertTrue(bundle.processor.processNextEligible(SendTrigger.WORKER))

        val campId = campaignRepo.findByBatchId(batchId).single().id
        val r = bundle.sendTemplate.sendForCampaign(campId)
        assertIs<SendWhatsAppTemplateResult.Failure>(r)
        assertTrue(r.reason.contains("PENDING"))
    }

    @Test
    fun sendBatch_dailyLimit_stopsAfterQuota() = runBlocking {
        val repo = testWhatsAppSettingsRepository(
            dailySendLimit = 1,
            sendDelayMinMinutes = 0,
            sendDelayMaxMinutes = 0,
        )
        val appConfig = testWhatsAppAppConfig(repository = repo)
        val body = """{"messaging_product":"whatsapp","messages":[{"id":"wamid.Q"}]}"""
        val bundle = buildBundle(appConfig, body)
        val l1 = insertLead("44444444000156", "11955554444", "A")
        val l2 = insertLead("55555555000147", "11944443333", "B")
        val batchId = batchRepo.insertBatch(2)
        batchRepo.linkLeads(batchId, listOf(l1, l2))
        bundle.prepare.prepare(batchId)

        val r = bundle.sendBatch.scheduleBatchSend(batchId)
        assertEquals(2, r.newlyScheduledCount)
        assertEquals(1, r.remainingQuotaToday)

        assertTrue(bundle.processor.processNextEligible(SendTrigger.WORKER))
        val afterFirst = campaignRepo.findByBatchId(batchId)
        assertEquals(1, afterFirst.count { it.status == LeadCampaignStatus.SENT })
        assertEquals(1, afterFirst.count { it.status == LeadCampaignStatus.PENDING })

        assertTrue(bundle.processor.processNextEligible(SendTrigger.WORKER))
        val stillPending = campaignRepo.findByBatchId(batchId).single { it.status == LeadCampaignStatus.PENDING }
        assertTrue(stillPending.scheduledAt != null)
        assertTrue(stillPending.scheduledAt!!.isAfter(LocalDateTime.now()))
    }

    @Test
    fun dispatch_respectsGlobalMinDelayBetweenSends() = runBlocking {
        val repo = testWhatsAppSettingsRepository(
            dailySendLimit = 10,
            sendDelayMinMinutes = 5,
            sendDelayMaxMinutes = 5,
        )
        val appConfig = testWhatsAppAppConfig(repository = repo)
        val body = """{"messaging_product":"whatsapp","messages":[{"id":"wamid.DELAY"}]}"""
        val bundle = buildBundle(appConfig, body)
        val l1 = insertLead("10101010000110", "11999990001", "Delay A")
        val l2 = insertLead("20202020000120", "11999990002", "Delay B")
        val batchId = batchRepo.insertBatch(2)
        batchRepo.linkLeads(batchId, listOf(l1, l2))
        bundle.prepare.prepare(batchId)
        bundle.sendBatch.scheduleBatchSend(batchId)

        val now = LocalDateTime.now()
        campaignRepo.findByBatchId(batchId).forEach {
            campaignRepo.setScheduledAt(it.id, now, now)
        }

        assertTrue(bundle.processor.processNextEligible(SendTrigger.WORKER))
        assertTrue(!bundle.processor.processNextEligible(SendTrigger.WORKER))
    }

    @Test
    fun webhook_statusAndInbound_updateCampaignAndLead() {
        val bundle = buildBundle(testWhatsAppAppConfig(), "{}")
        val leadId = insertLead("66666666000138", "11933332222", "Inbound Loja")
        val batchId = batchRepo.insertBatch(1)
        batchRepo.linkLeads(batchId, listOf(leadId))
        bundle.prepare.prepare(batchId)

        transaction {
            val cid = campaignRepo.findPendingByBatchId(batchId).single().id
            val now = LocalDateTime.now()
            campaignRepo.updateAfterSendSuccess(cid, "wamid.WH", now, now)
        }

        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val delivered = """
            {"entry":[{"changes":[{"value":{
              "statuses":[{"id":"wamid.WH","status":"delivered","timestamp":"1730000000"}]
            }}]}]}
        """.trimIndent()
        bundle.webhook.handlePayload(json.decodeFromString(WhatsAppWebhookRoot.serializer(), delivered))
        assertEquals(
            LeadCampaignStatus.DELIVERED,
            campaignRepo.findByWaMessageId("wamid.WH")!!.status,
        )

        val inbound = """
            {"entry":[{"changes":[{"value":{
              "messages":[{
                "from":"5511933332222",
                "id":"wamid.IN",
                "timestamp":"1730000001",
                "type":"text",
                "text":{"body":"quero saber mais"}
              }]
            }}]}]}
        """.trimIndent()
        bundle.webhook.handlePayload(json.decodeFromString(WhatsAppWebhookRoot.serializer(), inbound))

        val lead = leadRepo.findById(leadId)!!
        assertEquals(LeadStatus.RESPONDEU.name, lead.status)
        assertTrue(lead.respondeu)
        assertTrue(lead.proximoFollowupEm == null)
        assertEquals(LeadCampaignStatus.RESPONDED, campaignRepo.findByWaMessageId("wamid.WH")!!.status)

        val inboundIx = interactionRepo.listByLeadId(leadId).find {
            it.interactionType == com.gearsales.leadengine.domain.model.LeadInteractionTypes.WHATSAPP_INBOUND_MESSAGE
        }
        assertTrue(inboundIx != null)
        assertEquals("quero saber mais", inboundIx!!.note)
    }

    @Test
    fun readService_batchCampaigns_listFiltered_dashboard_batchSummary() {
        val appConfig = testWhatsAppAppConfig()
        val engine = WhatsAppEngineOperationalService(appConfig, campaignRepo)
        val read = WhatsAppCampaignReadService(appConfig, campaignRepo, batchRepo, leadRepo, engine)
        val bundle = buildBundle(appConfig, "{}")
        val leadId = insertLead("77777777000100", "11911112222", "Loja X")
        val batchId = batchRepo.insertBatch(1)
        batchRepo.linkLeads(batchId, listOf(leadId))
        bundle.prepare.prepare(batchId)

        val joined = campaignRepo.listCampaignsWithLeadForBatch(batchId)
        assertEquals(1, joined.size)
        assertEquals("Loja X", joined.single().nomeFantasia)

        val api = read.batchCampaigns(batchId)
        assertEquals(1, api.totalCampaigns)
        assertEquals("Loja X", api.campaigns.single().lojaNome)
        assertEquals(LeadCampaignStatus.PENDING.name, api.campaigns.single().status)

        val (page, total) = campaignRepo.listCampaignsWithLeadFiltered(LeadCampaignStatus.PENDING, null, null, 10, 0)
        assertEquals(1, page.size)
        assertEquals(1L, total)

        val (emptyBatch, totalBatch) = campaignRepo.listCampaignsWithLeadFiltered(null, 999L, null, 10, 0)
        assertEquals(0, emptyBatch.size)
        assertEquals(0L, totalBatch)

        val dash = read.dashboardSummary()
        assertEquals(1L, dash.totalCampaigns)
        assertEquals(appConfig.effective().dailySendLimit, dash.dailyLimit)
        assertEquals(appConfig.effective().defaultTemplateName, dash.activeTemplateName)
        assertEquals(appConfig.effective().defaultTemplateLanguage, dash.activeTemplateLanguage)
        assertEquals(WhatsAppEngineStatus.PENDING_WITHOUT_SCHEDULE, dash.engineStatus.currentStatus)

        val bs = read.batchWhatsappSummary(batchId)!!
        assertEquals(1, bs.summary.campaignsCreated)
        assertEquals(1, bs.summary.pending)
        assertEquals(0, bs.summary.sent)
        assertEquals(1, bs.leads.size)
        assertEquals(LeadCampaignStatus.PENDING.name, bs.leads.single().campaignStatus)
    }

    @Test
    fun processor_skipsWhenServicePaused_noClaim() = runBlocking {
        val repo = testWhatsAppSettingsRepository(servicePaused = true)
        val appConfig = testWhatsAppAppConfig(repository = repo)
        val successBody = """{"messaging_product":"whatsapp","messages":[{"id":"wamid.PAUSED"}]}"""
        val bundle = buildBundle(appConfig, successBody)
        val leadId = insertLead("88888888000199", "11900001111", "Paused Loja")
        val batchId = batchRepo.insertBatch(1)
        batchRepo.linkLeads(batchId, listOf(leadId))
        bundle.prepare.prepare(batchId)
        bundle.sendBatch.scheduleBatchSend(batchId)

        assertTrue(!bundle.processor.processNextEligible(SendTrigger.WORKER))
        val camp = campaignRepo.findByBatchId(batchId).single()
        assertEquals(LeadCampaignStatus.PENDING, camp.status)
    }
}
