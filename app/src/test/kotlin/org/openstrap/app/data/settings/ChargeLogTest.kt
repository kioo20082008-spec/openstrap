package org.openstrap.app.data.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargeLogTest {
    @Test fun `a reading while charging is a charge`() = assertTrue(ChargeLog.charged(50, 52, charging = true))

    @Test fun `a first reading that isn't charging is not`() = assertFalse(ChargeLog.charged(null, 90, charging = false))

    @Test fun `a jump up between readings is a charge`() = assertTrue(ChargeLog.charged(35, 100, charging = false))

    @Test fun `a small rise or a fall is not`() {
        assertFalse(ChargeLog.charged(50, 53, charging = false))
        assertFalse(ChargeLog.charged(50, 49, charging = false))
    }
}
