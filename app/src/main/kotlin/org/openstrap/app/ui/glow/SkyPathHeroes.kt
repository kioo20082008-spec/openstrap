package org.openstrap.app.ui.glow

import androidx.compose.ui.graphics.Color

/** One sleep stage's line on the Sleep hero: its name, its share of the longest stage, its time, its colour. */
@androidx.compose.runtime.Immutable
internal data class StageLine(val name: String, val share: Float, val time: String, val color: Color)
