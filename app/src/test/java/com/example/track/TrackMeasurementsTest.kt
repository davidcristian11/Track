package com.example.track

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class TrackMeasurementsTest {
    private val today = LocalDate.of(2026, 9, 9)

    @Test fun parsingAcceptsDecimalSeparatorsAndRejectsInvalidNonblankValues() {
        mapOf("82" to 82.0, "82.5" to 82.5, "82,5" to 82.5, "82.25" to 82.25,
            " 10 " to 10.0, "300" to 300.0).forEach { (input, expected) ->
            assertEquals(expected, parseMeasurement(input)!!, 0.0)
        }
        listOf("", " ", "-82", "0", "9.99", "300.01", "NaN", "Infinity", "abc", "82..5", "82,2.5", "1e2").forEach {
            assertNull(it, parseMeasurement(it))
        }
        listOf(-1.0, 0.0, 9.99, 300.01, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach {
            assertFalse(isValidMeasurement(it))
        }
        assertTrue(BodyMeasurements().isEmpty)
        assertTrue(BodyMeasurements().isValid)
        assertFalse(BodyMeasurements(waistCm = Double.NaN).isValid)
    }

    @Test fun formattingRetainsUsefulPrecisionAndNeutralChanges() {
        assertEquals("82.0", formatMeasurement(82.0))
        assertEquals("82.5", formatMeasurement(82.5))
        assertEquals("82.25", formatMeasurement(82.25))
        assertEquals("−1.5 cm", formatMeasurementChange(-1.5))
        assertEquals("+0.5 cm", formatMeasurementChange(0.5))
        assertEquals("0.0 cm", formatMeasurementChange(0.0))
        assertEquals("—", formatMeasurementChange(null))
    }

    @Test fun latestIsIndependentPerMetricAndFallsBackAfterClearingOrDeleting() {
        val first = BodyMeasurement(today.minusDays(8), BodyMeasurements(82.0, 100.0, thighCm = 55.0))
        val second = BodyMeasurement(today.minusDays(4), BodyMeasurements(81.0))
        val third = BodyMeasurement(today.minusDays(1), BodyMeasurements(armCm = 36.0))
        val future = BodyMeasurement(today.plusDays(1), BodyMeasurements(90.0))
        assertEquals(BodyMeasurements(81.0, 100.0, armCm = 36.0, thighCm = 55.0),
            latestMeasurements(listOf(third, future, first, second), today))
        assertEquals(82.0, latestMeasurements(listOf(first, third), today).waistCm!!, 0.0)
        assertNull(latestMeasurements(emptyList(), today).thighCm)
    }

    @Test fun everyMetricChangeUsesOnlyActualValuesInsideInclusiveRange() {
        MeasurementMetric.entries.forEach { metric ->
            fun entry(day: LocalDate, value: Double): BodyMeasurement = BodyMeasurement(day, when (metric) {
                MeasurementMetric.Waist -> BodyMeasurements(waistCm = value)
                MeasurementMetric.Chest -> BodyMeasurements(chestCm = value)
                MeasurementMetric.Hips -> BodyMeasurements(hipsCm = value)
                MeasurementMetric.Arm -> BodyMeasurements(armCm = value)
                MeasurementMetric.Thigh -> BodyMeasurements(thighCm = value)
            })
            ProgressRange.entries.forEach { range ->
                val start = range.startDay(today)
                val outside = listOf(entry(start.minusDays(1), 150.0), entry(today.plusDays(1), 200.0))
                assertNull(measurementChange(outside, metric, range, today))
                assertNull(measurementChange(outside + entry(start, 82.0), metric, range, today))
                val entries = outside + entry(today, 80.5) + entry(start, 82.0) + BodyMeasurement(today.minusDays(1), BodyMeasurements())
                assertEquals(-1.5, measurementChange(entries, metric, range, today)!!, 0.0)
                assertEquals(listOf(start, today.minusDays(1), today), measurementsInRange(entries, range, today).map { it.day })
            }
        }
    }
}
