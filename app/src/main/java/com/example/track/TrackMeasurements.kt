package com.example.track

import java.time.LocalDate
import kotlin.math.abs

data class BodyMeasurements(
    val waistCm: Double? = null,
    val chestCm: Double? = null,
    val hipsCm: Double? = null,
    val armCm: Double? = null,
    val thighCm: Double? = null,
) {
    val isEmpty: Boolean get() = MeasurementMetric.entries.all { it.value(this) == null }
    val isValid: Boolean get() = MeasurementMetric.entries.all { metric ->
        metric.value(this)?.let(::isValidMeasurement) != false
    }
}

enum class MeasurementMetric(val label: String) {
    Waist("Waist"), Chest("Chest"), Hips("Hips"), Arm("Arm"), Thigh("Thigh");

    fun value(values: BodyMeasurements): Double? = when (this) {
        Waist -> values.waistCm
        Chest -> values.chestCm
        Hips -> values.hipsCm
        Arm -> values.armCm
        Thigh -> values.thighCm
    }
}

data class BodyMeasurement(val day: LocalDate, val values: BodyMeasurements)
data class MeasurementHistoryState(
    val entries: List<BodyMeasurement> = emptyList(),
    val loading: Boolean = true,
    val error: Boolean = false,
)

enum class MeasurementSaveResult { Saved, DateOccupied, MissingEntry, Invalid, Failed }

internal fun isValidMeasurement(value: Double): Boolean = value.isFinite() && value in 10.0..300.0

// Blank is missing; callers distinguish it from a nonblank invalid input.
internal fun parseMeasurement(input: String): Double? {
    val normalized = input.trim().replace(',', '.')
    if (!Regex("[0-9]+(?:\\.[0-9]*)?").matches(normalized)) return null
    return normalized.toDoubleOrNull()?.takeIf(::isValidMeasurement)
}

internal fun formatMeasurement(value: Double): String = formatWeight(value)
internal fun formatMeasurementChange(change: Double?): String {
    if (change == null) return "—"
    val magnitude = formatMeasurement(abs(change))
    val sign = when { magnitude == "0.0" -> ""; change < 0 -> "−"; else -> "+" }
    return "$sign$magnitude cm"
}

internal fun measurementsInRange(entries: List<BodyMeasurement>, range: ProgressRange, today: LocalDate) =
    entries.filter { it.day >= range.startDay(today) && it.day <= today }.sortedBy { it.day }

internal fun latestMeasurements(entries: List<BodyMeasurement>, today: LocalDate): BodyMeasurements {
    val ordered = entries.filter { it.day <= today }.sortedByDescending { it.day }
    fun latest(metric: MeasurementMetric) = ordered.firstNotNullOfOrNull { metric.value(it.values) }
    return BodyMeasurements(latest(MeasurementMetric.Waist), latest(MeasurementMetric.Chest),
        latest(MeasurementMetric.Hips), latest(MeasurementMetric.Arm), latest(MeasurementMetric.Thigh))
}

internal fun measurementChange(
    entries: List<BodyMeasurement>, metric: MeasurementMetric, range: ProgressRange, today: LocalDate,
): Double? {
    val values = measurementsInRange(entries, range, today).mapNotNull { metric.value(it.values) }
    return if (values.size < 2) null else values.last() - values.first()
}

internal fun measurementSummary(values: BodyMeasurements): String = MeasurementMetric.entries.mapNotNull {
    metric -> metric.value(values)?.let { "${metric.label} ${formatMeasurement(it)}" }
}.joinToString(" · ")
