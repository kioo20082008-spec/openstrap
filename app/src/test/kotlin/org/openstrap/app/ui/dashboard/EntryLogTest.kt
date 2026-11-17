package org.openstrap.app.ui.dashboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Each screen's entrance (rings filling, numbers counting up) plays once a session. */
class EntryLogTest {
    @Test fun `a screen plays until its entrance has begun`() {
        val log = EntryLog(window = 1_000)
        assertTrue(log.plays("today", 0))
        assertTrue(log.plays("today", 500))
    }

    @Test fun `pieces arriving with the entrance join it, later ones don't`() {
        val log = EntryLog(window = 1_000)
        log.begin("today", 100)
        assertTrue(log.plays("today", 1_099))
        assertFalse(log.plays("today", 1_100))
        assertFalse(log.plays("today", 90_000))
    }

    @Test fun `the first mark stands and screens are separate`() {
        val log = EntryLog(window = 1_000)
        log.begin("today", 100)
        log.begin("today", 5_000)
        assertFalse(log.plays("today", 5_000))
        assertTrue(log.plays("sleep", 5_000))
    }

    @Test fun `a piece with no screen always plays and marks nothing`() {
        val log = EntryLog(window = 1_000)
        log.begin(null, 0)
        assertTrue(log.plays(null, 50_000))
    }
}
