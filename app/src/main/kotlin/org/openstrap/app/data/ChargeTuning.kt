package org.openstrap.app.data

/**
 * Personal multipliers on three Energy constants, found from the morning check-ins by [EnergyCalibration]. Every one is
 * 1 at the default, which reproduces the published constants exactly, and is kept within [MIN]..[MAX] of it:
 * - [sleep] scales the sleep recharge rate ([Recovery.SLEEP_K]),
 * - [exercise] scales the part of the exercise drain that follows heart-rate reserve ([Recovery.EXERCISE_DRAIN_PER_HRR]),
 * - [carry] scales how much a hard day cuts the next night's recharge ([Recovery.CARRY_MAX]).
 */
data class ChargeTuning(val sleep: Float = 1f, val exercise: Float = 1f, val carry: Float = 1f) {
    val isDefault: Boolean get() = this == Default

    /** The same multipliers forced into [MIN]..[MAX], for values read back from storage. */
    fun clamped() = ChargeTuning(sleep.coerceIn(MIN, MAX), exercise.coerceIn(MIN, MAX), carry.coerceIn(MIN, MAX))

    companion object {
        const val MIN = 0.7f
        const val MAX = 1.3f
        val Default = ChargeTuning()
    }
}
