package com.example.track

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class TrackWeightTest {
    private val today = LocalDate.of(2026, 9, 7)
    private fun entry(daysAgo: Long, kg: Double) = WeightEntry(today.minusDays(daysAgo), kg)

    @Test fun existingRangeLabelsStayIntact() {
        assertEquals(listOf("7D", "30D", "3M", "6M", "1Y"), ProgressRange.entries.map { it.label })
    }

    @Test fun dayRangesIncludeTodayAndPreviousCalendarDays() {
        assertEquals(LocalDate.of(2026, 9, 1), ProgressRange.SevenDays.startDay(today))
        assertEquals(LocalDate.of(2026, 8, 9), ProgressRange.ThirtyDays.startDay(today))
    }

    @Test fun calendarRangesUseMonthsAndYears() {
        assertEquals(LocalDate.of(2026, 6, 8), ProgressRange.ThreeMonths.startDay(today))
        assertEquals(LocalDate.of(2026, 3, 8), ProgressRange.SixMonths.startDay(today))
        assertEquals(LocalDate.of(2025, 9, 8), ProgressRange.OneYear.startDay(today))
    }

    @Test fun rangesHandleLeapYearsMonthEndsAndYearBoundaries() {
        assertEquals(LocalDate.of(2024, 2, 29), ProgressRange.SevenDays.startDay(LocalDate.of(2024, 3, 6)))
        assertEquals(LocalDate.of(2024, 3, 1), ProgressRange.ThreeMonths.startDay(LocalDate.of(2024, 5, 31)))
        assertEquals(LocalDate.of(2023, 3, 1), ProgressRange.OneYear.startDay(LocalDate.of(2024, 2, 29)))
        assertEquals(LocalDate.of(2025, 12, 4), ProgressRange.ThirtyDays.startDay(LocalDate.of(2026, 1, 2)))
    }

    @Test fun everyRangeIncludesBoundariesExcludesOutsideAndSortsActualEntries() {
        ProgressRange.entries.forEach { range ->
            val start = range.startDay(today)
            val entries = listOf(WeightEntry(today, 74.2), WeightEntry(start.minusDays(1), 76.0),
                WeightEntry(start, 75.0), WeightEntry(today.plusDays(1), 74.0))
            assertEquals(listOf(start, today), entries.inRange(range, today).map { it.day })
        }
    }

    @Test fun emptyStateHasNoLatestChangeOrChartPoints() {
        val entries = emptyList<WeightEntry>()
        assertNull(entries.latestWeight(today))
        assertNull(weightChange(entries))
        assertTrue(entries.inRange(ProgressRange.SevenDays, today).isEmpty())
        assertTrue(weightChartScale(entries, today.minusDays(6), today).points.isEmpty())
        assertEquals("—", formatWeightChange(null))
    }

    @Test fun onePointHasCurrentWeightButNoTrendAndFinitePosition() {
        val entries = listOf(entry(0, 75.0))
        assertEquals(entries.single(), entries.latestWeight(today))
        assertNull(weightChange(entries))
        val point = weightChartScale(entries, today.minusDays(6), today).points.single()
        assertEquals(1f, point.x, 0f)
        assertEquals(0.5f, point.y, 0f)
    }

    @Test fun latestWeightCanPrecedeTodayAndRangeButNeverBeFuture() {
        val old = entry(40, 74.3)
        val entries = listOf(old, entry(-1, 75.0))
        assertEquals(old, entries.latestWeight(today))
        assertTrue(entries.inRange(ProgressRange.ThirtyDays, today).isEmpty())
    }

    @Test fun selectedPastDayNeverBorrowsAnotherDaysWeightIncludingReferenceDay() {
        val entries = listOf(entry(0, 74.3), entry(2, 74.5))
        assertNull(entries.weightForSelectedDay(today.minusDays(1), today))
        assertNull(entries.weightForSelectedDay(TrackDemoBaseline.referenceDay, today))
        assertEquals(entry(2, 74.5), entries.weightForSelectedDay(today.minusDays(2), today))
        assertEquals(entry(2, 74.5), listOf(entry(2, 74.5)).weightForSelectedDay(today, today))
    }

    @Test fun changeUsesOnlyFirstAndLastInSelectedRange() {
        val entries = listOf(entry(0, 74.2), entry(20, 80.0), entry(5, 75.0), entry(2, 73.5))
        assertEquals(-0.8, weightChange(entries.inRange(ProgressRange.SevenDays, today))!!, 0.00001)
        assertEquals(-5.8, weightChange(entries.inRange(ProgressRange.ThirtyDays, today))!!, 0.00001)
    }

    @Test fun formattingShowsPrecisionAndExplicitTrendSignWithoutNegativeZero() {
        assertEquals("75.0", formatWeight(75.0))
        assertEquals("73.85", formatWeight(73.85))
        assertEquals("73.86", formatWeight(73.855))
        assertEquals("−0.8 kg", formatWeightChange(74.2 - 75.0))
        assertEquals("+0.2 kg", formatWeightChange(0.2))
        assertEquals("0.0 kg", formatWeightChange(-0.000001))
    }

    @Test fun inputAcceptsDecimalDotOrCommaAndBroadInclusiveLimits() {
        assertEquals(73.85, parseWeight(" 73,85 ")!!, 0.0)
        assertEquals(74.3, parseWeight("74.3")!!, 0.0)
        assertEquals(20.0, parseWeight("20")!!, 0.0)
        assertEquals(400.0, parseWeight("400")!!, 0.0)
    }

    @Test fun invalidInputIsRejectedWithoutClamping() {
        listOf("", " ", "19.99", "400.01", "0", "-74", "NaN", "Infinity", "74..3", "74,3.2", "7e1", "abc").forEach {
            assertNull(it, parseWeight(it))
        }
        assertFalse(isValidWeight(Double.NaN))
        assertFalse(isValidWeight(Double.POSITIVE_INFINITY))
    }

    @Test fun chartKeepsCalendarGapsAndHasNoSyntheticPoints() {
        val entries = listOf(entry(6, 75.0), entry(5, 74.9), entry(0, 74.8))
        val points = weightChartScale(entries, today.minusDays(6), today).points
        assertEquals(3, points.size)
        assertEquals(0f, points[0].x, 0f)
        assertEquals(1f / 6f, points[1].x, 0.0001f)
        assertEquals(1f, points[2].x, 0f)
    }

    @Test fun chartPadsFlatAndSmallChangesWithoutStartingAtZero() {
        val flat = weightChartScale(listOf(entry(5, 75.0), entry(0, 75.0)), today.minusDays(6), today)
        assertEquals(74.0, flat.low, 0.0)
        assertEquals(76.0, flat.high, 0.0)
        assertTrue(flat.points.all { it.y == 0.5f })
        val small = weightChartScale(listOf(entry(5, 75.0), entry(0, 74.8)), today.minusDays(6), today)
        assertTrue(small.high - small.low >= 2.0)
        assertTrue(small.points.all { it.y in 0.4f..0.6f })
    }
}
