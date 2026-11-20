package org.openstrap.app.data

/**
 * Personal multipliers on Energy's constants, each 1 (or its own neutral value) at the default, which reproduces the
 * published constants exactly. Two sets of them, found by two different things:
 *
 * From the morning check-ins ([EnergyCalibration]), each kept within [MIN]..[MAX] of the published constant:
 * - [sleep] scales the sleep recharge rate ([Recovery.SLEEP_K]),
 * - [exercise] scales the part of the exercise drain that follows heart-rate reserve ([Recovery.EXERCISE_DRAIN_PER_HRR]),
 * - [carry] scales how much a hard day cuts the next night's recharge ([Recovery.CARRY_MAX]).
 *
 * From the app's own plausibility checks (docs/algorithms/self-check.md), each bounded by its `Knob` and decaying back to
 * the default within two weeks unless the evidence repeats:
 * - [alert] is how much of the body-signal Alert's effect on Energy is kept ([Recovery.ALERT_RECHARGE], [Recovery.ALERT_DRAIN]):
 *   1 the published effect, 0 none,
 * - [active] scales the drain of Activity and Exercise minutes, on top of [exercise],
 * - [rest] scales the drain of a still, awake Rest minute ([Recovery.REST_DRAIN]),
 * - [sustainedCap] is the longest run of raised heart rate without steps counted as exercise, in minutes
 *   ([Recovery.SUSTAINED_EFFORT_MAX_MINUTES] at the default),
 * - [restingOffset] is added to the daytime resting heart rate Energy measures reserve against, in bpm.
 */
data class ChargeTuning(
    val sleep: Float = 1f,
    val exercise: Float = 1f,
    val carry: Float = 1f,
    val alert: Float = 1f,
    val active: Float = 1f,
    val rest: Float = 1f,
    val sustainedCap: Int = Recovery.SUSTAINED_EFFORT_MAX_MINUTES,
    val restingOffset: Float = 0f,
) {
    val isDefault: Boolean get() = this == Default

    /**
     * The check-in multipliers forced into [MIN]..[MAX] and the self-check ones into their bounds, for values read back
     * from storage.
     */
    fun clamped() = ChargeTuning(
        sleep.coerceIn(MIN, MAX), exercise.coerceIn(MIN, MAX), carry.coerceIn(MIN, MAX),
        alert.coerceIn(ALERT_MIN, ALERT_MAX), active.coerceIn(ACTIVE_MIN, ACTIVE_MAX), rest.coerceIn(REST_MIN, REST_MAX),
        sustainedCap.coerceIn(CAP_MIN, CAP_MAX), restingOffset.coerceIn(OFFSET_MIN, OFFSET_MAX),
    )

    /** This tuning's check-in part with [learned]'s self-check part: the two come from different places and never mix. */
    fun withLearned(learned: ChargeTuning) = copy(
        alert = learned.alert, active = learned.active, rest = learned.rest, sustainedCap = learned.sustainedCap,
        restingOffset = learned.restingOffset,
    )

    /** Only the self-check part of this tuning: the check-in multipliers are the defaults. */
    fun learnedPart() = Default.withLearned(this)

    companion object {
        const val MIN = 0.7f
        const val MAX = 1.3f
        const val ALERT_MIN = 0f
        const val ALERT_MAX = 1f
        const val ACTIVE_MIN = 0.6f
        const val ACTIVE_MAX = 1.4f
        const val REST_MIN = 0.5f
        const val REST_MAX = 1.5f
        const val CAP_MIN = 30
        const val CAP_MAX = Recovery.SUSTAINED_EFFORT_MAX_MINUTES
        const val OFFSET_MIN = 0f
        const val OFFSET_MAX = 8f
        val Default = ChargeTuning()
    }
}
