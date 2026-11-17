package org.openstrap.app.ui.components

import androidx.compose.ui.graphics.TransformOrigin
import org.junit.Assert.assertEquals
import org.junit.Test

/** A detail grows from where it was pressed and shrinks back into it. */
class OpenOriginTest {
    @Test fun `a press is a share of the stack, kept on it`() {
        assertEquals(TransformOrigin(0.25f, 0.5f), OpenOriginMath.origin(100f, 400f, 400, 800))
        assertEquals(TransformOrigin(1f, 0f), OpenOriginMath.origin(900f, -20f, 400, 800))
        assertEquals(TransformOrigin.Center, OpenOriginMath.origin(10f, 10f, 0, 800))
    }

    @Test fun `going deeper uses the last press and back returns to the one that opened the page`() {
        val origin = OpenOrigin()
        origin.press(100f, 200f, 400, 800)
        val opened = origin.forChange(0, 1)
        assertEquals(TransformOrigin(0.25f, 0.25f), opened)
        // Pressing inside the detail (a button, a scrub) doesn't move where back goes.
        origin.press(390f, 780f, 400, 800)
        assertEquals(opened, origin.forChange(1, 0))
    }

    @Test fun `a page never opened by a press goes back to the last one`() {
        val origin = OpenOrigin()
        origin.press(200f, 400f, 400, 800)
        assertEquals(TransformOrigin(0.5f, 0.5f), origin.forChange(2, 1))
    }
}
