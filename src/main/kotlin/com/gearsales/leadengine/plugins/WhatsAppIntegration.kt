package com.gearsales.leadengine.plugins

import com.gearsales.leadengine.config.WhatsAppAppConfig
import com.gearsales.leadengine.config.WhatsAppInfraSettings
import com.gearsales.leadengine.config.WhatsAppOperationalSeed
import com.gearsales.leadengine.database.repositories.BatchRepository
import com.gearsales.leadengine.database.repositories.LeadInteractionRepository
import com.gearsales.leadengine.database.repositories.LeadMessageCampaignRepository
import com.gearsales.leadengine.database.repositories.LeadRepository
import com.gearsales.leadengine.database.repositories.WhatsAppSettingsRepository
import com.gearsales.leadengine.domain.service.CampaignDispatchProcessor
import com.gearsales.leadengine.domain.service.CampaignSendWorker
import com.gearsales.leadengine.domain.service.PrepareBatchCampaignService
import com.gearsales.leadengine.domain.service.SendBatchCampaignService
import com.gearsales.leadengine.domain.service.SendWhatsAppTemplateService
import com.gearsales.leadengine.domain.service.WhatsAppCampaignReadService
import com.gearsales.leadengine.domain.service.OperationalDataPurgeService
import com.gearsales.leadengine.domain.service.WhatsAppEngineOperationalService
import com.gearsales.leadengine.domain.service.WhatsAppSettingsAdminService
import com.gearsales.leadengine.domain.service.WhatsAppWebhookService
import com.gearsales.leadengine.whatsapp.cloudapi.WhatsAppCloudApiClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.util.AttributeKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.Json
import java.time.LocalDateTime

val SendWhatsAppTemplateServiceKey = AttributeKey<SendWhatsAppTemplateService>("SendWhatsAppTemplateService")
val PrepareBatchCampaignServiceKey = AttributeKey<PrepareBatchCampaignService>("PrepareBatchCampaignService")
val SendBatchCampaignServiceKey = AttributeKey<SendBatchCampaignService>("SendBatchCampaignService")
val WhatsAppWebhookServiceKey = AttributeKey<WhatsAppWebhookService>("WhatsAppWebhookService")
val WhatsAppCampaignReadServiceKey = AttributeKey<WhatsAppCampaignReadService>("WhatsAppCampaignReadService")
val WhatsAppEngineOperationalServiceKey = AttributeKey<WhatsAppEngineOperationalService>("WhatsAppEngineOperationalService")
val WhatsAppAppConfigKey = AttributeKey<WhatsAppAppConfig>("WhatsAppAppConfig")
val WhatsAppSettingsAdminServiceKey = AttributeKey<WhatsAppSettingsAdminService>("WhatsAppSettingsAdminService")
val WhatsAppCloudApiClientKey = AttributeKey<WhatsAppCloudApiClient>("WhatsAppCloudApiClient")

private val whatsAppDispatchScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

