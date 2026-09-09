package com.example.track

import java.text.NumberFormat

internal const val MaxDailySteps = 200_000

// Null is unlogged; an explicit zero is a valid daily entry.
internal fun isValidSteps(steps: Int?): Boolean = steps == null || steps in 0..MaxDailySteps
internal fun isValidSleepMinutes(minutes: Int?): Boolean = minutes == null || minutes in 0..1_440

internal fun sleepMinutesFromParts(hours: Int, minutes: Int): Int? =
    if (hours in 0..24 && minutes in 0..59 && (hours < 24 || minutes == 0)) hours * 60 + minutes else null

internal fun parseSteps(input: String): Int? = input.trim().toIntOrNull()?.takeIf { isValidSteps(it) }

internal fun formatSteps(steps: Int): String = NumberFormat.getIntegerInstance().format(steps)

internal fun formatSleep(minutes: Int): String {
    require(isValidSleepMinutes(minutes))
    val hours = minutes / 60
    val remainder = minutes % 60
    return if (remainder == 0) "${hours}h" else "${hours}h ${remainder}m"
}
