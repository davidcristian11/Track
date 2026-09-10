package com.example.track

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BarcodeStabilityTrackerTest {
    private val codeA = "12345670"
    private val codeB = "76543210"

    @Test fun sameCodeMustRemainStableBeforeOneShotAcceptance() {
        val tracker = BarcodeStabilityTracker()
        assertNull(tracker.observe(codeA, 0))
        assertNull(tracker.observe(codeA, 300))
        assertEquals(codeA, tracker.observe(codeA, 700))
        assertNull(tracker.observe(codeA, 1_400))
    }

    @Test fun differentCodeRestartsTheCandidateTimer() {
        val tracker = BarcodeStabilityTracker()
        assertNull(tracker.observe(codeA, 0))
        assertNull(tracker.observe(codeA, 500))
        assertNull(tracker.observe(codeB, 600))
        assertNull(tracker.observe(codeB, 1_200))
        assertEquals(codeB, tracker.observe(codeB, 1_300))
    }

    @Test fun aSustainedDisappearanceClearsTheCandidate() {
        val tracker = BarcodeStabilityTracker()
        assertNull(tracker.observe(codeA, 0))
        assertNull(tracker.observe(codeA, 500))
        assertNull(tracker.observe(null, 550))
        assertNull(tracker.observe(null, 800))
        assertNull(tracker.observe(codeA, 900))
        assertEquals(codeA, tracker.observe(codeA, 1_600))
    }

    @Test fun normalizationTrimsButRejectsNonRetailValues() {
        assertEquals(codeA, normalizeRetailBarcode("  $codeA "))
        assertNull(normalizeRetailBarcode("1234"))
        assertNull(normalizeRetailBarcode("1234-5670"))
    }
}
