package org.openstrap.app.data.settings

/** When the strap was last charged ([atSeconds], epoch seconds) and its battery then ([percent], when known). */
data class LastCharge(val atSeconds: Long, val percent: Int?)

/**
 * What counts as the strap having been charged, from the battery readings the app sees. A strap off the wrist on its
 * charger is often out of reach, so a reading that is higher than the last by [JUMP] points or more also counts:
 * a battery only goes up by being charged.
 */
object ChargeLog {
    /** The rise, in battery points between two readings, that can only be a charge. */
    const val JUMP = 8

    /** Whether the reading [percent] is a charge: [charging] now, or [JUMP] or more above the [previous] reading. */
    fun charged(previous: Int?, percent: Int, charging: Boolean): Boolean =
        charging || (previous != null && percent - previous >= JUMP)
}