fun Application.configureWhatsAppIntegration() {
    val infra = WhatsAppInfraSettings.load(environment.config)
    val seed = WhatsAppOperationalSeed.loadFromYamlOnly(environment.config)
    val waSettingsRepo = WhatsAppSettingsRepository()
    val now = LocalDateTime.now()
    waSettingsRepo.ensureSingletonFromSeed(
        phoneNumberId = seed.phoneNumberId,
        defaultTemplateName = seed.defaultTemplateName,
        defaultTemplateLanguage = seed.defaultTemplateLanguage,
        dailySendLimit = seed.dailySendLimit,
        sendDelayMinMinutes = seed.sendDelayMinMinutes,
        sendDelayMaxMinutes = seed.sendDelayMaxMinutes,
        batchSize = seed.batchSize,
        executionStartTime = seed.executionStartTime,
        executionEndTime = seed.executionEndTime,
        servicePaused = seed.servicePaused,
        now = now,
    )
    val whatsappConfig = WhatsAppAppConfig(infra, waSettingsRepo)
    attributes.put(WhatsAppAppConfigKey, whatsappConfig)
    attributes.put(
        WhatsAppSettingsAdminServiceKey,
        WhatsAppSettingsAdminService(waSettingsRepo, OperationalDataPurgeService()),
    )

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    val campaignRepository = LeadMessageCampaignRepository()
    val leadRepository = LeadRepository()
    val interactionRepository = LeadInteractionRepository()
    val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
    }
    val apiClient = WhatsAppCloudApiClient(httpClient, json, whatsappConfig)
    attributes.put(WhatsAppCloudApiClientKey, apiClient)
    val sendService = SendWhatsAppTemplateService(
        whatsappConfig = whatsappConfig,
        apiClient = apiClient,
        campaignRepository = campaignRepository,
        leadRepository = leadRepository,
        interactionRepository = interactionRepository,
    )
    attributes.put(SendWhatsAppTemplateServiceKey, sendService)
    val prepareBatchService = PrepareBatchCampaignService(
        whatsappConfig = whatsappConfig,
        batchRepository = BatchRepository(),
        leadRepository = leadRepository,
        campaignRepository = campaignRepository,
        interactionRepository = interactionRepository,
    )
    attributes.put(PrepareBatchCampaignServiceKey, prepareBatchService)
    val sendBatchService = SendBatchCampaignService(
        whatsappConfig = whatsappConfig,
        campaignRepository = campaignRepository,
    )
    attributes.put(SendBatchCampaignServiceKey, sendBatchService)
    val dispatchProcessor = CampaignDispatchProcessor(
        whatsappConfig = whatsappConfig,
        campaignRepository = campaignRepository,
        sendWhatsAppTemplateService = sendService,
    )
    val campaignSendWorker = CampaignSendWorker(whatsAppDispatchScope, whatsappConfig, dispatchProcessor)
    campaignSendWorker.start()
    val webhookService = WhatsAppWebhookService(
        infra = infra,
        campaignRepository = campaignRepository,
        leadRepository = leadRepository,
        interactionRepository = interactionRepository,
    )
    attributes.put(WhatsAppWebhookServiceKey, webhookService)
    val engineOperationalService = WhatsAppEngineOperationalService(
        whatsappConfig = whatsappConfig,
        campaignRepository = campaignRepository,
    )
    attributes.put(WhatsAppEngineOperationalServiceKey, engineOperationalService)
    val readService = WhatsAppCampaignReadService(
        whatsappConfig = whatsappConfig,
        campaignRepository = campaignRepository,
        batchRepository = BatchRepository(),
        leadRepository = leadRepository,
        engineOperationalService = engineOperationalService,
    )
    attributes.put(WhatsAppCampaignReadServiceKey, readService)

    monitor.subscribe(ApplicationStopped) {
        campaignSendWorker.stop()
        whatsAppDispatchScope.cancel()
        httpClient.close()
    }
}

fun Application.sendWhatsAppTemplateService(): SendWhatsAppTemplateService =
    attributes[SendWhatsAppTemplateServiceKey]

fun Application.prepareBatchCampaignService(): PrepareBatchCampaignService =
    attributes[PrepareBatchCampaignServiceKey]

fun Application.sendBatchCampaignService(): SendBatchCampaignService =
    attributes[SendBatchCampaignServiceKey]

fun Application.whatsappWebhookService(): WhatsAppWebhookService =
    attributes[WhatsAppWebhookServiceKey]

fun Application.whatsappCampaignReadService(): WhatsAppCampaignReadService =
    attributes[WhatsAppCampaignReadServiceKey]

fun Application.whatsAppEngineOperationalService(): WhatsAppEngineOperationalService =
    attributes[WhatsAppEngineOperationalServiceKey]

fun Application.whatsappAppConfig(): WhatsAppAppConfig =
    attributes[WhatsAppAppConfigKey]

fun Application.whatsappSettingsAdminService(): WhatsAppSettingsAdminService =
    attributes[WhatsAppSettingsAdminServiceKey]

fun Application.whatsappCloudApiClient(): WhatsAppCloudApiClient =
    attributes[WhatsAppCloudApiClientKey]
