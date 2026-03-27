package com.gearsales.leadengine.web.viewmodels

data class DailyBatchViewModel(
    val id: Long = 0L,
    val createdAt: String = "",
    val totalLeads: Int = 0,
    val leads: List<BatchLeadRowViewModel> = emptyList(),
)
