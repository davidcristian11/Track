package com.example.track

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ProgressPhotoTest {
    @Test fun generatedNamesAreUniqueAndCannotTraverseDirectories() {
        val names = (1..1000).map { newPhotoFileName() }
        assertEquals(1000, names.toSet().size)
        assertTrue(names.all(::isSafePhotoFileName))
        listOf("../photo.jpg", "/photo.jpg", "photo_a.jpg", "", "pending/photo.jpg", "photo_../../secret.jpg")
            .forEach { assertFalse(isSafePhotoFileName(it)) }
    }

    @Test fun photoDatesAllowTodayAndPastButRejectFuture() {
        val today = LocalDate.of(2026, 9, 9)
        assertTrue(isValidPhotoDay(today, today))
        assertTrue(isValidPhotoDay(today.minusYears(10), today))
        assertFalse(isValidPhotoDay(today.plusDays(1), today))
    }

    @Test fun persistedSourcesParseWithSafeFallback() {
        assertEquals(ProgressPhotoSource.CAMERA, ProgressPhotoSource.parse("CAMERA"))
        assertEquals(ProgressPhotoSource.GALLERY, ProgressPhotoSource.parse("GALLERY"))
        assertEquals(ProgressPhotoSource.GALLERY, ProgressPhotoSource.parse("unknown"))
    }
}
