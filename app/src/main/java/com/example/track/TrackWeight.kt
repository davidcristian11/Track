package com.example.track

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

// Inclusive windows ending today. Calendar periods exclude the matching start date.
enum class ProgressRange(val label: String) {
    SevenDays("7D"), ThirtyDays("30D"), ThreeMonths("3M"), SixMonths("6M"), OneYear("1Y");

    fun startDay(today: LocalDate): LocalDate = when (this) {
        SevenDays -> today.minusDays(6)
        ThirtyDays -> today.minusDays(29)
        ThreeMonths -> today.minusMonths(3).plusDays(1)
        SixMonths -> today.minusMonths(6).plusDays(1)
        OneYear -> today.minusYears(1).plusDays(1)
    }
}

data class WeightEntry(val day: LocalDate, val weightKg: Double)

data class WeightHistoryState(
    val entries: List<WeightEntry> = emptyList(),
    val loading: Boolean = true,
    val error: Boolean = false,
)

internal fun isValidWeight(weightKg: Double): Boolean = weightKg.isFinite() && weightKg in 20.0..400.0

internal fun parseWeight(input: String): Double? {
    val normalized = input.trim().replace(',', '.')
    if (!Regex("[0-9]+(?:\\.[0-9]*)?").matches(normalized)) return null
    return normalized.toDoubleOrNull()?.takeIf(::isValidWeight)
}

internal fun formatWeight(weightKg: Double): String {
    val rounded = BigDecimal.valueOf(weightKg).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros()
    return rounded.setScale(max(1, rounded.scale())).toPlainString()
}

internal fun formatWeightChange(change: Double?): String {
    if (change == null) return "—"
    val magnitude = formatWeight(abs(change))
    val sign = when {
        magnitude == "0.0" -> ""
        change < 0 -> "−"
        else -> "+"
    }
    return "$sign$magnitude kg"
}

internal fun List<WeightEntry>.inRange(range: ProgressRange, today: LocalDate): List<WeightEntry> =
    filter { it.day >= range.startDay(today) && it.day <= today }.sortedBy { it.day }

internal fun List<WeightEntry>.latestWeight(today: LocalDate): WeightEntry? =
    filter { it.day <= today }.maxByOrNull { it.day }

internal fun List<WeightEntry>.weightForSelectedDay(day: LocalDate, today: LocalDate): WeightEntry? =
    if (day == today) latestWeight(today) else firstOrNull { it.day == day }

internal fun weightChange(entries: List<WeightEntry>): Double? {
    if (entries.size < 2) return null
    return entries.maxBy { it.day }.weightKg - entries.minBy { it.day }.weightKg
}

internal fun weightDateLabel(day: LocalDate): String =
    day.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))

// Fractions keep calendar spacing and chart scaling testable without Canvas.
internal data class WeightChartPoint(val x: Float, val y: Float)
internal data class WeightChartScale(val low: Double, val high: Double, val points: List<WeightChartPoint>)

internal fun weightChartScale(entries: List<WeightEntry>, start: LocalDate, end: LocalDate): WeightChartScale {
    if (entries.isEmpty()) return WeightChartScale(0.0, 0.0, emptyList())
    val low = entries.minOf { it.weightKg }
    val high = entries.maxOf { it.weightKg }
    val padding = max(1.0, (high - low) * 0.2)
    val span = (end.toEpochDay() - start.toEpochDay()).coerceAtLeast(1).toDouble()
    return WeightChartScale(low - padding, high + padding, entries.map {
        WeightChartPoint(
            ((it.day.toEpochDay() - start.toEpochDay()) / span).toFloat(),
            ((high + padding - it.weightKg) / (high - low + padding * 2)).toFloat(),
        )
    })
}
