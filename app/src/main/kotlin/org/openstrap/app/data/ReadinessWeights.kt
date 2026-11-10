package org.openstrap.app.data

import org.openstrap.app.data.Recovery.Factor

/**
 * Personal multipliers on Readiness' contributor weights ([Factor.weight]), found from the morning check-ins by
 * [ReadinessCalibration]. A factor missing from [scale] counts 1, so [Default] reproduces today's score exactly. Each
 * multiplier is kept within [MIN]..[MAX] of the default.
 */
class ReadinessWeights private constructor(private val scale: Map<Factor, Float>) {
    /** [factor]'s weight: its default weight times its multiplier. */
    fun of(factor: Factor): Float = factor.weight * (scale[factor] ?: 1f)

    /** The multiplier on [factor] (1 at the default). */
    fun multiplier(factor: Factor): Float = scale[factor] ?: 1f

    val isDefault: Boolean get() = scale.isEmpty()

    override fun equals(other: Any?) = other is ReadinessWeights && other.scale == scale
    override fun hashCode() = scale.hashCode()
    override fun toString() = "ReadinessWeights($scale)"

    companion object {
        const val MIN = 0.5f
        const val MAX = 1.5f
        val Default = ReadinessWeights(emptyMap())

        /** Weights from [multipliers], clamped to [MIN]..[MAX]; a multiplier of 1 is the same as none. */
        fun of(multipliers: Map<Factor, Float>): ReadinessWeights =
            ReadinessWeights(multipliers.mapValues { it.value.coerceIn(MIN, MAX) }.filterValues { it != 1f })
    }
}
