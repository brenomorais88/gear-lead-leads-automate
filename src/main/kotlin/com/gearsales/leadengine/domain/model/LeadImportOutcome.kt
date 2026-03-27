package com.gearsales.leadengine.domain.model

data class LeadImportOutcome(
    val preview: LeadImportPreview,
    val result: LeadImportResult,
)
