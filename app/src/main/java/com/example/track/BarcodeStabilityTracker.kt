package com.example.track

internal const val BarcodeStableDurationMillis = 700L
internal const val BarcodeAbsenceGraceMillis = 250L

internal fun normalizeRetailBarcode(rawCode: String?): String? =
    rawCode?.trim()?.takeIf(::isRetailBarcode)

/** Deterministic one-shot gate driven by monotonic timestamps from the camera analyzer. */
internal class BarcodeStabilityTracker(
    private val stableDurationMillis: Long = BarcodeStableDurationMillis,
    private val absenceGraceMillis: Long = BarcodeAbsenceGraceMillis,
) {
    private var candidate: String? = null
    private var firstSeenAt = 0L
    private var missingSince: Long? = null
    private var accepted = false

    init {
        require(stableDurationMillis >= 0L)
        require(absenceGraceMillis >= 0L)
    }

    fun observe(rawCode: String?, nowMillis: Long): String? {
        if (accepted) return null
        val code = normalizeRetailBarcode(rawCode)
        if (code == null) {
            if (candidate != null) {
                val missingAt = missingSince ?: nowMillis.also { missingSince = it }
                if (nowMillis - missingAt >= absenceGraceMillis) clearCandidate()
            }
            return null
        }

        missingSince = null
        if (candidate != code) {
            candidate = code
            firstSeenAt = nowMillis
        }
        return if (nowMillis - firstSeenAt >= stableDurationMillis) {
            accepted = true
            code
        } else {
            null
        }
    }

    private fun clearCandidate() {
        candidate = null
        firstSeenAt = 0L
        missingSince = null
    }
}
