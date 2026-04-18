package com.gearsales.leadengine.domain.service

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Janela diária [start, end] no fuso [zone], com horários em `HH:mm`.
 * - `start == end`: interpretado como **24h** (sempre dentro), para não bloquear o sistema por engano.
 * - `start` > `end`: janela cruza meia-noite (ex.: 22:00–06:00).
 */
object ExecutionWindowEvaluator {
    private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun parseTime(value: String): LocalTime? =
        try {
            LocalTime.parse(value.trim(), formatter)
        } catch (_: DateTimeParseException) {
            null
        }

    fun validateFields(startRaw: String, endRaw: String): List<String> {
        val errors = mutableListOf<String>()
        val s = startRaw.trim()
        val e = endRaw.trim()
        if (s.isEmpty()) errors.add("Horário de início (executionStartTime) é obrigatório (formato HH:mm).")
        if (e.isEmpty()) errors.add("Horário de fim (executionEndTime) é obrigatório (formato HH:mm).")
        if (errors.isNotEmpty()) return errors
        if (parseTime(s) == null) errors.add("Horário de início inválido; use HH:mm (ex.: 08:00).")
        if (parseTime(e) == null) errors.add("Horário de fim inválido; use HH:mm (ex.: 19:30).")
        return errors
    }

    fun isWithinWindow(now: LocalDateTime, zone: ZoneId, startRaw: String, endRaw: String): Boolean {
        val start = parseTime(startRaw) ?: return false
        val end = parseTime(endRaw) ?: return false
        if (start == end) {
            return true
        }
        val t = now.atZone(zone).toLocalTime()
        return if (start < end) {
            !t.isBefore(start) && !t.isAfter(end)
        } else {
            !t.isBefore(start) || !t.isAfter(end)
        }
    }
}
