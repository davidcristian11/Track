package com.example.track

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object TrackDateProvider {
    fun today(): LocalDate = LocalDate.now(ZoneId.systemDefault())
}

internal fun LocalDate.toDayKey(): String = toString()
internal fun String.toTrackDay(): LocalDate = LocalDate.parse(this)

internal fun previousTrackDay(day: LocalDate): LocalDate = day.minusDays(1)
internal fun canNavigateNext(day: LocalDate, today: LocalDate): Boolean = day < today
internal fun nextTrackDay(day: LocalDate, today: LocalDate): LocalDate =
    if (canNavigateNext(day, today)) day.plusDays(1) else today

internal fun formatTrackDate(day: LocalDate, today: LocalDate, locale: Locale = Locale.getDefault()): String =
    day.format(DateTimeFormatter.ofPattern(if (day.year == today.year) "EEEE, MMMM d" else "EEEE, MMMM d, yyyy", locale))

internal fun trackDayTitle(day: LocalDate, today: LocalDate, locale: Locale = Locale.getDefault()): String =
    if (day == today) "Today" else day.format(DateTimeFormatter.ofPattern("EEEE", locale))

internal fun formatWorkoutDate(day: LocalDate, today: LocalDate, locale: Locale = Locale.getDefault()): String {
    val date = day.format(DateTimeFormatter.ofPattern(if (day.year == today.year) "MMM d" else "MMM d, yyyy", locale))
    return if (day == today) "Today, $date" else date
}
