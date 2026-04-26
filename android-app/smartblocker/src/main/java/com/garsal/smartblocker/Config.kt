package com.garsal.smartblocker

data class Schedule(val hour: Int, val minute: Int)

object Config {
    const val PIN = "2059"
    const val MAX_SNOOZES = 2
    const val SNOOZE_DURATION_MS = 15 * 60 * 1000L   // 15 minuti
    const val CHECK_INTERVAL_MS = 30_000L              // controllo ogni 30s

    // Ogni giorno alle 08:00 e alle 22:00
    val SCHEDULES = listOf(
        Schedule(8, 0),
        Schedule(22, 0)
    )
}
