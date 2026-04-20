package com.gearsales.leadengine.domain.model

import java.time.LocalDateTime

data class SystemEventRecord(
    val id: Long,
    val level: String,
    val category: String,
    val summary: String,
    val details: String?,
    val httpMethod: String?,
    val httpPath: String?,
    val httpStatus: Int?,
    val createdAt: LocalDateTime,
)
