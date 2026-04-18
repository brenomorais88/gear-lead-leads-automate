package com.gearsales.leadengine.domain.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WhatsAppSendDelayPickerTest {

    @Test
    fun randomDelayMinutesInclusive_singleValue() {
        repeat(20) {
            assertEquals(7, WhatsAppSendDelayPicker.randomDelayMinutesInclusive(7, 7))
        }
    }

    @Test
    fun randomDelayMinutesInclusive_swappedMinMax_stillInRange() {
        repeat(50) {
            val d = WhatsAppSendDelayPicker.randomDelayMinutesInclusive(10, 3)
            assertTrue(d in 3..10)
        }
    }

    @Test
    fun randomDelayMinutesInclusive_negativeClampedToZero() {
        repeat(30) {
            val d = WhatsAppSendDelayPicker.randomDelayMinutesInclusive(-5, 2)
            assertTrue(d in 0..2)
        }
    }
}
