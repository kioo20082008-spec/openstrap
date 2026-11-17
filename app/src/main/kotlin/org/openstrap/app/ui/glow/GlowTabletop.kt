package org.openstrap.app.ui.glow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.sp
import org.openstrap.app.ui.theme.NumberFamily
import org.openstrap.app.ui.theme.AppColors
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.openstrap.app.R
import org.openstrap.app.ui.dashboard.DayUiState
import org.openstrap.app.ui.theme.MetricColors

/**
 * The top half of a foldable standing half-open on a table: your heart rate, big enough to read from across
 * the room, beating at the latest reading, with the day's trace under it. The app carries on in the bottom half.
 * [liveEnabled] null hides the Live switch (no strap connected).
 */
@Composable
fun GlowTabletopHeart(
    state: DayUiState,
    live: Int?,
    liveEnabled: Boolean?,
    onLiveToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val now = live ?: state.latestHeartRate
    // The rings theme: one flat solid card, the number bold, no glow.
    val hue = MetricColors.heart
    Column(modifier.statusBarsPadding().padding(horizontal = GlowLayout.Gutter, vertical = 12.dp)) {
        Column(Modifier.fillMaxSize().clip(TabletopCard).background(AppColors.glass, TabletopCard).padding(horizontal = 20.dp, vertical = 18.dp)) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                GlowHeader(
                    stringResource(R.string.glow_tabletop_heart_rate),
                    trailing = if (live == null && liveEnabled == null) stringResource(R.string.glow_tabletop_latest) else null,
                    trailingContent = liveEnabled?.let { on ->
                        {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(R.string.glow_tabletop_live), color = AppColors.ink.copy(alpha = 0.85f),
                                    style = GlowType.secondary,
                                )
                                Spacer(Modifier.width(8.dp))
                                GlowSwitch(on, onLiveToggle, accent = hue)
                            }
                        }
                    },
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(now?.toString() ?: "-", color = AppColors.ink, style = TabletopNumber, maxLines = 1)
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(
                            stringResource(R.string.glow_unit_bpm), color = AppColors.readable(hue), style = GlowType.cardTitle,
                            maxLines = 1,
                        )
                    }
                    state.restingHeartRate?.let {
                        Text(
                            stringResource(R.string.glow_tabletop_resting, it), color = AppColors.ink.copy(alpha = 0.85f),
                            style = GlowType.body,
                        )
                    }
                }
                if (state.heartRate.isNotEmpty()) {
                    val values = remember(state.heartRate, state.dayStart, state.dayEnd) {
                        bucket(state.heartRate, state.dayStart..state.dayEnd, 5 * 60)
                    }
                    val resources = LocalResources.current
                    GlowLineChart(values, height = GlowLayout.ChartHeight, labels = listOf("00", "06", "12", "18", "24")) { _, v ->
                        resources.getString(R.string.glow_tabletop_bpm, v.toInt())
                    }
                }
            }
        }
    }
}

/** The tabletop card's corners, the rings theme's 22 dp. */
private val TabletopCard = RoundedCornerShape(22.dp)

/** The heart rate across the room: bold, left to right, tabular. */
private val TabletopNumber
    get() = TextStyle(
        textDirection = TextDirection.Ltr, fontFamily = NumberFamily, fontWeight = FontWeight.ExtraBold, fontSize = 112.sp,
        lineHeight = 112.sp, fontFeatureSettings = "tnum",
    )
