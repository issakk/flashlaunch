package com.flashlaunch.data

import kotlin.math.pow

/** Launch history of one app: how often it was opened, and when it was last opened. */
data class Usage(
    val count: Int,
    val lastUsedAt: Long = 0L,
) {

    /**
     * Frequency weighted by recency: every [HALF_LIFE_DAYS] without a launch
     * halves the weight, so an app opened every day outranks one that was
     * hammered last month and forgotten. Records written before recency was
     * tracked keep a small fixed weight so they do not fall to the bottom.
     */
    fun score(now: Long): Double {
        if (count <= 0) return 0.0
        if (lastUsedAt <= 0L) return count * LEGACY_WEIGHT
        val age = (now - lastUsedAt).coerceAtLeast(0L)
        return count * 0.5.pow(age.toDouble() / HALF_LIFE_MS)
    }

    private companion object {
        const val HALF_LIFE_DAYS = 7L
        const val HALF_LIFE_MS = HALF_LIFE_DAYS * 24 * 60 * 60 * 1000
        const val LEGACY_WEIGHT = 0.25
    }
}
