package com.example.track

import java.time.LocalDate
import java.util.UUID

enum class ProgressPhotoSource {
    CAMERA, GALLERY;

    companion object {
        fun parse(value: String): ProgressPhotoSource = entries.firstOrNull { it.name == value } ?: GALLERY
    }
}

data class ProgressPhoto(
    val id: Long,
    val day: LocalDate,
    val localFileName: String,
    val source: ProgressPhotoSource,
    val createdAt: Long,
)

data class ProgressPhotoHistory(
    val photos: List<ProgressPhoto> = emptyList(),
    val loading: Boolean = true,
    val error: Boolean = false,
)

data class ProgressPhotoDraft(val fileName: String, val source: ProgressPhotoSource)
data class ProgressPhotoEditState(
    val draft: ProgressPhotoDraft? = null,
    val busy: Boolean = false,
    val error: String? = null,
)

internal fun newPhotoFileName(): String = "photo_${UUID.randomUUID()}.jpg"
internal fun isSafePhotoFileName(name: String): Boolean =
    Regex("photo_[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.jpg").matches(name)
internal fun isValidPhotoDay(day: LocalDate, today: LocalDate): Boolean = day <= today
