package com.gearsales.leadengine.domain.service

import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExecutionWindowEvaluatorTest {

    private val zone: ZoneId = ZoneId.of("America/Sao_Paulo")

    @Test
    fun sameStartEndMeansAlwaysInside() {
        val now = LocalDateTime.parse("2026-04-01T03:00:00")
        assertTrue(ExecutionWindowEvaluator.isWithinWindow(now, zone, "09:00", "09:00"))
    }

    @Test
    fun normalWindow_inside() {
        val now = LocalDateTime.parse("2026-04-01T12:00:00")
        assertTrue(ExecutionWindowEvaluator.isWithinWindow(now, zone, "08:00", "19:30"))
    }

    @Test
    fun normalWindow_outsideBefore() {
        val now = LocalDateTime.parse("2026-04-01T06:00:00")
        assertFalse(ExecutionWindowEvaluator.isWithinWindow(now, zone, "08:00", "19:30"))
    }

    @Test
    fun normalWindow_outsideAfter() {
        val now = LocalDateTime.parse("2026-04-01T20:00:00")
        assertFalse(ExecutionWindowEvaluator.isWithinWindow(now, zone, "08:00", "19:30"))
    }

    @Test
    fun overnightWindow_insideLate() {
        val now = LocalDateTime.parse("2026-04-01T23:00:00")
        assertTrue(ExecutionWindowEvaluator.isWithinWindow(now, zone, "22:00", "06:00"))
    }

    @Test
    fun overnightWindow_insideEarly() {
        val now = LocalDateTime.parse("2026-04-01T05:00:00")
        assertTrue(ExecutionWindowEvaluator.isWithinWindow(now, zone, "22:00", "06:00"))
    }

    @Test
    fun overnightWindow_outsideMidday() {
        val now = LocalDateTime.parse("2026-04-01T12:00:00")
        assertFalse(ExecutionWindowEvaluator.isWithinWindow(now, zone, "22:00", "06:00"))
    }

    @Test
    fun validateFields_rejectsGarbage() {
        val e = ExecutionWindowEvaluator.validateFields("25:00", "18:00")
        assertTrue(e.isNotEmpty())
    }
}
