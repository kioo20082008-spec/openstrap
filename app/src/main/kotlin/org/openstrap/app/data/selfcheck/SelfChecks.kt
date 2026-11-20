package org.openstrap.app.data.selfcheck

/**
 * Every check the self-check runs, one small class each. Adding one is adding a class and a line here, and its words in the
 * presentation copy: [inspectAll] never lets one check's failure stop the others, and the ids, which are stored, must stay
 * unique and never change.
 */
object SelfChecks {
    val all: List<Check> = listOf(
        EnergyStuckEmpty(), EnergyStuckFull(), EnergyLowWake(), EnergyDrainFast(), EnergyAlertStreak(),
        SittingAsEffort(), PeakOverMax(), StepsWithoutResponse(), StepsMostlyFalse(),
        SleepImplausible(), SleepScoreGap(), SleepMidpointJump(),
        ReadinessJump(), ReadinessStuck(),
        CaloriesImplausible(), StrapGaps(), StrapBatteryLow(),
    )

    fun byId(id: String): Check? = all.firstOrNull { it.id == id }

    /** Every finding of [checks] in [facts], oldest day first. A check that throws is skipped. */
    fun inspectAll(facts: Facts, checks: List<Check> = all): List<Finding> =
        checks.flatMap { c -> try { c.inspect(facts) } catch (e: RuntimeException) { emptyList() } }.sortedBy { it.day }
}
