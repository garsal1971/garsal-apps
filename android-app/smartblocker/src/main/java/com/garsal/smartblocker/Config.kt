package com.garsal.smartblocker

object Config {
    const val PIN = "2059"
    const val MAX_SNOOZES = 2
    const val SNOOZE_DURATION_MS = 15 * 60 * 1000L   // 15 minuti
    const val CHECK_INTERVAL_MS = 30_000L              // controllo ogni 30s
    const val CHALLENGE_HOLD_MS = 2000L                // tenuta SÌ/NO sfide Ta Firi?
}
