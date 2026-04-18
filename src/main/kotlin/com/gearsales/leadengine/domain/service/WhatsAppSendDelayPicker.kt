package com.gearsales.leadengine.domain.service

import kotlin.random.Random

object WhatsAppSendDelayPicker {

    fun randomDelayMinutesInclusive(minMinutes: Int, maxMinutes: Int): Int {
        val lo = minOf(minMinutes, maxMinutes).coerceAtLeast(0)
        val hi = maxOf(minMinutes, maxMinutes).coerceAtLeast(lo)
        return Random.Default.nextInt(lo, hi + 1)
    }
}
