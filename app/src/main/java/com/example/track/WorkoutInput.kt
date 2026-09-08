package com.example.track

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun isValidWorkoutDuration(minutes: Int): Boolean = minutes in 1..1_440
internal fun isAllowedWorkoutDate(day: LocalDate, today: LocalDate): Boolean = day <= today

// Broad activity estimates for an approximately 75 kg adult, independent of profile weight.
// These are intentionally approximate; intensity and individual energy use vary.
fun estimateWorkoutCalories(type: WorkoutType, durationMinutes: Int): Int {
    require(isValidWorkoutDuration(durationMinutes))
    return type.caloriesPerMinute * durationMinutes
}

// An immutable form submission carries its own date; dashboard navigation cannot redirect it.
data class WorkoutInput(
    val day: LocalDate,
    val type: WorkoutType = WorkoutType.Strength,
    val durationMinutes: Int = 45,
    val startTime: String = formatWorkoutTime(LocalTime.now()),
    val notes: String = "",
) {
    fun isValid(today: LocalDate): Boolean = isAllowedWorkoutDate(day, today) &&
        isValidWorkoutDuration(durationMinutes) && runCatching {
            formatWorkoutTime(LocalTime.parse(startTime)) == startTime
        }.getOrDefault(false)
}

internal fun formatWorkoutTime(time: LocalTime): String =
    time.format(DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT))

// Material's date picker encodes calendar dates at UTC midnight, not local midnight.
internal fun LocalDate.toWorkoutPickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
internal fun Long.toWorkoutPickerDay(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
