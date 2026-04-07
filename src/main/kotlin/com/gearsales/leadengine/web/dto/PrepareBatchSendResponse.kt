package com.gearsales.leadengine.web.dto

import kotlinx.serialization.Serializable

@Serializable
data class PrepareBatchSendResponse(
    val batchId: Long,
    val totalLeadsNoLote: Int,
    val campaignsCreated: Int,
    val skipped: Int,
    val skippedReasons: Map<String, Int> = emptyMap(),
)

@Serializable
data class ApiErrorResponse(val error: String)
