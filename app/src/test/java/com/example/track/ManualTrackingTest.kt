package com.example.track

import org.junit.Assert.*
import org.junit.Test

class ManualTrackingTest {
    @Test fun unsetIsDistinctFromExplicitZero() {
        assertTrue(isValidSteps(null))
        assertTrue(isValidSleepMinutes(null))
        assertNull(TrackSessionData(TrackDemoBaseline.referenceDay).steps)
        assertNull(TrackSessionData(TrackDemoBaseline.referenceDay).sleepMinutes)
        assertEquals(0, parseSteps("0"))
        assertNull(parseSteps(""))
    }
    @Test fun stepsAcceptBoundariesAndTypicalCounts() {
        listOf(0, 5000, 10000, 10250, 25000, 200000).forEach { assertTrue(isValidSteps(it)) }
        listOf(-1, 200001, Int.MAX_VALUE, Int.MIN_VALUE).forEach { assertFalse(isValidSteps(it)) }
    }
    @Test fun stepsRejectMalformedOrOverflowingInputWithoutClamping() {
        listOf("-1", "200001", "999999999999", "8.5", "8,432", "abc").forEach { assertNull(parseSteps(it)) }
        assertEquals(8432, parseSteps(" 8432 "))
    }
    @Test fun sleepConvertsHumanDurationAndAcceptsBoundaries() {
        assertEquals(0, sleepMinutesFromParts(0, 0))
        assertEquals(30, sleepMinutesFromParts(0, 30))
        assertEquals(450, sleepMinutesFromParts(7, 30))
        assertEquals(480, sleepMinutesFromParts(8, 0))
        assertEquals(1439, sleepMinutesFromParts(23, 59))
        assertEquals(1440, sleepMinutesFromParts(24, 0))
        listOf(0, 450, 1440).forEach { assertTrue(isValidSleepMinutes(it)) }
    }
    @Test fun sleepRejectsInvalidPartsAndTotals() {
        listOf(0 to 60, -1 to 0, 1 to -1, 25 to 0, 24 to 1, Int.MAX_VALUE to 0).forEach {
            assertNull(sleepMinutesFromParts(it.first, it.second))
        }
        listOf(-1, 1441, Int.MAX_VALUE).forEach { assertFalse(isValidSleepMinutes(it)) }
    }
    @Test fun sleepFormatsDeterministically() {
        mapOf(0 to "0h", 30 to "0h 30m", 60 to "1h", 450 to "7h 30m", 480 to "8h", 1440 to "24h").forEach {
            assertEquals(it.value, formatSleep(it.key))
        }
    }
}
