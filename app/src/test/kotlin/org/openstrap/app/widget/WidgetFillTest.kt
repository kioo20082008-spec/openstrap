package org.openstrap.app.widget

import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import androidx.compose.ui.unit.DpSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fill look (owner's choice, 2026-11): how high the liquid stands for a value, the level steps its bitmaps are
 * cached by, when the number takes dark ink, and that the layout fits every cell down to the smallest, with the strap's
 * button and the largest font, the liquid's room never under the number.
 */
class WidgetFillTest {
    @Test fun `a value fills its share of the room, a sliver at least, never past full`() {
        assertEquals(0.59f, FillLevel.percent(59), 1e-6f)
        assertEquals(FillLevel.MIN, FillLevel.percent(0), 0f)
        assertEquals(FillLevel.MIN, FillLevel.percent(3), 0f)
        assertEquals(0.06f, FillLevel.percent(6), 1e-6f)
        assertEquals(1f, FillLevel.percent(100), 0f)
        assertEquals(1f, FillLevel.percent(140), 0f)
        assertEquals(FillLevel.MIN, FillLevel.percent(-5), 0f)
        // No number yet: no liquid at all, not the sliver.
        assertEquals(0f, FillLevel.percent(null), 0f)
        assertEquals(0f, FillLevel.of(Float.NaN, 100f), 0f)
    }

    @Test fun `steps fill as their share of the goal, full from the goal on`() {
        assertEquals(0.5f, FillLevel.steps(4_000, 8_000), 1e-6f)
        assertEquals(570f / 8_000f, FillLevel.steps(570, 8_000), 1e-6f)
        assertEquals(FillLevel.MIN, FillLevel.steps(0, 8_000), 0f)
        assertEquals(FillLevel.MIN, FillLevel.steps(240, 8_000), 0f)
        assertEquals(1f, FillLevel.steps(8_000, 8_000), 0f)
        assertEquals(1f, FillLevel.steps(12_000, 8_000), 0f)
        assertEquals(1f, FillLevel.steps(10, 0), 0f)
    }

    @Test fun `levels are drawn and cached in steps of 1 percent`() {
        assertEquals(59, FillLevel.bucket(0.594f))
        assertEquals(60, FillLevel.bucket(0.596f))
        assertEquals(100, FillLevel.bucket(2f))
        assertEquals(0, FillLevel.bucket(-1f))
        assertEquals(0.59f, FillLevel.ofBucket(59), 1e-6f)
        assertEquals(1f, FillLevel.ofBucket(140), 0f)
        // The surface in a room from 66 to 182 (the mockup's Small).
        assertEquals(124f, FillLevel.surface(66f, 182f, 0.5f), 1e-4f)
        assertEquals(66f, FillLevel.surface(66f, 182f, 1f), 0f)
        assertEquals(182f, FillLevel.surface(66f, 182f, 0f), 0f)
    }

    @Test fun `the number takes dark ink only on the lime liquid, once its middle is under the surface`() {
        // The mockup's Small: a room 116 high, the number's middle 34 up from the bottom, so lime from about 30%.
        assertTrue(FillLevel.darkInk(lightFill = true, level = 0.59f, middleFromBottom = 34f, areaHeight = 116f))
        assertTrue(FillLevel.darkInk(lightFill = true, level = 0.30f, middleFromBottom = 34f, areaHeight = 116f))
        assertFalse(FillLevel.darkInk(lightFill = true, level = 0.25f, middleFromBottom = 34f, areaHeight = 116f))
        assertFalse(FillLevel.darkInk(lightFill = false, level = 0.9f, middleFromBottom = 34f, areaHeight = 116f))
        assertFalse(FillLevel.darkInk(lightFill = true, level = 0f, middleFromBottom = 0f, areaHeight = 116f))
        assertFalse(FillLevel.onFill(1f, 34f, 0f))
        // Only Recovery's lime is light enough to need it.
        assertEquals(listOf(FillMetric.Recovery), FillMetric.entries.filter { it.lightFill })
    }

    private val zone = ZoneOffset.UTC
    private val now = LocalDate.of(2026, 11, 21).atTime(12, 0).atZone(zone).toEpochSecond()
    private val button = StatusLine.of(StrapStatus.StaleSync(now - 3 * 86_400), now, zone, Locale.UK)
    private val figures = listOf(RingLine("Resting HR", "52 bpm"), RingLine("HRV", "64 ms"), RingLine("Sleep", "82"))

    private fun frame(bucket: DpSize, w: Float, h: Float, scale: Float, status: StatusLine?) =
        WidgetFrame(bucket, w, h, 2.75f, scale, w, h, status)

    @Test fun `the layout fits every cell down to the smallest, with the strap's button and the largest font`() {
        val cells = listOf(
            WidgetSizes.Small to (110f to 110f), WidgetSizes.Small to (150f to 150f), WidgetSizes.Small to (155.4f to 158.5f),
            WidgetSizes.Wide to (280f to 160f), WidgetSizes.Wide to (353.5f to 201.1f),
            WidgetSizes.Large to (280f to 260f), WidgetSizes.Large to (353.5f to 375.8f),
        )
        for ((bucket, size) in cells) for (scale in listOf(1f, 1.3f)) for (status in listOf(null, button)) {
            val f = frame(bucket, size.first, size.second, scale, status)
            val p = fillPlan(f, "Recovery", "This morning", "Ready", figures, 60f)
            val bottom = p.fillTop + p.numeral * FillLook.CAP + FillLook.BASELINE_DP
            assertTrue("$bucket $size at $scale, ${status != null}: the number runs into the lines ($bottom > ${size.second})", bottom <= size.second + 0.5f)
            assertTrue(p.numeral in FillLook.NUMERAL_SMALL + FillLook.NUMERAL_WIDE + FillLook.NUMERAL_LARGE)
        }
    }

    @Test fun `a Small keeps its line under the title with the button and the largest font, and shows no figures`() {
        val p = fillPlan(frame(WidgetSizes.Small, 150f, 150f, 1.3f, button), "Last night", "22:58 to 06:56", "Fair · 8 h 11", figures, null)
        assertEquals(listOf(FillRow("Fair · 8 h 11", null)), p.rows)
        assertEquals(null, p.note)
        assertFalse(p.below)
        assertEquals(FillLook.NUMERAL_SMALL.first(), p.numeral, 0f)
    }

    @Test fun `a Large keeps its figures and its chart over the liquid at the usual font`() {
        val p = fillPlan(frame(WidgetSizes.Large, 280f, 260f, 1f, null), "Recovery", "This morning", "Ready", figures, 60f)
        assertTrue(p.below)
        assertEquals(figures, p.rows.mapNotNull { it.line })
        assertEquals("Ready", p.rows.first().status)
        assertEquals(FillLook.NUMERAL_LARGE.first(), p.numeral, 0f)
        // The liquid's room starts under the chart.
        assertTrue(p.fillTop > FillLook.TOP_DP + 60f)
    }

    @Test fun `without a chart the liquid starts right under the lines`() {
        val p = fillPlan(frame(WidgetSizes.Small, 150f, 150f, 1f, null), "Energy", null, "left", emptyList(), null)
        val expected = FillLook.TOP_DP + WidgetText.line(RingWidget.TITLE_SIZES.first()) +
            FillLook.ROW_GAP_DP + WidgetText.line(FillLook.STATUS_SP) + FillLook.FILL_GAP_DP
        assertEquals(expected, p.fillTop, 1e-3f)
    }
}
