package org.openstrap.app.ui.dashboard

/**
 * Which screens have already played their entrance (rings filling from empty, numbers counting up) in this run of the
 * app, so each plays once a session: a screen coming back (the pager rebuilding a tab, a detail closing over it) shows
 * its figures as they are. A screen's pieces that arrive together, as one pass of the first composition or a moment
 * after it, still play; [window] is how long after the first one starts that is.
 *
 * Plain state on the main thread; [now] is the clock in milliseconds, given so tests can run it.
 */
class EntryLog(private val window: Long = WINDOW_MS) {
    private val started = HashMap<String, Long>()

    /** Whether a piece of [screen] shown at [now] plays its entrance: the screen's first, or one arriving with it. A null screen always plays. */
    fun plays(screen: String?, now: Long): Boolean {
        if (screen == null) return true
        val at = started[screen] ?: return true
        return now - at < window
    }

    /** Marks [screen]'s entrance as begun at [now]; the first mark stands. */
    fun begin(screen: String?, now: Long) {
        if (screen != null) started.getOrPut(screen) { now }
    }

    companion object {
        /** How long after a screen's entrance begins that a piece shown later still joins it. */
        const val WINDOW_MS = 3_500L

        /** The app's, one for the process. */
        val session = EntryLog()
    }
}
